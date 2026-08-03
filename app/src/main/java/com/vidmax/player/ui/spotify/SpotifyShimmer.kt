package com.vidmax.player.ui.spotify

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Meld-এর ShimmerHost-এর spirit অনুসরণ করে, তবে কোনো বাহ্যিক লাইব্রেরি ছাড়া
 * pure Compose animation API (rememberInfiniteTransition + drawWithContent)
 * দিয়ে তৈরি। gradient sweep অসীমভাবে চলতে থাকে — হোম সেকশন লোড হওয়ার সময়
 * placeholder গুলোতে এই shimmer ব্যবহার হয়।
 */
@Composable
fun Modifier.shimmerBackground(
    baseAlpha: Float = 0.08f,
    highlightAlpha: Float = 0.16f,
    durationMillis: Int = 1200,
): Modifier {
    val transition = rememberInfiniteTransition(label = "spotifyShimmer")
    val translateX by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = baseAlpha)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = highlightAlpha)

    return drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(baseColor, highlightColor, baseColor),
                start = Offset(size.width * (translateX - 0.6f), 0f),
                end = Offset(size.width * (translateX + 0.6f), size.height),
            ),
            blendMode = BlendMode.SrcIn,
        )
    }
}

/**
 * shimmer ব্যাকগ্রাউন্ডসহ একটি সাধারণ placeholder বক্স।
 * থাম্বনেইল / কার্ড placeholder হিসাবে ব্যবহার হয়।
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .shimmerBackground()
    )
}

/**
 * টেক্সট লাইনের মতো দেখতে লম্বা-পাতলা shimmer placeholder।
 */
@Composable
fun TextPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    widthFraction: Float = 0.9f,
    shape: CornerBasedShape = RoundedCornerShape(50),
) {
    ShimmerBox(
        modifier = modifier
            .height(height)
            .fillMaxWidth(widthFraction),
        shape = shape,
    )
}
