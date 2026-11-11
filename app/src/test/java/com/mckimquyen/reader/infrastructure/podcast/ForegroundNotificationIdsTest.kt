package com.mckimquyen.reader.infrastructure.podcast

import com.mckimquyen.reader.infrastructure.audio.CommuteMediaSessionService
import com.mckimquyen.reader.infrastructure.audio.TtsForegroundService
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundNotificationIdsTest {

    @Test
    fun everyForegroundMediaServiceHasItsOwnNotificationId() {
        val ids = listOf(
            TtsForegroundService.NOTIFICATION_ID,
            CommuteMediaSessionService.NOTIFICATION_ID,
            PodcastPlaybackService.NOTIFICATION_ID,
        )

        assertEquals("two services share a notification id and overwrite each other: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun existingIdsAreUnchanged() {
        assertEquals(9182, TtsForegroundService.NOTIFICATION_ID)
        assertEquals(9183, CommuteMediaSessionService.NOTIFICATION_ID)
    }
}
