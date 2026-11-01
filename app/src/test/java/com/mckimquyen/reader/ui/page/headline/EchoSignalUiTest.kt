package com.mckimquyen.reader.ui.page.headline

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.headline.ArticleSignalResult
import com.mckimquyen.reader.domain.headline.FlaggedPassage
import com.mckimquyen.reader.domain.headline.HeadlineLanguage
import com.mckimquyen.reader.domain.headline.HeadlineTone
import com.mckimquyen.reader.domain.headline.HypeCueCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoSignalUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun result(
        hype: Int,
        tone: HeadlineTone = HeadlineTone.UNCLEAR,
        passages: List<FlaggedPassage> = emptyList(),
    ) = ArticleSignalResult(HeadlineLanguage.ENGLISH, hype, tone, passages)

    private fun passage(text: String, score: Int = 80, vararg cues: HypeCueCategory) =
        FlaggedPassage(text, score, cues.toList().ifEmpty { listOf(HypeCueCategory.PROMOTIONAL_PHRASE) })

    @Test
    fun nullResult_rendersNothing() {
        rule.setContent { EchoSignalGauges(result = null, onClick = {}) }

        rule.onAllNodesWithTag(ECHO_GAUGES_TAG).assertCountEquals(0)
    }

    @Test
    fun nonNoteworthyResult_rendersNothing() {
        rule.setContent { EchoSignalGauges(result = result(hype = 10), onClick = {}) }

        rule.onAllNodesWithTag(ECHO_GAUGES_TAG).assertCountEquals(0)
    }

    @Test
    fun noteworthyHype_showsHypeBarAndPercent() {
        rule.setContent { EchoSignalGauges(result = result(hype = 55), onClick = {}) }

        rule.onNodeWithTag(ECHO_GAUGES_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ECHO_HYPE_BAR_TAG).assertIsDisplayed()
        rule.onNodeWithText("Hype: 55%").assertIsDisplayed()
    }

    @Test
    fun belowWarning_showsNoWarningCopy() {
        rule.setContent { EchoSignalGauges(result = result(hype = 55), onClick = {}) }

        rule.onAllNodesWithTag(ECHO_WARNING_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun atWarningThreshold_showsWarningCopy() {
        rule.setContent { EchoSignalGauges(result = result(hype = 70), onClick = {}) }

        // The gauge block is one merged button for TalkBack, so its children only exist in the unmerged tree.
        rule.onNodeWithTag(ECHO_WARNING_TAG, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Strong promotional or exaggerated wording detected", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun gaugeIsOneAccessibleButtonDescribingHypeAndTone() {
        rule.setContent { EchoSignalGauges(result = result(hype = 72, tone = HeadlineTone.NEGATIVE), onClick = {}) }

        val node = rule.onNodeWithTag(ECHO_GAUGES_TAG).fetchSemanticsNode()
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue(description, description.contains("Hype: 72%"))
        assertTrue(description, description.contains("Negative"))
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun gaugeMeetsTheMinimumTouchTarget() {
        rule.setContent { EchoSignalGauges(result = result(hype = 55), onClick = {}) }

        val heightPx = rule.onNodeWithTag(ECHO_GAUGES_TAG).fetchSemanticsNode().size.height
        val minPx = 48 * RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue("height=$heightPx min=$minPx", heightPx >= minPx.toInt())
    }

    @Test
    fun toneOnlySignal_showsToneAndHidesHypeBar() {
        rule.setContent { EchoSignalGauges(result = result(hype = 5, tone = HeadlineTone.POSITIVE), onClick = {}) }

        rule.onNodeWithTag(ECHO_TONE_BAR_TAG).assertIsDisplayed()
        rule.onNodeWithText("🟢 Positive").assertIsDisplayed()
        rule.onAllNodesWithTag(ECHO_HYPE_BAR_TAG).assertCountEquals(0)
    }

    @Test
    fun clickingTheGauges_invokesCallbackOnce() {
        var clicks = 0
        rule.setContent { EchoSignalGauges(result = result(hype = 60), onClick = { clicks++ }) }

        rule.onNodeWithTag(ECHO_GAUGES_TAG).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun sheet_showsTitleDisclaimerAndPassages() {
        val flagged = listOf(
            passage("Revolutionary game-changing platform!!!", 92, HypeCueCategory.PROMOTIONAL_PHRASE, HypeCueCategory.EXCLAMATION),
            passage("Guaranteed best ever results.", 61, HypeCueCategory.ABSOLUTE_CLAIM),
        )
        rule.setContent { EchoSignalDetailsSheet(result(hype = 85, passages = flagged), onClose = {}) }

        rule.onNodeWithText("Body Language Signals").assertIsDisplayed()
        rule.onNodeWithTag(ECHO_DISCLAIMER_TAG).assertIsDisplayed()
        rule.onNodeWithText("Flagged passages (2)").assertIsDisplayed()
        rule.onNodeWithTag(ECHO_PASSAGE_TAG_PREFIX + "0").assertIsDisplayed()
        rule.onNodeWithText("Revolutionary game-changing platform!!!", substring = true).assertIsDisplayed()
    }

    @Test
    fun sheet_disclaimerStatesTheLimits() {
        rule.setContent { EchoSignalDetailsSheet(result(hype = 85), onClose = {}) }

        rule.onNodeWithText("not a fact-check", substring = true).assertIsDisplayed()
        rule.onNodeWithText("not neutral", substring = true).assertIsDisplayed()
    }

    @Test
    fun sheet_withoutPassages_showsEmptyMessage() {
        rule.setContent { EchoSignalDetailsSheet(result(hype = 5, tone = HeadlineTone.NEGATIVE), onClose = {}) }

        rule.onNodeWithText("No individual paragraph crossed the Hype threshold.").assertIsDisplayed()
        rule.onAllNodesWithTag(ECHO_PASSAGE_TAG_PREFIX + "0").assertCountEquals(0)
    }

    @Test
    fun sheet_closeButtonInvokesCallback() {
        var closed = 0
        rule.setContent { EchoSignalDetailsSheet(result(hype = 85), onClose = { closed++ }) }

        rule.onNodeWithTag(ECHO_CLOSE_TAG).performClick()

        assertEquals(1, closed)
    }
}
