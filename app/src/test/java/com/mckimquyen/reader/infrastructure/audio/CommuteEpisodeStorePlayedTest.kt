package com.mckimquyen.reader.infrastructure.audio

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteEpisodeStorePlayedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: CommuteEpisodeStore

    @Before
    fun setUp() {
        store = CommuteEpisodeStore(context, Gson(), Dispatchers.Unconfined)
        runBlocking { store.clear() }
    }

    @Test fun nothingMarked_isNotPlayed() = runBlocking { assertFalse(store.isPlayed("a")) }

    @Test fun markedEpisode_isPlayed() = runBlocking {
        store.markPlayed("a")
        assertTrue(store.isPlayed("a"))
    }

    @Test fun anotherEpisode_isNotPlayed_evenAfterOneWasMarked() = runBlocking {
        store.markPlayed("a")
        assertFalse(store.isPlayed("b"))
    }

    @Test fun markingANewEpisode_forgetsTheOldOne() = runBlocking {
        store.markPlayed("a")
        store.markPlayed("b")
        assertFalse(store.isPlayed("a"))
        assertTrue(store.isPlayed("b"))
    }

    @Test fun clear_forgetsThePlayedFlag() = runBlocking {
        store.markPlayed("a")
        store.clear()
        assertFalse(store.isPlayed("a"))
    }

    @Test fun savingANewEpisode_doesNotMarkItPlayed() = runBlocking {
        store.markPlayed("old")
        store.save(com.mckimquyen.reader.domain.model.commute.CommuteEpisode(
            id = "new", title = "t",
            dialogues = listOf(com.mckimquyen.reader.domain.model.commute.CommuteDialogue(com.mckimquyen.reader.domain.model.commute.CommuteSpeaker.ALEX, "hi")),
        ))
        assertFalse(store.isPlayed("new"))
    }
}
