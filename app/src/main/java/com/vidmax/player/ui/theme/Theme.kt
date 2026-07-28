package com.vidmax.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Extension functions for color manipulation
 */
fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red + (1 - red) * factor).coerceIn(0f, 1f),
        green = (green + (1 - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun Color.compositeOver(background: Color): Color {
    val bgAlpha = background.alpha
    val fgAlpha = alpha
    val a = fgAlpha + bgAlpha * (1f - fgAlpha)
    return if (a == 0f) {
        Color.Transparent
    } else {
        Color(
            red = (red * fgAlpha + background.red * bgAlpha * (1f - fgAlpha)) / a,
            green = (green * fgAlpha + background.green * bgAlpha * (1f - fgAlpha)) / a,
            blue = (blue * fgAlpha + background.blue * bgAlpha * (1f - fgAlpha)) / a,
            alpha = a
        )
    }
}

/**
 * ---------------------------------------------------------------------------------
 * Tonal color system
 * ---------------------------------------------------------------------------------
 * Calculates color lightness and contrast ratio to guarantee readability per WCAG AA guidelines.
 */

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val r = red; val g = green; val b = blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f
    if (maxC == minC) return Hsl(0f, 0f, l)
    val d = maxC - minC
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h = when (maxC) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } / 6f
    return Hsl(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    if (s == 0f) return Color(l, l, l, alpha)
    fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val r = hue2rgb(p, q, h + 1f / 3f)
    val g = hue2rgb(p, q, h)
    val b = hue2rgb(p, q, h - 1f / 3f)
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), alpha)
}

/** Relative luminance per WCAG 2.1 (sRGB). */
private fun Color.relativeLuminance(): Float {
    fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * lin(red) + 0.7152f * lin(green) + 0.0722f * lin(blue)
}

/** WCAG contrast ratio between two colors. Always >= 1f. */
fun Color.contrastRatioWith(other: Color): Float {
    val l1 = relativeLuminance() + 0.05f
    val l2 = other.relativeLuminance() + 0.05f
    return max(l1, l2) / min(l1, l2)
}

/** Re-lights color to an absolute lightness [tone]. */
fun Color.tone(tone: Float, saturationMultiplier: Float = 1f): Color {
    val hsl = toHsl()
    val newS = if (hsl.s > 0.02f) (hsl.s * saturationMultiplier).coerceIn(0f, 1f) else hsl.s
    return hslToColor(hsl.h, newS, tone.coerceIn(0f, 1f), alpha)
}

/** Nudges color lightness to guarantee minimum contrast ratio. */
private fun Color.ensureContrast(
    background: Color,
    minRatio: Float,
    darken: Boolean,
    saturationFloor: Float = 0.35f
): Color {
    if (contrastRatioWith(background) >= minRatio) return this
    val hsl = toHsl()
    val s = if (hsl.s > 0.02f) hsl.s.coerceAtLeast(saturationFloor) else hsl.s
    var lo = if (darken) 0f else hsl.l
    var hi = if (darken) hsl.l else 1f
    repeat(30) {
        val mid = (lo + hi) / 2f
        val candidate = hslToColor(hsl.h, s, mid, alpha)
        val ok = candidate.contrastRatioWith(background) >= minRatio
        if (darken) {
            if (ok) lo = mid else hi = mid
        } else {
            if (ok) hi = mid else lo = mid
        }
    }
    return hslToColor(hsl.h, s, if (darken) lo else hi, alpha)
}

private fun Color.ensureContrast(background: Color, minRatio: Float): Color =
    ensureContrast(background, minRatio, darken = background.relativeLuminance() > 0.5f)

private fun bestOnColor(seedLight: Color, seedDark: Color, against: Color, minRatio: Float = 4.5f): Color {
    val darkCandidate = seedLight.tone(0.15f)
    val lightCandidate = seedDark.tone(0.95f)
    return if (darkCandidate.contrastRatioWith(against) >= lightCandidate.contrastRatioWith(against)) {
        darkCandidate.ensureContrast(against, minRatio, darken = true)
    } else {
        lightCandidate.ensureContrast(against, minRatio, darken = false)
    }
}

/**
 * App themes Enum
 */
