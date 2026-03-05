package com.example.funder.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.remote.FundSearchResultDto
import com.example.funder.data.remote.FundValuationDto
import com.example.funder.data.repository.FundRepository
import com.example.funder.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchlistItemState(
    val code: String,
    val name: String,
    val valuation: FundValuationDto? = null,
    val isHolding: Boolean = false
)

data class WatchlistUiState(
    val items: List<WatchlistItemState> = emptyList(),
    val isRefreshing: Boolean = false,
    val holdingCodes: Set<String> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<FundSearchResultDto> = emptyList(),
    val isSearching: Boolean = false
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val repository: FundRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getAllHoldings().collect { holdings ->
                val codes = holdings.map { it.fundCode }.toSet()
                _uiState.update { it.copy(holdingCodes = codes) }
            }
        }
        viewModelScope.launch {
            repository.getAllWatchlist().collect { list ->
                val current = _uiState.value
                val newItems = list.map { entity ->
                    current.items.find { it.code == entity.fundCode }
                        ?: WatchlistItemState(entity.fundCode, entity.fundName)
                }
                _uiState.update { it.copy(items = newItems) }
                fetchValuations()
            }
        }
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
                fetchValuations()
            }
        }
    }

    fun refresh() { viewModelScope.launch { fetchValuations() } }

    private suspend fun fetchValuations() {
        val codes = _uiState.value.items.map { it.code }
        if (codes.isEmpty()) return
        _uiState.update { it.copy(isRefreshing = true) }
        try {
            val valuations = repository.getValuations(codes)
            val holdingCodes = _uiState.value.holdingCodes
            _uiState.update { state ->
                state.copy(
                    items = state.items.map { item ->
                        item.copy(
                            valuation = valuations[item.code],
                            isHolding = item.code in holdingCodes
                        )
                    },
                    isRefreshing = false
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val results = repository.searchFund(query)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    fun addToWatchlist(code: String, name: String) {
        viewModelScope.launch { repository.addToWatchlist(code, name) }
    }

    fun removeFromWatchlist(code: String) {
        viewModelScope.launch { repository.removeFromWatchlist(code) }
    }
}
