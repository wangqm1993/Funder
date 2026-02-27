package com.example.funder.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "funder_settings")

/**
 * 预定义的刷新间隔选项（单位：秒）。
 */
enum class RefreshInterval(val seconds: Int, val label: String) {
    SECONDS_10(10, "10秒"),
    SECONDS_15(15, "15秒"),
    SECONDS_30(30, "30秒"),
    SECONDS_60(60, "1分钟"),
    SECONDS_120(120, "2分钟"),
    SECONDS_300(300, "5分钟");

    companion object {
        fun fromSeconds(s: Int): RefreshInterval =
            entries.find { it.seconds == s } ?: SECONDS_30
    }
}

/**
 * 主题模式选项
 */
enum class ThemeMode(val value: String, val label: String) {
    LIGHT("light", "浅色模式"),
    DARK("dark", "暗黑模式"),
    SYSTEM("system", "跟随系统");

    companion object {
        fun fromValue(value: String): ThemeMode =
            entries.find { it.value == value } ?: SYSTEM
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval_seconds")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_CARD_CORNER_RADIUS = floatPreferencesKey("card_corner_radius")
        private val KEY_SETTLEMENT_NOTIFY = booleanPreferencesKey("settlement_notification_enabled")
        private val KEY_LAST_NOTIFIED_DATE = stringPreferencesKey("last_settlement_notified_date")
        const val DEFAULT_REFRESH_SECONDS = 30
        const val DEFAULT_CARD_CORNER_RADIUS = 16f
    }

    /**
     * 以 Flow 形式观察刷新间隔设置（单位：秒）。
     */
    val refreshIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_REFRESH_INTERVAL] ?: DEFAULT_REFRESH_SECONDS
    }

    /**
     * 以 Flow 形式观察主题模式设置。
     */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val value = prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.value
        ThemeMode.fromValue(value)
    }

    /**
     * 更新刷新间隔。
     */
    suspend fun setRefreshInterval(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REFRESH_INTERVAL] = seconds
        }
    }

    /**
     * 更新主题模式。
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.value
        }
    }

    /**
     * 以 Flow 形式观察卡片圆角半径设置（单位：dp）。
     */
    val cardCornerRadius: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_CARD_CORNER_RADIUS] ?: DEFAULT_CARD_CORNER_RADIUS
    }

    /**
     * 更新卡片圆角半径。
     */
    suspend fun setCardCornerRadius(radius: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CARD_CORNER_RADIUS] = radius
        }
    }

    val settlementNotificationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETTLEMENT_NOTIFY] ?: true
    }

    suspend fun setSettlementNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SETTLEMENT_NOTIFY] = enabled
        }
    }

    val lastSettlementNotifiedDate: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_NOTIFIED_DATE] ?: ""
    }

    suspend fun setLastSettlementNotifiedDate(date: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_NOTIFIED_DATE] = date
        }
    }

    suspend fun getLastSettlementNotifiedDateSync(): String {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_LAST_NOTIFIED_DATE] ?: ""
        }.first()
    }

    suspend fun isSettlementNotificationEnabledSync(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_SETTLEMENT_NOTIFY] ?: true
        }.first()
    }
}