enum class AppTheme(
    val primaryLight: Color,
    val primaryDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val tertiaryLight: Color,
    val tertiaryDark: Color,
    val backgroundLight: Color,
    val backgroundDark: Color,
    val isDynamic: Boolean = false,
) {
    Default(
        primaryLight = Color(0xFF794F81),
        primaryDark = Color(0xFFE8B5EF),
        secondaryLight = Color(0xFF6A596C),
        secondaryDark = Color(0xFFD6C0D6),
        tertiaryLight = Color(0xFF82524D),
        tertiaryDark = Color(0xFFF5B7B0),
        backgroundLight = Color(0xFFFFF7FB),
        backgroundDark = Color(0xFF161217),
    ),
    Dynamic(
        primaryLight = Color(0xFF6750A4),
        primaryDark = Color(0xFFD0BCFF),
        secondaryLight = Color(0xFF625B71),
        secondaryDark = Color(0xFFCCC2DC),
        tertiaryLight = Color(0xFF7D5260),
        tertiaryDark = Color(0xFFEFB8C8),
        backgroundLight = Color(0xFFFFFBFF),
        backgroundDark = Color(0xFF1C1B1F),
        isDynamic = true,
    ),
    Cloudflare(
        primaryLight = Color(0xFFF6821F),
        primaryDark = Color(0xFFFFB77C),
        secondaryLight = Color(0xFF6B5E4C),
        secondaryDark = Color(0xFFD6C5AC),
        tertiaryLight = Color(0xFF4A7856),
        tertiaryDark = Color(0xFFA5D6A7),
        backgroundLight = Color(0xFFFFFBF7),
        backgroundDark = Color(0xFF1A1612),
    ),
    CottonCandy(
        primaryLight = Color(0xFFE993C1),
        primaryDark = Color(0xFFFFB1D5),
        secondaryLight = Color(0xFF70A2C2),
        secondaryDark = Color(0xFF9ED0EF),
        tertiaryLight = Color(0xFF9C68AC),
        tertiaryDark = Color(0xFFDEB0E9),
        backgroundLight = Color(0xFFFFF8FA),
        backgroundDark = Color(0xFF1A1418),
    ),
    Doom(
        primaryLight = Color(0xFFD32F2F),
        primaryDark = Color(0xFFFF6659),
        secondaryLight = Color(0xFF424242),
        secondaryDark = Color(0xFFB0B0B0),
        tertiaryLight = Color(0xFF8D6E63),
        tertiaryDark = Color(0xFFD7CCC8),
        backgroundLight = Color(0xFFFCF5F5),
        backgroundDark = Color(0xFF120C0C),
    ),
    GreenApple(
        primaryLight = Color(0xFF2E7D32),
        primaryDark = Color(0xFF81C784),
        secondaryLight = Color(0xFF33691E),
        secondaryDark = Color(0xFFAED581),
        tertiaryLight = Color(0xFF00695C),
        tertiaryDark = Color(0xFF4DB6AC),
        backgroundLight = Color(0xFFF4F9F4),
        backgroundDark = Color(0xFF0D140D),
    ),
    Gruvbox(
        primaryLight = Color(0xFFD65D0E),
        primaryDark = Color(0xFFFE8019),
        secondaryLight = Color(0xFF98971A),
        secondaryDark = Color(0xFFB8BB26),
        tertiaryLight = Color(0xFF458588),
        tertiaryDark = Color(0xFF83A598),
        backgroundLight = Color(0xFFFBF1C7),
        backgroundDark = Color(0xFF282828),
    ),
    Kanagawa(
        primaryLight = Color(0xFF7E9CD8),
        primaryDark = Color(0xFF9AB8F5),
        secondaryLight = Color(0xFFC8C093),
        secondaryDark = Color(0xFFE6DCA9),
        tertiaryLight = Color(0xFF957FB8),
        tertiaryDark = Color(0xFFB8A5D6),
        backgroundLight = Color(0xFFF2ECBC),
        backgroundDark = Color(0xFF1F1F28),
    ),
    Midnight(
        primaryLight = Color(0xFF1A237E),
        primaryDark = Color(0xFF7986CB),
        secondaryLight = Color(0xFF37474F),
        secondaryDark = Color(0xFF90A4AE),
        tertiaryLight = Color(0xFF006064),
        tertiaryDark = Color(0xFF4DD0E1),
        backgroundLight = Color(0xFFF4F6F9),
        backgroundDark = Color(0xFF0A0E14),
    ),
    Mocha(
        primaryLight = Color(0xFF4E342E),
        primaryDark = Color(0xFFA1887F),
        secondaryLight = Color(0xFF8D6E63),
        secondaryDark = Color(0xFFD7CCC8),
        tertiaryLight = Color(0xFFE65100),
        tertiaryDark = Color(0xFFFFB74D),
        backgroundLight = Color(0xFFFDF8F5),
        backgroundDark = Color(0xFF171210),
    ),
    Strawberry(
        primaryLight = Color(0xFFC2185B),
        primaryDark = Color(0xFFF06292),
        secondaryLight = Color(0xFFF48FB1),
        secondaryDark = Color(0xFFF8BBD9),
        tertiaryLight = Color(0xFF7B1FA2),
        tertiaryDark = Color(0xFFBA68C8),
        backgroundLight = Color(0xFFFFF5F7),
        backgroundDark = Color(0xFF1A0E12),
    ),
    Tidal(
        primaryLight = Color(0xFF00695C),
        primaryDark = Color(0xFF4DB6AC),
        secondaryLight = Color(0xFF00838F),
        secondaryDark = Color(0xFF80DEEA),
        tertiaryLight = Color(0xFF283593),
        tertiaryDark = Color(0xFF7986CB),
        backgroundLight = Color(0xFFF2FBFA),
        backgroundDark = Color(0xFF0B1413),
    ),
    Nord(
        primaryLight = Color(0xFF5E81AC),
        primaryDark = Color(0xFF88C0D0),
        secondaryLight = Color(0xFF4C566A),
        secondaryDark = Color(0xFFD8DEE9),
        tertiaryLight = Color(0xFFB48EAD),
        tertiaryDark = Color(0xFFD8A9C4),
        backgroundLight = Color(0xFFECEFF4),
        backgroundDark = Color(0xFF2E3440),
    ),
    RosePine(
        primaryLight = Color(0xFF907AA9),
        primaryDark = Color(0xFFC4A7E7),
        secondaryLight = Color(0xFFEBBCBA),
        secondaryDark = Color(0xFFFFD7D5),
        tertiaryLight = Color(0xFF31748F),
        tertiaryDark = Color(0xFF9CCFD8),
        backgroundLight = Color(0xFFFAF4ED),
        backgroundDark = Color(0xFF232136),
    ),
    TokyoNight(
        primaryLight = Color(0xFF33467C),
        primaryDark = Color(0xFF7AA2F7),
        secondaryLight = Color(0xFF5A4A78),
        secondaryDark = Color(0xFFBB9AF7),
        tertiaryLight = Color(0xFF415C33),
        tertiaryDark = Color(0xFFA6DA95),
        backgroundLight = Color(0xFFF0F1F5),
        backgroundDark = Color(0xFF1A1B26),
    ),
    Violet(
        primaryLight = Color(0xFF512DA8),
        primaryDark = Color(0xFF9575CD),
        secondaryLight = Color(0xFFC2185B),
        secondaryDark = Color(0xFFF06292),
        tertiaryLight = Color(0xFF303F9F),
        tertiaryDark = Color(0xFF7986CB),
        backgroundLight = Color(0xFFF9F5FE),
        backgroundDark = Color(0xFF120A17),
    ),
    Amber(
        primaryLight = Color(0xFFFF8F00),
        primaryDark = Color(0xFFFFCA28),
        secondaryLight = Color(0xFFE65100),
        secondaryDark = Color(0xFFFFB74D),
        tertiaryLight = Color(0xFF33691E),
        tertiaryDark = Color(0xFFAED581),
        backgroundLight = Color(0xFFFFFBF0),
        backgroundDark = Color(0xFF17130A),
    ),
    Dracula(
        primaryLight = Color(0xFF6272A4),
        primaryDark = Color(0xFFBD93F9),
        secondaryLight = Color(0xFF44475A),
        secondaryDark = Color(0xFFFF79C6),
        tertiaryLight = Color(0xFF2B8040),
        tertiaryDark = Color(0xFF50FA7B),
        backgroundLight = Color(0xFFF8F8F2),
        backgroundDark = Color(0xFF282A36),
    ),
    NeonLime(
        primaryLight = Color(0xFF6C8B23),
        primaryDark = Color(0xFFCEE1A3),
        secondaryLight = Color(0xFF004D40),
        secondaryDark = Color(0xFF80CBC4),
        tertiaryLight = Color(0xFFD84315),
        tertiaryDark = Color(0xFFFF8A65),
        backgroundLight = Color(0xFFF9FBEF),
        backgroundDark = Color(0xFF10140A),
    ),
    DeepIndigo(
        primaryLight = Color(0xFF3B389F),
        primaryDark = Color(0xFF9592FF),
        secondaryLight = Color(0xFF56698F),
        secondaryDark = Color(0xFFB3C5EB),
        tertiaryLight = Color(0xFF855EBA),
        tertiaryDark = Color(0xFFD7CCE6),
        backgroundLight = Color(0xFFF5F5FA),
        backgroundDark = Color(0xFF0E0E14),
    ),
    Monochrome(
        primaryLight = Color(0xFF212121),
        primaryDark = Color(0xFFE0E0E0),
        secondaryLight = Color(0xFF616161),
        secondaryDark = Color(0xFFBDBDBD),
        tertiaryLight = Color(0xFF757575),
        tertiaryDark = Color(0xFF9E9E9E),
        backgroundLight = Color(0xFFFFFFFF),
        backgroundDark = Color(0xFF0A0A0A),
    ),
    ChartreuseGunMetal(
        primaryLight = Color(0xFFE1FF51),
        primaryDark = Color(0xFFCBE549),
        secondaryLight = Color(0xFF00272C),
        secondaryDark = Color(0xFF3A5F64),
        tertiaryLight = Color(0xFF396B73),
        tertiaryDark = Color(0xFF75A6AE),
        backgroundLight = Color(0xFFFAFCF5),
        backgroundDark = Color(0xFF0F1516),
    ),
    MaroonXanthous(
        primaryLight = Color(0xFF780115),
        primaryDark = Color(0xFFFF8993),
        secondaryLight = Color(0xFFF7B638),
        secondaryDark = Color(0xFFFFD470),
        tertiaryLight = Color(0xFF8F584C),
        tertiaryDark = Color(0xFFE0B4AA),
        backgroundLight = Color(0xFFFFF5F5),
        backgroundDark = Color(0xFF1F0D10),
    ),
    ButtermilkMidBlue(
        primaryLight = Color(0xFFFFF2BD),
        primaryDark = Color(0xFFE3D6A1),
        secondaryLight = Color(0xFF285CCC),
        secondaryDark = Color(0xFF7A9FF2),
        tertiaryLight = Color(0xFF5A7299),
        tertiaryDark = Color(0xFFA1B8E0),
        backgroundLight = Color(0xFFFEFEFA),
        backgroundDark = Color(0xFF11141A),
    ),
    LavenderVelvet(
        primaryLight = Color(0xFFD3C5F6),
        primaryDark = Color(0xFFBCAEE0),
        secondaryLight = Color(0xFF3B2A60),
        secondaryDark = Color(0xFF8E7AB5),
        tertiaryLight = Color(0xFF7A6894),
        tertiaryDark = Color(0xFFC3B2DE),
        backgroundLight = Color(0xFFFBF9FF),
        backgroundDark = Color(0xFF15121C),
    ),
    SalmonMidGreen(
        primaryLight = Color(0xFFFF947A),
        primaryDark = Color(0xFFE57B62),
        secondaryLight = Color(0xFF025259),
        secondaryDark = Color(0xFF4C939A),
        tertiaryLight = Color(0xFF608B85),
        tertiaryDark = Color(0xFFA4CFC9),
        backgroundLight = Color(0xFFFFF8F6),
        backgroundDark = Color(0xFF101718),
    );

    fun getLightColorScheme(): ColorScheme {
        val bg = backgroundLight

        val primary = primaryLight.ensureContrast(bg, 4.5f, darken = true)
        val onPrimary = bestOnColor(primaryLight, primaryDark, primary)
        val primaryContainer = primaryLight.tone(0.90f, saturationMultiplier = 0.55f)
        val onPrimaryContainer = primaryLight.tone(0.25f).ensureContrast(primaryContainer, 4.5f, darken = true)

        val secondary = secondaryLight.ensureContrast(bg, 4.5f, darken = true)
        val onSecondary = bestOnColor(secondaryLight, secondaryDark, secondary)
        val secondaryContainer = secondaryLight.tone(0.90f, saturationMultiplier = 0.50f)
        val onSecondaryContainer = secondaryLight.tone(0.25f).ensureContrast(secondaryContainer, 4.5f, darken = true)

        val tertiary = tertiaryLight.ensureContrast(bg, 4.5f, darken = true)
        val onTertiary = bestOnColor(tertiaryLight, tertiaryDark, tertiary)
        val tertiaryContainer = tertiaryLight.tone(0.90f, saturationMultiplier = 0.55f)
        val onTertiaryContainer = tertiaryLight.tone(0.25f).ensureContrast(tertiaryContainer, 4.5f, darken = true)

        val onBackground = Color(0xFF1C1B1F).ensureContrast(bg, 7f, darken = true)
        val surfaceVariant = primaryLight.tone(0.92f, saturationMultiplier = 0.18f)
        val onSurfaceVariant = Color(0xFF49454F).ensureContrast(surfaceVariant, 4.5f, darken = true)
        val outline = secondaryLight.tone(0.45f, saturationMultiplier = 0.35f).ensureContrast(bg, 3f, darken = true)
        val outlineVariant = primaryLight.tone(0.82f, saturationMultiplier = 0.20f)

        return lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF93000A),
            background = bg,
            onBackground = onBackground,
            surface = bg,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = backgroundDark,
            inverseOnSurface = Color(0xFFF4EFF4),
            inversePrimary = primaryDark,
            surfaceContainerLowest = bg,
            surfaceContainerLow = primaryLight.tone(0.97f, saturationMultiplier = 0.12f),
            surfaceContainer = primaryLight.tone(0.94f, saturationMultiplier = 0.15f),
            surfaceContainerHigh = primaryLight.tone(0.92f, saturationMultiplier = 0.18f),
            surfaceContainerHighest = primaryLight.tone(0.90f, saturationMultiplier = 0.20f),
        )
    }

    fun getDarkColorScheme(): ColorScheme {
        val bg = backgroundDark

        val primary = primaryDark.ensureContrast(bg, 4.5f, darken = false)
        val onPrimary = bestOnColor(primaryLight, primaryDark, primary)
        val primaryContainer = primaryLight.tone(0.28f, saturationMultiplier = 0.75f)
        val onPrimaryContainer = primaryDark.tone(0.90f).ensureContrast(primaryContainer, 4.5f, darken = false)

        val secondary = secondaryDark.ensureContrast(bg, 4.5f, darken = false)
        val onSecondary = bestOnColor(secondaryLight, secondaryDark, secondary)
        val secondaryContainer = secondaryLight.tone(0.26f, saturationMultiplier = 0.70f)
        val onSecondaryContainer = secondaryDark.tone(0.90f).ensureContrast(secondaryContainer, 4.5f, darken = false)

        val tertiary = tertiaryDark.ensureContrast(bg, 4.5f, darken = false)
        val onTertiary = bestOnColor(tertiaryLight, tertiaryDark, tertiary)
        val tertiaryContainer = tertiaryLight.tone(0.28f, saturationMultiplier = 0.75f)
        val onTertiaryContainer = tertiaryDark.tone(0.90f).ensureContrast(tertiaryContainer, 4.5f, darken = false)

        val onBackground = Color(0xFFE6E1E5).ensureContrast(bg, 7f, darken = false)
        val surfaceVariant = primaryDark.tone(0.24f, saturationMultiplier = 0.22f)
        val onSurfaceVariant = Color(0xFFCAC4D0).ensureContrast(surfaceVariant, 4.5f, darken = false)
        val outline = secondaryDark.tone(0.62f, saturationMultiplier = 0.30f).ensureContrast(bg, 3f, darken = false)
        val outlineVariant = primaryDark.tone(0.32f, saturationMultiplier = 0.25f)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = bg,
            onBackground = onBackground,
            surface = bg,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = backgroundLight,
            inverseOnSurface = Color(0xFF313033),
            inversePrimary = primaryLight,
            surfaceContainerLowest = bg.darken(0.2f),
            surfaceContainerLow = primaryDark.tone(0.14f, saturationMultiplier = 0.18f),
            surfaceContainer = primaryDark.tone(0.17f, saturationMultiplier = 0.20f),
            surfaceContainerHigh = primaryDark.tone(0.20f, saturationMultiplier = 0.22f),
            surfaceContainerHighest = primaryDark.tone(0.24f, saturationMultiplier = 0.25f),
        )
    }
}

/**
 * Main App Theme Component
 */
@Composable
fun VidMaxTheme(
    appTheme: AppTheme,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        appTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme && amoledMode) dynamicDarkColorScheme(context).copy(background = Color.Black, surface = Color.Black)
            else if (useDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        useDarkTheme && amoledMode -> appTheme.getDarkColorScheme().copy(background = Color.Black, surface = Color.Black)
        useDarkTheme -> appTheme.getDarkColorScheme()
        else -> appTheme.getLightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
