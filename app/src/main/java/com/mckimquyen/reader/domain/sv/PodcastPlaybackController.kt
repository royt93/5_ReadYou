package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import com.mckimquyen.reader.domain.model.podcast.SleepTimer
import com.mckimquyen.reader.infrastructure.podcast.PodcastPositionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Single owner of podcast playback state. Every method runs on the main thread. */
@Singleton
class PodcastPlaybackController @Inject constructor(
    private val positions: PodcastPositionStore,
    private val tts: TtsGate,
    private val starter: PodcastServiceStarter,
    private val sleepTimer: SleepTimer,
) {
    private val _state = MutableStateFlow(PodcastPlaybackState())
    val state: StateFlow<PodcastPlaybackState> = _state.asStateFlow()

    private var engine: PodcastEngine? = null
    private var audio: AudioInfo? = null
    private var preparedArticleId: String? = null
    private var pendingPlay = false
    private var playingTicks = 0
    private var pendingSleepMinutes: Int? = null

    init {
        tts.setOnPlayRequested { pause() }
    }

    /** Selects an episode and restores its saved position. Never autoplays. */
    fun open(articleId: String, title: String, feedName: String, audio: AudioInfo) {
        if (_state.value.articleId == articleId || _state.value.isActive()) return
        selectEpisode(articleId, title, feedName, audio)
    }

    fun playEpisode(articleId: String, title: String, feedName: String, audio: AudioInfo) {
        if (_state.value.articleId != articleId) selectEpisode(articleId, title, feedName, audio)
        play()
    }

    private fun selectEpisode(articleId: String, title: String, feedName: String, audio: AudioInfo) {
        saveCurrentPosition()
        if (preparedArticleId != null) engine?.stop()
        preparedArticleId = null
        pendingPlay = false
        playingTicks = 0
        this.audio = audio
        _state.value = PodcastPlaybackState(
            articleId = articleId,
            title = title,
            feedName = feedName,
            status = PodcastStatus.PAUSED,
            positionSec = positions.load(articleId),
            durationSec = audio.durationSec ?: 0,
            speed = _state.value.speed,
            sleepRemainingSec = sleepTimer.remainingSec(),
        )
    }

    fun attachEngine(engine: PodcastEngine) {
        this.engine = engine
        if (pendingPlay) {
            pendingPlay = false
            startPlayback(engine)
        }
    }

    fun detachEngine(engine: PodcastEngine) {
        if (this.engine !== engine) return
        saveCurrentPosition()
        this.engine = null
        preparedArticleId = null
        pendingPlay = false
        if (_state.value.isActive()) update { copy(status = PodcastStatus.PAUSED) }
    }

    fun play() {
        if (audio == null || _state.value.articleId == null) return
        if (tts.isSpeaking) tts.stop()
        pendingSleepMinutes?.let {
            sleepTimer.start(it)
            pendingSleepMinutes = null
            update { copy(sleepRemainingSec = sleepTimer.remainingSec()) }
        }
        val e = engine
        if (e == null) {
            if (!pendingPlay) {
                pendingPlay = true
                starter.start()
            }
            update { copy(status = PodcastStatus.PREPARING) }
        } else {
            startPlayback(e)
        }
    }

    fun pause() {
        if (!_state.value.isActive()) return
        pendingPlay = false
        engine?.pause()
        saveCurrentPosition()
        update { copy(status = PodcastStatus.PAUSED) }
    }

    fun toggle() = if (_state.value.isActive()) pause() else play()

    fun stop() {
        saveCurrentPosition()
        engine?.stop()
        preparedArticleId = null
        pendingPlay = false
        playingTicks = 0
        cancelSleepTimer()
        update { copy(status = PodcastStatus.IDLE) }
    }

    fun skip(seconds: Int) = seekTo(_state.value.positionSec + seconds)

    fun seekTo(sec: Int) {
        val current = _state.value
        val id = current.articleId ?: return
        val upper = if (current.durationSec > 0) current.durationSec else Int.MAX_VALUE
        val target = sec.coerceIn(0, upper)
        if (preparedArticleId == id) engine?.seekToSec(target)
        update { copy(positionSec = target) }
    }

    fun setSpeed(speed: Float) {
        if (speed !in SPEEDS) return
        engine?.setSpeed(speed)
        update { copy(speed = speed) }
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes !in SleepTimer.OPTIONS_MINUTES) return
        if (!_state.value.isActive()) {
            sleepTimer.cancel()
            pendingSleepMinutes = minutes
            update { copy(sleepRemainingSec = minutes * SECONDS_PER_MINUTE) }
        } else {
            pendingSleepMinutes = null
            sleepTimer.start(minutes)
            update { copy(sleepRemainingSec = sleepTimer.remainingSec()) }
        }
    }

    fun cancelSleepTimer() {
        pendingSleepMinutes = null
        sleepTimer.cancel()
        update { copy(sleepRemainingSec = null) }
    }

    /** Called every [TICK_MS] by the service while it is alive. */
    fun onEngineTick() {
        val e = engine ?: return
        val id = _state.value.articleId ?: return
        if (preparedArticleId != id) return
        val position = e.positionSec()
        val duration = e.durationSec().takeIf { it > 0 } ?: _state.value.durationSec
        val wasStarting = _state.value.status.let { it == PodcastStatus.PREPARING || it == PodcastStatus.PLAYING }
        update {
            copy(
                positionSec = position,
                durationSec = duration,
                status = if (status != PodcastStatus.ERROR) e.status() else status,
                sleepRemainingSec = sleepTimer.remainingSec(),
            )
        }
        if (wasStarting && ++playingTicks % SAVE_EVERY_TICKS == 0) positions.save(id, position, duration)
        if (sleepTimer.isExpired()) {
            pause()
            cancelSleepTimer()
        }
    }

    fun onEngineEnded() {
        val id = _state.value.articleId ?: return
        positions.save(id, 0, null)
        engine?.pause()
        preparedArticleId = null
        update { copy(status = PodcastStatus.PAUSED, positionSec = 0) }
    }

    fun onEngineError() {
        if (_state.value.articleId == null) return
        saveCurrentPosition()
        preparedArticleId = null
        pendingPlay = false
        update { copy(status = PodcastStatus.ERROR) }
    }

    fun retry() {
        if (_state.value.status == PodcastStatus.ERROR) play()
    }

    private fun startPlayback(e: PodcastEngine) {
        val current = _state.value
        val id = current.articleId ?: return
        val source = audio ?: return
        if (preparedArticleId != id) {
            e.prepare(source.url, current.positionSec)
            e.setSpeed(current.speed)
            preparedArticleId = id
            playingTicks = 0
            update { copy(status = PodcastStatus.PREPARING) }
        } else {
            update { copy(status = PodcastStatus.PLAYING) }
        }
        e.play()
    }

    private fun saveCurrentPosition() {
        val current = _state.value
        val id = current.articleId ?: return
        positions.save(id, current.positionSec, current.durationSec.takeIf { it > 0 })
    }

    private fun update(change: PodcastPlaybackState.() -> PodcastPlaybackState) {
        _state.value = _state.value.change()
    }

    private fun PodcastPlaybackState.isActive() =
        status == PodcastStatus.PLAYING || status == PodcastStatus.PREPARING

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        const val SKIP_INTERVAL_SEC = 15
        const val TICK_MS = 1_000L
        val SPEEDS = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        const val SAVE_EVERY_TICKS = 5
    }
}
