package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * The point of DJ-05, checked on a real device: an episode prepared by the morning worker must
 * still be there for a brand-new process, so tapping the notification plays what was promised
 * instead of calling the AI again for a different bulletin.
 */
@RunWith(AndroidJUnit4::class)
class CommuteEpisodePersistenceIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val episode = CommuteEpisode(
        id = "ep_integration",
        title = "Integration Morning Bulletin",
        date = Date(1_700_000_000_000L),
        dialogues = listOf(
            CommuteDialogue(CommuteSpeaker.ALEX, "Solar output set a record this quarter."),
            CommuteDialogue(CommuteSpeaker.SAM, "And battery installs doubled year over year."),
            CommuteDialogue(CommuteSpeaker.ALEX, "That is the morning bulletin."),
        ),
        articleIds = listOf("art_a", "art_b"),
        isDeepDive = false,
    )

    private fun newStore() = CommuteEpisodeStore(context, Gson(), Dispatchers.IO)

    @Before
    fun setUp() = clearPrefs()

    @After
    fun tearDown() = clearPrefs()

    private fun clearPrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun anEpisodeSavedByOneProcessIsReadableByTheNext() = runBlocking {
        // First "process": the morning worker prepares and stores the episode.
        newStore().save(episode)

        // Second "process": a fresh store instance, exactly what Hilt builds after the system has
        // reclaimed the app and the user taps the notification.
        val restored = newStore().load()

        assertNotNull("The episode must survive the process that created it", restored)
        assertEquals(episode.id, restored!!.id)
        assertEquals(episode.title, restored.title)
        assertEquals(episode.date.time, restored.date.time)
        assertEquals(episode.dialogues, restored.dialogues)
        assertEquals(episode.articleIds, restored.articleIds)
        assertEquals(episode.isDeepDive, restored.isDeepDive)
    }

    @Test
    fun aRestoredEpisodeLoadsIntoThePlayerWithoutSpeaking() = runBlocking {
        newStore().save(episode)
        val restored = newStore().load()
        assertNotNull(restored)

        val player = CommuteAudioPlayer(context)
        try {
            player.prepareEpisode(restored!!)

            val state = player.playerState.value
            assertEquals(episode.id, state.episode?.id)
            assertEquals(0, state.currentDialogueIndex)
            assertFalse("Restoring must not start audio on its own", state.isPlaying)
            assertFalse(state.isAwaitingPlayback)
        } finally {
            player.shutdown()
        }
    }

    @Test
    fun aCorruptPrimaryStillYieldsTheLastGoodEpisode() = runBlocking {
        val store = newStore()
        store.save(episode)
        store.save(episode.copy(id = "ep_newer", title = "Newer Bulletin"))

        // Simulate a write that a process death cut in half.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EPISODE, "{\"id\":\"truncated\"")
            .commit()

        val restored = newStore().load()
        assertNotNull("A half-written primary must not lose the episode", restored)
        assertEquals(episode.id, restored!!.id)
    }

    @Test
    fun clearingLeavesNothingBehindForTheNextProcess() = runBlocking {
        newStore().save(episode)
        newStore().clear()

        assertNull(newStore().load())
    }

    private companion object {
        const val PREFS_NAME = "commute_cast_prefs"
        const val KEY_EPISODE = "latest_episode"
    }
}
