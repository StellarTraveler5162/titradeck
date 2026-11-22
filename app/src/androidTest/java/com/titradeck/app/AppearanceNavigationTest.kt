package com.titradeck.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val preferences get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("appearance", 0)

    @Before fun resetAppearance() {
        preferences.edit().clear().commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test fun customBackgroundPersistsRejectsInvalidInputAndResets() {
        capture("system-theme")
        openSettings()
        listOf("界面风格", "澎湃 HyperOS", "ColorOS", "OriginOS", "MagicOS", "Flyme").forEach {
            compose.onNodeWithText(it, substring = false).assertDoesNotExist()
        }
        selectCustomBackground()
        compose.onNodeWithTag("background_hex").performTextReplacement("e8f2ff")
        applyBackground()
        assertEquals("#E8F2FF", preferences.getString("backgroundHex", null))
        compose.onNodeWithTag("nav_item_0").performClick()
        capture("custom-background")

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals("#E8F2FF", preferences.getString("backgroundHex", null))
        openSettings()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("background_hex"))
        compose.onNodeWithTag("background_hex").assertTextContains("#E8F2FF")
        compose.onNodeWithTag("background_hex").performTextReplacement("#12GG34")
        applyBackground()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("请输入 6 位十六进制颜色，例如 #F4F6FA"))
        compose.onNodeWithText("请输入 6 位十六进制颜色，例如 #F4F6FA").assertExists()
        assertEquals("An invalid edit must not replace the saved background", "#E8F2FF", preferences.getString("backgroundHex", null))

        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("跟随系统背景"))
        compose.onNodeWithText("跟随系统背景", substring = false).performClick()
        assertFalse("System background must remove the stored override", preferences.contains("backgroundHex"))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertFalse(preferences.contains("backgroundHex"))
    }

    @Test fun darkCustomBackgroundAndPresetsAreApplied() {
        openSettings()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("外观模式"))
        compose.onNodeWithText("浅色", substring = false).performClick()
        selectCustomBackground()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("background_preset_FFF8EE"))
        compose.onNodeWithTag("background_preset_FFF8EE").performClick()
        assertEquals("#FFF8EE", preferences.getString("backgroundHex", null))
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("background_hex"))
        compose.onNodeWithTag("background_hex").performTextReplacement("#101820")
        applyBackground()
        assertEquals("#101820", preferences.getString("backgroundHex", null))
        compose.onNodeWithTag("nav_item_0").performClick()
        capture("dark-background")
        openSettings()
        capture("appearance-settings")
    }

    @Test fun customThemeColorPersistsAndCanReturnToSystem() {
        openSettings()
        val list = compose.onNodeWithTag("settings_list")
        list.performScrollToNode(hasText("自定义主题色"))
        compose.onNodeWithText("自定义主题色", substring = false).performClick()
        list.performScrollToNode(hasTestTag("theme_preset_2865A6"))
        compose.onNodeWithTag("theme_preset_2865A6").performClick()
        assertEquals("#2865A6", preferences.getString("themeHex", null))
        list.performScrollToNode(hasTestTag("theme_hex"))
        compose.onNodeWithTag("theme_hex").performTextReplacement("AF3F6F")
        list.performScrollToNode(hasTestTag("apply_theme"))
        compose.onNodeWithTag("apply_theme").performClick()
        assertEquals("#AF3F6F", preferences.getString("themeHex", null))
        assertFalse(preferences.contains("backgroundHex"))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        list.performScrollToNode(hasTestTag("theme_hex"))
        compose.onNodeWithTag("theme_hex").assertTextContains("#AF3F6F")
        list.performScrollToNode(hasText("跟随系统主题色"))
        compose.onNodeWithText("跟随系统主题色", substring = false).performClick()
        assertFalse(preferences.contains("themeHex"))
    }

    @Test fun bottomNavigationHasEqualCenteredIconsAndLabelsInEverySelection() {
        val density = compose.activity.resources.displayMetrics.density
        val tolerance = 1.6f
        for (selected in 0..3) {
            compose.onNodeWithTag("nav_item_$selected").performClick()
            compose.waitForIdle()
            val items = (0..3).map { compose.onNodeWithTag("nav_item_$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            val icons = (0..3).map { compose.onNodeWithTag("nav_icon_$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            val labels = (0..3).map { compose.onNodeWithTag("nav_label_$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            for (index in 0..3) {
                assertEquals("All nav targets must have equal width", items[0].width, items[index].width, tolerance)
                assertTrue("Nav touch target is too short", items[index].height >= 48f * density)
                assertEquals("Icon width must remain 24 dp", 24f * density, icons[index].width, tolerance)
                assertEquals("Icon height must remain 24 dp", 24f * density, icons[index].height, tolerance)
                assertEquals("Icon is not horizontally centered in its button", items[index].center.x, icons[index].center.x, tolerance)
                assertEquals("Label is not horizontally centered in its button", items[index].center.x, labels[index].center.x, tolerance)
                assertEquals("Icons are not on the same vertical center", icons[0].center.y, icons[index].center.y, tolerance)
                assertEquals("Labels are not on the same vertical center", labels[0].center.y, labels[index].center.y, tolerance)
                assertTrue("Label overlaps its icon", labels[index].top >= icons[index].bottom - tolerance)
            }
        }
        compose.onNodeWithTag("nav_item_0").performClick()
        capture("navigation")
    }

    private fun openSettings() {
        compose.onNodeWithTag("nav_item_3").performClick()
    }

    private fun selectCustomBackground() {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("自定义背景"))
        compose.onNodeWithText("自定义背景", substring = false).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("background_hex"))
    }

    private fun applyBackground() {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("apply_background"))
        compose.onNodeWithTag("apply_background").performClick()
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val destination = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TitraDeck")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }) ?: error("Could not create screenshot output")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        resolver.openOutputStream(destination)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        resolver.update(destination, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        bitmap.recycle()
    }
}
