package com.mckimquyen.reader.ui.page.rsvp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpCalibrationDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test
    fun intro_showsTitleAndStartButton_andStartInvokesCallback() {
        var started = 0
        compose.setContent {
            RsvpCalibrationScreen(
                step = CalibrationStep.Intro,
                onStart = { started++ },
                onPassageFinished = {},
                onSubmitAnswers = {},
                onApply = {},
                onSkip = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText(str(R.string.zen_calibration_intro_title), substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ZEN_CALIBRATION_START_TAG).assertIsDisplayed()
        compose.onNodeWithTag(ZEN_CALIBRATION_START_TAG).performClick()

        assertEquals(1, started)
    }

    @Test
    fun readingStep_showsRoundHeaderWithWpm() {
        compose.setContent {
            RsvpCalibrationScreen(
                step = CalibrationStep.Reading(roundIndex = 0, wpm = 300, text = "Short sample"),
                onStart = {},
                onPassageFinished = {},
                onSubmitAnswers = {},
                onApply = {},
                onSkip = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText(str(R.string.zen_calibration_round_header, 1, 300)).assertIsDisplayed()
        compose.onNodeWithText("300 WPM").assertIsDisplayed()
    }
}
