package com.example.funder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.funder.data.repository.SettingsRepository
import com.example.funder.data.repository.ThemeMode
import com.example.funder.ui.navigation.FunderNavGraph
import com.example.funder.ui.theme.FunderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 同步读取 DataStore 初始值，防止第一帧因使用 SYSTEM 默认值而闪现错误主题
        val initialThemeMode = runBlocking { settingsRepository.themeMode.first() }
        val initialCardRadius = runBlocking { settingsRepository.cardCornerRadius.first() }

        setContent {
            val cardCornerRadius by settingsRepository.cardCornerRadius.collectAsState(
                initial = initialCardRadius
            )
            val themeMode by settingsRepository.themeMode.collectAsState(
                initial = initialThemeMode
            )
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            
            FunderTheme(
                darkTheme = darkTheme,
                cardCornerRadius = cardCornerRadius.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FunderNavGraph()
                }
            }
        }
    }
}
