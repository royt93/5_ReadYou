package com.mckimquyen.reader.domain.model.podcast

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking
import com.mckimquyen.reader.ui.ext.podcastStudioGeminiConsent
import com.mckimquyen.reader.ui.ext.putBlocking
import com.mckimquyen.reader.ui.ext.setPodcastStudioGeminiConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PodcastStudioModelsTest {

    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            context.dataStore.edit { it.remove(DataStoreKeys.PodcastStudioGeminiConsent.key) }
        }
    }

    @Test fun podcastStudioEpisodePreservesWrappedEpisodeAndSource() {
        val episode = CommuteEpisode(
            id = "ep_1",
            title = "Article Episode",
            date = Date(1_794_052_800_000L),
            dialogues = listOf(
                CommuteDialogue(CommuteSpeaker.ALEX, "Hello Sam"),
                CommuteDialogue(CommuteSpeaker.SAM, "Hi Alex"),
            ),
            articleIds = listOf("art_1"),
            isDeepDive = false,
        )
        val studioEpisodeGemini = PodcastStudioEpisode(episode, PodcastScriptSource.GEMINI)
        assertEquals(episode, studioEpisodeGemini.episode)
        assertEquals(PodcastScriptSource.GEMINI, studioEpisodeGemini.source)

        val studioEpisodeHeuristic = PodcastStudioEpisode(episode, PodcastScriptSource.HEURISTIC)
        assertEquals(PodcastScriptSource.HEURISTIC, studioEpisodeHeuristic.source)
    }

    @Test fun consentKeyUsesItsPinnedPreferenceName() {
        assertEquals("podcast_studio_gemini_consent", DataStoreKeys.PodcastStudioGeminiConsent.key.name)
    }

    @Test fun consentIsNullWhenTheUserNeverDecided() {
        assertNull(context.podcastStudioGeminiConsent)
    }

    @Test fun consentGetterFollowsEveryWrite() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.PodcastStudioGeminiConsent, true)
        assertEquals(true, context.podcastStudioGeminiConsent)

        context.setPodcastStudioGeminiConsent(false)
        awaitCondition("consent becomes false") { context.podcastStudioGeminiConsent == false }
        assertEquals(false, context.podcastStudioGeminiConsent)

        context.setPodcastStudioGeminiConsent(true)
        awaitCondition("consent becomes true") { context.podcastStudioGeminiConsent == true }
        assertEquals(true, context.podcastStudioGeminiConsent)
    }

    private fun awaitCondition(what: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(10)
        }
    }
}
