package com.example.funder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.funder.data.remote.ChartPoint
import com.example.funder.ui.theme.LocalIsDarkTheme
import com.example.funder.ui.theme.TooltipBgDark
import com.example.funder.ui.theme.TooltipBgLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 图表上一条线的数据。 */
data class LineData(
    val label: String,
    val points: List<ChartPoint>,
    val color: Color
)

/**
 * 使用Compose Canvas绘制的折线图，支持触摸交互。
 * 点击或拖动可显示十字准线和数值提示框。
 *
 * @param lines 要绘制的数据系列。
 * @param yAxisSuffix Y轴标签的后缀（例如"%"或""）。
 * @param showPercentAxis 如果为true，将Y值视为百分比。
 * @param heightDp 图表高度（单位：dp）。
 */
@Composable
fun FundLineChart(
    lines: List<LineData>,
    modifier: Modifier = Modifier,
    yAxisSuffix: String = "",
    showPercentAxis: Boolean = false,
    heightDp: Int = 220
) {
    if (lines.isEmpty() || lines.all { it.points.isEmpty() }) return

    val isDark = LocalIsDarkTheme.current
    var selectedX by remember { mutableStateOf<Float?>(null) }

    if (lines.size > 1 || lines.first().label.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(line.color)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        line.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
        }
    }

    val allPoints = lines.flatMap { it.points }
    val minTs = allPoints.minOf { it.timestamp }
    val maxTs = allPoints.maxOf { it.timestamp }
    val allValues = allPoints.map { it.value }
    val rawMin = allValues.min()
    val rawMax = allValues.max()
    val padding = if (rawMax - rawMin < 0.0001) 0.1 else (rawMax - rawMin) * 0.1
    val yMin = rawMin - padding
    val yMax = rawMax + padding
    val tsRange = (maxTs - minTs).coerceAtLeast(1)
    val yRange = (yMax - yMin).coerceAtLeast(0.0001)

    val leftPad = 60f
    val rightPad = 16f
    val topPad = 12f
    val bottomPad = 36f
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    val sortedLines = remember(lines) {
        lines.map { it.copy(points = it.points.sortedBy { p -> p.timestamp }) }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selectedX = down.position.x
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let {
                            selectedX = it.position.x
                            it.consume()
                        }
                        pressed = event.changes.any { it.pressed }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val chartW = w - leftPad - rightPad
        val chartH = h - topPad - bottomPad

        fun xOf(ts: Long) = leftPad + (ts - minTs).toFloat() / tsRange * chartW
        fun yOf(v: Double) = topPad + ((yMax - v) / yRange * chartH).toFloat()
        fun tsOf(x: Float) = minTs + ((x - leftPad) / chartW * tsRange).toLong()

        val textPaint = android.graphics.Paint().apply {
            color = axisLabelColor.toArgb()
            textSize = 24f
            isAntiAlias = true
        }

        // 网格线和Y轴标签
        val yTicks = 5
        for (i in 0..yTicks) {
            val v = yMin + yRange * i / yTicks
            val y = yOf(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(w - rightPad, y), 1f)
            val label = if (showPercentAxis) "${"%.1f".format(v)}%" else "%.4f".format(v)
            drawContext.canvas.nativeCanvas.drawText(label, 4f, y + 8f, textPaint)
        }

        // 绘制线条
        for (line in sortedLines) {
            if (line.points.size < 2) continue
            val path = Path()
            line.points.forEachIndexed { idx, pt ->
                val x = xOf(pt.timestamp)
                val y = yOf(pt.value)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line.color, style = Stroke(width = 4f))
        }

        // X轴标签
        val xLabelCount = minOf(5, allPoints.size)
        if (xLabelCount > 1) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            for (i in 0 until xLabelCount) {
                val ts = minTs + tsRange * i / (xLabelCount - 1)
                val x = xOf(ts)
                val label = sdf.format(Date(ts))
                val tw = textPaint.measureText(label)
                val tx = (x - tw / 2).coerceIn(leftPad, w - rightPad - tw)
                drawContext.canvas.nativeCanvas.drawText(label, tx, h - 4f, textPaint)
            }
        }

        // 十字准线和数值提示框
        val sx = selectedX
        if (sx != null && sx >= leftPad && sx <= w - rightPad) {
            val targetTs = tsOf(sx)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

            // 竖线（虚线）
            drawLine(
                color = crosshairColor,
                start = Offset(sx, topPad),
                end = Offset(sx, h - bottomPad),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
            )

            data class HitInfo(
                val label: String,
                val value: Double,
                val color: Color,
                val x: Float,
                val y: Float
            )

            val hits = mutableListOf<HitInfo>()
            var hitDateStr = ""

            for (line in sortedLines) {
                if (line.points.isEmpty()) continue
                val nearest = line.points.minByOrNull { abs(it.timestamp - targetTs) } ?: continue
                val nx = xOf(nearest.timestamp)
                val ny = yOf(nearest.value)

                // 横线（虚线）经过选中点
                drawLine(
                    color = crosshairColor,
                    start = Offset(leftPad, ny),
                    end = Offset(w - rightPad, ny),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )

                // 选中点圆圈
                drawCircle(Color.White, radius = 7f, center = Offset(nx, ny))
                drawCircle(line.color, radius = 4.5f, center = Offset(nx, ny))

                val valueStr = if (showPercentAxis) {
                    "${"%.2f".format(nearest.value)}$yAxisSuffix"
                } else {
                    "%.4f".format(nearest.value)
                }
                hits.add(HitInfo(line.label, nearest.value, line.color, nx, ny))
                if (hitDateStr.isEmpty()) hitDateStr = sdf.format(Date(nearest.timestamp))
            }

            if (hits.isNotEmpty()) {
                val tooltipTextPaint = android.graphics.Paint().apply {
                    textSize = 24f
                    isAntiAlias = true
                }

                val tooltipEntries = mutableListOf<Triple<String, Int, Boolean>>()
                tooltipEntries.add(Triple(hitDateStr, 0xCCFFFFFF.toInt(), false))
                for (hit in hits) {
                    val valueStr = if (showPercentAxis) {
                        "${"%.2f".format(hit.value)}$yAxisSuffix"
                    } else {
                        "%.4f".format(hit.value)
                    }
                    val text = if (hits.size > 1) "${hit.label}: $valueStr" else valueStr
                    tooltipEntries.add(Triple(text, hit.color.toArgb(), true))
                }

                val lineHeight = 32f
                val padH = 14f
                val padV = 10f
                val maxTextW = tooltipEntries.maxOf { (text, _, _) ->
                    tooltipTextPaint.measureText(text)
                }
                val tooltipW = maxTextW + padH * 2
                val tooltipH = tooltipEntries.size * lineHeight + padV * 2 - 4f

                var tooltipX = sx - tooltipW / 2
                tooltipX = tooltipX.coerceIn(leftPad, w - rightPad - tooltipW)
                val tooltipY = topPad

                drawRoundRect(
                    color = if (isDark) TooltipBgDark else TooltipBgLight,
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(tooltipW, tooltipH),
                    cornerRadius = CornerRadius(8f)
                )

                // 提示框文字
                tooltipEntries.forEachIndexed { i, (text, argb, isBold) ->
                    tooltipTextPaint.color = argb
                    tooltipTextPaint.isFakeBoldText = isBold
                    tooltipTextPaint.textSize = if (i == 0) 22f else 24f
                    drawContext.canvas.nativeCanvas.drawText(
                        text,
                        tooltipX + padH,
                        tooltipY + padV + (i + 1) * lineHeight - 8f,
                        tooltipTextPaint
                    )
                }
            }
        }
    }
}

