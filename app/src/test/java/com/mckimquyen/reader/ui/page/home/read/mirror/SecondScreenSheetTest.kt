package com.mckimquyen.reader.ui.page.home.read.mirror

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.mirror.MirrorFailure
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
class SecondScreenSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun str(id: Int, vararg a: Any) = compose.activity.getString(id, *a)
    private val url = "http://192.168.1.20:8080/m/" + "a".repeat(32)

    private fun show(
        state: MirrorUiState,
        onToggle: (Boolean) -> Unit = {},
        onCopy: (String) -> Unit = {},
        onShare: (String) -> Unit = {},
    ) = compose.setContent { SecondScreenSheetContent(state, onToggle, onCopy, onShare) }

    @Test fun off_showsAnOffSwitchAndNoUrlNoButtonsNoWarning() {
        show(MirrorUiState.Off)
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).assertIsOff()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_COPY_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_SHARE_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_WARNING_TAG).assertCountEquals(0)
    }

    @Test fun turningTheSwitchOnReportsTrue() {
        var toggled: Boolean? = null
        show(MirrorUiState.Off, onToggle = { toggled = it })
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).performClick()
        compose.runOnIdle { assertEquals(true, toggled) }
    }

    @Test fun running_showsUrlWarningViewersAndBothButtons() {
        show(MirrorUiState.Running(url, clients = 2, expiresAtMillis = 3_600_000L))
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).assertIsOn()
        compose.onNodeWithText(url).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_WARNING_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.second_screen_warning)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.second_screen_viewers, 2)).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_COPY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_SHARE_TAG).assertIsDisplayed()
    }

    @Test fun copyAndShareReportTheExactUrl() {
        var copied: String? = null
        var shared: String? = null
        show(MirrorUiState.Running(url, 0, 1L), onCopy = { copied = it }, onShare = { shared = it })
        compose.onNodeWithTag(SECOND_SCREEN_COPY_TAG).performClick()
        compose.onNodeWithTag(SECOND_SCREEN_SHARE_TAG).performClick()
        compose.runOnIdle { assertEquals(url, copied); assertEquals(url, shared) }
    }

    @Test fun turningTheSwitchOffWhileRunningReportsFalse() {
        var toggled: Boolean? = null
        show(MirrorUiState.Running(url, 0, 1L), onToggle = { toggled = it })
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).performClick()
        compose.runOnIdle { assertEquals(false, toggled) }
    }

    @Test fun starting_showsTheStatusAndNoUrl() {
        show(MirrorUiState.Starting)
        compose.onNodeWithText(str(R.string.second_screen_starting)).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_WARNING_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).assertIsOn()
    }

    @Test fun noNetwork_explainsAndOffersRetry() {
        var toggled: Boolean? = null
        show(MirrorUiState.NoNetwork, onToggle = { toggled = it })
        compose.onNodeWithText(str(R.string.second_screen_no_network)).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_WARNING_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onNodeWithTag(SECOND_SCREEN_RETRY_TAG).performClick()
        compose.runOnIdle { assertEquals(true, toggled) }
    }

    @Test fun expired_explainsAndHidesTheOldUrl() {
        show(MirrorUiState.Expired)
        compose.onNodeWithText(str(R.string.second_screen_expired)).assertIsDisplayed()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_COPY_TAG).assertCountEquals(0)
    }

    @Test fun error_showsTheMessageAndRetry() {
        show(MirrorUiState.Error(MirrorFailure.BIND))
        compose.onNodeWithText(str(R.string.second_screen_error)).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_RETRY_TAG).assertIsDisplayed()
    }
}
