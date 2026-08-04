package com.vidmax.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Skeleton shimmer background — an infinite linear-gradient sweep used as a
 * loading placeholder. Pair with [ArtworkImage]'s onSuccess to fade it out.
 */
@Composable
fun Modifier.shimmer(
    baseColor: Color = Color(0xFF2A2A2E),
    highlightColor: Color = Color(0xFF3D3D44),
    durationMillis: Int = 1100,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translate by transition.animateFloat(
        initialValue = -200f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    background(
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(translate - 200f, 0f),
            end = Offset(translate, 200f),
        )
    )
}
