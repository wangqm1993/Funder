package com.example.funder.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.local.FundHoldingEntity
import com.example.funder.data.repository.FundRepository
import com.example.funder.data.repository.SettingsRepository
import com.example.funder.data.repository.ThemeMode
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 备份文件的 JSON 结构。 */
data class FunderBackup(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("exportTime") val exportTime: String = "",
    @SerializedName("holdings") val holdings: List<FundHoldingEntity> = emptyList()
)

sealed class BackupStatus {
    data object Idle : BackupStatus()
    data object Working : BackupStatus()
    data class Success(val message: String) : BackupStatus()
    data class Error(val message: String) : BackupStatus()
}

data class SettingsUiState(
    val refreshInterval: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cardCornerRadius: Float = SettingsRepository.DEFAULT_CARD_CORNER_RADIUS,
    val settlementNotificationEnabled: Boolean = true,
    val backupStatus: BackupStatus = BackupStatus.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val fundRepository: FundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val gson = GsonBuilder().setPrettyPrinting().create()

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
        viewModelScope.launch { settingsRepository.setRefreshInterval(seconds) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCardCornerRadius(radius: Float) {
        viewModelScope.launch { settingsRepository.setCardCornerRadius(radius) }
    }

    fun setSettlementNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSettlementNotificationEnabled(enabled) }
    }

    // ---- 备份 / 恢复 ----

    /** 把持仓导出为 JSON 文件，并弹出系统分享菜单。返回是否成功。 */
    fun exportHoldings(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = BackupStatus.Working) }
            try {
                val holdings = fundRepository.getAllHoldings().first()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backup = FunderBackup(
                    exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    holdings = holdings
                )
                val json = gson.toJson(backup)

                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = File(dir, "funder_backup_$stamp.json")
                file.writeText(json)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "基金宝持仓备份 $stamp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "导出持仓备份").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                _uiState.update {
                    it.copy(backupStatus = BackupStatus.Success("已导出 ${holdings.size} 条持仓"))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(backupStatus = BackupStatus.Error("导出失败：${e.message}"))
                }
            }
        }
    }

    /** 从用户选择的 URI 读取 JSON，合并导入持仓。 */
    fun importHoldings(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = BackupStatus.Working) }
            try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: throw IllegalStateException("无法读取文件")

                val backup = gson.fromJson(json, FunderBackup::class.java)
                    ?: throw IllegalStateException("文件格式无效")

                if (backup.holdings.isEmpty()) {
                    _uiState.update {
                        it.copy(backupStatus = BackupStatus.Error("备份文件中没有持仓数据"))
                    }
                    return@launch
                }

                // 逐条合并（已存在的跳过，不覆盖）
                var imported = 0
                for (holding in backup.holdings) {
                    if (fundRepository.getHolding(holding.fundCode) == null) {
                        fundRepository.addHolding(holding)
                        imported++
                    }
                }
                _uiState.update {
                    it.copy(
                        backupStatus = BackupStatus.Success(
                            if (imported > 0) "已导入 $imported 条持仓"
                            else "备份中的 ${backup.holdings.size} 条持仓已全部存在，无需导入"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(backupStatus = BackupStatus.Error("导入失败：${e.message}"))
                }
            }
        }
    }

    fun clearBackupStatus() {
        _uiState.update { it.copy(backupStatus = BackupStatus.Idle) }
    }
}
