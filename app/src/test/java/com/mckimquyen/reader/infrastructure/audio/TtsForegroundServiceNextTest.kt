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

    @Test
    fun onlyAFinishedQueueEndsTheService() {
        for (outcome in com.mckimquyen.reader.domain.sv.ReadNewsOutcome.values()) {
            val expected = outcome == com.mckimquyen.reader.domain.sv.ReadNewsOutcome.QUEUE_FINISHED
            assertEquals("$outcome", expected, TtsNotificationActions.endsService(outcome))
        }
    }

    @Test
    fun theServiceStopsItselfWhenNextRunsOutOfStories() {
        val src = listOf(java.io.File("src/main/java"), java.io.File("app/src/main/java")).first { it.exists() }
        val service = java.io.File(src, "com/mckimquyen/reader/infrastructure/audio/TtsForegroundService.kt").readText()
        val advance = service.substringAfter("private fun advanceReadNews()").substringBefore("private fun createNotificationChannel")
        assertEquals(true, "TtsNotificationActions.endsService(outcome)" in advance)
        assertEquals(true, "stopForeground(STOP_FOREGROUND_REMOVE)" in advance && "stopSelf()" in advance)
    }

    @Test
    fun aFinishedSingleArticleEndsTheServiceButAQueueKeepsItForNext() {
        assertEquals(true, TtsNotificationActions.endsServiceAfterCompletion(hasQueue = false))
        assertEquals(false, TtsNotificationActions.endsServiceAfterCompletion(hasQueue = true))
    }

    /** On the Pixel (2026-11-25) the service and its undismissable ongoing notification were still there a minute after the voice had finished one article. */
    @Test
    fun theServiceListensForTheEndOfSpeechAndStopsItselfThenWhenNothingIsQueued() {
        val src = listOf(java.io.File("src/main/java"), java.io.File("app/src/main/java")).first { it.exists() }
        val service = java.io.File(src, "com/mckimquyen/reader/infrastructure/audio/TtsForegroundService.kt").readText()
        val onCreate = service.substringAfter("override fun onCreate()").substringBefore("override fun onStartCommand")
        assertEquals(true, "ttsManager.completions.collect" in onCreate)
        val handler = onCreate.substringAfter("ttsManager.completions.collect")
        assertEquals(true, "TtsNotificationActions.endsServiceAfterCompletion(readNewsController.hasQueue.value)" in handler)
        assertEquals(true, "stopForeground(STOP_FOREGROUND_REMOVE)" in handler && "stopSelf()" in handler)
    }
}