/**
 * 期间选择器按钮：月/季/半年/一年/三年/五年/成立以来
 * 支持横向滚动以获得更好的移动端体验。
 */
@Composable
fun PeriodSelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val periods = listOf("月", "季", "半年", "一年", "三年", "五年", "成立以来")
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        periods.forEach { period ->
            val isSelected = period == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(period) },
                label = {
                    Text(
                        period,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.height(34.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(17.dp)
            )
        }
    }
}

/** 根据相对于最后一点日期的期间过滤图表点。 */
fun filterByPeriod(points: List<ChartPoint>, period: String): List<ChartPoint> {
    if (points.isEmpty()) return points
    val lastTs = points.maxOf { it.timestamp }
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastTs }
    when (period) {
        "月" -> cal.add(java.util.Calendar.MONTH, -1)
        "季" -> cal.add(java.util.Calendar.MONTH, -3)
        "半年" -> cal.add(java.util.Calendar.MONTH, -6)
        "一年" -> cal.add(java.util.Calendar.YEAR, -1)
        "三年" -> cal.add(java.util.Calendar.YEAR, -3)
        "五年" -> cal.add(java.util.Calendar.YEAR, -5)
        "成立以来" -> return points
    }
    val startTs = cal.timeInMillis
    return points.filter { it.timestamp >= startTs }
}
