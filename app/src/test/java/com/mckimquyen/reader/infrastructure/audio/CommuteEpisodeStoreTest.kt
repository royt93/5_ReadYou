package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteEpisodeStoreTest {

    private lateinit var context: Context
    private lateinit var store: CommuteEpisodeStore
    private val dispatcher = UnconfinedTestDispatcher()

    private val sampleEpisode = CommuteEpisode(
        id = "ep_morning_42",
        title = "Morning Coffee Bulletin",
        date = Date(1_700_000_000_000L),
        dialogues = listOf(
            CommuteDialogue(CommuteSpeaker.ALEX, "Welcome to the morning bulletin."),
            CommuteDialogue(CommuteSpeaker.SAM, "Here are the top three stories today."),
        ),
        articleIds = listOf("art_1", "art_2"),
        isDeepDive = false,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("commute_cast_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        store = CommuteEpisodeStore(context, Gson(), dispatcher)
    }

    @Test
    fun `starts empty when nothing was ever saved`() = runTest(dispatcher) {
        assertNull(store.load())
    }

    @Test
    fun `saves and loads an episode with its dialogues and ids intact`() = runTest(dispatcher) {
        store.save(sampleEpisode)

        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(sampleEpisode.id, loaded!!.id)
        assertEquals(sampleEpisode.title, loaded.title)
        assertEquals(sampleEpisode.date.time, loaded.date.time)
        assertEquals(sampleEpisode.dialogues, loaded.dialogues)
        assertEquals(sampleEpisode.articleIds, loaded.articleIds)
        assertEquals(sampleEpisode.isDeepDive, loaded.isDeepDive)
    }

    @Test
    fun `falls back to the backup copy when the primary JSON is corrupt`() = runTest(dispatcher) {
        val first = sampleEpisode.copy(id = "ep_first", title = "First Good Copy")
        val second = sampleEpisode.copy(id = "ep_second", title = "Second Good Copy")

        // First write sets primary; second write moves first to backup and sets second as primary.
        store.save(first)
        store.save(second)

        // Corrupt the primary to simulate a write cut off mid-flush by a killed process.
        context.getSharedPreferences("commute_cast_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("latest_episode", "{malformed json here...")
            .commit()

        val loaded = store.load()
        assertNotNull("Must recover the last good copy instead of losing the episode", loaded)
        assertEquals(first.id, loaded!!.id)
        assertEquals("First Good Copy", loaded.title)
    }

    @Test
    fun `rejects a corrupt payload with missing dialogues and returns null`() = runTest(dispatcher) {
        context.getSharedPreferences("commute_cast_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("latest_episode", """{"id":"broken","title":"Empty","dialogues":[]}""")
            .commit()

        assertNull("An episode with no dialogues cannot be played and must be rejected", store.load())
    }

    @Test
    fun `clear removes both primary and backup copies`() = runTest(dispatcher) {
        store.save(sampleEpisode)
        store.save(sampleEpisode.copy(id = "ep_second"))
        store.clear()

        assertNull(store.load())
    }
}
