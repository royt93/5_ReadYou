package com.mckimquyen.reader.domain.sv

import android.util.Log
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
import com.mckimquyen.reader.infrastructure.audio.CommuteEpisodeStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the morning episode, stores it, and only then announces it.
 *
 * Lives outside [CommuteWorker] on purpose: a `CoroutineWorker` needs `WorkerParameters`, which
 * cannot be constructed in a JVM test, so logic kept inside `doWork()` could not be tested for real.
 * Here every branch — empty script, storage, engine never becoming ready — is reachable from a
 * plain unit test.
 */
@Singleton
class CommuteEpisodePreparer @Inject constructor(
    private val scriptService: CommuteScriptService,
    private val episodeStore: CommuteEpisodeStore,
    private val audioPlayer: CommuteAudioPlayer,
    private val notificationHelper: NotificationHelper,
) {

    /** What the caller should do next, mirroring `ListenableWorker.Result` without depending on it. */
    enum class Outcome { SUCCESS, RETRY }

    /**
     * Generates an episode from [articles], persists it, and notifies only once it can really be
     * played.
     *
     * Order matters: storing before notifying is what lets a tap on the notification play the exact
     * bulletin that was promised, even after the system has reclaimed the process.
     */
    suspend fun prepareAndNotify(articles: List<Article>, isDeepDive: Boolean): Outcome {
        val episode = scriptService.generateScript(articles, isDeepDive = isDeepDive)

        if (episode.dialogues.isEmpty()) {
            // Nothing to say: announcing it would promise audio that is silence.
            Log.w(TAG, "Generated CommuteCast episode had no dialogue; not notifying.")
            return Outcome.RETRY
        }

        episodeStore.save(episode)
        audioPlayer.prepareEpisode(episode)

        if (!audioPlayer.awaitReady(TTS_READY_TIMEOUT_MS)) {
            // The episode is already saved, so nothing is lost — but a notification would lead to
            // silence on a device whose speech engine never came up.
            Log.w(TAG, "TTS not ready within ${TTS_READY_TIMEOUT_MS}ms; episode saved but not announced.")
            return Outcome.RETRY
        }

        notificationHelper.notifyCommuteCast(episode)
        Log.d(TAG, "CommuteCast episode persisted and announced: ${episode.id}")
        return Outcome.SUCCESS
    }

    private companion object {
        const val TAG = "CommuteEpisodePreparer"

        /** Generous enough for a cold speech engine, short enough that the worker is not held open. */
        const val TTS_READY_TIMEOUT_MS = 5_000L
    }
}
