package com.mckimquyen.reader.ui.page.rsvp

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mckimquyen.reader.domain.rsvp.CalibrationQuestion
import com.mckimquyen.reader.domain.rsvp.Confidence
import com.mckimquyen.reader.domain.rsvp.RsvpSuggestion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RsvpCalibrationDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val questions = listOf(
        CalibrationQuestion("Question one", listOf("A1", "B1", "C1", "D1"), 0),
        CalibrationQuestion("Question two", listOf("A2", "B2", "C2", "D2"), 1),
    )

    /** The options sit inside a scrolling column, so bring the card into view before tapping it. */
    private fun pick(question: Int, option: Int) {
        compose.onNodeWithTag("${ZEN_CALIBRATION_OPTION_TAG_PREFIX}${question}_$option")
            .performScrollTo()
            .performClick()
    }

    private fun show(
        step: CalibrationStep,
        onStart: () -> Unit = {},
        onPassageFinished: () -> Unit = {},
        onSubmit: (List<Int>) -> Unit = {},
        onApply: () -> Unit = {},
        onSkip: () -> Unit = {},
    ) = compose.setContent {
        RsvpCalibrationScreen(
            step = step,
            onStart = onStart,
            onPassageFinished = onPassageFinished,
            onSubmitAnswers = onSubmit,
            onApply = onApply,
            onSkip = onSkip,
            onDismiss = {},
        )
    }

    @Test
    fun intro_showsTheHonestLimitation_andStartFiresOnce() {
        var started = 0
        show(CalibrationStep.Intro, onStart = { started++ })

        compose.onNodeWithText("rough estimate", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ZEN_CALIBRATION_START_TAG).performClick()
        assertEquals(1, started)
    }

    @Test
    fun questions_submitIsDisabledUntilEveryQuestionIsAnswered() {
        show(CalibrationStep.Questions(roundIndex = 0, wpm = 300, questions = questions))

        compose.onNodeWithTag(ZEN_CALIBRATION_SUBMIT_TAG).assertIsNotEnabled()
        pick(0, 0)
        compose.onNodeWithTag(ZEN_CALIBRATION_SUBMIT_TAG).assertIsNotEnabled()
        pick(1, 1)
        compose.onNodeWithTag(ZEN_CALIBRATION_SUBMIT_TAG).assertIsEnabled()
    }

    @Test
    fun questions_submitReportsTheChosenIndicesInOrder() {
        var submitted: List<Int>? = null
        show(CalibrationStep.Questions(roundIndex = 1, wpm = 450, questions = questions), onSubmit = { submitted = it })

        pick(0, 2)
        pick(1, 3)
        compose.onNodeWithTag(ZEN_CALIBRATION_SUBMIT_TAG).performScrollTo().performClick()

        assertEquals(listOf(2, 3), submitted)
    }

    @Test
    fun questions_changingAnAnswerReplacesTheEarlierChoice() {
        var submitted: List<Int>? = null
        show(CalibrationStep.Questions(roundIndex = 0, wpm = 300, questions = questions), onSubmit = { submitted = it })

        pick(0, 0)
        pick(0, 3)
        pick(1, 1)
        compose.onNodeWithTag(ZEN_CALIBRATION_SUBMIT_TAG).performScrollTo().performClick()

        assertEquals(listOf(3, 1), submitted)
    }

    @Test
    fun result_showsSuggestedSpeedAndLimitation_applyAndSkipFireOnce() {
        var applied = 0
        var skipped = 0
        val suggestion = RsvpSuggestion(450, 1.0f, Confidence.MEDIUM, listOf(300, 450))
        show(CalibrationStep.Result(suggestion), onApply = { applied++ }, onSkip = { skipped++ })

        compose.onNodeWithText("450", substring = true).assertIsDisplayed()
        compose.onNodeWithText("rough estimate", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ZEN_CALIBRATION_APPLY_TAG).performClick()
        compose.onNodeWithTag(ZEN_CALIBRATION_SKIP_TAG).performClick()
        assertEquals(1, applied)
        assertEquals(1, skipped)
    }

    @Test
    fun result_lowConfidenceShowsTheExtraNote_mediumDoesNot() {
        val low = RsvpSuggestion(600, 1.5f, Confidence.LOW, listOf(600))
        show(CalibrationStep.Result(low))
        compose.onNodeWithText("answers varied", substring = true).assertIsDisplayed()
    }

    @Test
    fun result_mediumConfidenceHidesTheLowConfidenceNote() {
        val medium = RsvpSuggestion(450, 1.0f, Confidence.MEDIUM, listOf(300, 450))
        show(CalibrationStep.Result(medium))
        compose.onAllNodesWithText("answers varied", substring = true).assertCountEquals(0)
    }
}
