package com.vidmax.player.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * Wavy Slider — a fully custom canvas re-implementation of Meld's WavySlider
 * (which uses the Material3 Expressive API `LinearWavyProgressIndicator`).
 *
 * Since the project uses Material3 1.3.1 (no Expressive API), the wave is drawn
 * manually: a moving sine wave whose amplitude springs up when playing and
 * flattens when paused, with a circular thumb sitting in a gap at the progress.
 */
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
    strokeWidth: Dp = 4.dp,
    thumbRadius: Dp = 8.dp,
    wavelength: Dp = 48.dp,
    waveAmplitude: Dp = 5.dp,
    waveSpeed: Float = 1.6f,
) {
    val duration = valueRange.endInclusive - valueRange.start
    val normalized = if (duration > 0f) ((value - valueRange.start) / duration).coerceIn(0f, 1f) else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(normalized) }
    val displayValue = if (isDragging) dragValue else normalized

    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "amplitude",
    )

    // Frame-driven wave scroll (phase advances in pixels per second).
    var phaseOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val wavelengthPx = with(density) { wavelength.toPx() }
    val waveAmplitudePx = with(density) { waveAmplitude.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }

    LaunchedEffect(isPlaying, wavelengthPx) {
        if (!isPlaying || wavelengthPx <= 0f) return@LaunchedEffect
        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffsetPx += deltaTime * waveSpeed * wavelengthPx
                lastFrameTime = frameTimeMillis
            }
        }
    }

    val activeColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor
    val thumbColor = colors.thumbColor

    val containerHeight = maxOf(thumbRadius * 2 + 8.dp, waveAmplitude * 4)

    val baseModifier = modifier
        .fillMaxWidth()
        .height(containerHeight)

    val interactiveModifier = if (enabled) {
        baseModifier
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    val mappedValue = valueRange.start + newValue * duration
                    onValueChange(mappedValue)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        val mappedValue = valueRange.start + dragValue * duration
                        onValueChange(mappedValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { _, dragAmount ->
                        dragValue = (dragValue + dragAmount / size.width).coerceIn(0f, 1f)
                        val mappedValue = valueRange.start + dragValue * duration
                        onValueChange(mappedValue)
                    },
                )
            }
    } else {
        baseModifier
    }

    Box(
        modifier = interactiveModifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val gapSizePx = thumbRadiusPx + 4.dp.toPx()
            val thumbX = size.width * displayValue
            val leftGap = (thumbX - gapSizePx / 2f).coerceIn(0f, size.width)
            val rightGap = (thumbX + gapSizePx / 2f).coerceIn(0f, size.width)

            val amplitudePx = waveAmplitudePx * animatedAmplitude
            val phase = (phaseOffsetPx / wavelengthPx) * (2f * PI.toFloat())
            val disabledAlpha = 77f / 255f
            val trackColor = inactiveColor.copy(alpha = disabledAlpha)

            val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

            // Flat background track across the whole width.
            drawWavePath(
                startX = 0f, endX = size.width, centerY = centerY, wavelengthPx = wavelengthPx,
                amplitudePx = 0f, phase = 0f, color = trackColor, stroke = stroke,
            )

            // Flat inactive track after the gap.
            drawWavePath(
                startX = rightGap, endX = size.width, centerY = centerY, wavelengthPx = wavelengthPx,
                amplitudePx = 0f, phase = 0f, color = inactiveColor, stroke = stroke,
            )

            // Wavy active track up to the gap (amplitude fades toward the gap).
            drawWavePath(
                startX = 0f, endX = leftGap, centerY = centerY, wavelengthPx = wavelengthPx,
                amplitudePx = amplitudePx, phase = phase,
                color = activeColor, stroke = stroke, fadeRight = leftGap,
            )

            // Circular thumb sitting inside the gap, aligned with the wave.
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbX, centerY),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavePath(
    startX: Float,
    endX: Float,
    centerY: Float,
    wavelengthPx: Float,
    amplitudePx: Float,
    phase: Float,
    color: androidx.compose.ui.graphics.Color,
    stroke: Stroke,
    fadeRight: Float = -1f,
) {
    if (endX <= startX) return
    val freq = (2f * PI.toFloat()) / wavelengthPx
    val path = Path()
    var x = startX
    path.moveTo(startX, centerY)
    var first = true
    while (x <= endX) {
        var amp = amplitudePx
        if (fadeRight > 0f && x > fadeRight - wavelengthPx) {
            val t = ((fadeRight - x) / wavelengthPx).coerceIn(0f, 1f)
            amp *= t
        }
        val y = centerY + sin(x * freq + phase) * amp
        if (first) {
            path.moveTo(x, y)
            first = false
        } else {
            path.lineTo(x, y)
        }
        x += 6f
    }
    if (first) path.lineTo(startX, centerY)
    drawPath(path = path, color = color, style = stroke)
}
