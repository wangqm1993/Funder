package com.example.funder.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * 全局卡片圆角半径，通过 CompositionLocal 在整个 App 中传递
 */
val LocalCardCornerRadius = compositionLocalOf { 16.dp }
val LocalIsDarkTheme = compositionLocalOf { false }

/**
 * 获取当前卡片圆角形状的便捷属性
 */
val cardShape: RoundedCornerShape
    @Composable get() = RoundedCornerShape(LocalCardCornerRadius.current)

// 浅色主题颜色
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = SurfaceLight,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = PrimaryDark,
    secondary = Secondary,
    onSecondary = SurfaceLight,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = TextPrimary,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = ErrorRed,
    onError = SurfaceLight,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = OutlineLight
)

// 深色主题颜色（遵循 Material3 官方暗黑模式规范）
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = PrimaryDark,
    primaryContainer = Primary,
    onPrimaryContainer = PrimaryContainer,
    secondary = Secondary,
    onSecondary = SurfaceLight,
    secondaryContainer = SecondaryContainer.copy(alpha = 0.3f),
    onSecondaryContainer = SurfaceLight,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorRed,
    onError = SurfaceLight,
    onBackground = SurfaceLight,
    onSurface = SurfaceLight,
    outline = OutlineDark
)

@Composable
fun FunderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    cardCornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalCardCornerRadius provides cardCornerRadius,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
