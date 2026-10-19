package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TtsForegroundServiceNextTest {

    @Test
    fun actionNames_withoutQueue_hasNoNext() {
        assertEquals(listOf("toggle", "stop"), TtsNotificationActions.actionNames(isPlaying = true, hasQueue = false))
        assertEquals(listOf("toggle", "stop"), TtsNotificationActions.actionNames(isPlaying = false, hasQueue = false))
    }

    @Test
    fun actionNames_withQueue_putsNextBetweenToggleAndStop() {
        assertEquals(listOf("toggle", "next", "stop"), TtsNotificationActions.actionNames(isPlaying = true, hasQueue = true))
        assertEquals(listOf("toggle", "next", "stop"), TtsNotificationActions.actionNames(isPlaying = false, hasQueue = true))
    }

    @Test
    fun compactIndices_coverEveryActionShown() {
        assertEquals(listOf(0, 1), TtsNotificationActions.compactIndices(hasQueue = false))
        assertEquals(listOf(0, 1, 2), TtsNotificationActions.compactIndices(hasQueue = true))
    }

    @Test
    fun compactIndices_matchActionCount() {
        for (hasQueue in listOf(false, true)) {
            val count = TtsNotificationActions.actionNames(true, hasQueue).size
            assertEquals(count, TtsNotificationActions.compactIndices(hasQueue).size)
        }
    }

    @Test
    fun actionNext_isDistinctFromExistingActions() {
        val others = setOf(
            TtsForegroundService.ACTION_PLAY,
            TtsForegroundService.ACTION_PAUSE,
            TtsForegroundService.ACTION_STOP,
        )
        assertEquals(false, TtsForegroundService.ACTION_NEXT in others)
        assertNotEquals("", TtsForegroundService.ACTION_NEXT)
    }
}
