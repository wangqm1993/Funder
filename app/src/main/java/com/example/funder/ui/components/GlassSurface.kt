package com.example.funder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Frosted glass (glassmorphism) surface with semi-transparent background
 * and subtle light border. Designed for overlay on colored backgrounds.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.White,
    backgroundAlpha: Float = 0.1f,
    borderColor: Color = Color.White,
    borderAlpha: Float = 0.18f,
    borderWidth: Dp = 0.5.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor.copy(alpha = backgroundAlpha))
            .border(borderWidth, borderColor.copy(alpha = borderAlpha), shape),
        content = content
    )
}
