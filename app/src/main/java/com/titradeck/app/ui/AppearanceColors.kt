package com.titradeck.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.util.Locale
import kotlin.math.pow

/** Opaque sRGB backgrounds only: alpha would make contrast depend on unseen layers. */
object BackgroundColors {
    fun normalize(input: String): String? {
        val value = input.trim().removePrefix("#")
        return if (value.matches(Regex("[0-9a-fA-F]{6}"))) "#${value.uppercase(Locale.ROOT)}" else null
    }

    fun parseColor(input: String?): Color? = input?.let(::normalize)?.let {
        Color(0xFF000000L or it.substring(1).toLong(16))
    }

    fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }

    fun prefersDarkSurfaces(background: Color): Boolean =
        contrast(Color.White, background) > contrast(Color.Black, background)

    internal fun withAccent(palette: ColorScheme, seed: Color): ColorScheme {
        val base = palette.copy(
            background = palette.background.neutral(),
            onBackground = palette.onBackground.neutral(),
            surface = palette.surface.neutral(),
            onSurface = palette.onSurface.neutral(),
            surfaceVariant = palette.surfaceVariant.neutral(),
            onSurfaceVariant = palette.onSurfaceVariant.neutral(),
            surfaceBright = palette.surfaceBright.neutral(),
            surfaceDim = palette.surfaceDim.neutral(),
            surfaceContainerLowest = palette.surfaceContainerLowest.neutral(),
            surfaceContainerLow = palette.surfaceContainerLow.neutral(),
            surfaceContainer = palette.surfaceContainer.neutral(),
            surfaceContainerHigh = palette.surfaceContainerHigh.neutral(),
            surfaceContainerHighest = palette.surfaceContainerHighest.neutral(),
            inverseSurface = palette.inverseSurface.neutral(),
            inverseOnSurface = palette.inverseOnSurface.neutral(),
            outline = palette.outline.neutral(),
            outlineVariant = palette.outlineVariant.neutral(),
        )
        val dark = prefersDarkSurfaces(base.surface)
        val ink = if (dark) Color.White else Color.Black
        val surfaces = listOf(base.surface, base.surfaceContainer, base.surfaceContainerHigh, base.surfaceContainerHighest)
        val primary = fit(seed, surfaces, ink, 4.6)
        val muted = lerp(seed, base.onSurface, .2f)
        val secondary = fit(muted, surfaces, ink, 4.6)
        val primaryContainer = lerp(base.surface, seed, if (dark) .40f else .22f)
        val secondaryContainer = lerp(base.surface, seed, if (dark) .27f else .12f)
        val inverse = fit(seed, listOf(base.inverseSurface), if (dark) Color.Black else Color.White, 4.6)
        return base.copy(
            primary = primary,
            onPrimary = foregroundFor(base.onPrimary, primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = foregroundFor(base.onSurface, primaryContainer),
            inversePrimary = inverse,
            secondary = secondary,
            onSecondary = foregroundFor(base.onSecondary, secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = foregroundFor(base.onSurface, secondaryContainer),
            tertiary = secondary,
            onTertiary = foregroundFor(base.onTertiary, secondary),
            tertiaryContainer = secondaryContainer,
            onTertiaryContainer = foregroundFor(base.onSurface, secondaryContainer),
            surfaceTint = primary,
        )
    }

    /**
     * The caller chooses the system's light/dark palette with prefersDarkSurfaces.
     * Keep its accent hue and tonal containers; only adjust ink brightness where
     * the chosen background would make system-colored labels hard to read.
     */
    fun withBackground(base: ColorScheme, background: Color): ColorScheme {
        val surfaces = listOf(background, base.surface, base.surfaceContainer,
            base.surfaceContainerLow, base.surfaceContainerHigh, base.surfaceContainerHighest)
        val ink = if (prefersDarkSurfaces(background)) Color.White else Color.Black
        val primary = fit(base.primary, surfaces, ink, 4.5)
        val secondary = fit(base.secondary, surfaces, ink, 4.5)
        val tertiary = fit(base.tertiary, surfaces, ink, 4.5)
        val error = fit(base.error, surfaces, ink, 4.5)
        return base.copy(
            background = background,
            onBackground = fit(base.onBackground, listOf(background), ink, 4.5),
            onSurface = fit(base.onSurface, surfaces, ink, 4.5),
            onSurfaceVariant = fit(base.onSurfaceVariant, surfaces, ink, 4.5),
            primary = primary, onPrimary = foregroundFor(base.onPrimary, primary),
            secondary = secondary, onSecondary = foregroundFor(base.onSecondary, secondary),
            tertiary = tertiary, onTertiary = foregroundFor(base.onTertiary, tertiary),
            error = error, onError = foregroundFor(base.onError, error),
            outline = fit(base.outline, surfaces, ink, 3.0),
            outlineVariant = fit(base.outlineVariant, surfaces, ink, 3.0),
            surfaceTint = primary,
        )
    }

    private fun foregroundFor(preferred: Color, fill: Color): Color = fit(preferred, listOf(fill),
        if (prefersDarkSurfaces(fill)) Color.White else Color.Black, 4.5)

    private fun Color.neutral(): Color {
        val light = luminance()
        // Convert linear luminance back to an sRGB channel to preserve surface brightness.
        val gray = if (light <= .0031308f) 12.92f * light else 1.055f * light.pow(1f / 2.4f) - .055f
        return Color(gray, gray, gray, alpha)
    }

    private fun fit(preferred: Color, backgrounds: List<Color>, ink: Color, minimum: Double): Color {
        fun passes(candidate: Color) = backgrounds.all { contrast(candidate, it) >= minimum }
        if (passes(preferred)) return preferred
        // Blend channels toward black/white without introducing a different hue.
        fun mix(amount: Float) = Color(
            red = preferred.red + (ink.red - preferred.red) * amount,
            green = preferred.green + (ink.green - preferred.green) * amount,
            blue = preferred.blue + (ink.blue - preferred.blue) * amount,
            alpha = 1f)
        var low = 0f
        var high = 1f
        repeat(24) {
            val middle = (low + high) / 2f
            if (passes(mix(middle))) high = middle else low = middle
        }
        return mix(high)
    }
}
