package com.mckimquyen.reader.ui.page.headline

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.headline.HeadlineLanguage
import com.mckimquyen.reader.domain.headline.HeadlineSignal
import com.mckimquyen.reader.domain.headline.HeadlineTone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HeadlineSignalBadgesTest {

    @get:Rule
    val rule = createComposeRule()

    private fun signal(score: Int, tone: HeadlineTone) =
        HeadlineSignal(HeadlineLanguage.ENGLISH, score, tone)

    @Test
    fun score70_hidesClickbaitButStillShowsTone() {
        rule.setContent { HeadlineSignalBadges(signal(70, HeadlineTone.UNCLEAR)) }

        rule.onAllNodesWithTag(CLICKBAIT_BADGE_TAG).assertCountEquals(0)
        rule.onNodeWithTag(TONE_BADGE_TAG).assertIsDisplayed()
        rule.onNodeWithText("⚪ Unclear").assertIsDisplayed()
    }

    @Test
    fun score71_showsClickbaitBadge() {
        rule.setContent { HeadlineSignalBadges(signal(71, HeadlineTone.POSITIVE)) }

        rule.onNodeWithTag(CLICKBAIT_BADGE_TAG).assertIsDisplayed()
        rule.onNodeWithTag(TONE_BADGE_TAG).assertIsDisplayed()
        rule.onNodeWithText("🎯 Clickbait 71%").assertIsDisplayed()
        rule.onNodeWithText("🟢 Positive").assertIsDisplayed()
    }

    @Test
    fun positiveToneHasTextAndIconLabel() {
        rule.setContent { HeadlineSignalBadges(signal(0, HeadlineTone.POSITIVE), showClickbait = false) }
        rule.onNodeWithTag(TONE_BADGE_TAG).assertIsDisplayed()
        rule.onNodeWithText("🟢 Positive").assertIsDisplayed()
    }

    @Test
    fun unclearToneHasTextAndIconLabel() {
        rule.setContent { HeadlineSignalBadges(signal(0, HeadlineTone.UNCLEAR), showClickbait = false) }
        rule.onNodeWithTag(TONE_BADGE_TAG).assertIsDisplayed()
        rule.onNodeWithText("⚪ Unclear").assertIsDisplayed()
    }

    @Test
    fun negativeToneHasTextAndIconLabel() {
        rule.setContent { HeadlineSignalBadges(signal(0, HeadlineTone.NEGATIVE), showClickbait = false) }
        rule.onNodeWithTag(TONE_BADGE_TAG).assertIsDisplayed()
        rule.onNodeWithText("🔴 Negative").assertIsDisplayed()
    }

    @Test
    fun nullSignalShowsNoBadge() {
        rule.setContent { HeadlineSignalBadges(null) }
        rule.onAllNodesWithTag(CLICKBAIT_BADGE_TAG).assertCountEquals(0)
        rule.onAllNodesWithTag(TONE_BADGE_TAG).assertCountEquals(0)
    }

    @Test
    fun visibilityCanHideEitherBadgeIndependently() {
        rule.setContent { HeadlineSignalBadges(signal(90, HeadlineTone.NEGATIVE), showClickbait = true, showTone = false) }
        rule.onNodeWithTag(CLICKBAIT_BADGE_TAG).assertIsDisplayed()
        rule.onAllNodesWithTag(TONE_BADGE_TAG).assertCountEquals(0)
    }
}
