package com.mckimquyen.reader.domain.sv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import com.mckimquyen.reader.domain.model.podcast.SleepTimer
import com.mckimquyen.reader.infrastructure.podcast.PodcastPositionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PodcastPlaybackControllerTest {

    private class FakeEngine : PodcastEngine {
        val calls = mutableListOf<String>()
        var position = 0
        var duration = 0
        var reportedStatus = PodcastStatus.PLAYING
        override fun prepare(url: String, startPositionSec: Int) { calls += "prepare:$url@$startPositionSec" }
        override fun play() { calls += "play"; reportedStatus = PodcastStatus.PLAYING }
        override fun pause() { calls += "pause"; reportedStatus = PodcastStatus.PAUSED }
        override fun stop() { calls += "stop" }
        override fun seekToSec(sec: Int) { calls += "seek:$sec" }
        override fun setSpeed(speed: Float) { calls += "speed:$speed" }
        override fun status() = reportedStatus
        override fun positionSec() = position
        override fun durationSec() = duration
    }

    private class FakeTts : TtsGate {
        override var isSpeaking = false
        var stops = 0
        var callback: (() -> Unit)? = null
        override fun stop() { stops++; isSpeaking = false }
        override fun setOnPlayRequested(callback: (() -> Unit)?) { this.callback = callback }
    }

    private lateinit var positions: PodcastPositionStore
    private lateinit var tts: FakeTts
    private var starts = 0
    private var now = 0L
    private lateinit var controller: PodcastPlaybackController
    private val audio = AudioInfo(URL, "audio/mpeg", 600)

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PodcastPositionStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        positions = PodcastPositionStore(context)
        tts = FakeTts()
        starts = 0
        now = 0L
        controller = PodcastPlaybackController(positions, tts, { starts++ }, SleepTimer { now })
    }

    private fun attached(): FakeEngine = FakeEngine().also { controller.attachEngine(it) }

    @Test fun open_restoresPositionAndStaysPaused() {
        positions.save("a1", 120, 600)
        controller.open("a1", "T", "F", audio)
        val s = controller.state.value
        assertEquals("a1", s.articleId)
        assertEquals(120, s.positionSec)
        assertEquals(600, s.durationSec)
        assertEquals(PodcastStatus.PAUSED, s.status)
        assertEquals(0, starts)
    }

    @Test fun play_withoutEngine_startsServiceThenPreparesOnAttach() {
        positions.save("a1", 120, 600)
        controller.open("a1", "T", "F", audio)
        controller.play()
        assertEquals(PodcastStatus.PREPARING, controller.state.value.status)
        assertEquals(1, starts)
        controller.play()
        assertEquals(1, starts)

        val engine = attached()
        assertEquals(listOf("prepare:$URL@120", "speed:1.0", "play"), engine.calls)
        engine.position = 121
        controller.onEngineTick()
        assertEquals(PodcastStatus.PLAYING, controller.state.value.status)
        assertEquals(121, controller.state.value.positionSec)
    }

    @Test fun play_withEngine_preparesOncePerEpisode() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        controller.pause()
        controller.play()
        assertEquals(1, engine.calls.count { it.startsWith("prepare") })
        assertEquals(2, engine.calls.count { it == "play" })
    }

    @Test fun play_whileTtsSpeaking_stopsTtsFirst() {
        attached()
        controller.open("a1", "T", "F", audio)
        tts.isSpeaking = true
        controller.play()
        assertEquals(1, tts.stops)
    }

    @Test fun ttsPlayRequested_pausesPlayingPodcast() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.position = 5
        controller.onEngineTick()
        tts.callback!!.invoke()
        assertEquals(PodcastStatus.PAUSED, controller.state.value.status)
        assertEquals(1, engine.calls.count { it == "pause" })
        tts.callback!!.invoke()
        assertEquals(1, engine.calls.count { it == "pause" })
    }

    @Test fun skip_clampsToBounds() {
        val engine = attached()
        positions.save("a1", 10, 600)
        controller.open("a1", "T", "F", audio)
        controller.play()
        controller.skip(-PodcastPlaybackController.SKIP_INTERVAL_SEC)
        assertEquals(0, controller.state.value.positionSec)
        assertTrue(engine.calls.contains("seek:0"))
        controller.seekTo(590)
        controller.skip(PodcastPlaybackController.SKIP_INTERVAL_SEC)
        assertEquals(600, controller.state.value.positionSec)
        assertTrue(engine.calls.contains("seek:600"))
    }

    @Test fun seekTo_beforePlay_onlyUpdatesState() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.seekTo(300)
        assertEquals(300, controller.state.value.positionSec)
        assertTrue(engine.calls.isEmpty())
        controller.play()
        assertEquals("prepare:$URL@300", engine.calls.first())
    }

    @Test fun setSpeed_acceptsOnlyListedSpeeds() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.setSpeed(1.5f)
        assertEquals(1.5f, controller.state.value.speed, 0f)
        assertTrue(engine.calls.contains("speed:1.5"))
        controller.setSpeed(3.0f)
        controller.setSpeed(0.5f)
        assertEquals(1.5f, controller.state.value.speed, 0f)
    }

    @Test fun onEngineTick_savesPositionEveryFiveTicks() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.duration = 600
        repeat(PodcastPlaybackController.SAVE_EVERY_TICKS - 1) { engine.position += 10; controller.onEngineTick() }
        assertEquals(0, positions.load("a1"))
        engine.position += 10
        controller.onEngineTick()
        assertEquals(engine.position, positions.load("a1"))
    }

    @Test fun sleepTimer_countsDownThenPausesAndClears() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        controller.startSleepTimer(15)
        now += 60_000
        engine.position = 3
        controller.onEngineTick()
        assertEquals(840, controller.state.value.sleepRemainingSec)
        assertEquals(PodcastStatus.PLAYING, controller.state.value.status)

        now += 15 * 60_000L
        controller.onEngineTick()
        assertEquals(PodcastStatus.PAUSED, controller.state.value.status)
        assertNull(controller.state.value.sleepRemainingSec)
        assertTrue(engine.calls.contains("pause"))
    }

    @Test fun sleepTimer_cancelAndInvalidMinutes() {
        attached()
        controller.open("a1", "T", "F", audio)
        controller.startSleepTimer(20)
        assertNull(controller.state.value.sleepRemainingSec)
        controller.startSleepTimer(30)
        assertEquals(1800, controller.state.value.sleepRemainingSec)
        controller.cancelSleepTimer()
        assertNull(controller.state.value.sleepRemainingSec)
    }

    @Test fun onEngineEnded_resetsPositionAndPauses() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.position = 599
        engine.duration = 600
        controller.onEngineTick()
        controller.onEngineEnded()
        assertEquals(0, positions.load("a1"))
        assertEquals(PodcastStatus.PAUSED, controller.state.value.status)
        assertEquals(0, controller.state.value.positionSec)
    }

    @Test fun onEngineError_thenRetryResumesFromSavedPosition() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.position = 42
        controller.onEngineTick()
        controller.onEngineError()
        assertEquals(PodcastStatus.ERROR, controller.state.value.status)
        engine.calls.clear()
        controller.retry()
        assertEquals("prepare:$URL@42", engine.calls.first())
        assertEquals(PodcastStatus.PREPARING, controller.state.value.status)
    }

    @Test fun open_otherEpisodeWhilePlaying_keepsCurrentEpisode() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.calls.clear()
        controller.open("a2", "T2", "F", AudioInfo("https://x/2.mp3", null, null))
        assertTrue(engine.calls.isEmpty())
        assertEquals("a1", controller.state.value.articleId)
    }

    @Test fun open_sameEpisodeAgain_keepsPlaying() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.position = 9
        controller.onEngineTick()
        controller.open("a1", "T", "F", audio)
        assertEquals(PodcastStatus.PLAYING, controller.state.value.status)
        assertEquals(9, controller.state.value.positionSec)
    }

    @Test fun stop_returnsToIdle() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        controller.stop()
        assertEquals(PodcastStatus.IDLE, controller.state.value.status)
        assertEquals("a1", controller.state.value.articleId)
        assertTrue(engine.calls.contains("stop"))
        engine.calls.clear()
        controller.play()
        assertTrue(engine.calls.any { it.startsWith("prepare:") })
    }

    @Test fun detachEngine_whilePlaying_setsPausedAndKeepsPosition() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.position = 77
        controller.onEngineTick()
        controller.detachEngine(engine)
        assertEquals(PodcastStatus.PAUSED, controller.state.value.status)
        assertEquals(77, controller.state.value.positionSec)
        assertEquals(77, positions.load("a1"))
    }

    @Test fun timerSetBeforePlayback_startsCountingOnlyWhenPlayRequested() {
        controller.open("a1", "T", "F", audio)
        controller.startSleepTimer(15)
        now += 20 * 60_000L
        controller.play()
        val engine = attached()
        controller.onEngineTick()
        assertEquals(900, controller.state.value.sleepRemainingSec)
        assertEquals(PodcastStatus.PLAYING, controller.state.value.status)
        assertTrue(engine.calls.contains("play"))
    }

    @Test fun tick_doesNotClaimPlayingWhileBuffering() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        engine.reportedStatus = PodcastStatus.PREPARING
        controller.onEngineTick()
        assertEquals(PodcastStatus.PREPARING, controller.state.value.status)
    }

    @Test fun tick_reflectsEnginePauseFromAudioFocusLoss() {
        val engine = attached()
        controller.open("a1", "T", "F", audio)
        controller.play()
        controller.onEngineTick()
        engine.reportedStatus = PodcastStatus.PAUSED
        controller.onEngineTick()
        assertEquals(PodcastStatus.PAUSED, controller.state.value.status)
    }

    @Test fun explicitPlayEpisode_replacesPlayingEpisode() {
        val engine = attached()
        controller.open("a", "A", "F", audio)
        controller.play()
        engine.calls.clear()
        controller.playEpisode("b", "B", "F", AudioInfo("https://x/b.mp3", null, 600))
        assertEquals("b", controller.state.value.articleId)
        assertTrue(engine.calls.contains("stop"))
        assertTrue(engine.calls.contains("prepare:https://x/b.mp3@0"))
        assertTrue(engine.calls.contains("play"))
    }

    private companion object {
        const val URL = "https://x/1.mp3"
    }
}
