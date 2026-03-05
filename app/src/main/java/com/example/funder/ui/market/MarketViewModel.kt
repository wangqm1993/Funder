package com.example.funder.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.remote.MarketIndexDto
import com.example.funder.data.remote.SectorDto
import com.example.funder.data.repository.FundRepository
import com.example.funder.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketUiState(
    val indices: List<MarketIndexDto> = emptyList(),
    val gainers: List<SectorDto> = emptyList(),
    val losers: List<SectorDto> = emptyList(),
    val allSectors: List<SectorDto> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastUpdateTime: String = ""
)

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: FundRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch { fetchAll(initial = true) }
        viewModelScope.launch {
            settingsRepository.refreshIntervalSeconds.collect { interval ->
                restartAutoRefresh(interval.toLong())
            }
        }
    }

    private fun restartAutoRefresh(intervalSeconds: Long) {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(intervalSeconds * 1000L)
                fetchAll()
            }
        }
    }

    fun refresh() { viewModelScope.launch { fetchAll() } }

    private suspend fun fetchAll(initial: Boolean = false) {
        if (!initial) _uiState.update { it.copy(isRefreshing = true) }
        // 两个 API 相互独立，任意一个失败不影响另一个
        val indices = runCatching { repository.getMarketIndices() }.getOrElse { emptyList() }
        val sectors = runCatching { repository.getSectors() }.getOrElse { emptyList() }
        val gainers = sectors.filter { it.changePercent > 0 }.take(10)
        val losers  = sectors.filter { it.changePercent < 0 }
            .sortedBy { it.changePercent }.take(10)
        val now = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        _uiState.update { state ->
            state.copy(
                indices       = if (indices.isNotEmpty()) indices else state.indices,
                gainers       = if (sectors.isNotEmpty()) gainers else state.gainers,
                losers        = if (sectors.isNotEmpty()) losers  else state.losers,
                allSectors    = if (sectors.isNotEmpty()) sectors else state.allSectors,
                isLoading     = false,
                isRefreshing  = false,
                lastUpdateTime = now
            )
        }
    }
}
