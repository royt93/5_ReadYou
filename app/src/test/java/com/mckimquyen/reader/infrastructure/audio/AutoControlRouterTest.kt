package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoControlRouterTest {

    private class Recorder(private val name: String, private val log: MutableList<String>) : AutoTransport {
        override fun play() { log += "$name.play" }
        override fun pause() { log += "$name.pause" }
        override fun stop() { log += "$name.stop" }
        override fun next() { log += "$name.next" }
        override fun previous() { log += "$name.previous" }
        override fun fastForward() { log += "$name.fastForward" }
        override fun rewind() { log += "$name.rewind" }
        override fun seekToMs(positionMs: Long) { log += "$name.seek:$positionMs" }
    }

    private val log = mutableListOf<String>()
    private var side = AutoSide.NONE
    private val router = AutoControlRouter({ side }, Recorder("commute", log), Recorder("podcast", log)) { log += "idle.play" }

    private fun driveAll() {
        router.play(); router.pause(); router.stop(); router.next(); router.previous()
        router.fastForward(); router.rewind(); router.seekToMs(42_000L)
    }

    @Test
    fun podcastSide_sendsEveryCommandToPodcastOnly() {
        side = AutoSide.PODCAST
        driveAll()

        assertEquals(
            listOf("play", "pause", "stop", "next", "previous", "fastForward", "rewind", "seek:42000").map { "podcast.$it" },
            log,
        )
    }

    @Test
    fun commuteSide_sendsEveryCommandToCommuteOnly() {
        side = AutoSide.COMMUTE
        driveAll()

        assertEquals(
            listOf("play", "pause", "stop", "next", "previous", "fastForward", "rewind", "seek:42000").map { "commute.$it" },
            log,
        )
    }

    @Test
    fun noSide_playStartsTheLatestCommuteEpisodeAndEverythingElseIsIgnored() {
        side = AutoSide.NONE
        driveAll()

        assertEquals(listOf("idle.play"), log)
    }

    @Test
    fun sideIsReadAtCallTime() {
        side = AutoSide.PODCAST
        router.pause()
        side = AutoSide.COMMUTE
        router.pause()

        assertEquals(listOf("podcast.pause", "commute.pause"), log)
    }
}
