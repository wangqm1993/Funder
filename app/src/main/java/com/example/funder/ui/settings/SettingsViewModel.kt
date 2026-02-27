package com.example.funder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.repository.SettingsRepository
import com.example.funder.data.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val refreshInterval: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cardCornerRadius: Float = SettingsRepository.DEFAULT_CARD_CORNER_RADIUS,
    val settlementNotificationEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.refreshIntervalSeconds.collect { interval ->
                _uiState.update { it.copy(refreshInterval = interval) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.cardCornerRadius.collect { radius ->
                _uiState.update { it.copy(cardCornerRadius = radius) }
            }
        }
        viewModelScope.launch {
            settingsRepository.settlementNotificationEnabled.collect { enabled ->
                _uiState.update { it.copy(settlementNotificationEnabled = enabled) }
            }
        }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setRefreshInterval(seconds)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setCardCornerRadius(radius: Float) {
        viewModelScope.launch {
            settingsRepository.setCardCornerRadius(radius)
        }
    }

    fun setSettlementNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSettlementNotificationEnabled(enabled)
        }
    }
}
