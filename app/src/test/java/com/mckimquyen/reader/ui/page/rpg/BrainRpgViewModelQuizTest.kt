package com.mckimquyen.reader.ui.page.rpg

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.AiQuizFailure
import com.mckimquyen.reader.domain.sv.AiQuizResult
import com.mckimquyen.reader.domain.sv.AiQuizService
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import com.mckimquyen.reader.ui.ext.quizGeminiConsent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Same pattern as PodcastStudioViewModelTest: Main and the view model's dispatchers are unconfined, so state changes
 * happen as soon as they can, and anything that touches the real DataStore on Dispatchers.IO is awaited in real time
 * with a deadline (a test scheduler cannot wait for that, which is what hung the first version of this class).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainRpgViewModelQuizTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var ai: AiQuizService
    private lateinit var viewModel: BrainRpgViewModel

    private val ready = QuizQuestion("art-1", "Which?", listOf("a one", "b two", "c three", "d four"), 1, "Because.", "General Knowledge")

    @Before fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.dataStore.edit { it.clear() } }
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        val dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        val repository = BrainRpgRepository(context, database, dao, importer, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        ai = mockk()
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returns AiQuizResult.Ready(ready)
        viewModel = BrainRpgViewModel(context, repository, QuizGeneratorService(), ai, mockk(relaxed = true), Dispatchers.Unconfined, Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun seedConsent(agreed: Boolean) {
        context.dataStore.putBlocking(DataStoreKeys.QuizGeminiConsent, agreed)
    }

    private fun show(articleId: String = "art-1", title: String = "Rocket launch", preview: String = "An agency launched a rocket.") =
        viewModel.onQuizCardShown(articleId, title, preview, "BODY-THAT-MUST-NOT-BE-SENT")

    private fun awaitState(what: String, timeoutMs: Long = WAIT_MS, condition: (QuizUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition(viewModel.quizState.value)) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for $what; state is ${viewModel.quizState.value}")
            Thread.sleep(POLL_MS)
        }
    }

    private fun awaitConsent(expected: Boolean?) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (context.quizGeminiConsent != expected) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("consent never became $expected, is ${context.quizGeminiConsent}")
            Thread.sleep(POLL_MS)
        }
    }

    @Test fun beforeTheCardIsShown_theStateIsHidden() {
        assertEquals(QuizUiState.Hidden, viewModel.quizState.value)
    }

    @Test fun neverAsked_showsTheConsentQuestion_andMakesNoRequest() {
        show()
        awaitState("the consent question") { it == QuizUiState.AwaitingConsent }

        coVerify(exactly = 0) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun declinedConsent_showsTheGuidance_andMakesNoRequest() {
        seedConsent(false)
        show()
        awaitState("the guidance") { it == QuizUiState.ConsentDeclined }

        coVerify(exactly = 0) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun agreedConsent_makesExactlyOneRequest_andShowsTheQuiz() {
        seedConsent(true)
        show()
        awaitState("the quiz") { it == QuizUiState.Ready(ready) }

        coVerify(exactly = 1) { ai.generate("art-1", "Rocket launch", "An agency launched a rocket.", any(), any()) }
    }

    @Test fun theBodyOfTheArticleIsNeverPassedToTheService() {
        seedConsent(true)
        show()
        awaitState("the quiz") { it is QuizUiState.Ready }

        coVerify(exactly = 0) { ai.generate(any(), any(), match { it.contains("BODY-THAT-MUST-NOT-BE-SENT") }, any(), any()) }
        coVerify(exactly = 0) { ai.generate(match { it.contains("BODY") }, match { it.contains("BODY") }, any(), any(), any()) }
    }

    @Test fun agreeingInTheCard_savesTrue_thenGeneratesForThePendingArticle() {
        show()
        awaitState("the consent question") { it == QuizUiState.AwaitingConsent }

        viewModel.onQuizConsent(true)
        awaitConsent(true)
        awaitState("the quiz") { it == QuizUiState.Ready(ready) }

        assertEquals(true, context.quizGeminiConsent)
        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun decliningInTheCard_savesFalse_andShowsTheGuidance() {
        show()
        awaitState("the consent question") { it == QuizUiState.AwaitingConsent }

        viewModel.onQuizConsent(false)
        awaitConsent(false)
        awaitState("the guidance") { it == QuizUiState.ConsentDeclined }

        coVerify(exactly = 0) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun later_savesNothing_andKeepsAsking() {
        show()
        awaitState("the consent question") { it == QuizUiState.AwaitingConsent }

        viewModel.onQuizConsentLater()

        assertEquals(null, context.quizGeminiConsent)
        assertEquals(QuizUiState.AwaitingConsent, viewModel.quizState.value)
    }

    @Test fun reopeningTheSameArticle_usesTheCache_withoutANewRequest() {
        seedConsent(true)
        show()
        awaitState("the quiz") { it == QuizUiState.Ready(ready) }
        show()
        awaitState("the cached quiz") { it == QuizUiState.Ready(ready) }

        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun aSecondCallWhileLoading_doesNotStartAnotherRequest() {
        seedConsent(true)
        val gate = CompletableDeferred<AiQuizResult>()
        coEvery { ai.generate(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        show()
        awaitState("loading") { it == QuizUiState.Loading }

        show()
        gate.complete(AiQuizResult.Ready(ready))
        awaitState("the quiz") { it == QuizUiState.Ready(ready) }

        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun aFailure_isShownAndNotCached_andIsNotRetriedAutomatically() {
        seedConsent(true)
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returns AiQuizResult.Failed(AiQuizFailure.NETWORK)
        show()
        awaitState("the failure") { it == QuizUiState.Failed(AiQuizFailure.NETWORK) }
        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }

        show() // reopening after a failure asks again: a failure was never cached
        awaitState("the failure again") { it == QuizUiState.Failed(AiQuizFailure.NETWORK) }
        coVerify(exactly = 2) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun retryAfterAFailure_makesOneNewRequest() {
        seedConsent(true)
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returnsMany
            listOf(AiQuizResult.Failed(AiQuizFailure.REJECTED), AiQuizResult.Ready(ready))
        show()
        awaitState("the failure") { it == QuizUiState.Failed(AiQuizFailure.REJECTED) }

        viewModel.retryQuizGeneration()
        awaitState("the quiz") { it == QuizUiState.Ready(ready) }

        coVerify(exactly = 2) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun retryWhenNotFailed_doesNothing() {
        seedConsent(true)
        show()
        awaitState("the quiz") { it == QuizUiState.Ready(ready) }

        viewModel.retryQuizGeneration()

        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun movingToAnotherArticle_dropsTheStateOfTheFirst() {
        seedConsent(true)
        val gate = CompletableDeferred<AiQuizResult>()
        coEvery { ai.generate("art-1", any(), any(), any(), any()) } coAnswers { gate.await() }
        coEvery { ai.generate("art-2", any(), any(), any(), any()) } returns AiQuizResult.Ready(ready.copy(articleId = "art-2"))
        show("art-1")
        awaitState("loading art-1") { it == QuizUiState.Loading }

        show("art-2")
        awaitState("the quiz for art-2") { it is QuizUiState.Ready && it.quiz.articleId == "art-2" }
        gate.complete(AiQuizResult.Ready(ready)) // the late answer for art-1 must not overwrite art-2
        Thread.sleep(LATE_ANSWER_GRACE_MS)

        assertTrue((viewModel.quizState.value as QuizUiState.Ready).quiz.articleId == "art-2")
    }

    @Test fun anArticleWithNoTitleAndNoPreview_isShownAsFailedWithoutARequest() {
        seedConsent(true)
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returns AiQuizResult.Failed(AiQuizFailure.NOTHING_TO_ASK)

        show(title = "", preview = "")
        awaitState("the failure") { it == QuizUiState.Failed(AiQuizFailure.NOTHING_TO_ASK) }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
        const val LATE_ANSWER_GRACE_MS = 200L
    }
}
