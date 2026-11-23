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
}
