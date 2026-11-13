package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
import com.mckimquyen.reader.infrastructure.audio.CommuteEpisodeStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.setCommuteScriptGeminiConsent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

// Robolectric, because the preparer logs through android.util.Log, which a bare JVM test cannot
// resolve — the same reason the other CommuteCast unit tests in this module run under it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteEpisodePreparerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scriptService: CommuteScriptService
    private lateinit var episodeStore: CommuteEpisodeStore
    private lateinit var audioPlayer: CommuteAudioPlayer
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var preparer: CommuteEpisodePreparer

    private val articles = listOf<Article>(mockk(relaxed = true))

    private val goodEpisode = CommuteEpisode(
        id = "ep_1",
        title = "Morning Bulletin",
        date = Date(),
        dialogues = listOf(CommuteDialogue(CommuteSpeaker.ALEX, "Good morning.")),
    )

    private val emptyEpisode = goodEpisode.copy(id = "ep_empty", dialogues = emptyList())

    @Before
    fun setUp() {
        scriptService = mockk()
        episodeStore = mockk()
        audioPlayer = mockk()
        notificationHelper = mockk()

        coEvery { episodeStore.save(any()) } just Runs
        every { audioPlayer.prepareEpisode(any()) } just Runs
        every { notificationHelper.notifyCommuteCast(any(), any()) } just Runs

        runBlocking { context.dataStore.edit { it.clear() } }
        preparer = CommuteEpisodePreparer(context, scriptService, episodeStore, audioPlayer, notificationHelper)
    }

    @Test
    fun `persists before notifying when the engine is ready`() = runTest {
        coEvery { scriptService.generateScript(articles, any(), any(), any()) } returns goodEpisode
        coEvery { audioPlayer.awaitReady(any()) } returns true

        val outcome = preparer.prepareAndNotify(articles, isDeepDive = false)

        assertEquals(CommuteEpisodePreparer.Outcome.SUCCESS, outcome)
        coVerify(exactly = 1) { episodeStore.save(goodEpisode) }
        verify(exactly = 1) { audioPlayer.prepareEpisode(goodEpisode) }
        verify(exactly = 1) { notificationHelper.notifyCommuteCast(goodEpisode, any()) }
    }

    @Test
    fun `an episode with no dialogue is neither stored nor announced`() = runTest {
        coEvery { scriptService.generateScript(articles, any(), any(), any()) } returns emptyEpisode

        val outcome = preparer.prepareAndNotify(articles, isDeepDive = false)

        assertEquals(CommuteEpisodePreparer.Outcome.RETRY, outcome)
        coVerify(exactly = 0) { episodeStore.save(any()) }
        verify(exactly = 0) { notificationHelper.notifyCommuteCast(any(), any()) }
        coVerify(exactly = 0) { audioPlayer.awaitReady(any()) }
    }

    @Test
    fun `stores the episode but stays silent when the engine never becomes ready`() = runTest {
        coEvery { scriptService.generateScript(articles, any(), any(), any()) } returns goodEpisode
        coEvery { audioPlayer.awaitReady(any()) } returns false

        val outcome = preparer.prepareAndNotify(articles, isDeepDive = false)

        assertEquals(CommuteEpisodePreparer.Outcome.RETRY, outcome)
        // The work is not lost — it is saved — but promising audio the device cannot speak is not on.
        coVerify(exactly = 1) { episodeStore.save(goodEpisode) }
        verify(exactly = 0) { notificationHelper.notifyCommuteCast(any(), any()) }
    }

    @Test
    fun `passes the deep dive flag through to script generation`() = runTest {
        coEvery { scriptService.generateScript(articles, true, any(), any()) } returns goodEpisode
        coEvery { audioPlayer.awaitReady(any()) } returns true

        preparer.prepareAndNotify(articles, isDeepDive = true)

        coVerify(exactly = 1) { scriptService.generateScript(articles, true, any(), any()) }
    }

    @Test
    fun `without the user's agreement the script is written on the device`() = runTest {
        coEvery { scriptService.generateScript(any(), any(), any(), any()) } returns goodEpisode
        coEvery { audioPlayer.awaitReady(any()) } returns true

        preparer.prepareAndNotify(articles, isDeepDive = false)

        coVerify(exactly = 1) { scriptService.generateScript(articles, false, any(), false) }
    }

    @Test
    fun `after the user agrees the script may use Gemini`() = runTest {
        context.setCommuteScriptGeminiConsent(true)
        coEvery { scriptService.generateScript(any(), any(), any(), any()) } returns goodEpisode
        coEvery { audioPlayer.awaitReady(any()) } returns true

        preparer.prepareAndNotify(articles, isDeepDive = false)

        coVerify(exactly = 1) { scriptService.generateScript(articles, false, any(), true) }
    }

    @Test
    fun `declining after agreeing turns it off again`() = runTest {
        context.setCommuteScriptGeminiConsent(true)
        context.setCommuteScriptGeminiConsent(false)
        coEvery { scriptService.generateScript(any(), any(), any(), any()) } returns goodEpisode
        coEvery { audioPlayer.awaitReady(any()) } returns true

        preparer.prepareAndNotify(articles, isDeepDive = false)

        coVerify(exactly = 1) { scriptService.generateScript(articles, false, any(), false) }
    }
}
