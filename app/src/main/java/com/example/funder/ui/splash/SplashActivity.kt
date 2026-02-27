package com.example.funder.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.funder.MainActivity
import kotlinx.coroutines.delay
import kotlin.math.sin

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
fun SplashScreen(onAnimationEnd: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    val lineProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "lineProgress"
    )

    // 末端光标脉冲
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )
    val textOffset by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 30f,
        animationSpec = tween(durationMillis = 350, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "textOffset"
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 600, easing = FastOutSlowInEasing),
        label = "subtitleAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1400)
        onAnimationEnd()
    }

    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF040D1A),
            Color(0xFF0A2440),
            Color(0xFF1565C0)
        )
    )

    val lineColor = Color(0xFF4FC3F7)
    val glowColor = Color(0xFF1976D2)
    val accentColor = Color(0xFFFFCA28)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        // 背景网格
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color.White.copy(alpha = 0.03f)
            val step = 50.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        // 走势折线图
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .offset(y = (-30).dp)
        ) {
            val w = size.width
            val h = size.height
            val padH = 30f
            val padV = 40f

            val points = mutableListOf<Offset>()
            val segments = 80
            for (i in 0..segments) {
                val px = padH + (w - padH * 2) * i / segments
                val progress = i.toFloat() / segments
                val trend = progress * 0.4f
                val wave1 = sin(progress * 14.0).toFloat() * 0.06f
                val wave2 = sin(progress * 7.0 + 1.0).toFloat() * 0.04f
                val bump = if (progress > 0.55f) (progress - 0.55f) * 0.45f else 0f
                val py = h - padV - (h - padV * 2) * (0.2f + trend + wave1 + wave2 + bump)
                points.add(Offset(px, py))
            }

            val visibleCount = (points.size * lineProgress).toInt().coerceAtLeast(1)
            val visiblePoints = points.take(visibleCount)

            if (visiblePoints.size >= 2) {
                // 面积填充渐变
                val areaPath = Path().apply {
                    moveTo(visiblePoints.first().x, h)
                    lineTo(visiblePoints.first().x, visiblePoints.first().y)
                    for (i in 1 until visiblePoints.size) {
                        val prev = visiblePoints[i - 1]
                        val curr = visiblePoints[i]
                        val midX = (prev.x + curr.x) / 2
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                    lineTo(visiblePoints.last().x, h)
                    close()
                }
                drawPath(
                    areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.15f),
                            glowColor.copy(alpha = 0.02f)
                        ),
                        startY = visiblePoints.minOf { it.y },
                        endY = h
                    ),
                    style = Fill
                )

                // 发光底层线（粗）
                val curvePath = Path().apply {
                    moveTo(visiblePoints.first().x, visiblePoints.first().y)
                    for (i in 1 until visiblePoints.size) {
                        val prev = visiblePoints[i - 1]
                        val curr = visiblePoints[i]
                        val midX = (prev.x + curr.x) / 2
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }
                drawPath(
                    curvePath,
                    color = glowColor.copy(alpha = 0.25f),
                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // 主折线
                drawPath(
                    curvePath,
                    color = lineColor,
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // 末端光标
                val lastPoint = visiblePoints.last()
                if (lineProgress > 0.2f) {
                    // 外圈脉冲
                    drawCircle(
                        color = accentColor.copy(alpha = pulseAlpha),
                        radius = 20f * pulseScale,
                        center = lastPoint
                    )
                    // 中间发光环
                    drawCircle(
                        color = accentColor.copy(alpha = 0.3f),
                        radius = 12f,
                        center = lastPoint
                    )
                    // 实心圆点
                    drawCircle(
                        color = accentColor,
                        radius = 7f,
                        center = lastPoint
                    )
                }
            }
        }

        // 文字区域
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 100.dp)
        ) {
            Text(
                text = "基金宝",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 6.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha
                    translationY = textOffset
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "智能基金管理 · 实时估值追踪",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.55f),
                letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha
                }
            )
        }
    }
}
