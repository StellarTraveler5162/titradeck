package com.titradeck.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.titradeck.app.chemistry.MoleculeAtom
import com.titradeck.app.chemistry.MoleculeBond
import com.titradeck.app.chemistry.MoleculeModel
import com.titradeck.app.data.PubChemCompound
import com.titradeck.app.ui.CompoundDepictionView
import com.titradeck.app.ui.MoleculeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class CompoundViewerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun depictionCanPinchZoomResetAndRequestSave() {
        val compound = PubChemCompound(702, "ethanol", "C2H6O", "CCO", "46.07")
        val image = ImageBitmap(320, 320)
        var saveRequests = 0
        compose.setContent { MaterialTheme { CompoundDepictionView(compound, image, true) { saveRequests++ } } }

        compose.onNodeWithContentDescription("放大二维结构").performClick()
        compose.onNodeWithText("125%").assertIsDisplayed()
        compose.onNodeWithContentDescription("全屏").performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithContentDescription("退出全屏").assertIsDisplayed()
        compose.onNodeWithText("125%").assertIsDisplayed()
        compose.onNodeWithContentDescription("退出全屏").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("125%").assertIsDisplayed()
        compose.onNodeWithContentDescription("重置二维结构缩放").performClick()
        compose.onNodeWithTag("compound_depiction_702").performTouchInput {
            val left = Offset(width * .15f, 0f)
            val right = Offset(width * .3f, 0f)
            pinch(center - left, center - right, center + left, center + right)
        }
        compose.onNodeWithContentDescription("缩小二维结构").assertIsEnabled()
        compose.onNodeWithContentDescription("重置二维结构缩放").performClick()
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("保存到相册").performClick()
        compose.runOnIdle { assertEquals(1, saveRequests) }
    }

    @Test fun ballStickSliderRedrawsModelAndResets() {
        val model = MoleculeModel(
            listOf(MoleculeAtom("H", -1f, 0f), MoleculeAtom("O", 0f, 0f), MoleculeAtom("H", 1f, 0f)),
            listOf(MoleculeBond(0, 1), MoleculeBond(1, 2)),
            "Test model",
        )
        compose.setContent { MaterialTheme { MoleculeView(model, allowFullscreen = true) } }

        val canvas = compose.onNodeWithContentDescription("可拖动旋转的分子模型：Test model")
        val defaultSize = canvas.captureToImage().asAndroidBitmap()
        compose.onNodeWithContentDescription("球棍大小").performSemanticsAction(SemanticsActions.SetProgress) { it(.35f) }
        compose.onNodeWithText("35%").assertIsDisplayed()
        assertFalse(defaultSize.sameAs(canvas.captureToImage().asAndroidBitmap()))
        compose.onNodeWithContentDescription("全屏").performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithText("35%").assertIsDisplayed()
        val beforePan = canvas.captureToImage().asAndroidBitmap()
        canvas.performTouchInput {
            val separation = Offset(0f, height * .12f)
            val translation = Offset(width * .2f, 0f)
            pinch(center - separation, center - separation + translation, center + separation, center + separation + translation)
        }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 100%"))
        assertFalse(beforePan.sameAs(canvas.captureToImage().asAndroidBitmap()))
        repeat(3) {
            canvas.performTouchInput {
                val start = Offset(width * .06f, 0f)
                val end = Offset(width * .32f, 0f)
                pinch(center - start, center - end, center + start, center + end)
            }
        }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 800%"))
        compose.onNodeWithContentDescription("退出全屏").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 800%"))
        compose.onNodeWithText("复位").performClick()
        compose.onNodeWithText("100%").assertIsDisplayed()
        assertTrue(defaultSize.sameAs(canvas.captureToImage().asAndroidBitmap()))
    }

    @Test fun rotationKeepsViewportCenterFixedAfterZoomAndPan() {
        // Both atoms start on the plane through the center of the viewport.
        val x = .45f * cos(.3f)
        val z = .45f * sin(.3f)
        val model = MoleculeModel(
            listOf(MoleculeAtom("O", x, 0f, z), MoleculeAtom("N", -x, 0f, -z)),
            listOf(MoleculeBond(0, 1)),
            "Rotation test",
        )
        compose.setContent { MaterialTheme { MoleculeView(model, allowFullscreen = true) } }

        val canvas = compose.onNodeWithContentDescription("可拖动旋转的分子模型：Rotation test")
        fun atomCenter(color: Int): Offset {
            val image = canvas.captureToImage().asAndroidBitmap()
            var left = image.width
            var right = -1
            var top = image.height
            var bottom = -1
            for (row in 0 until image.height) {
                for (column in 0 until image.width) {
                    if (image.getPixel(column, row) != color) continue
                    left = minOf(left, column)
                    right = maxOf(right, column)
                    top = minOf(top, row)
                    bottom = maxOf(bottom, row)
                }
            }
            assertTrue("Expected visible atom", right >= left && bottom >= top)
            return Offset((left + right + 1) / 2f, (top + bottom + 1) / 2f)
        }

        canvas.performTouchInput {
            val start = Offset(width * .12f, 0f)
            val end = Offset(width * .20f, 0f)
            pinch(center - start, center - end, center + start, center + end)
        }
        val zoomState = canvas.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(zoomState != "缩放 100%")

        val oxygen = 0xFFDC4B4F.toInt()
        val nitrogen = 0xFF446DD9.toInt()
        val oxygenBeforePan = atomCenter(oxygen)
        canvas.performTouchInput {
            val separation = Offset(0f, height * .14f)
            val translation = center - oxygenBeforePan
            down(0, center - separation)
            down(1, center + separation)
            updatePointerTo(0, center - separation + translation)
            updatePointerTo(1, center + separation + translation)
            move()
            up(1)
            up(0)
        }
        val centeredOxygen = atomCenter(oxygen)
        val originalNitrogen = atomCenter(nitrogen)
        canvas.performTouchInput {
            swipe(center + Offset(-width * .2f, -height * .2f), center + Offset(width * .18f, -height * .2f))
        }
        assertTrue((atomCenter(oxygen) - centeredOxygen).getDistance() <= 2f)
        assertTrue((atomCenter(nitrogen) - originalNitrogen).getDistance() > 10f)
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, zoomState))
    }

    @Test fun doubleBondHasVisibleGapAndCanPassInFrontOfAnAtom() {
        // Place the test atoms in camera coordinates at the viewer's reset orientation.
        fun cameraAtom(symbol: String, x: Float, depth: Float): MoleculeAtom {
            val rotatedZ = depth * cos(.2f)
            return MoleculeAtom(
                symbol,
                x * cos(.3f) - rotatedZ * sin(.3f),
                depth * sin(.2f),
                x * sin(.3f) + rotatedZ * cos(.3f),
            )
        }

        val model = MoleculeModel(
            listOf(cameraAtom("H", -1.5f, -.8f), cameraAtom("H", 1.5f, .8f), cameraAtom("O", 0f, 0f)),
            listOf(MoleculeBond(0, 1, order = 2)),
            "Depth test",
        )
        compose.setContent { MaterialTheme { MoleculeView(model) } }

        val image = compose.onNodeWithContentDescription("可拖动旋转的分子模型：Depth test")
            .captureToImage().asAndroidBitmap()
        fun pixel(x: Int, y: Int): Int = with(compose.density) {
            image.getPixel(image.width / 2 + (x * density).roundToInt(), image.height / 2 + (y * density).roundToInt())
        }

        val background = pixel(-36, -10)
        assertEquals(background, pixel(-36, 0))
        assertTrue(background != pixel(-36, -4))
        assertTrue(background != pixel(-36, 4))
        val oxygenColor = 0xFFDC4B4F.toInt()
        assertEquals(oxygenColor, pixel(-12, 0))
        assertTrue(oxygenColor != pixel(-12, -4))
        assertTrue(oxygenColor != pixel(-12, 4))
        assertEquals(oxygenColor, pixel(12, -4))
        assertEquals(oxygenColor, pixel(12, 4))
    }
}
