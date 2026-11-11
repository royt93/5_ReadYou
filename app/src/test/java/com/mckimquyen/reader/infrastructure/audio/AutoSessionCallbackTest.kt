package com.mckimquyen.reader.infrastructure.audio

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoSessionCallbackTest {

    private val log = mutableListOf<String>()
    private var side = AutoSide.PODCAST

    private fun transport(name: String) = object : AutoTransport {
        override fun play() { log += "$name.play" }
        override fun pause() { log += "$name.pause" }
        override fun stop() { log += "$name.stop" }
        override fun next() { log += "$name.next" }
        override fun previous() { log += "$name.previous" }
        override fun fastForward() { log += "$name.ff" }
        override fun rewind() { log += "$name.rew" }
        override fun seekToMs(positionMs: Long) { log += "$name.seek:$positionMs" }
    }

    private val callback = AutoSessionCallback(
        router = AutoControlRouter({ side }, transport("commute"), transport("podcast")) { log += "idle.play" },
        playLatestCommute = { log += "latestCommute" },
        playMediaId = { log += "mediaId:$it" },
        onServiceStop = { log += "serviceStop" },
    )

    @Test
    fun transportCommandsFollowTheActiveSide() {
        callback.onPlay(); callback.onPause(); callback.onSkipToNext(); callback.onSkipToPrevious()
        callback.onFastForward(); callback.onRewind(); callback.onSeekTo(9_000L)

        assertEquals(
            listOf("play", "pause", "next", "previous", "ff", "rew", "seek:9000").map { "podcast.$it" },
            log,
        )
    }

    @Test
    fun voiceSearchAlwaysStartsTheLatestCommuteEpisode_evenWhilePodcastIsActive() {
        callback.onPlayFromSearch("anything", null)
        callback.onPlayFromSearch(null, null)

        assertEquals(listOf("latestCommute", "latestCommute"), log)
    }

    @Test
    fun playFromMediaId_passesTheIdThroughUntouched() {
        callback.onPlayFromMediaId("podcast:a1", null)
        callback.onPlayFromMediaId(null, null)

        assertEquals(listOf("mediaId:podcast:a1", "mediaId:null"), log)
    }

    @Test
    fun stop_stopsTheActiveSideThenTheService() {
        side = AutoSide.COMMUTE
        callback.onStop()

        assertEquals(listOf("commute.stop", "serviceStop"), log)
    }

    @Test
    fun plainPlayWithNothingLoaded_startsTheLatestCommuteEpisode() {
        side = AutoSide.NONE
        callback.onPlay()

        assertEquals(listOf("idle.play"), log)
    }
}
