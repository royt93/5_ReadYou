package com.mckimquyen.reader.ui.page.digest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.digest.DailyDigest
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.DailyDigestDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.sv.DailyDigestSv
import com.mckimquyen.reader.infrastructure.ai.DailyDigestContent
import com.mckimquyen.reader.infrastructure.ai.DailyDigestItem
import com.mckimquyen.reader.infrastructure.ai.DailyDigestSection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class DigestViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sv = mockk<DailyDigestSv>()
    private val digestDao = mockk<DailyDigestDao>()
    private val articleDao = mockk<ArticleDao>()
    private val feedDao = mockk<FeedDao>()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val gson = Gson()

    private fun digest(id: String, sources: List<String> = listOf("a1"), createdAt: Long = 1L): DailyDigest {
        val content = DailyDigestContent(
            headline = "Head $id",
            sections = listOf(DailyDigestSection("S", listOf(DailyDigestItem("Item", sources)))),
        )
        return DailyDigest(
            id = id,
            createdAt = createdAt,
            headline = "Head $id",
            contentJson = gson.toJson(content),
            sourceArticleIdsJson = gson.toJson(sources),
            accountId = 1,
        )
    }

    private fun feed(name: String) = Feed(id = "f1", name = name, url = "https://f", groupId = "g", accountId = 1)

    private fun article(id: String, title: String) = ArticleWithFeed(
        article = Article(
            id = id,
            date = Date(0),
            title = title,
            rawDescription = "",
            shortDescription = "",
            link = "https://example.test/$id",
            feedId = "f1",
            accountId = 1,
        ),
        feed = feed("Feed"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { feedDao.queryById("f1") } returns feed("Feed One")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DigestViewModel(context, sv, digestDao, articleDao, feedDao, dispatcher)

    @Test
    fun startsLoading_thenShowsTheLatestSavedDigest() = runTest(dispatcher) {
        val latest = digest("new", createdAt = 2L)
        val older = digest("old", createdAt = 1L)
        coEvery { digestDao.queryRecent(any(), any()) } returns listOf(latest, older)
        coEvery { articleDao.queryById("a1") } returns article("a1", "Story")

        val vm = vm()
        assertEquals(DigestUiState.Loading, vm.state.value)
        advanceUntilIdle()

        val success = vm.state.value as DigestUiState.Success
        assertEquals("new", success.digest.id)
        assertEquals(listOf("new", "old"), success.history.map { it.id })
        assertEquals(DigestSourceUi("a1", "Story", "https://example.test/a1"), success.sources.single())
    }

    @Test
    fun noSavedDigest_showsEmpty() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } returns emptyList()
        val vm = vm()
        advanceUntilIdle()
        assertEquals(DigestUiState.Empty, vm.state.value)
    }

    @Test
    fun aSourceWhoseArticleWasDeleted_isDropped_notShownAsABrokenRow() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } returns listOf(digest("d", sources = listOf("a1", "gone")))
        coEvery { articleDao.queryById("a1") } returns article("a1", "Story")
        coEvery { articleDao.queryById("gone") } returns null

        val vm = vm()
        advanceUntilIdle()

        assertEquals(listOf("a1"), (vm.state.value as DigestUiState.Success).sources.map { it.id })
    }

    @Test
    fun aBlankArticleTitle_fallsBackToTheFeedName() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } returns listOf(digest("d"))
        coEvery { articleDao.queryById("a1") } returns article("a1", "   ")

        val vm = vm()
        advanceUntilIdle()

        assertEquals("Feed One", (vm.state.value as DigestUiState.Success).sources.single().title)
    }

    @Test
    fun aCorruptStoredDigest_showsError_insteadOfCrashing() = runTest(dispatcher) {
        val broken = digest("d").copy(contentJson = "{not json")
        coEvery { digestDao.queryRecent(any(), any()) } returns listOf(broken)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(DigestUiState.Error, vm.state.value)
    }

    @Test
    fun aFailingDatabase_showsError() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } throws IllegalStateException("db closed")
        val vm = vm()
        advanceUntilIdle()
        assertEquals(DigestUiState.Error, vm.state.value)
    }

    @Test
    fun generate_showsLoading_thenTheNewDigest() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } returns emptyList()
        val fresh = digest("fresh")
        val gate = CompletableDeferred<Unit>()
        coEvery { sv.generate(any()) } coAnswers { gate.await(); fresh }
        coEvery { sv.history(any()) } returns listOf(fresh)
        coEvery { articleDao.queryById("a1") } returns article("a1", "Story")
        val vm = vm()
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle() // generation is now suspended on the gate
        assertEquals("Loading while the model works", DigestUiState.Loading, vm.state.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("fresh", (vm.state.value as DigestUiState.Success).digest.id)
        coVerify { sv.generate(any()) }
    }

    @Test
    fun generate_withNothingToSummarise_showsEmpty() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } returns emptyList()
        coEvery { sv.generate(any()) } returns null
        val vm = vm()
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        assertEquals(DigestUiState.Empty, vm.state.value)
    }

    @Test
    fun generate_failure_showsError_andALaterGenerateCanRecover() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } returns emptyList()
        val fresh = digest("fresh")
        coEvery { sv.generate(any()) } throws IllegalStateException("network") andThen fresh
        coEvery { sv.history(any()) } returns listOf(fresh)
        coEvery { articleDao.queryById("a1") } returns article("a1", "Story")
        val vm = vm()
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()
        assertEquals(DigestUiState.Error, vm.state.value)

        vm.generate()
        advanceUntilIdle()
        assertTrue(vm.state.value is DigestUiState.Success)
    }

    @Test
    fun selectDigest_showsThePickedOne_andIgnoresAnUnknownId() = runTest(dispatcher) {
        val a = digest("a", createdAt = 2L)
        val b = digest("b", createdAt = 1L)
        coEvery { digestDao.queryRecent(any(), any()) } returns listOf(a, b)
        coEvery { articleDao.queryById("a1") } returns article("a1", "Story")
        val vm = vm()
        advanceUntilIdle()

        vm.selectDigest("b")
        advanceUntilIdle()
        assertEquals("b", (vm.state.value as DigestUiState.Success).digest.id)

        vm.selectDigest("missing")
        advanceUntilIdle()
        assertEquals("unknown id leaves the current digest", "b", (vm.state.value as DigestUiState.Success).digest.id)
    }

    @Test
    fun doubleTapOnGenerate_runsOneGeneration_notTwo() = runTest(dispatcher) {
        coEvery { digestDao.queryRecent(any(), any()) } returns emptyList()
        val gate = CompletableDeferred<Unit>()
        val fresh = digest("fresh")
        coEvery { sv.generate(any()) } coAnswers { gate.await(); fresh }
        coEvery { sv.history(any()) } returns listOf(fresh)
        coEvery { articleDao.queryById("a1") } returns article("a1", "Story")
        val vm = vm()
        advanceUntilIdle()

        vm.generate()
        vm.generate() // second tap while the first is still running
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { sv.generate(any()) }
        assertEquals("fresh", (vm.state.value as DigestUiState.Success).digest.id)
    }

    @Test
    fun aStoredDigestWithNoSections_showsError_notACrashInComposition() = runTest(dispatcher) {
        // Gson bypasses Kotlin null-safety: a row whose JSON lacks `sections` yields sections == null.
        val broken = digest("d").copy(contentJson = "{\"headline\":\"Only a headline\"}")
        coEvery { digestDao.queryRecent(any(), any()) } returns listOf(broken)
        // Everything else resolves fine, so ONLY the missing sections can make this an Error.
        coEvery { articleDao.queryById("a1") } returns article("a1", "Story")
        val vm = vm()
        advanceUntilIdle()
        val state = vm.state.value
        // Whatever the outcome, a Success must never carry null sections (DigestPage would NPE composing it).
        @Suppress("USELESS_CAST", "UNNECESSARY_SAFE_CALL")
        val sections = (state as? DigestUiState.Success)?.content?.sections as List<*>?
        assertEquals(DigestUiState.Error, state)
        assertEquals(null, sections)
    }

    @Test
    fun aStoredDigestWithNoSourceList_showsError() = runTest(dispatcher) {
        val broken = digest("d").copy(sourceArticleIdsJson = "null")
        coEvery { digestDao.queryRecent(any(), any()) } returns listOf(broken)
        coEvery { articleDao.queryById(any()) } returns article("a1", "Story")
        val vm = vm()
        advanceUntilIdle()
        assertEquals(DigestUiState.Error, vm.state.value)
    }
}
