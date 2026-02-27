package com.example.funder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.local.FundHoldingEntity
import com.example.funder.data.remote.FundValuationDto
import com.example.funder.data.repository.FundRepository
import com.example.funder.data.repository.RefreshInterval
import com.example.funder.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * 结算确认数据（来自历史净值 API）。
 * 只在当日净值已结算时有值。
 */
data class SettledNavInfo(
    val navDate: String,
    val confirmedNav: Double,   // 今日确认净值
    val previousNav: Double     // 昨日确认净值（用于计算真实当日收益）
)

data class FundHoldingWithValuation(
    val holding: FundHoldingEntity,
    val valuation: FundValuationDto? = null,
    val settledNavInfo: SettledNavInfo? = null  // 非 null 表示今日已结算
) {
    /** 估算当前价值 */
    val estimatedValue: Double
        get() {
            // 已结算：使用历史 API 的确认净值，最准确
            if (settledNavInfo != null) {
                return holding.shares * settledNavInfo.confirmedNav
            }
            // 交易中：优先实时估值
            val gsz = valuation?.estimatedNav?.toDoubleOrNull()
            if (gsz != null && gsz > 0) return holding.shares * gsz
            // Fallback：上一日确认净值
            val dwjz = valuation?.nav?.toDoubleOrNull()
            if (dwjz != null && dwjz > 0) return holding.shares * dwjz
            return holding.totalCost
        }

    /** 今日收益 */
    val todayProfit: Double
        get() {
            // 已结算：用今日确认净值 - 昨日确认净值，数据最准确
            if (settledNavInfo != null) {
                return holding.shares * (settledNavInfo.confirmedNav - settledNavInfo.previousNav)
            }
            // 交易中：用实时估值 - 上一日确认净值
            val gsz = valuation?.estimatedNav?.toDoubleOrNull() ?: return 0.0
            val dwjz = valuation?.nav?.toDoubleOrNull() ?: return 0.0
            if (gsz <= 0) return 0.0
            return holding.shares * (gsz - dwjz)
        }

    /** 总收益 = 当前市值 - 总成本 */
    val totalProfit: Double
        get() {
            val currentNav = settledNavInfo?.confirmedNav
                ?: valuation?.estimatedNav?.toDoubleOrNull()?.takeIf { it > 0 }
                ?: valuation?.nav?.toDoubleOrNull()
                ?: return 0.0
            if (currentNav <= 0) return 0.0
            return holding.shares * currentNav - holding.totalCost
        }

    /** 当前显示的净值（已结算显示确认净值，否则显示估算净值） */
    val displayNav: Double
        get() = settledNavInfo?.confirmedNav
            ?: valuation?.estimatedNav?.toDoubleOrNull()?.takeIf { it > 0 }
            ?: valuation?.nav?.toDoubleOrNull()
            ?: 0.0

    /** 当日涨跌幅（已结算用确认数据，否则用估算） */
    val growthRate: Double
        get() {
            if (settledNavInfo != null && settledNavInfo.previousNav > 0) {
                return (settledNavInfo.confirmedNav - settledNavInfo.previousNav) /
                    settledNavInfo.previousNav * 100
            }
            return valuation?.estimatedGrowth?.toDoubleOrNull() ?: 0.0
        }

    val isSettled: Boolean get() = settledNavInfo != null
}

