package com.mckimquyen.reader.ui.page.home.read.podcast

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PodcastEpisodeActionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsRequestedEpisode_andPlaysOnlyOnClick() {
        var plays = 0
        rule.setContent { PodcastEpisodeAction(title = "Episode B", onPlay = { plays++ }) }
        rule.onNodeWithText("Episode B").assertExists()
        assertEquals(0, plays)
        rule.onNodeWithContentDescription("Play: Episode B").performClick()
        assertEquals(1, plays)
    }
}
