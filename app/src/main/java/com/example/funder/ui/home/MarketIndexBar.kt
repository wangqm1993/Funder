package com.example.funder.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.funder.data.remote.MarketIndexDto
import com.example.funder.ui.theme.LocalIsDarkTheme
import com.example.funder.ui.theme.LossGreenOnLight
import com.example.funder.ui.theme.ProfitRed

/** 涨跌颜色（中国习惯：红涨绿跌） */
@Composable
private fun indexColor(isUp: Boolean, isDark: Boolean): Color = when {
    isUp -> ProfitRed
    isDark -> Color(0xFF00C853)
    else -> LossGreenOnLight
}

/**
 * 底部大盘指数条。
 *  - 折叠态：一行紧凑显示选中指数 + 展开箭头
 *  - 展开态：所有指数排成一排（横向滚动），点击选中并收起
 */
@Composable
fun MarketIndexBar(
    indices: List<MarketIndexDto>,
    modifier: Modifier = Modifier
) {
    if (indices.isEmpty()) return

    val isDark = LocalIsDarkTheme.current
    var expanded by remember { mutableStateOf(false) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    val current = indices.getOrElse(selectedIdx) { indices.first() }

    Column(modifier = modifier.fillMaxWidth()) {

        // ── 展开态：所有指数横向一排 ──
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(200),
                expandFrom = Alignment.Bottom
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(
                animationSpec = tween(160),
                shrinkTowards = Alignment.Bottom
            ) + fadeOut(tween(120))
        ) {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    indices.forEachIndexed { i, index ->
                        IndexCard(
                            index = index,
                            isDark = isDark,
                            isSelected = i == selectedIdx,
                            onClick = {
                                selectedIdx = i
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )

        // ── 折叠态顶部条 ──
        val color = indexColor(current.isUp, isDark)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = current.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 72.dp)
            )
            Text(
                text = formatIndexPrice(current.price),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
            Text(
                text = "${if (current.isUp) "+" else ""}${"%.2f".format(current.change)}",
                fontSize = 11.sp,
                color = color,
                maxLines = 1
            )
            Text(
                text = "${if (current.isUp) "+" else ""}${"%.2f".format(current.changePercent)}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                              else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IndexCard(
    index: MarketIndexDto,
    isDark: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = indexColor(index.isUp, isDark)
    val bgColor = color.copy(alpha = if (isSelected) 0.14f else 0.07f)

    Column(
        modifier = Modifier
            .width(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = index.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatIndexPrice(index.price),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Text(
            text = "${if (index.isUp) "+" else ""}${"%.2f".format(index.changePercent)}%",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

private fun formatIndexPrice(price: Double): String = "%.2f".format(price)
