package com.titradeck.app

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.titradeck.app.ui.TitraTheme
import com.titradeck.app.ui.SettingsScreen
import com.titradeck.app.ui.StartupAnimation
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AppearanceThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemAccentIsUsedAndCustomBackgroundsKeepTextReadable() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val observed = AtomicReference<ColorScheme>()
        var background by mutableStateOf<String?>(null)
        compose.setContent {
            TitraTheme(appearance = "light", backgroundHex = background) {
                val colors = MaterialTheme.colorScheme
                SideEffect { observed.set(colors) }
                Text("主题验证")
            }
        }
        compose.waitForIdle()
        assertEquals("Default theme must use the system dynamic accent", dynamicLightColorScheme(context).primary.toArgb(), observed.get().primary.toArgb())
        listOf("#101820", "#E8F2FF", "#FF0000").forEach { hex ->
            compose.runOnIdle { background = hex }
            compose.waitForIdle()
            val colors = observed.get()
            assertEquals(android.graphics.Color.parseColor(hex), colors.background.toArgb())
            assertContrast(colors.onBackground, colors.background, "page text on $hex")
            assertContrast(colors.onSurface, colors.surface, "surface text on $hex")
            assertContrast(colors.onSurface, colors.surfaceContainer, "card text on $hex")
            assertContrast(colors.onPrimary, colors.primary, "primary button text on $hex")
        }
        compose.runOnIdle { background = null }
        compose.waitForIdle()
        assertEquals(dynamicLightColorScheme(context).primary.toArgb(), observed.get().primary.toArgb())
        assertEquals(dynamicLightColorScheme(context).background.toArgb(), observed.get().background.toArgb())
    }

    @Test fun customAccentStylesControlsWithAnIndependentBackground() {
        val observed = AtomicReference<ColorScheme>()
        var themeHex by mutableStateOf("#166B58")
        compose.setContent {
            TitraTheme(appearance = "light", backgroundHex = "#E8F2FF", themeHex = themeHex) {
                val colors = MaterialTheme.colorScheme
                SideEffect { observed.set(colors) }
                Text("主题验证")
            }
        }
        compose.waitForIdle()
        val first = observed.get()
        compose.runOnIdle { themeHex = "#AF3F6F" }
        compose.waitForIdle()
        val second = observed.get()
        assertNotEquals(first.primary, second.primary)
        assertNotEquals(first.secondaryContainer, second.secondaryContainer)
        assertEquals(first.background, second.background)
        assertContrast(second.primary, second.surface, "accent on surface")
        assertContrast(second.onPrimary, second.primary, "accent button text")
        assertContrast(second.onSecondaryContainer, second.secondaryContainer, "selected option text")
    }

    @Test fun aboutButtonsOpenTheirExactProfileUrls() {
        val opened = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                SettingsScreen(null, "system", null, {}, {}, {})
            }
        }
        val list = compose.onNodeWithTag("settings_list")
        list.performScrollToNode(hasText("开发者：東站"))
        compose.onNodeWithText("化学工作台 · ${BuildConfig.VERSION_NAME}").assertExists()
        for (name in listOf("个人网站", "Bilibili", "X", "GitHub")) {
            list.performScrollToNode(hasContentDescription(name))
            compose.onNodeWithContentDescription(name).performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf(
                "https://blog.dongzhan.ink",
                "https://space.bilibili.com/521110373",
                "https://x.com/dongzhan4137",
                "https://github.com/dongzhan4137",
            ), opened)
        }
    }

    @Test fun startupShowsCenteredWordmarkBelowTheIconThenRevealsContent() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            StartupAnimation(showOnLaunch = true) { Text("首页") }
        }
        val icon = compose.onNodeWithTag("startup_icon").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("TITRADECK").fetchSemanticsNode().boundsInRoot
        assertTrue(label.top > icon.bottom)
        assertEquals(icon.center.x, label.center.x, 1f)
        compose.mainClock.advanceTimeBy(750)
        compose.onNodeWithText("首页").assertIsDisplayed()
        compose.onNodeWithText("TITRADECK").assertDoesNotExist()
    }

    private fun assertContrast(foreground: Color, background: Color, description: String) {
        val contrast = ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
        assertTrue("$description contrast is only $contrast", contrast >= 4.5)
    }
}
