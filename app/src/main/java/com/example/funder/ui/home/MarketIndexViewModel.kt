package com.example.funder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.remote.MarketIndexDto
import com.example.funder.data.repository.FundRepository
import com.example.funder.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketIndexViewModel @Inject constructor(
    private val repository: FundRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _indices = MutableStateFlow<List<MarketIndexDto>>(emptyList())
    val indices: StateFlow<List<MarketIndexDto>> = _indices.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        // 监听刷新间隔设置，每次变化都重启定时刷新
        viewModelScope.launch {
            settingsRepository.refreshIntervalSeconds.collect { intervalSeconds ->
                restartAutoRefresh(intervalSeconds.toLong())
            }
        }
    }

    /** 立即触发一次刷新（下拉刷新时调用） */
    fun refresh() {
        viewModelScope.launch { fetchAndUpdate() }
    }

    private fun restartAutoRefresh(intervalSeconds: Long) {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            fetchAndUpdate()           // 立刻拉取一次
            while (true) {
                delay(intervalSeconds * 1000L)
                fetchAndUpdate()
            }
        }
    }

    private suspend fun fetchAndUpdate() {
        try {
            val result = repository.getMarketIndices()
            if (result.isNotEmpty()) _indices.value = result
        } catch (_: Exception) { }
    }
}
