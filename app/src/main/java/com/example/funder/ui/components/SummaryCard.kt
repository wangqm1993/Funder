package com.example.funder.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.funder.ui.theme.cardShape
import com.example.funder.ui.theme.*

@Composable
fun SummaryCard(
    totalAssets: Double,
    todayProfit: Double,
    totalProfit: Double,
    totalProfitRate: Double,
    lastUpdateTime: String,
    isAllSettled: Boolean = false,
    hasAnySettled: Boolean = false,
    settledCount: Int = 0,
    totalCount: Int = 0,
    modifier: Modifier = Modifier
) {
    // 进入动画
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    // 渐变动画
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )

    val isDarkTheme = LocalIsDarkTheme.current
    
    val gradientColors = if (isDarkTheme) {
        // 暗黑模式 - 使用深色渐变
        listOf(
            Color(0xFF1E3A5F),
            Color(0xFF2A4A70),
            Color(0xFF1565C0)
        )
    } else {
        // 浅色模式 - 使用亮色渐变
        listOf(
            Color(0xFF1565C0),
            Color(0xFF2979FF),
            Color(0xFF448AFF)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = gradientColors,
                        start = androidx.compose.ui.geometry.Offset(
                            gradientOffset * 1000f,
                            gradientOffset * 1000f
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            (1 - gradientOffset) * 1000f,
                            (1 - gradientOffset) * 1000f
                        )
                    )
                )
        ) {
            // Frosted glass decorative orbs
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .offset(
                        x = (-35 + gradientOffset * 10).dp,
                        y = (-40 + gradientOffset * 8).dp
                    )
                    .blur(60.dp)
                    .background(Color.White.copy(alpha = 0.13f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomEnd)
                    .offset(
                        x = (30 - gradientOffset * 8).dp,
                        y = (25 - gradientOffset * 6).dp
                    )
                    .blur(50.dp)
                    .background(Color.White.copy(alpha = 0.09f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .align(Alignment.CenterEnd)
                    .offset(
                        x = (-20 + gradientOffset * 5).dp,
                        y = (-20 + gradientOffset * 4).dp
                    )
                    .blur(40.dp)
                    .background(PrimaryLight.copy(alpha = 0.10f), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "总资产 (估)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = totalAssets,
                    transitionSpec = {
                        slideInVertically { it } + fadeIn() togetherWith
                                slideOutVertically { -it } + fadeOut()
                    },
                    label = "totalAssets"
                ) { amount ->
                    Text(
                        text = "¥${formatMoney(amount)}",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp
                        ),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundAlpha = 0.08f,
                    borderAlpha = 0.15f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ProfitItem(
                            label = "今日收益",
                            value = todayProfit,
                            modifier = Modifier.weight(0.9f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        ProfitItem(
                            label = "累计收益",
                            value = totalProfit,
                            rate = totalProfitRate,
                            modifier = Modifier.weight(1.1f),
                            alignment = Alignment.End
                        )
                    }
                }

                if (lastUpdateTime.isNotEmpty() || hasAnySettled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (lastUpdateTime.isNotEmpty()) {
                            Text(
                                text = "更新: $lastUpdateTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        if (hasAnySettled) {
                            GlassSurface(
                                shape = RoundedCornerShape(12.dp),
                                backgroundAlpha = if (isAllSettled) 0.2f else 0.12f,
                                borderAlpha = 0.2f
                            ) {
                                Text(
                                    text = if (isAllSettled) "✓ 已结算"
                                           else "结算中 $settledCount/$totalCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfitItem(
    label: String,
    value: Double,
    rate: Double? = null,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    val valueColor by animateColorAsState(
        targetValue = when {
            value > 0.005 -> SummaryProfitColor
            value < -0.005 -> SummaryLossColor
            else -> Color.White.copy(alpha = 0.9f)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "valueColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (value != 0.0) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = alignment
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            ),
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()).togetherWith(
                    slideOutVertically { -it / 2 } + fadeOut()
                )
            },
            label = "profitValue"
        ) { targetValue ->
            Text(
                text = formatSignedMoney(targetValue),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                ),
                color = valueColor,
                modifier = Modifier.scale(scale)
            )
        }

        if (rate != null) {
            Spacer(modifier = Modifier.height(6.dp))

            val rateTextColor = when {
                rate > 0.005 -> SummaryProfitColor
                rate < -0.005 -> SummaryLossColor
                else -> Color.White.copy(alpha = 0.7f)
            }

            AnimatedContent(
                targetState = rate,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(
                        fadeOut() + scaleOut(targetScale = 0.8f)
                    )
                },
                label = "profitRate"
            ) { targetRate ->
                GlassSurface(
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = when {
                        rate > 0.005 -> SummaryProfitColor
                        rate < -0.005 -> SummaryLossColor
                        else -> Color.White
                    },
                    backgroundAlpha = 0.25f,
                    borderAlpha = 0.2f
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (targetRate >= 0) "↑" else "↓",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = rateTextColor
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${"%.2f".format(kotlin.math.abs(targetRate))}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.sp
                            ),
                            color = rateTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

fun formatMoney(amount: Double): String {
    val formatted = "%.2f".format(amount)
    val parts = formatted.split(".")
    val intPart = parts[0].let { str ->
        val negative = str.startsWith("-")
        val digits = if (negative) str.substring(1) else str
        val withCommas = digits.reversed().chunked(3).joinToString(",").reversed()
        if (negative) "-$withCommas" else withCommas
    }
    return "$intPart.${parts[1]}"
}

fun formatSignedMoney(amount: Double): String {
    val sign = if (amount >= 0) "+" else ""
    return "$sign${formatMoney(amount)}"
}
