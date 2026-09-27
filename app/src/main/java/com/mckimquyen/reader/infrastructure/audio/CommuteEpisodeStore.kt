package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable storage for the most recent CommuteCast episode.
 *
 * The episode used to live only in [CommuteAudioPlayer]'s in-memory state, so once the system
 * reclaimed the process — routine after a WorkManager job finishes — tapping the morning
 * notification found nothing and had to call the AI again, producing a different bulletin from the
 * one the notification promised. This survives that.
 *
 * Only the latest episode is kept: the notification only ever offers one, so a history would be
 * storage nobody reads. Persisting through [android.content.SharedPreferences] rather than Room
 * follows `WatchdogManager`, down to the backup key that rescues a primary corrupted by a write
 * that a process death cut in half.
 */
@Singleton
class CommuteEpisodeStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * Wire format. Deliberately flat primitives, and the date is milliseconds rather than a
     * [Date]: Gson formats dates with the runtime's default locale, so an episode saved under one
     * app language would fail to parse after the user switches to another.
     */
    private data class EpisodeDto(
        // Every field is nullable because Gson populates missing JSON keys with null whatever the
        // Kotlin type says. Declaring the truth here is what makes the checks in toEpisode() real
        // rather than dead code the compiler folds away.
        val id: String?,
        val title: String?,
        val dateMillis: Long?,
        val dialogues: List<DialogueDto>?,
        val articleIds: List<String>?,
        val isDeepDive: Boolean?,
    )

    private data class DialogueDto(
        val speaker: String?,
        val text: String?,
    )

    suspend fun save(episode: CommuteEpisode): Unit = withContext(ioDispatcher) {
        try {
            val json = gson.toJson(episode.toDto())
            val previous = prefs.getString(KEY_EPISODE, null)
            prefs.edit().apply {
                putString(KEY_EPISODE, json)
                // Keep the last good copy: if this write is interrupted, the next load recovers it
                // instead of resetting to "no episode" and regenerating from the AI.
                if (!previous.isNullOrBlank()) putString(KEY_EPISODE_BACKUP, previous)
            }.commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist CommuteCast episode: ${e.message}", e)
        }
    }

    /** Returns the stored episode, or null when nothing was ever saved or both copies are unusable. */
    suspend fun load(): CommuteEpisode? = withContext(ioDispatcher) {
        val primary = prefs.getString(KEY_EPISODE, null)
        parse(primary)?.let { return@withContext it }

        if (!primary.isNullOrBlank()) {
            Log.e(TAG, "CommuteCast episode JSON corrupt, falling back to last-known-good backup")
        }

        parse(prefs.getString(KEY_EPISODE_BACKUP, null))
    }

    suspend fun clear(): Unit = withContext(ioDispatcher) {
        prefs.edit().remove(KEY_EPISODE).remove(KEY_EPISODE_BACKUP).commit()
    }

    private fun parse(json: String?): CommuteEpisode? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson(json, EpisodeDto::class.java)?.toEpisode()
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Malformed CommuteCast episode JSON: ${e.message}")
            null
        }
    }

    private fun CommuteEpisode.toDto() = EpisodeDto(
        id = id,
        title = title,
        dateMillis = date.time,
        dialogues = dialogues.map { DialogueDto(speaker = it.speaker.name, text = it.text) },
        articleIds = articleIds,
        isDeepDive = isDeepDive,
    )

    private fun EpisodeDto.toEpisode(): CommuteEpisode? {
        // A truncated or hand-edited payload is rejected here: an episode with no id or no lines
        // would play as silence, which is worse than honestly reporting nothing was stored.
        val episodeId = id?.takeIf { it.isNotBlank() } ?: return null
        val safeDialogues = dialogues?.mapNotNull { it.toDialogue() }.orEmpty()
        if (safeDialogues.isEmpty()) return null

        return CommuteEpisode(
            id = episodeId,
            title = title.orEmpty(),
            date = Date(dateMillis ?: 0L),
            dialogues = safeDialogues,
            articleIds = articleIds.orEmpty(),
            isDeepDive = isDeepDive ?: false,
        )
    }

    private fun DialogueDto.toDialogue(): CommuteDialogue? {
        val safeText = text ?: return null
        val safeSpeaker = CommuteSpeaker.entries.firstOrNull { it.name == speaker } ?: return null
        return CommuteDialogue(speaker = safeSpeaker, text = safeText)
    }

    private companion object {
        const val TAG = "CommuteEpisodeStore"
        const val PREFS_NAME = "commute_cast_prefs"
        const val KEY_EPISODE = "latest_episode"
        const val KEY_EPISODE_BACKUP = "latest_episode_backup"
    }
}
