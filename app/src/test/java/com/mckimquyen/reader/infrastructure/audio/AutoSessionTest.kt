package com.mckimquyen.reader.infrastructure.audio

import android.support.v4.media.session.PlaybackStateCompat
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AutoSessionTest {

    private val idlePodcast = PodcastPlaybackState()
    private val idleCommute = CommutePlayerState()
    private val episode = CommuteEpisode("e1", "Edition", Date(0), listOf(CommuteDialogue(CommuteSpeaker.ALEX, "Hi there")))

    private fun podcast(status: PodcastStatus, pos: Int = 30, dur: Int = 600, speed: Float = 1.5f) =
        PodcastPlaybackState("a1", "Ep", "Feed", status, pos, dur, speed)

    private fun commute(playing: Boolean = false, waiting: Boolean = false, withEpisode: Boolean = true) =
        CommutePlayerState(episode = if (withEpisode) episode else null, isPlaying = playing, isAwaitingPlayback = waiting)

    // --- side tracker ---

    @Test
    fun nothingLoaded_isNone() {
        assertEquals(AutoSide.NONE, AutoSideTracker().update(idlePodcast, idleCommute))
    }

    @Test
    fun podcastPlayingWins() {
        assertEquals(AutoSide.PODCAST, AutoSideTracker().update(podcast(PodcastStatus.PLAYING), idleCommute))
        assertEquals(AutoSide.PODCAST, AutoSideTracker().update(podcast(PodcastStatus.PREPARING), idleCommute))
    }

    @Test
    fun commutePlayingOrWaitingWins() {
        assertEquals(AutoSide.COMMUTE, AutoSideTracker().update(idlePodcast, commute(playing = true)))
        assertEquals(AutoSide.COMMUTE, AutoSideTracker().update(idlePodcast, commute(waiting = true)))
    }

    @Test
    fun pausedSideKeepsControlUntilTheOtherStarts() {
        val t = AutoSideTracker()
        assertEquals(AutoSide.PODCAST, t.update(podcast(PodcastStatus.PLAYING), commute()))
        assertEquals("paused podcast still owns the car controls", AutoSide.PODCAST, t.update(podcast(PodcastStatus.PAUSED), commute()))
        assertEquals(AutoSide.COMMUTE, t.update(podcast(PodcastStatus.PAUSED), commute(playing = true)))
        assertEquals("commute paused, podcast paused: last active stays", AutoSide.COMMUTE, t.update(podcast(PodcastStatus.PAUSED), commute()))
        assertEquals(AutoSide.PODCAST, t.update(podcast(PodcastStatus.PLAYING), commute()))
    }

    @Test
    fun lastSideWithoutContentFallsBackToNone() {
        val t = AutoSideTracker()
        t.update(podcast(PodcastStatus.PLAYING), idleCommute)
        assertEquals(AutoSide.NONE, t.update(idlePodcast, idleCommute))
    }

    @Test
    fun whenBothReportPlaying_theOneThatStartedMostRecentlyWins() {
        val t = AutoSideTracker()
        t.update(podcast(PodcastStatus.PLAYING), idleCommute)
        assertEquals(AutoSide.COMMUTE, t.update(podcast(PodcastStatus.PLAYING), commute(playing = true)))
        assertEquals("no new start: the winner stays", AutoSide.COMMUTE, t.update(podcast(PodcastStatus.PLAYING), commute(playing = true)))
    }

    @Test
    fun pausedWithContentOnlyOnTheOtherSide_usesThatSide() {
        assertEquals(AutoSide.COMMUTE, AutoSideTracker().update(idlePodcast, commute()))
    }

    // --- podcast mapping ---

    @Test
    fun podcastStatusesMapToSessionStates() {
        val expected = mapOf(
            PodcastStatus.PLAYING to PlaybackStateCompat.STATE_PLAYING,
            PodcastStatus.PREPARING to PlaybackStateCompat.STATE_BUFFERING,
            PodcastStatus.PAUSED to PlaybackStateCompat.STATE_PAUSED,
            PodcastStatus.ERROR to PlaybackStateCompat.STATE_ERROR,
            PodcastStatus.IDLE to PlaybackStateCompat.STATE_STOPPED,
        )
        expected.forEach { (status, state) -> assertEquals(status.name, state, AutoSessionMapper.podcast(podcast(status)).state) }
    }

    @Test
    fun podcastSnapshot_carriesPositionDurationAndMetadata() {
        val s = AutoSessionMapper.podcast(podcast(PodcastStatus.PLAYING, pos = 30, dur = 600, speed = 1.5f))

        assertEquals(30_000L, s.positionMs)
        assertEquals(600_000L, s.durationMs)
        assertEquals(1.5f, s.speed, 0f)
        assertEquals("Ep", s.title)
        assertEquals("Feed", s.subtitle)
    }

    @Test
    fun podcastSpeedIsZeroUnlessPlaying() {
        assertEquals(0f, AutoSessionMapper.podcast(podcast(PodcastStatus.PAUSED)).speed, 0f)
        assertEquals(0f, AutoSessionMapper.podcast(podcast(PodcastStatus.ERROR)).speed, 0f)
    }

    @Test
    fun podcastActions_includeSeekAndFastForward() {
        val a = AutoSessionMapper.podcast(podcast(PodcastStatus.PAUSED)).actions
        listOf(
            PlaybackStateCompat.ACTION_PLAY, PlaybackStateCompat.ACTION_PAUSE, PlaybackStateCompat.ACTION_STOP,
            PlaybackStateCompat.ACTION_SEEK_TO, PlaybackStateCompat.ACTION_FAST_FORWARD, PlaybackStateCompat.ACTION_REWIND,
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID,
        ).forEach { assertTrue("missing action $it", a and it != 0L) }
    }

    // --- commute mapping ---

    @Test
    fun commuteStatesMapToSessionStates() {
        assertEquals(PlaybackStateCompat.STATE_PLAYING, AutoSessionMapper.commute(commute(playing = true), "A").state)
        assertEquals(PlaybackStateCompat.STATE_BUFFERING, AutoSessionMapper.commute(commute(waiting = true), "A").state)
        assertEquals(PlaybackStateCompat.STATE_PAUSED, AutoSessionMapper.commute(commute(), "A").state)
        assertEquals(PlaybackStateCompat.STATE_STOPPED, AutoSessionMapper.commute(commute(withEpisode = false), "A").state)
    }

    @Test
    fun commuteSnapshot_hasNoTimelineAndNoSeek() {
        val s = AutoSessionMapper.commute(commute(playing = true), "Alex & Sam")

        assertEquals(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, s.positionMs)
        assertEquals(AutoSessionMapper.UNKNOWN_DURATION_MS, s.durationMs)
        assertEquals("Edition", s.title)
        assertEquals("Alex & Sam", s.subtitle)
        assertEquals(0L, s.actions and PlaybackStateCompat.ACTION_SEEK_TO)
        assertTrue(s.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L)
        assertTrue(s.actions and PlaybackStateCompat.ACTION_FAST_FORWARD != 0L)
    }
}