data class HomeUiState(
    val holdings: List<FundHoldingWithValuation> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastUpdateTime: String = "",
    val refreshIntervalSeconds: Int = SettingsRepository.DEFAULT_REFRESH_SECONDS,
    val showSettingsSheet: Boolean = false,
    val error: String? = null
) {
    val totalAssets: Double
        get() = holdings.sumOf { it.estimatedValue }

    val todayTotalProfit: Double
        get() = holdings.sumOf { it.todayProfit }

    val totalProfit: Double
        get() = holdings.sumOf { it.totalProfit }

    val totalCost: Double
        get() = holdings.sumOf { it.holding.totalCost }

    val totalProfitRate: Double
        get() = if (totalCost > 0) totalProfit / totalCost * 100 else 0.0

    /** 全部持仓是否已结算 */
    val isAllSettled: Boolean
        get() = holdings.isNotEmpty() && holdings.all { it.isSettled }

    /** 部分持仓已结算 */
    val hasAnySettled: Boolean
        get() = holdings.any { it.isSettled }

    val settledCount: Int
        get() = holdings.count { it.isSettled }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FundRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var latestHoldings: List<FundHoldingEntity> = emptyList()
    private var autoRefreshJob: Job? = null
    /** 已确认的结算信息缓存，key=基金代码 */
    private var settledNavCache: Map<String, SettledNavInfo> = emptyMap()
    /** settledNavCache 对应的日期，跨天时自动清除 */
    private var settledNavDay: String = ""
    /** 标记是否正在处理排序写库，期间跳过 getAllHoldings Flow 的重新刷新 */
    @Volatile private var sortingInProgress = false

    init {
        viewModelScope.launch {
            settingsRepository.refreshIntervalSeconds.collect { seconds ->
                _uiState.update { it.copy(refreshIntervalSeconds = seconds) }
                startAutoRefresh()
            }
        }

        viewModelScope.launch {
            var isFirst = true
            repository.getAllHoldings().collect { holdingList ->
                latestHoldings = holdingList
                if (isFirst) {
                    isFirst = false
                    loadValuationsFirstTime(holdingList)
                    startAutoRefresh()
                } else if (!sortingInProgress) {
                    // 跳过仅因排序写库触发的 Flow emit，避免重新 fetch 导致结算状态丢失
                    refreshValuationsQuietly(holdingList)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshValuationsQuietly(latestHoldings)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun deleteHolding(fundCode: String) {
        viewModelScope.launch {
            repository.deleteHolding(fundCode)
        }
    }

    fun updateHolding(fundCode: String, shares: Double, costPrice: Double) {
        viewModelScope.launch {
            val existing = repository.getHolding(fundCode)
            if (existing != null) {
                val updated = existing.copy(
                    shares = shares,
                    costPrice = costPrice,
                    totalCost = shares * costPrice
                )
                repository.updateHolding(updated)
            }
        }
    }

    fun updateHoldingsOrder(orderedList: List<FundHoldingWithValuation>) {
        _uiState.update { it.copy(holdings = orderedList) }
        viewModelScope.launch {
            sortingInProgress = true
            try {
                val pairs = orderedList.mapIndexed { index, h -> h.holding.fundCode to index }
                repository.updateSortOrders(pairs)
            } finally {
                sortingInProgress = false
            }
        }
    }

    // ---- 设置 ----

    fun showSettings() {
        _uiState.update { it.copy(showSettingsSheet = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setRefreshInterval(seconds)
            // uiState 将通过上面的 collect 自动更新
        }
    }

    private suspend fun loadValuationsFirstTime(holdingList: List<FundHoldingEntity>) {
        if (holdingList.isEmpty()) {
            _uiState.update { it.copy(holdings = emptyList(), isLoading = false) }
            return
        }

        try {
            val codes = holdingList.map { it.fundCode }
            val valuations = repository.getValuations(codes)
            // 首次加载也检查结算状态（可能是收盘后打开 app）
            settledNavCache = fetchSettledNavInfo(codes)
            val holdingsWithValuation = holdingList.map { holding ->
                FundHoldingWithValuation(
                    holding = holding,
                    valuation = valuations[holding.fundCode],
                    settledNavInfo = settledNavCache[holding.fundCode]
                )
            }
            val lastTime = valuations.values.firstOrNull()?.estimateTime ?: ""
            _uiState.update {
                it.copy(holdings = holdingsWithValuation, isLoading = false,
                    lastUpdateTime = lastTime, error = null)
            }
        } catch (e: Exception) {
            val fallback = holdingList.map { FundHoldingWithValuation(holding = it) }
            _uiState.update { it.copy(holdings = fallback, isLoading = false,
                error = "获取估值失败: ${e.message}") }
        }
    }

    /**
     * 静默刷新估值数据（交易时间内调用）。
     * 只更新 fundgz 实时估值，保持结算状态不变。
     */
    private suspend fun refreshValuationsQuietly(holdingList: List<FundHoldingEntity>) {
        if (holdingList.isEmpty()) {
            _uiState.update { it.copy(holdings = emptyList(), error = null) }
            return
        }
        try {
            val codes = holdingList.map { it.fundCode }
            val valuations = repository.getValuations(codes)
            val holdingsWithValuation = holdingList.map { holding ->
                FundHoldingWithValuation(
                    holding = holding,
                    valuation = valuations[holding.fundCode],
                    settledNavInfo = settledNavCache[holding.fundCode]   // 保持结算状态
                )
            }
            val lastTime = valuations.values.firstOrNull()?.estimateTime ?: ""
            _uiState.update {
                it.copy(holdings = holdingsWithValuation, lastUpdateTime = lastTime, error = null)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "获取估值失败: ${e.message}") }
        }
    }

    /**
     * 仅刷新结算状态（收盘后调用）。
     * 从历史净值 API 获取今日和昨日净值，计算真实收益。
     */
    private suspend fun refreshSettlementOnly(holdingList: List<FundHoldingEntity>) {
        if (holdingList.isEmpty()) return
        try {
            val fresh = fetchSettledNavInfo(holdingList.map { it.fundCode })
            if (fresh.isEmpty()) return
            // 合并：新数据优先，缓存保底（防单次 API 失败清除已结算状态）
            settledNavCache = settledNavCache + fresh
            val updated = _uiState.value.holdings.map { item ->
                item.copy(settledNavInfo = settledNavCache[item.holding.fundCode])
            }
            _uiState.update { it.copy(holdings = updated) }
        } catch (_: Exception) { }
    }

    /**
     * 并发拉取各基金历史净值（今日+昨日），构建 SettledNavInfo。
     * 只返回今日已结算的基金；自动在新一天清除缓存。
     */
    private suspend fun fetchSettledNavInfo(codes: List<String>): Map<String, SettledNavInfo> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Calendar.getInstance().time)
        if (settledNavDay != today) {
            settledNavCache = emptyMap()
            settledNavDay = today
        }
        return supervisorScope {
            codes.map { code ->
                async {
                    val history = repository.getNavHistory(code, page = 1, perPage = 2)
                    val todayEntry = history.firstOrNull() ?: return@async null
                    if (todayEntry.date != today) return@async null  // 今日净值未结算
                    val prevEntry = history.getOrNull(1) ?: return@async null
                    code to SettledNavInfo(
                        navDate = todayEntry.date,
                        confirmedNav = todayEntry.nav,
                        previousNav = prevEntry.nav
                    )
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val marketOpen = isMarketOpen()
                val delayMs = if (marketOpen) {
                    _uiState.value.refreshIntervalSeconds * 1000L
                } else {
                    60_000L  // 非交易时间每分钟检查一次结算状态
                }
                delay(delayMs)
                when {
                    isMarketOpen() -> {
                        // 交易时间：只刷新估值，不查结算（结算还没发生）
                        refreshValuationsQuietly(latestHoldings)
                    }
                    isSettlementWindow() && !_uiState.value.isAllSettled -> {
                        // 结算窗口（15:00~23:00）：只查结算状态
                        refreshSettlementOnly(latestHoldings)
                    }
                }
            }
        }
    }

    private fun isMarketOpen(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return currentMinutes in (9 * 60 + 30)..(15 * 60)
    }

    /** 结算窗口：交易日 15:00 ~ 23:00 */
    private fun isSettlementWindow(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour in 15..22
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
