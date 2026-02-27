package com.example.funder.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 主色调 - 鲜艳的蓝色
val Primary = Color(0xFF2979FF)
val PrimaryDark = Color(0xFF1565C0)
val PrimaryLight = Color(0xFF82B1FF)
val PrimaryContainer = Color(0xFFE3F2FD)

// 次要色 - 紫色强调
val Secondary = Color(0xFF7C4DFF)
val SecondaryContainer = Color(0xFFEDE7F6)

// 背景色
val BackgroundLight = Color(0xFFF5F7FA)
val BackgroundDark = Color(0xFF121212)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceVariantLight = Color(0xFFE7E0EC)
val SurfaceVariantDark = Color(0xFF49454F)

// 暗黑模式专用
val OnSurfaceVariantLight = Color(0xFF111111)
val OnSurfaceVariantDark = Color(0xFFF0EEF2)
val OutlineLight = Color(0xFF555555)
val OutlineDark = Color(0xFFB0ACB6)

// 股市颜色（中国惯例：红色=涨，绿色=跌）
val ProfitRed = Color(0xFFFF3D00)        // 涨 - 亮红色
val LossGreen = Color(0xFF00C853)        // 跌 - 亮绿色
val ProfitRedLight = Color(0xFFFFEBEE)   // 浅红色背景（浅色模式）
val LossGreenLight = Color(0xFFE8F5E9)   // 浅绿色背景（浅色模式）
val ProfitRedDark = Color(0xFF4E1A1A)    // 深红色背景（暗黑模式）
val LossGreenDark = Color(0xFF1A3A1A)    // 深绿色背景（暗黑模式）
val Neutral = Color(0xFF9E9E9E)          // 平 - 灰色

// 鲜艳强调色
val GoldAccent = Color(0xFFFFAB00)
val OrangeAccent = Color(0xFFFF6D00)
val PinkAccent = Color(0xFFFF4081)
val TealAccent = Color(0xFF00BFA5)
val ErrorRed = Color(0xFFFF1744)
val SuccessGreen = Color(0xFF00E676)

// 奖牌色（持仓排名）
val MedalGold = Color(0xFFFFD700)
val MedalGoldText = Color(0xFFFFAA00)
val MedalSilver = Color(0xFFC0C0C0)
val MedalSilverText = Color(0xFF888888)
val MedalBronze = Color(0xFFCD7F32)
val MedalBronzeText = Color(0xFFAA6633)

// 图表提示框
val TooltipBgLight = Color(0xE6333333)
val TooltipBgDark = Color(0xE6555555)

// SummaryCard 收益颜色（在蓝色渐变背景上使用）
val SummaryProfitColor = Color(0xFFFFE082)
val SummaryLossColor = Color(0xFF81C784)

// 文本颜色
val TextPrimary = Color(0xFF0D0D0D)
val TextSecondary = Color(0xFF757575)
val TextHint = Color(0xFFBDBDBD)

// 卡片
val CardBorder = Color(0xFFE0E0E0)
val DividerColor = Color(0xFFF0F0F0)

// 渐变
val GradientBlue = Brush.linearGradient(
    colors = listOf(Color(0xFF2979FF), Color(0xFF448AFF))
)

val GradientPurple = Brush.linearGradient(
    colors = listOf(Color(0xFF7C4DFF), Color(0xFFB388FF))
)

val GradientProfit = Brush.linearGradient(
    colors = listOf(Color(0xFFFF3D00), Color(0xFFFF6E40))
)

val GradientLoss = Brush.linearGradient(
    colors = listOf(Color(0xFF00C853), Color(0xFF69F0AE))
)

val GradientGold = Brush.linearGradient(
    colors = listOf(Color(0xFFFFAB00), Color(0xFFFFD740))
)

val GradientSunset = Brush.linearGradient(
    colors = listOf(Color(0xFFFF6D00), Color(0xFFFF9E80))
)
