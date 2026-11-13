package com.mckimquyen.reader.infrastructure.audio

import android.app.Application
import com.mckimquyen.reader.domain.commute.CommuteAutoPlayPolicy
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

// Robolectric, because the handler logs through android.util.Log, which a bare JVM test cannot resolve.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HeadsetAutoPlayHandlerTest {

    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private class Fake(val now: Long) : HeadsetAutoPlayHandler.Deps {
        var switch = true
        var episode: CommuteEpisode? = null
        var played = false
        var lastDay: Long? = null
        var otherAudio = false
        var permission = true
        var startResult = true
        var failWith: Throwable? = null
        val calls = mutableListOf<String>()
        var savedDay: Long? = null

        override suspend fun switchOn(): Boolean { calls += "switchOn"; failWith?.let { throw it }; return switch }
        override suspend fun episode() = episode.also { calls += "episode" }
        override suspend fun episodePlayed(id: String) = played.also { calls += "played" }
        override suspend fun lastAutoPlayEpochDay() = lastDay.also { calls += "lastDay" }
        override suspend fun saveAutoPlayEpochDay(day: Long) { calls += "save"; savedDay = day }
        override fun otherAudioPlaying() = otherAudio
        override fun hasBluetoothPermission() = permission.also { calls += "permission" }
        override fun startPlayback(): Boolean { calls += "start"; return startResult }
        override fun offerPlayButton() { calls += "offer" }
        override fun nowMillis() = now
    }

    private fun episode(ageMillis: Long) = CommuteEpisode(
        id = "ep", title = "t", date = Date(now - ageMillis),
        dialogues = listOf(CommuteDialogue(CommuteSpeaker.ALEX, "hi")),
    )

    private fun run(f: Fake, audioDevice: Boolean = true) =
        runBlocking { HeadsetAutoPlayHandler(f).onAudioDeviceConnected(audioDevice) }

    @Test fun everythingRight_startsPlayback_andThenRemembersToday() {
        val f = Fake(now).apply { episode = episode(hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.PLAYED, run(f))
        assertEquals(CommuteAutoPlayPolicy.epochDayUtc(now), f.savedDay)
        assertTrue("the day is saved only AFTER playback started", f.calls.indexOf("start") < f.calls.indexOf("save"))
    }

    @Test fun withoutTheBluetoothPermission_nothingElseIsRead() {
        val f = Fake(now).apply { episode = episode(hour); permission = false }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertEquals(listOf("permission"), f.calls)
    }

    @Test fun switchOff_skips_andNeverStartsOrOffers() {
        val f = Fake(now).apply { episode = episode(hour); switch = false }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertTrue("start" !in f.calls && "offer" !in f.calls)
    }

    @Test fun noEpisode_skips() {
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(Fake(now)))
    }

    @Test fun anEpisodeThatIsTooOld_skips() {
        val f = Fake(now).apply { episode = episode(3 * hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
    }

    @Test fun anAlreadyHeardEpisode_skips() {
        val f = Fake(now).apply { episode = episode(hour); played = true }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
    }

    @Test fun alreadyAutoPlayedToday_skips() {
        val f = Fake(now).apply { episode = episode(hour); lastDay = CommuteAutoPlayPolicy.epochDayUtc(now) }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertTrue("start" !in f.calls)
    }

    @Test fun aDeviceThatIsNotAudio_skips() {
        val f = Fake(now).apply { episode = episode(hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f, audioDevice = false))
    }

    @Test fun otherAudioPlaying_offersTheButton_doesNotInterrupt_andDoesNotUseUpTheDay() {
        val f = Fake(now).apply { episode = episode(hour); otherAudio = true }
        assertEquals(HeadsetAutoPlayHandler.Result.OFFERED, run(f))
        assertTrue("start" !in f.calls)
        assertEquals(null, f.savedDay)
    }

    @Test fun androidRefusingTheStart_offersTheButton_andDoesNotUseUpTheDay() {
        val f = Fake(now).apply { episode = episode(hour); startResult = false }
        assertEquals(HeadsetAutoPlayHandler.Result.OFFERED, run(f))
        assertTrue("offer" in f.calls)
        assertEquals("a refused start must not count as today's auto-play", null, f.savedDay)
    }

    @Test fun anyFailure_isAbsorbed_andReportedAsSkipped() {
        val f = Fake(now).apply { failWith = IllegalStateException("boom") }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
    }

    @Test fun cancellation_isRethrown_notSwallowed() {
        val f = Fake(now).apply { failWith = CancellationException("stop") }
        val thrown = runCatching { run(f) }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test fun theSecondConnectionTheSameDay_doesNotPlayAgain() {
        val f = Fake(now).apply { episode = episode(hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.PLAYED, run(f))
        f.lastDay = f.savedDay
        f.calls.clear()
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertTrue("start" !in f.calls)
    }
}
