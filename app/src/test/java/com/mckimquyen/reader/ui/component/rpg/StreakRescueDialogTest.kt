package com.mckimquyen.reader.ui.component.rpg

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer
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
class StreakRescueDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val offer = StreakRescueOffer(streakDays = 5, missedDays = 3)

    @Test
    fun dialog_rendersFormattedMessage_andInvokesAdCallback() {
        var adClicked = 0
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = null,
                onRescueWithAd = { adClicked++ },
                onRestartFromScratch = {},
                onDismissLater = {},
            )
        }

        compose.onNodeWithTag(STREAK_RESCUE_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText("You missed 3 days of reading. Your 5-day streak will be reset on your next read.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(STREAK_RESCUE_AD_TAG).performClick()
        assertEquals(1, adClicked)
    }

    @Test
    fun restartButton_invokesRestartCallback() {
        var restarted = 0
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = null,
                onRescueWithAd = {},
                onRestartFromScratch = { restarted++ },
                onDismissLater = {},
            )
        }

        compose.onNodeWithTag(STREAK_RESCUE_RESTART_TAG).performClick()
        assertEquals(1, restarted)
    }

    @Test
    fun laterButton_invokesDismissCallback() {
        var dismissed = 0
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = null,
                onRescueWithAd = {},
                onRestartFromScratch = {},
                onDismissLater = { dismissed++ },
            )
        }

        compose.onNodeWithTag(STREAK_RESCUE_LATER_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun loadingAd_disablesActions() {
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = true,
                errorMessage = null,
                onRescueWithAd = {},
                onRestartFromScratch = {},
                onDismissLater = {},
            )
        }

        compose.onNodeWithTag(STREAK_RESCUE_AD_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(STREAK_RESCUE_RESTART_TAG).assertIsNotEnabled()
    }

    @Test
    fun errorState_showsMessage() {
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = "Ad failed to load",
                onRescueWithAd = {},
                onRestartFromScratch = {},
                onDismissLater = {},
            )
        }

        compose.onNodeWithText("Ad failed to load").assertIsDisplayed()
    }
}
