package com.titradeck.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Composable
fun TitraTheme(appearance: String, backgroundHex: String?, themeHex: String? = null, content: @Composable () -> Unit) {
    val requestedDark = when (appearance) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val customBackground = BackgroundColors.parseColor(backgroundHex)
    // A dark custom background needs dark tonal surfaces even if the OS is light,
    // so text fields, dialogs and cards remain readable alongside the exact color.
    val dark = customBackground?.let(BackgroundColors::prefersDarkSurfaces) ?: requestedDark
    val context = LocalContext.current
    val accent = BackgroundColors.parseColor(themeHex)
    val basePalette = if (accent != null) {
        remember(accent, dark) {
            BackgroundColors.withAccent(if (dark) darkColorScheme() else lightColorScheme(), accent)
        }
    } else if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(primary = Color(0xFF8BD5BF), onPrimary = Color(0xFF00382B),
            primaryContainer = Color(0xFF00513F), onPrimaryContainer = Color(0xFFA7F2DB),
            secondaryContainer = Color(0xFF354B43), onSecondaryContainer = Color(0xFFD0E8DD))
    } else {
        lightColorScheme(primary = Color(0xFF166B58), onPrimary = Color.White,
            primaryContainer = Color(0xFFA7F2DB), onPrimaryContainer = Color(0xFF002117),
            secondaryContainer = Color(0xFFD0E8DD), onSecondaryContainer = Color(0xFF0C2018))
    }
    val palette = remember(basePalette, customBackground) {
        customBackground?.let { BackgroundColors.withBackground(basePalette, it) } ?: basePalette
    }
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !BackgroundColors.prefersDarkSurfaces(palette.background)
                isAppearanceLightNavigationBars = !BackgroundColors.prefersDarkSurfaces(palette.surfaceContainer)
            }
        }
    }
    val base = Typography()
    val typography = base.copy(
        headlineLarge = base.headlineLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 25.sp))
    MaterialTheme(colorScheme = palette, typography = typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)), content = content)
}
