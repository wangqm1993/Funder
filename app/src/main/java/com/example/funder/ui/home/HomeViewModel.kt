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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class FundHoldingWithValuation(
    val holding: FundHoldingEntity,
    val valuation: FundValuationDto? = null
) {
    /** 估算当前价值 = 份额 * 估算净值（如果没有估算值则使用最新净值） */
    val estimatedValue: Double
        get() {
            // 优先使用实时估值，如果没有估值则使用最新净值
            val gsz = valuation?.estimatedNav?.toDoubleOrNull()
            if (gsz != null && gsz > 0) {
                return holding.shares * gsz
            }
            // 使用最新净值作为fallback
            val dwjz = valuation?.nav?.toDoubleOrNull()
            if (dwjz != null && dwjz > 0) {
                return holding.shares * dwjz
            }
            // 如果都没有，返回成本
            return holding.totalCost
        }

    /** 今日估算收益 = 估算价值 - (份额 * 最新净值) */
    val todayProfit: Double
        get() {
            val gsz = valuation?.estimatedNav?.toDoubleOrNull()
            val dwjz = valuation?.nav?.toDoubleOrNull() ?: return 0.0
            // 只有当有实时估值时才计算今日收益
            if (gsz != null && gsz > 0) {
                return holding.shares * (gsz - dwjz)
            }
            return 0.0
        }

    /** 总收益 = 当前价值 - 总成本 */
    val totalProfit: Double
        get() {
            // 使用当前市值计算累计收益
            val currentNav = valuation?.estimatedNav?.toDoubleOrNull() 
                ?: valuation?.nav?.toDoubleOrNull()
                ?: return 0.0
            
            if (currentNav <= 0) return 0.0
            
            val currentValue = holding.shares * currentNav
            return currentValue - holding.totalCost
        }

    /** 来自 API 的估算增长率 */
    val growthRate: Double
        get() = valuation?.estimatedGrowth?.toDoubleOrNull() ?: 0.0

    /** 净值是否已结算（navDate 等于今天的日期） */
    val isSettled: Boolean
        get() {
            val navDate = valuation?.navDate ?: return false
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(
                Calendar.getInstance().time
            )
            return navDate == today
        }
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

    init {
        viewModelScope.launch {
            settingsRepository.refreshIntervalSeconds.collect { seconds ->
                _uiState.update { it.copy(refreshIntervalSeconds = seconds) }
                startAutoRefresh()
            }
        }

        // 只用一个 Flow，直接从数据库读取，不经过 stateIn
        viewModelScope.launch {
            var isFirst = true
            repository.getAllHoldings().collect { holdingList ->
                latestHoldings = holdingList
                if (isFirst) {
                    isFirst = false
                    // 首次：获取估值后再关闭骨架屏
                    loadValuationsFirstTime(holdingList)
                    startAutoRefresh()
                } else {
                    // 后续变化：静默更新
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
            val pairs = orderedList.mapIndexed { index, h -> h.holding.fundCode to index }
            repository.updateSortOrders(pairs)
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

    /**
     * 首次加载：骨架屏 → 获取数据 → 直接显示结果（一步到位，不会出现中间状态）
     */
    private suspend fun loadValuationsFirstTime(holdingList: List<FundHoldingEntity>) {
        if (holdingList.isEmpty()) {
            // 数据库真的没数据，显示暂无持仓
            _uiState.update {
                it.copy(holdings = emptyList(), isLoading = false)
            }
            return
        }

        try {
            val codes = holdingList.map { it.fundCode }
            val valuations = repository.getValuations(codes)
            val holdingsWithValuation = holdingList.map { holding ->
                FundHoldingWithValuation(holding = holding, valuation = valuations[holding.fundCode])
            }
            val lastTime = valuations.values.firstOrNull()?.estimateTime ?: ""

            // 一次性更新：关闭骨架屏 + 设置数据，不会有中间状态
            _uiState.update {
                it.copy(
                    holdings = holdingsWithValuation,
                    isLoading = false,
                    lastUpdateTime = lastTime,
                    error = null
                )
            }
        } catch (e: Exception) {
            // 网络失败也要显示本地数据
            val holdingsWithoutValuation = holdingList.map { FundHoldingWithValuation(holding = it) }
            _uiState.update {
                it.copy(
                    holdings = holdingsWithoutValuation,
                    isLoading = false,
                    error = "获取估值失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 静默刷新：不影响加载状态，只更新数据
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
                FundHoldingWithValuation(holding = holding, valuation = valuations[holding.fundCode])
            }
            val lastTime = valuations.values.firstOrNull()?.estimateTime ?: ""

            _uiState.update {
                it.copy(holdings = holdingsWithValuation, lastUpdateTime = lastTime, error = null)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "获取估值失败: ${e.message}") }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val intervalMs = _uiState.value.refreshIntervalSeconds * 1000L
                delay(intervalMs)
                if (isMarketOpen()) {
                    refreshValuationsQuietly(latestHoldings)
                }
            }
        }
    }

    /**
     * 检查股票市场是否当前开放。
     * 交易时间：周一至周五，9:30 - 15:00（北京时间）
     */
    private fun isMarketOpen(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // 周末检查
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false

        val currentMinutes = hour * 60 + minute
        val marketOpen = 9 * 60 + 30   // 9:30
        val marketClose = 15 * 60       // 15:00

        return currentMinutes in marketOpen..marketClose
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
