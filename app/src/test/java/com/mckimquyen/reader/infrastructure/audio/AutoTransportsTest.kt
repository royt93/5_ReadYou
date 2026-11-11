package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoTransportsTest {

    private val log = mutableListOf<String>()

    private val commute = CommuteAutoTransport(
        playLatest = { log += "playLatest" }, pauseAction = { log += "pause" }, stopAction = { log += "stop" },
        skipNext = { log += "skipNext" }, skipPrevious = { log += "skipPrevious" },
    )
    private val podcast = PodcastAutoTransport(
        playAction = { log += "play" }, pauseAction = { log += "pause" }, stopAction = { log += "stop" },
        skipSec = { log += "skip:$it" }, seekToSec = { log += "seek:$it" }, skipIntervalSec = 15,
    )

    @Test
    fun commute_mapsSeekingToAdjacentLinesAndIgnoresSeekTo() {
        commute.play(); commute.pause(); commute.stop()
        commute.next(); commute.previous(); commute.fastForward(); commute.rewind(); commute.seekToMs(5_000L)

        assertEquals(
            listOf("playLatest", "pause", "stop", "skipNext", "skipPrevious", "skipNext", "skipPrevious"),
            log,
        )
    }

    @Test
    fun podcast_nextAndFastForwardSkipForwardByTheInterval_previousAndRewindSkipBack() {
        podcast.next(); podcast.fastForward(); podcast.previous(); podcast.rewind()

        assertEquals(listOf("skip:15", "skip:15", "skip:-15", "skip:-15"), log)
    }

    @Test
    fun podcast_seekConvertsMillisecondsToWholeSeconds() {
        podcast.seekToMs(42_999L)
        podcast.seekToMs(0L)

        assertEquals(listOf("seek:42", "seek:0"), log)
    }

    @Test
    fun podcast_playPauseStopPassThrough() {
        podcast.play(); podcast.pause(); podcast.stop()

        assertEquals(listOf("play", "pause", "stop"), log)
    }
}
