package com.titradeck.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.titradeck.app.ui.BackgroundColors
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class AppearanceColorsTest {
    @Test fun acceptsOnlyCompleteOpaqueSrgbColors() {
        assertEquals("#12ABEF", BackgroundColors.normalize("  #12abEF  "))
        assertEquals("#FFFFFF", BackgroundColors.normalize("ffffff"))
        assertEquals(0xFF12ABEF.toInt(), BackgroundColors.parseColor("#12ABEF")!!.toArgb())
        listOf("", "#123", "#12345678", "#GG0000", "##123456", "red", "12 3456", "＃123456").forEach {
            assertNull(it, BackgroundColors.normalize(it))
        }
        assertNull(BackgroundColors.parseColor(null))
    }

    @Test fun foregroundAndControlsStayReadableAcrossSaturatedBackgrounds() {
        val samples = listOf("#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF",
            "#FFFF00", "#FF00FF", "#00FFFF", "#777777", "#7F00FF", "#F4F6FA", "#172033")
        samples.forEach { hex ->
            val background = BackgroundColors.parseColor(hex)!!
            val base = if (BackgroundColors.prefersDarkSurfaces(background)) darkColorScheme() else lightColorScheme()
            val scheme = BackgroundColors.withBackground(base, background)
            assertEquals("Do not alter the chosen background", background, scheme.background)
            val foregrounds = listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.secondary, scheme.tertiary, scheme.error)
            val surfaces = listOf(background, scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHighest)
            foregrounds.forEach { foreground -> surfaces.forEach { surface ->
                assertTrue("$hex: foreground ${foreground.toArgb()} is unreadable", wcag(foreground, surface) >= 4.49)
            } }
            assertTrue(wcag(scheme.onPrimary, scheme.primary) >= 4.49)
            assertTrue(wcag(scheme.onError, scheme.error) >= 4.49)
            assertTrue(wcag(scheme.outline, background) >= 2.99)
        }
    }

    @Test fun backgroundBrightnessSelectsReadableTonalFamily() {
        assertTrue(BackgroundColors.prefersDarkSurfaces(Color.Black))
        assertTrue(BackgroundColors.prefersDarkSurfaces(Color.Blue))
        assertFalse(BackgroundColors.prefersDarkSurfaces(Color.White))
        assertFalse(BackgroundColors.prefersDarkSurfaces(Color.Yellow))
        assertEquals(21.0, wcag(Color.Black, Color.White), .0001)
    }

    // Independent sRGB calculation, rather than checking the helper against itself.
    private fun wcag(first: Color, second: Color): Double {
        fun luminance(color: Color): Double {
            fun linear(channel: Float): Double = channel.toDouble().let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
            return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
        }
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
