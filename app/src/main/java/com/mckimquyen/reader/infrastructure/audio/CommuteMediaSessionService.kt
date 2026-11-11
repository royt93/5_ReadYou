package com.mckimquyen.reader.infrastructure.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.SessionSyncPolicy
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.PodcastPlaybackController
import com.mckimquyen.reader.infrastructure.android.MainActivity
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service hosting CommuteCast's [MediaSessionCompat], lock-screen/Bluetooth media
 * controls, and — via [MediaBrowserServiceCompat] — the browsable content tree Android Auto and
 * Android Automotive OS need to list and play CommuteCast from the car's own screen.
 *
 * Binding for browsing (Android Auto connecting, listing content) works without ever starting the
 * foreground service or posting a notification; only actually playing promotes this to a started,
 * foreground service, exactly as [CommuteAudioPlayer.handleUtteranceStart] already triggers via
 * [start].
 */
@AndroidEntryPoint
class CommuteMediaSessionService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var audioPlayer: CommuteAudioPlayer

    @Inject
    lateinit var episodeStore: CommuteEpisodeStore

    @Inject
    lateinit var podcast: PodcastPlaybackController

    @Inject
    lateinit var articleDao: ArticleDao

    private var mediaSession: MediaSessionCompat? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val sideTracker = AutoSideTracker()
    private val podcastSync = SessionSyncPolicy()
    private var activeSide = AutoSide.NONE
    private var renderedSide = AutoSide.NONE
    private var lastMetadataKey: Triple<AutoSide, String, String>? = null
    private val tree by lazy {
        AutoMediaTree(getString(R.string.auto_branch_commute), getString(R.string.auto_branch_podcasts))
    }
    private val podcastLauncher by lazy {
        AutoPodcastLauncher(
            tree = tree,
            loadShown = ::loadPodcastItems,
            loadAudio = ::loadPodcastAudio,
            start = podcast::playEpisode,
        )
    }

    /**
     * Decoded once and reused: car head units and the lock screen both want the same artwork.
     * `@Volatile` because it is written from the background decode in [onCreate] and read from the
     * main thread in [buildNotification]/[publishMetadata]/[toMediaItem].
     */
    @Volatile
    private var cachedArtwork: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Decoded off the main thread once at startup rather than lazily inline: BitmapFactory is
        // disk/CPU work, and CLAUDE.md's threading rule (no heavy I/O on Main) applies to a Service
        // exactly as it does everywhere else. A play request arriving before this finishes simply
        // gets a notification/metadata without artwork for that one call; the next state update
        // (there is always at least one more per dialogue line) fills it in.
        scope.launch(Dispatchers.Default) { decodeArtwork() }
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(
                AutoSessionCallback(
                    router = AutoControlRouter(
                        side = { activeSide },
                        commute = CommuteAutoTransport(
                            playLatest = ::resumeOrPlayLatestEpisode,
                            pauseAction = audioPlayer::pause,
                            stopAction = audioPlayer::stopAndReset,
                            skipNext = audioPlayer::skipNext,
                            skipPrevious = audioPlayer::skipPrevious,
                        ),
                        podcast = PodcastAutoTransport(
                            playAction = podcast::play,
                            pauseAction = podcast::pause,
                            stopAction = podcast::stop,
                            skipSec = podcast::skip,
                            seekToSec = podcast::seekTo,
                            skipIntervalSec = PodcastPlaybackController.SKIP_INTERVAL_SEC,
                        ),
                        playWhenIdle = ::resumeOrPlayLatestEpisode,
                    ),
                    playLatestCommute = ::resumeOrPlayLatestEpisode,
                    playMediaId = ::playFromMediaId,
                    onServiceStop = ::stopServiceGracefully,
                )
            )
            isActive = true
        }
        sessionToken = mediaSession?.sessionToken
        scope.launch {
            combine(podcast.state, audioPlayer.playerState) { p, c -> p to c }.collect { (p, c) -> renderSession(p, c) }
        }
    }

    private fun resumeOrPlayLatestEpisode() {
        val existing = audioPlayer.playerState.value.episode
        if (existing != null) {
            audioPlayer.resume()
            return
        }
        scope.launch {
            val stored = episodeStore.load()
            if (stored == null) {
                Log.w(TAG, "No CommuteCast episode available to play from a media control request.")
                return@launch
            }
            audioPlayer.playEpisode(stored)
        }
    }

    // --- MediaBrowserServiceCompat: content tree Android Auto/Automotive OS browse and play ---

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: android.os.Bundle?,
    ): BrowserRoot? {
        if (!CommuteAutoPackageValidator.isTrusted(clientPackageName, packageName)) {
            Log.w(TAG, "Rejected MediaBrowser connection from untrusted package: $clientPackageName")
            return null
        }
        return BrowserRoot(AutoMediaIds.ROOT, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaItem>>) {
        result.detach()
        scope.launch {
            val children = try {
                val podcasts = if (parentId == AutoMediaIds.PODCAST_BRANCH) loadPodcastItems() else emptyList()
                val commute = if (parentId == AutoMediaIds.COMMUTE_BRANCH) {
                    (audioPlayer.playerState.value.episode ?: episodeStore.load())
                        ?.let { CommuteEntry(it.title, it.dialogues.firstOrNull()?.text.orEmpty()) }
                } else {
                    null
                }
                tree.children(parentId, commute, podcasts)?.map { it.toMediaItem() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load children of $parentId", e)
                emptyList()
            }
            result.sendResult(children)
        }
    }

    private fun AutoNode.toMediaItem(): MediaItem {
        val extras = durationSec?.let {
            Bundle().apply { putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it * MILLIS_PER_SECOND) }
        }
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIconBitmap(getOrDecodeArtwork())
            .setExtras(extras)
            .build()
        return MediaItem(description, if (browsable) MediaItem.FLAG_BROWSABLE else MediaItem.FLAG_PLAYABLE)
    }

    private fun playFromMediaId(mediaId: String?) {
        if (mediaId == AutoMediaIds.COMMUTE_EPISODE) {
            resumeOrPlayLatestEpisode()
            return
        }
        scope.launch {
            val started = try {
                podcastLauncher.play(mediaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the requested podcast", e)
                false
            }
            if (!started) Log.w(TAG, "onPlayFromMediaId: ignored mediaId=$mediaId")
        }
    }

    private suspend fun loadPodcastItems(): List<PodcastBrowseItem> = withContext(Dispatchers.IO) {
        articleDao.queryLatestUnreadPodcastEpisodes(applicationContext.currentAccountId, AutoMediaIds.MAX_PODCAST_ITEMS)
    }

    private suspend fun loadPodcastAudio(articleId: String): AudioInfo? = withContext(Dispatchers.IO) {
        val article = articleDao.queryById(articleId)?.article ?: return@withContext null
        val url = article.audioUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        AudioInfo(url, article.audioMime, article.audioDurationSec)
    }

    /** Mirrors whichever engine is active into the car session; the podcast tick is throttled like its own service. */
    private fun renderSession(p: PodcastPlaybackState, c: CommutePlayerState) {
        val side = sideTracker.update(p, c)
        activeSide = side
        val snapshot = when (side) {
            AutoSide.PODCAST -> AutoSessionMapper.podcast(p)
            AutoSide.COMMUTE -> AutoSessionMapper.commute(c, getString(R.string.commute_media_artist))
            AutoSide.NONE -> return
        }
        val sideChanged = side != renderedSide
        renderedSide = side
        val needsState = sideChanged || side == AutoSide.COMMUTE ||
            podcastSync.needsUpdate(p.status, p.speed, p.positionSec, SystemClock.elapsedRealtime())
        if (needsState) {
            val builder = PlaybackStateCompat.Builder()
                .setActions(snapshot.actions)
                .setState(snapshot.state, snapshot.positionMs, snapshot.speed)
            if (snapshot.state == PlaybackStateCompat.STATE_ERROR) {
                builder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, getString(R.string.podcast_error))
            }
            mediaSession?.setPlaybackState(builder.build())
        }
        val key = Triple(side, snapshot.title, snapshot.subtitle)
        if (key != lastMetadataKey) {
            lastMetadataKey = key
            mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.subtitle)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.durationMs)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getOrDecodeArtwork())
                    .build()
            )
        }
    }

    private fun decodeArtwork() {
        if (cachedArtwork != null) return
        try {
            // Downsampled 4x (960 -> ~240px): plenty sharp for a car tile or lock-screen art, far
            // cheaper to decode/pass than the full-resolution launcher asset.
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            cachedArtwork = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_960, options)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode CommuteCast artwork", e)
        }
    }

    /** Whatever is cached right now — null on the very first call until [decodeArtwork] finishes. */
    private fun getOrDecodeArtwork(): Bitmap? = cachedArtwork

    private fun publishMetadata(title: String, dialogueText: String) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getString(R.string.commute_media_artist))
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, getString(R.string.commute_media_album))
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, dialogueText)
            // Unknown/indefinite duration: the engine has no fixed-length audio track to report.
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getOrDecodeArtwork())
            .build()
        mediaSession?.setMetadata(metadata)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_PLAY
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?: audioPlayer.playerState.value.episode?.title
            ?: getString(R.string.commute_cast_title)
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE)
            ?: audioPlayer.playerState.value.currentDialogue?.let { "${it.speaker.name}: ${it.text}" }
            ?: ""

        when (action) {
            ACTION_PLAY -> {
                publishMetadata(title, subtitle)
                val notification = buildNotification(title, subtitle, isPlaying = true)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_PAUSE -> {
                val notification = buildNotification(title, subtitle, isPlaying = false)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_SKIP_NEXT -> {
                audioPlayer.skipNext()
            }
            ACTION_SKIP_PREV -> {
                audioPlayer.skipPrevious()
            }
            ACTION_STOP -> {
                stopServiceGracefully()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopServiceGracefully() {
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
    }

    private fun buildNotification(title: String, subtitle: String, isPlaying: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_SKIP_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            getString(R.string.commute_media_action_prev),
            prevIntent
        ).build()

        val toggleAction = if (isPlaying) {
            val pauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                getString(R.string.commute_media_action_pause),
                pauseIntent
            ).build()
        } else {
            val playIntent = PendingIntent.getService(
                this, 2,
                Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                getString(R.string.commute_media_action_play),
                playIntent
            ).build()
        }

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_SKIP_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            getString(R.string.commute_media_action_next),
            nextIntent
        ).build()

        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            getString(R.string.commute_media_action_stop),
            stopIntent
        ).build()

        val token = mediaSession?.sessionToken

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(getOrDecodeArtwork())
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(toggleAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.commute_media_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.commute_media_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
        cachedArtwork = null
        super.onDestroy()
    }

    companion object {
        const val TAG = "CommuteMediaSession"
        const val CHANNEL_ID = "commute_cast_playback_channel"
        const val NOTIFICATION_ID = 9183

        /** Root id Android Auto/Automotive OS ask for children of; the whole tree is one level deep. */
        const val MEDIA_ROOT_ID = AutoMediaIds.ROOT

        /** The only playable item CommuteCast currently exposes: its single latest episode. */
        const val MEDIA_ID_LATEST_EPISODE = AutoMediaIds.COMMUTE_EPISODE

        const val ACTION_PLAY = "com.mckimquyen.reader.commute.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mckimquyen.reader.commute.ACTION_PAUSE"
        const val ACTION_SKIP_NEXT = "com.mckimquyen.reader.commute.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.mckimquyen.reader.commute.ACTION_SKIP_PREV"
        const val ACTION_STOP = "com.mckimquyen.reader.commute.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        private const val MILLIS_PER_SECOND = 1_000L

        fun start(context: Context, title: String, subtitle: String) {
            val intent = Intent(context, CommuteMediaSessionService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, CommuteMediaSessionService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CommuteMediaSessionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
