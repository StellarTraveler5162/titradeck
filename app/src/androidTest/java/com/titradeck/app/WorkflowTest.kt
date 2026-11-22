package com.titradeck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetAppearance() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("appearance", 0).edit().clear().commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test fun formulaCalculationPersistsAndRestoresInputs() {
        compose.onNodeWithText("解析一个化学式  →").performClick()
        compose.onNodeWithText("化学式", substring = false).performTextReplacement("C6H12O6")
        compose.onNodeWithText("计算并保存").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("180.156", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("历史", substring = false).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("恢复输入并查看过程  →").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("恢复输入并查看过程  →")[0].performClick()
        compose.onNodeWithTag("calculator_list").performScrollToNode(hasText("化学式"))
        compose.onNodeWithText("化学式", substring = false).assertTextContains("C6H12O6")
        compose.onNodeWithText("化学式", substring = false).performTextReplacement("Xx2")
        compose.onNodeWithText("计算并保存").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("未知元素", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun elementDetailsAndAppearanceModeWork() {
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithText("搜索符号、名称或原子序数").performTextInput("Fe")
        compose.onNodeWithText("铁 · Iron").performClick()
        compose.onNodeWithText("原子序数", substring = false).assertExists()
        compose.onNodeWithText("关闭", substring = false).performClick()
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("外观模式"))
        compose.onNodeWithText("深色", substring = false).performClick()
        compose.onNodeWithText("计算", substring = false).performClick()
        compose.onNodeWithText("化学，有据可算").assertExists()
    }

    @Test fun returningFromToolRestoresHomeScrollAndTabSwitchKeepsIt() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("‹  返回", substring = false).performClick()
        compose.onNodeWithText("化合物查询", substring = false).assertIsDisplayed()
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithText("计算", substring = false).performClick()
        compose.onNodeWithText("化合物查询", substring = false).assertIsDisplayed()
    }

    @Test fun pubChemLookupShowsPropertiesAndStructure() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("查询内容").performTextInput("乙醇")
        compose.onNodeWithText("查询 PubChem").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("PubChem CID 702", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("IUPAC 名称  ethanol").assertExists()
        val list = compose.onNodeWithTag("compound_lookup_list")
        list.performScrollToNode(hasTestTag("show_compound_model"))
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_702").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("show_compound_model").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_structure_702").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun chineseInorganicNameShowsDepiction() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("查询内容").performTextInput("氯化钠")
        compose.onNodeWithText("查询 PubChem").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("PubChem CID 5234", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("compound_lookup_list").performScrollToNode(hasTestTag("show_compound_model"))
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_5234").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun selectingIsomersUpdatesDetailsAndStructure() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("分子式", substring = false).performClick()
        compose.onNodeWithText("查询内容").performTextInput("C2H6O")
        compose.onNodeWithText("查询 PubChem").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("第 1 页").fetchSemanticsNodes().isNotEmpty() }
        val list = compose.onNodeWithTag("compound_lookup_list")
        list.performScrollToNode(hasText("methoxymethane", substring = false))
        compose.onNodeWithText("methoxymethane", substring = false).performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("PubChem CID 8254", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SMILES  COC").assertIsDisplayed()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_8254").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("ethanol", substring = false))
        compose.onNodeWithText("ethanol", substring = false).performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("PubChem CID 702", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SMILES  CCO").assertIsDisplayed()
        compose.onNodeWithTag("compound_depiction_8254").assertDoesNotExist()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_702").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("下一页", substring = false))
        compose.onNodeWithText("下一页").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("第 2 页").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("CID 12201690", substring = true))
        compose.onNodeWithText("ethanol", substring = false).assertDoesNotExist()
        list.performScrollToNode(hasText("上一页", substring = false))
        compose.onNodeWithText("上一页").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("第 1 页").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("ethanol", substring = false))
        compose.onNodeWithText("ethanol", substring = false).assertIsDisplayed()
    }

    @Test fun referenceSearchSurvivesOpeningCalculator() {
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithText("常数与参考值").performClick()
        compose.onNodeWithText("搜索常数、电势、Ksp 或 pKa").performTextInput("乙酸")
        compose.onNodeWithText("用于计算").performClick()
        compose.onNodeWithText("‹  返回", substring = false).performClick()
        compose.onNodeWithText("搜索常数、电势、Ksp 或 pKa").assertTextContains("乙酸")
        compose.onNodeWithText("用于计算").assertIsDisplayed()
    }

}
