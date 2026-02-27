package com.example.funder.ui.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.local.FundHoldingEntity
import com.example.funder.data.remote.FundDetailDto
import com.example.funder.data.remote.FundValuationDto
import com.example.funder.data.remote.NavHistoryItem
import com.example.funder.data.remote.StockHolding
import com.example.funder.data.repository.FundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DetailUiState(
    val fundCode: String = "",
    val fundName: String = "",
    val holding: FundHoldingEntity? = null,
    val valuation: FundValuationDto? = null,
    val detail: FundDetailDto? = null,
    val navHistory: List<NavHistoryItem> = emptyList(),
    // 带实时价格的股票持仓
    val stockHoldings: List<StockHolding> = emptyList(),
    val isLoadingValuation: Boolean = true,
    val isLoadingDetail: Boolean = true,
    val isLoadingHistory: Boolean = true,
    val error: String? = null
) {
    val estimatedNav: String get() = valuation?.estimatedNav ?: "--"
    val estimatedGrowth: String get() = valuation?.estimatedGrowth ?: "--"
    val lastNav: String get() = valuation?.nav ?: "--"
    val lastNavDate: String get() = valuation?.navDate ?: "--"
    val estimateTime: String get() = valuation?.estimateTime ?: ""

    val holdingValue: Double?
        get() {
            val h = holding ?: return null
            val gsz = valuation?.estimatedNav?.toDoubleOrNull() ?: return null
            return h.shares * gsz
        }

    val todayProfit: Double?
        get() {
            val h = holding ?: return null
            val gsz = valuation?.estimatedNav?.toDoubleOrNull() ?: return null
            val dwjz = valuation?.nav?.toDoubleOrNull() ?: return null
            return h.shares * (gsz - dwjz)
        }

    val totalProfit: Double?
        get() {
            val h = holding ?: return null
            val value = holdingValue ?: return null
            return value - h.totalCost
        }
}

@HiltViewModel
class FundDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FundRepository
) : ViewModel() {

    private val fundCode: String = savedStateHandle["fundCode"] ?: ""

    private val _uiState = MutableStateFlow(DetailUiState(fundCode = fundCode))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        loadAll()
    }

    private fun loadAll() {
        loadHolding()
        loadValuation()
        loadDetail()
        loadNavHistory()
        startAutoRefresh()
    }

    fun refresh() {
        loadValuation()
    }

    private fun loadHolding() {
        viewModelScope.launch {
            val holding = repository.getHolding(fundCode)
            _uiState.update { it.copy(holding = holding) }
        }
    }

    private fun loadValuation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingValuation = true) }
            try {
                val valuation = repository.getValuation(fundCode)
                _uiState.update {
                    it.copy(
                        valuation = valuation,
                        fundName = valuation?.name ?: it.fundName,
                        isLoadingValuation = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingValuation = false, error = e.message) }
            }
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true) }
            try {
                val detail = repository.getFundDetail(fundCode)
                _uiState.update {
                    it.copy(
                        detail = detail,
                        fundName = if (it.fundName.isEmpty()) detail?.name ?: "" else it.fundName,
                        isLoadingDetail = false
                    )
                }
                // 从HTML API加载详细持仓
                loadDetailedHoldings()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDetail = false, error = e.message) }
            }
        }
    }

    private fun loadDetailedHoldings() {
        viewModelScope.launch {
            try {
                // 获取带名称和占比的详细持仓
                val (date, holdings) = repository.getDetailedHoldings(fundCode)
                
                // 获取基本信息以补充详情
                val basicInfo = repository.getFundBasicInfo(fundCode)
                
                if (holdings.isNotEmpty() || basicInfo.isNotEmpty()) {
                    _uiState.update { state ->
                        val currentDetail = state.detail
                        val updatedDetail = currentDetail?.copy(
                            holdingsDate = date.ifEmpty { currentDetail.holdingsDate },
                            type = basicInfo["type"]?.ifEmpty { currentDetail.type } ?: currentDetail.type,
                            managementCompany = basicInfo["company"]?.ifEmpty { currentDetail.managementCompany } ?: currentDetail.managementCompany,
                            fundManager = basicInfo["manager"]?.ifEmpty { currentDetail.fundManager } ?: currentDetail.fundManager,
                            establishDate = basicInfo["establishDate"]?.ifEmpty { currentDetail.establishDate } ?: currentDetail.establishDate,
                            fundScale = basicInfo["scale"]?.ifEmpty { currentDetail.fundScale } ?: currentDetail.fundScale
                        )
                        state.copy(detail = updatedDetail)
                    }
                    
                    // 如果有持仓，获取股票价格
                    if (holdings.isNotEmpty()) {
                        loadStockPrices(holdings)
                    }
                }
            } catch (e: Exception) {
                Log.e("FundDetailViewModel", "Error loading detailed holdings: ${e.message}", e)
                // 如果可用，回退到详情中的topHoldings
                _uiState.value.detail?.topHoldings?.let { holdings ->
                    if (holdings.isNotEmpty()) {
                        loadStockPrices(holdings)
                    }
                }
            }
        }
    }

    private fun loadStockPrices(holdings: List<StockHolding>) {
        viewModelScope.launch {
            try {
                val codes = holdings.map { it.code }
                val prices = repository.getStockPrices(codes)
                val enriched = holdings.map { h ->
                    val (price, change) = prices[h.code] ?: ("--" to "0")
                    h.copy(price = price, change = change)
                }
                _uiState.update { it.copy(stockHoldings = enriched) }
            } catch (e: Exception) {
                // 回退：显示不带价格的持仓
                _uiState.update { it.copy(stockHoldings = holdings) }
            }
        }
    }

    private fun loadNavHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true) }
            try {
                val history = repository.getNavHistory(fundCode, page = 1, perPage = 30)
                _uiState.update { it.copy(navHistory = history, isLoadingHistory = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingHistory = false, error = e.message) }
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (isMarketOpen()) loadValuation()
            }
        }
    }

    private fun isMarketOpen(): Boolean {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return mins in (9 * 60 + 30)..(15 * 60)
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
