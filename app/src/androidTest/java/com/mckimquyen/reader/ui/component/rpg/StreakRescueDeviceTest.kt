package com.mckimquyen.reader.ui.component.rpg

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreakRescueDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val offer = StreakRescueOffer(streakDays = 5, missedDays = 3)

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun show(
        isLoadingAd: Boolean = false,
        errorMessage: String? = null,
        onAd: () -> Unit = {},
        onRestart: () -> Unit = {},
        onLater: () -> Unit = {},
    ) = compose.setContent {
        StreakRescueDialog(
            offer = offer,
            isLoadingAd = isLoadingAd,
            errorMessage = errorMessage,
            onRescueWithAd = onAd,
            onRestartFromScratch = onRestart,
            onDismissLater = onLater,
        )
    }

    @Test
    fun dialog_showsLocalizedMessageWithBothNumbers() {
        show()

        compose.onNodeWithTag(STREAK_RESCUE_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.streak_rescue_message, 3, 5), substring = true).assertIsDisplayed()
    }

    @Test
    fun allThreeActions_fireTheirCallbackOnce() {
        var ad = 0
        var restart = 0
        var later = 0
        show(onAd = { ad++ }, onRestart = { restart++ }, onLater = { later++ })

        compose.onNodeWithTag(STREAK_RESCUE_AD_TAG).performClick()
        compose.onNodeWithTag(STREAK_RESCUE_RESTART_TAG).performClick()
        compose.onNodeWithTag(STREAK_RESCUE_LATER_TAG).performClick()

        assertEquals(listOf(1, 1, 1), listOf(ad, restart, later))
    }

    @Test
    fun whileTheAdLoads_theDestructiveActionsAreDisabled() {
        show(isLoadingAd = true)

        compose.onNodeWithTag(STREAK_RESCUE_AD_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(STREAK_RESCUE_RESTART_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(STREAK_RESCUE_LATER_TAG).assertIsNotEnabled()
    }
}
