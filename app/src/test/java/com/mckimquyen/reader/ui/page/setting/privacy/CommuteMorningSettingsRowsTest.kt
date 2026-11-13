package com.mckimquyen.reader.ui.page.setting.privacy

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommuteMorningSettingsRowsTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val consent = mutableListOf<Boolean>()
    private val autoPlay = mutableListOf<Boolean>()

    private fun show(consentOn: Boolean = false, autoPlayOn: Boolean = false, denied: Boolean = false) = compose.setContent {
        CommuteMorningSettingsRows(
            consent = consentOn, listenMinuteOfDay = 7 * 60, autoPlay = autoPlayOn, permissionDenied = denied,
            activity = compose.activity,
            onConsent = { consent += it }, onPickTime = { _, _ -> }, onAutoPlay = { autoPlay += it },
        )
    }

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun showsTheThreeSettings_withTheirExplanations() {
        show()
        compose.onNodeWithText(str(R.string.commute_consent_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.commute_listen_time_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.commute_autoplay_title)).assertIsDisplayed()
    }

    @Test fun theListenTimeIsShownAs0700() {
        show()
        compose.onNodeWithText("07:00").assertIsDisplayed()
    }

    @Test fun tappingTheConsentRow_reportsTheOppositeOfTheCurrentValue() {
        show(consentOn = false)
        compose.onNodeWithText(str(R.string.commute_consent_title)).performClick()
        assertEquals(listOf(true), consent)
    }

    @Test fun tappingTheConsentRowWhenOn_reportsOff() {
        show(consentOn = true)
        compose.onNodeWithText(str(R.string.commute_consent_title)).performClick()
        assertEquals(listOf(false), consent)
    }

    @Test fun tappingTheAutoPlayRow_reportsTheOppositeOfTheCurrentValue() {
        show(autoPlayOn = false)
        compose.onNodeWithText(str(R.string.commute_autoplay_title)).performClick()
        assertEquals(listOf(true), autoPlay)
    }

    @Test fun aDeniedPermission_showsTheExplanation() {
        show(denied = true)
        compose.onNodeWithText(str(R.string.commute_autoplay_permission_denied)).assertIsDisplayed()
    }

    @Test fun withoutADenial_theDeniedExplanationIsAbsent() {
        show(denied = false)
        assertEquals(0, compose.onAllNodesWithTextCount(str(R.string.commute_autoplay_permission_denied)))
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
