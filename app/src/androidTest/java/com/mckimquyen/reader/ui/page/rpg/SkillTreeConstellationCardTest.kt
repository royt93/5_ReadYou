package com.mckimquyen.reader.ui.page.rpg

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.model.rpg.SkillTreeLayout
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SkillTreeConstellationCardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id)

    @Test
    fun decayedNode_tapShowsDetailAndRecoveryActionForThatCategory() {
        val recovered = mutableListOf<String>()
        compose.setContent {
            SkillTreeConstellationCard(
                progress = UserProgress(
                    categoryXp = mapOf(QuizGeneratorService.CATEGORY_TECH to 90L),
                    categoryBaseXp = mapOf(QuizGeneratorService.CATEGORY_TECH to 100L),
                ),
                onRecover = recovered::add,
            )
        }

        compose.onNodeWithTag(SKILL_TREE_NODE_TAG_PREFIX + QuizGeneratorService.CATEGORY_TECH).performClick()
        compose.onNodeWithTag(SKILL_TREE_DETAIL_DIALOG_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("90 XP").assertCountEquals(2)
        compose.onNodeWithText(str(R.string.brain_rpg_recover_action)).performClick()

        assertEquals(listOf(QuizGeneratorService.CATEGORY_TECH), recovered)
    }

    @Test
    fun freshNode_tapShowsDetailWithoutRecoveryAction() {
        compose.setContent {
            SkillTreeConstellationCard(
                progress = UserProgress(
                    categoryXp = mapOf(QuizGeneratorService.CATEGORY_TECH to 100L),
                    categoryBaseXp = mapOf(QuizGeneratorService.CATEGORY_TECH to 100L),
                ),
                onRecover = { throw AssertionError("A fresh category must not be recoverable") },
            )
        }

        compose.onNodeWithTag(SKILL_TREE_NODE_TAG_PREFIX + QuizGeneratorService.CATEGORY_TECH).performClick()
        compose.onNodeWithTag(SKILL_TREE_DETAIL_DIALOG_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("100 XP").assertCountEquals(2)
        compose.onAllNodesWithText(str(R.string.brain_rpg_recover_action)).assertCountEquals(0)
    }

    @Test
    fun pendingRecoveryDisablesRecoveryButton() {
        compose.setContent {
            SkillTreeConstellationCard(
                progress = UserProgress(
                    categoryXp = mapOf(QuizGeneratorService.CATEGORY_TECH to 90L),
                    categoryBaseXp = mapOf(QuizGeneratorService.CATEGORY_TECH to 100L),
                ),
                recoveringCategoryId = QuizGeneratorService.CATEGORY_TECH,
                onRecover = {},
            )
        }

        compose.onNodeWithTag(SKILL_TREE_NODE_TAG_PREFIX + QuizGeneratorService.CATEGORY_TECH).performClick()
        compose.onNodeWithText(str(R.string.brain_rpg_recover_pending)).assertIsNotEnabled()
    }

    @Test
    fun allSixNodesAndDecorativeCanvasArePresent() {
        compose.setContent { SkillTreeConstellationCard(UserProgress()) }

        compose.onNodeWithTag(SKILL_TREE_CONSTELLATION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SKILL_TREE_CANVAS_TAG).assertIsDisplayed()
        assertTrue(SkillTreeLayout.nodes.size == 6)
    }
}
