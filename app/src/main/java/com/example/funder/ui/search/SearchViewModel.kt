package com.example.funder.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.local.FundHoldingEntity
import com.example.funder.data.remote.FundSearchResultDto
import com.example.funder.data.remote.FundValuationDto
import com.example.funder.data.repository.FundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<FundSearchResultDto> = emptyList(),
    val isSearching: Boolean = false,
    val addDialogFund: FundSearchResultDto? = null,
    val addShares: String = "",
    val addCost: String = "",
    val isAdding: Boolean = false,
    val addSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }

        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(results = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // 防抖
            _uiState.update { it.copy(isSearching = true) }

            try {
                // 首先尝试搜索 API
                var results = repository.searchFund(query)

                // 如果搜索 API 没有返回结果且查询是 6 位数字代码，
                // 尝试直接使用估值 API
                if (results.isEmpty() && query.length == 6 && query.all { it.isDigit() }) {
                    val valuation = repository.getValuation(query)
                    if (valuation != null && valuation.name.isNotEmpty()) {
                        results = listOf(
                            FundSearchResultDto(
                                code = valuation.fundCode,
                                name = valuation.name,
                                type = "基金"
                            )
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        results = results,
                        isSearching = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "搜索失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun showAddDialog(fund: FundSearchResultDto) {
        _uiState.update {
            it.copy(
                addDialogFund = fund,
                addShares = "",
                addCost = "",
                addSuccess = false
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update {
            it.copy(addDialogFund = null, addSuccess = false)
        }
    }

    fun onSharesChanged(shares: String) {
        _uiState.update { it.copy(addShares = shares) }
    }

    fun onCostChanged(cost: String) {
        _uiState.update { it.copy(addCost = cost) }
    }

    fun addFund() {
        val fund = _uiState.value.addDialogFund ?: return
        val shares = _uiState.value.addShares.toDoubleOrNull()
        val cost = _uiState.value.addCost.toDoubleOrNull()

        if (shares == null || shares <= 0 || cost == null || cost <= 0) {
            _uiState.update { it.copy(error = "请输入有效的份额和金额") }
            return
        }

        _uiState.update { it.copy(isAdding = true) }

        viewModelScope.launch {
            try {
                val holding = FundHoldingEntity(
                    fundCode = fund.code,
                    fundName = fund.name,
                    shares = shares,
                    totalCost = cost,
                    costPrice = cost / shares
                )
                repository.addHolding(holding)

                _uiState.update {
                    it.copy(
                        isAdding = false,
                        addSuccess = true,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        error = "添加失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(addSuccess = false) }
    }
}
