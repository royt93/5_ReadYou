package com.mckimquyen.reader.infrastructure.podcast

import com.mckimquyen.reader.infrastructure.audio.TtsForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastNotificationActionsTest {

    @Test fun actionOrder_isSameWhenPlayingOrPaused() {
        val expected = listOf("rewind", "toggle", "forward", "stop")
        assertEquals(expected, PodcastNotificationActions.actionNames(true))
        assertEquals(expected, PodcastNotificationActions.actionNames(false))
    }

    @Test fun compactIndices_pointAtRewindToggleForward() {
        val names = PodcastNotificationActions.actionNames(true)
        assertEquals(
            listOf("rewind", "toggle", "forward"),
            PodcastNotificationActions.compactIndices.map { names[it] },
        )
    }

    @Test fun serviceActions_areDistinctAndNotTtsActions() {
        val podcast = listOf(
            PodcastPlaybackService.ACTION_PLAY, PodcastPlaybackService.ACTION_PAUSE,
            PodcastPlaybackService.ACTION_REWIND, PodcastPlaybackService.ACTION_FORWARD,
            PodcastPlaybackService.ACTION_STOP,
        )
        assertEquals(podcast.size, podcast.toSet().size)
        val tts = setOf(
            TtsForegroundService.ACTION_PLAY, TtsForegroundService.ACTION_PAUSE,
            TtsForegroundService.ACTION_STOP, TtsForegroundService.ACTION_NEXT,
        )
        assertTrue(podcast.none { it in tts })
    }

    @Test fun notificationIds_differFromTts() {
        assertFalse(PodcastPlaybackService.NOTIFICATION_ID == TtsForegroundService.NOTIFICATION_ID)
        assertFalse(PodcastPlaybackService.CHANNEL_ID == TtsForegroundService.CHANNEL_ID)
    }
}
