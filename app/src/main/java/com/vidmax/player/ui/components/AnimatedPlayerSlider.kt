package com.vidmax.player.ui.components

import android.content.Context
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class SliderStyle(val label: String) {
    CLASSIC("Classic"),
    SQUIGGLY("Squiggly"),
    WAVY("Wavy");

    companion object {
        fun fromName(name: String?): SliderStyle =
            entries.firstOrNull { it.name == name } ?: SQUIGGLY
    }
}

object SliderStylePrefs {
    private const val PREFS = "PlayerSliderPrefs"
    private const val KEY = "slider_style"

    fun get(context: Context): SliderStyle {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SliderStyle.fromName(prefs.getString(KEY, SliderStyle.SQUIGGLY.name))
    }

    fun set(context: Context, style: SliderStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, style.name)
            .apply()
    }
}

/**
 * Unified animated slider that switches between the three player slider styles
 * (Classic / Squiggly / Wavy) based on [style].
 */
@Composable
fun AnimatedPlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
    style: SliderStyle = SliderStyle.SQUIGGLY,
) {
    when (style) {
        SliderStyle.CLASSIC -> PlayerSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            isPlaying = isPlaying,
        )

        SliderStyle.SQUIGGLY -> SquigglySlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            isPlaying = isPlaying,
        )

        SliderStyle.WAVY -> WavySlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            isPlaying = isPlaying,
        )
    }
}
