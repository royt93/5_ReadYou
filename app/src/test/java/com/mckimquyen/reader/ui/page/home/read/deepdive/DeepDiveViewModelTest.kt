package com.mckimquyen.reader.ui.page.home.read.deepdive

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.ai.agents.DeepDiveImpact
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.ai.agents.DeepDivePerspective
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.domain.sv.AiDeepDiveFailure
import com.mckimquyen.reader.domain.sv.AiDeepDiveResult
import com.mckimquyen.reader.domain.sv.AiDeepDiveService
import com.mckimquyen.reader.infrastructure.ai.DeepDiveSharer
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.deepDiveGeminiConsent
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepDiveViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val service: AiDeepDiveService = mockk()
    private val sharer: DeepDiveSharer = mockk()
    private lateinit var cache: DeepDiveCache
    private lateinit var viewModel: DeepDiveViewModel

    private val report = DeepDiveReport(
        "art-1", listOf("a", "b", "c"),
        listOf(DeepDivePerspective("s1", "v1"), DeepDivePerspective("s2", "v2")),
        listOf(DeepDiveImpact("x", "y", "z"), DeepDiveImpact("x2", "y2", "z2")),
    )
    private val texts = DeepDivePdfTexts("AI", "Context", "Critical", "Impact", "Area", "Short", "Long", "More", "2026-11-11")

    @Before fun setUp() {
        context.dataStore.putBlocking(DataStoreKeys.DeepDiveGeminiConsent, true)
        Dispatchers.setMain(mainDispatcher)
        cache = DeepDiveCache()
        coEvery { service.generate(any(), any(), any(), any()) } returns AiDeepDiveResult.Ready(report)
        viewModel = DeepDiveViewModel(context, service, cache, sharer, Dispatchers.Unconfined)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun forgetConsent() = runBlocking { context.dataStore.edit { it.remove(DataStoreKeys.DeepDiveGeminiConsent.key) } }

    private fun awaitState(timeoutMs: Long = 3_000, condition: (DeepDiveUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition(viewModel.state.value)) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out; state=${viewModel.state.value}")
            Thread.sleep(10)
        }
    }

    @Test fun startsIdle() { assertEquals(DeepDiveUiState.Idle, viewModel.state.value) }

    @Test fun neverAsked_showsTheConsentQuestionAndSendsNothing() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun declined_showsGuidanceAndSendsNothing() {
        context.dataStore.putBlocking(DataStoreKeys.DeepDiveGeminiConsent, false)
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.ConsentDeclined }
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun agreed_makesExactlyOneRequestAndShowsTheReport() {
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        assertEquals(report, (viewModel.state.value as DeepDiveUiState.Ready).report)
        coVerify(exactly = 1) { service.generate("art-1", "Title", "<p>Body</p>", any()) }
        assertEquals(report, cache.get("art-1"))
    }

    @Test fun cachedReport_isShownWithoutAnyRequestEvenWithoutConsent() {
        forgetConsent()
        cache.put("art-1", report)
        viewModel.open("art-1", "Title", "<p>Body</p>")
        assertEquals(DeepDiveUiState.Ready(report), viewModel.state.value)
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun aNewViewModelReusesTheSharedCache() {
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        val second = DeepDiveViewModel(context, service, cache, sharer, Dispatchers.Unconfined)
        second.open("art-1", "Title", "<p>Body</p>")
        assertTrue(second.state.value is DeepDiveUiState.Ready)
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun openingTheSameArticleWhileLoading_doesNotStartASecondRequest() {
        val gate = CompletableDeferred<AiDeepDiveResult>()
        coEvery { service.generate(any(), any(), any(), any()) } coAnswers { gate.await() }
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.Loading }
        Thread.sleep(100) // let the consent read finish and the request start
        viewModel.open("art-1", "Title", "<p>Body</p>")
        gate.complete(AiDeepDiveResult.Ready(report))
        awaitState { it is DeepDiveUiState.Ready }
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun failure_isShownAndNotCached_andRetryAsksAgainOnlyOnTap() {
        coEvery { service.generate(any(), any(), any(), any()) } returns AiDeepDiveResult.Failed(AiDeepDiveFailure.REJECTED)
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Failed }
        assertEquals(DeepDiveUiState.Failed(AiDeepDiveFailure.REJECTED), viewModel.state.value)
        assertNull(cache.get("art-1"))
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }

        coEvery { service.generate(any(), any(), any(), any()) } returns AiDeepDiveResult.Ready(report)
        viewModel.retry()
        awaitState { it is DeepDiveUiState.Ready }
        coVerify(exactly = 2) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun retryWhileLoading_doesNotStartASecondRequest() {
        val gate = CompletableDeferred<AiDeepDiveResult>()
        coEvery { service.generate(any(), any(), any(), any()) } coAnswers { gate.await() }
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.Loading }
        Thread.sleep(100)
        viewModel.retry() // the state is Loading, not Failed: a tap must not start another request
        gate.complete(AiDeepDiveResult.Ready(report))
        awaitState { it is DeepDiveUiState.Ready }
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun retryOnAReadyReport_doesNotAskAgain() {
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        viewModel.retry()
        Thread.sleep(100)
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun retryDoesNothingUnlessTheStateIsFailed() {
        viewModel.retry()
        assertEquals(DeepDiveUiState.Idle, viewModel.state.value)
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun agreeingInTheSheet_storesTheConsentAndGenerates() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        viewModel.onConsent(true)
        awaitState { it is DeepDiveUiState.Ready }
        assertEquals(true, context.deepDiveGeminiConsent)
    }

    @Test fun decliningInTheSheet_storesFalseAndSendsNothing() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        viewModel.onConsent(false)
        awaitState { it == DeepDiveUiState.ConsentDeclined }
        assertEquals(false, context.deepDiveGeminiConsent)
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun notNow_closesTheSheetAndStoresNothing() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        viewModel.onConsentLater()
        assertEquals(DeepDiveUiState.Idle, viewModel.state.value)
        assertNull(context.deepDiveGeminiConsent)
    }

    @Test fun dismiss_cancelsTheRequestAndGoesIdle() {
        val gate = CompletableDeferred<AiDeepDiveResult>()
        coEvery { service.generate(any(), any(), any(), any()) } coAnswers { gate.await() }
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.Loading }
        viewModel.dismiss()
        assertEquals(DeepDiveUiState.Idle, viewModel.state.value)
        gate.complete(AiDeepDiveResult.Ready(report))
        Thread.sleep(150)
        assertEquals("a late answer must not reopen the sheet", DeepDiveUiState.Idle, viewModel.state.value)
        assertNull("a cancelled request must not be cached", cache.get("art-1"))
    }

    @Test fun openingAnotherArticle_dropsTheOlderRequest() {
        val gate = CompletableDeferred<AiDeepDiveResult>()
        coEvery { service.generate("art-1", any(), any(), any()) } coAnswers { gate.await() }
        coEvery { service.generate("art-2", any(), any(), any()) } returns AiDeepDiveResult.Ready(report.copy(articleId = "art-2"))
        viewModel.open("art-1", "One", "")
        awaitState { it == DeepDiveUiState.Loading }
        Thread.sleep(100)
        viewModel.open("art-2", "Two", "")
        awaitState { it is DeepDiveUiState.Ready }
        gate.complete(AiDeepDiveResult.Ready(report))
        Thread.sleep(150)
        assertEquals("art-2", (viewModel.state.value as DeepDiveUiState.Ready).report.articleId)
        assertNull(cache.get("art-1"))
    }

    @Test fun prepareShare_isNullUntilAReportIsReady() = runBlocking {
        assertNull(viewModel.prepareShare(texts, "Share"))
    }

    // The success path (real PDF bytes, one A4 page) cannot run here: Robolectric's PdfDocument is closed on creation.
    // It is covered on a device by DeepDiveSheetWidgetTest (Task 10). What is checkable here is that a failure while
    // building or writing the PDF is reported as null (no crash, no half-sent intent) and never reaches the chooser.
    @Test fun prepareShare_neverBuildsAChooserWhenThePdfCannotBeProduced() = runBlocking {
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        assertNull(viewModel.prepareShare(texts, "Share"))
        verify(exactly = 0) { sharer.buildShareIntent(any(), any()) }
    }

    @Test fun prepareShare_returnsNullWhenWritingFails() = runBlocking {
        coEvery { sharer.write(any(), any(), any()) } throws java.io.IOException("disk full")
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        assertNull(viewModel.prepareShare(texts, "Share"))
    }

    @Test fun theViewModelNeverTouchesAnArticleOrALink() {
        val source = File("src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveViewModel.kt").readText()
        listOf("Article", ".link", "feedId", "accountId", "ArticleWithFeed").forEach {
            assertTrue("$it must not appear in the view model", !source.contains(it))
        }
    }
}
