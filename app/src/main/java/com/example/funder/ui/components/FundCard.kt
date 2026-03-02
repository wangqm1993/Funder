package com.example.funder.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.funder.ui.home.FundHoldingWithValuation
import com.example.funder.ui.theme.cardShape
import com.example.funder.ui.theme.*

@Composable
fun FundCard(
    item: FundHoldingWithValuation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.33f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> Primary
                            SwipeToDismissBoxValue.EndToStart -> ErrorRed
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            ) {
                Text(
                    text = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> "编辑"
                        SwipeToDismissBoxValue.EndToStart -> "删除"
                        else -> ""
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = modifier
    ) {
        FundCardContent(item = item, onClick = onClick)
    }
}

@Composable
fun FundCardContent(
    item: FundHoldingWithValuation,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val growthRate = item.growthRate
    val isDark = LocalIsDarkTheme.current
    // 浅色模式下使用对比度更高的替代色
    val neutralColor = if (isDark) Neutral else TextSecondary
    val lossColor = if (isDark) LossGreen else LossGreenOnLight
    val profitColor by animateColorAsState(
        targetValue = when {
            growthRate > 0.001 -> ProfitRed
            growthRate < -0.001 -> lossColor
            else -> neutralColor
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "profitColor"
    )

    var visible by rememberSaveable(key = item.holding.fundCode) { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val slideOffset by animateDpAsState(
        targetValue = if (visible) 0.dp else 30.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "slide"
    )

    val accentColor = when {
        growthRate > 0.001 -> ProfitRed
        growthRate < -0.001 -> if (isDark) LossGreen else LossGreenOnLight
        else -> Neutral
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = slideOffset.toPx()
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // -- Top section: Fund name + Rate badge --
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.holding.fundName.ifEmpty {
                                item.valuation?.name ?: item.holding.fundCode
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.2.sp,
                                lineHeight = 20.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.holding.fundCode,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium, fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "  ·  ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "净值 ${"%.4f".format(item.displayNav).takeIf { item.displayNav > 0 } ?: "--"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium, fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.isSettled) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isDark) SuccessGreen.copy(alpha = 0.2f)
                                            else SuccessGreenOnLight.copy(alpha = 0.12f)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "已结算",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold, fontSize = 9.sp
                                        ),
                                        color = if (isDark) SuccessGreen else SuccessGreenOnLight
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val rateBgColor = when {
                        growthRate > 0.001 -> if (isDark) ProfitRedDark else ProfitRedLight
                        growthRate < -0.001 -> if (isDark) LossGreenDark else LossGreenLight
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    AnimatedContent(
                        targetState = growthRate,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.8f))
                                .togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
                        },
                        label = "rate"
                    ) { rate ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(rateBgColor)
                                .border(
                                    0.5.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f)
                                    else Color.Black.copy(alpha = 0.04f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${if (rate >= 0) "+" else ""}${"%.2f".format(rate)}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.sp
                                ),
                                color = profitColor,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // -- Bottom section: Profit + Holding --
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "今日收益",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium, fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        AnimatedContent(
                            targetState = item.todayProfit,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()).togetherWith(
                                    slideOutVertically { -it / 2 } + fadeOut()
                                )
                            },
                            label = "profit"
                        ) { profit ->
                            Text(
                                text = formatSignedMoney(profit),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    letterSpacing = 0.3.sp
                                ),
                                color = profitColor
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "持仓市值",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium, fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val currentValue = item.estimatedValue
                        Text(
                            text = "¥${formatMoney(currentValue)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.isSettled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "✓ 净值已更新",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (isDark) SuccessGreen else SuccessGreenOnLight
                            )
                        }
                    }
                }
            }

            // Bottom accent gradient line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0f),
                                accentColor.copy(alpha = 0.6f),
                                accentColor.copy(alpha = 0.6f),
                                accentColor.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }
    }
}
