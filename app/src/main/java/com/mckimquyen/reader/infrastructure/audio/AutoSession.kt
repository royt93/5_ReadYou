package com.mckimquyen.reader.infrastructure.audio

import android.support.v4.media.session.PlaybackStateCompat
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus

enum class AutoSide { NONE, COMMUTE, PODCAST }

/** What the car session should show right now; plain values so it can be tested without a MediaSession. */
data class AutoSnapshot(
    val state: Int,
    val positionMs: Long,
    val speed: Float,
    val actions: Long,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
)

/** Picks which engine the car controls drive: the one that most recently started, else the last one with content. */
class AutoSideTracker {
    private var last = AutoSide.NONE
    private var podcastWasActive = false
    private var commuteWasActive = false

    fun update(podcast: PodcastPlaybackState, commute: CommutePlayerState): AutoSide {
        val podcastActive = podcast.status == PodcastStatus.PLAYING || podcast.status == PodcastStatus.PREPARING
        val commuteActive = commute.isPlaying || commute.isAwaitingPlayback
        val podcastStarted = podcastActive && !podcastWasActive
        val commuteStarted = commuteActive && !commuteWasActive
        podcastWasActive = podcastActive
        commuteWasActive = commuteActive
        when {
            podcastStarted && !commuteStarted -> last = AutoSide.PODCAST
            commuteStarted && !podcastStarted -> last = AutoSide.COMMUTE
            last == AutoSide.NONE && podcastActive -> last = AutoSide.PODCAST
            last == AutoSide.NONE && commuteActive -> last = AutoSide.COMMUTE
        }
        val hasPodcast = podcast.articleId != null && podcast.status != PodcastStatus.IDLE
        val hasCommute = commute.episode != null
        if (last == AutoSide.PODCAST && !hasPodcast) last = if (hasCommute) AutoSide.COMMUTE else AutoSide.NONE
        if (last == AutoSide.COMMUTE && !hasCommute) last = if (hasPodcast) AutoSide.PODCAST else AutoSide.NONE
        if (last == AutoSide.NONE) last = when {
            hasPodcast -> AutoSide.PODCAST
            hasCommute -> AutoSide.COMMUTE
            else -> AutoSide.NONE
        }
        return last
    }
}

object AutoSessionMapper {
    const val UNKNOWN_DURATION_MS = -1L
    private const val MS_PER_SEC = 1_000L

    private const val BASE_ACTIONS = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_FAST_FORWARD or
        PlaybackStateCompat.ACTION_REWIND
    private const val PODCAST_ACTIONS = BASE_ACTIONS or PlaybackStateCompat.ACTION_SEEK_TO

    fun podcast(s: PodcastPlaybackState): AutoSnapshot {
        val state = when (s.status) {
            PodcastStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PodcastStatus.PREPARING -> PlaybackStateCompat.STATE_BUFFERING
            PodcastStatus.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PodcastStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
            PodcastStatus.IDLE -> PlaybackStateCompat.STATE_STOPPED
        }
        return AutoSnapshot(
            state = state,
            positionMs = s.positionSec * MS_PER_SEC,
            speed = if (s.status == PodcastStatus.PLAYING) s.speed else 0f,
            actions = PODCAST_ACTIONS,
            title = s.title,
            subtitle = s.feedName,
            durationMs = s.durationSec * MS_PER_SEC,
        )
    }

    fun commute(s: CommutePlayerState, artist: String): AutoSnapshot {
        val state = when {
            s.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            s.isAwaitingPlayback -> PlaybackStateCompat.STATE_BUFFERING
            s.episode != null -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        return AutoSnapshot(
            state = state,
            positionMs = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
            speed = if (s.isPlaying) 1.0f else 0f,
            actions = BASE_ACTIONS,
            title = s.episode?.title.orEmpty(),
            subtitle = artist,
            durationMs = UNKNOWN_DURATION_MS,
        )
    }
}
