package com.vidmax.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Classic player slider — a thick rounded track with a circular thumb.
 * Ported from Meld (Metrolist) PlayerSlider.kt and completed with touch
 * gestures so it can be used as a standalone component.
 */
@Composable
fun PlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
    trackHeight: Dp = 10.dp,
    thumbRadius: Dp = 10.dp,
) {
    val duration = valueRange.endInclusive - valueRange.start
    val normalized = if (duration > 0f) ((value - valueRange.start) / duration).coerceIn(0f, 1f) else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(normalized) }
    val displayValue = if (isDragging) dragValue else normalized

    val inactiveTrackColor = colors.inactiveTrackColor
    val activeTrackColor = colors.activeTrackColor
    val inactiveTickColor = colors.inactiveTickColor
    val activeTickColor = colors.activeTickColor

    val containerHeight = maxOf(trackHeight, thumbRadius * 2)

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
        Canvas(modifier = Modifier.fillMaxWidth().height(trackHeight)) {
            drawPlayerTrack(
                activeRangeEnd = displayValue,
                inactiveTrackColor = inactiveTrackColor,
                activeTrackColor = activeTrackColor,
                inactiveTickColor = inactiveTickColor,
                activeTickColor = activeTickColor,
                trackHeight = trackHeight,
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth()) {
            drawCircle(
                color = if (enabled) colors.thumbColor else colors.disabledThumbColor,
                radius = thumbRadius.toPx(),
                center = Offset(size.width * displayValue, size.height / 2f),
            )
        }
    }
}

private fun DrawScope.drawPlayerTrack(
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    inactiveTickColor: Color,
    activeTickColor: Color,
    trackHeight: Dp = 2.dp,
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val sliderLeft = Offset(0f, center.y)
    val sliderRight = Offset(size.width, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    val tickSize = 2.0.dp.toPx()
    val trackStrokeWidth = trackHeight.toPx()

    drawLine(
        inactiveTrackColor,
        sliderStart,
        sliderEnd,
        trackStrokeWidth,
        StrokeCap.Round,
    )

    val sliderValueEnd = Offset(
        sliderStart.x + (sliderEnd.x - sliderStart.x) * activeRangeEnd,
        center.y,
    )

    drawLine(
        activeTrackColor,
        sliderStart,
        sliderValueEnd,
        trackStrokeWidth,
        StrokeCap.Round,
    )

    val tickFractions = stepsToTickFractions(0)
    for (tick in tickFractions) {
        val outsideFraction = tick > activeRangeEnd
        drawCircle(
            color = if (outsideFraction) inactiveTickColor else activeTickColor,
            center = Offset(lerp(sliderStart, sliderEnd, tick).x, center.y),
            radius = tickSize / 2f,
        )
    }
}

private fun stepsToTickFractions(steps: Int): FloatArray {
    return if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }
}
