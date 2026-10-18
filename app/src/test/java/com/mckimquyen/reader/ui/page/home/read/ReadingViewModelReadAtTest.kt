package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelReadAtTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val articleDao: ArticleDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    private fun createViewModel(dao: ArticleDao? = articleDao): ReadingViewModel {
        return ReadingViewModel(
            rssService = rssService,
            rssHelper = rssHelper,
            ttsManager = ttsManager,
            summaryService = summaryService,
            zenAudioManager = zenAudioManager,
            defaultDispatcher = testDispatcher,
            articleReadPositionDao = null,
            articleDao = dao,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun recordOpened_callsMarkOpenedWithArticleAccountAndIdForUnreadArticle() = runTest(testDispatcher) {
        val article = sampleArticle("a1", accountId = 3, isUnread = true)
        coEvery { repo.findArticleById("a1") } returns article

        val vm = createViewModel()
        vm.initData("a1")
        advanceUntilIdle()

        vm.recordOpened(5_000L)
        advanceUntilIdle()

        coVerify(exactly = 1) { articleDao.markOpened(3, "a1", 5_000L) }
    }

    @Test
    fun recordOpened_alsoRecordsAlreadyReadArticle() = runTest(testDispatcher) {
        val article = sampleArticle("a2", accountId = 3, isUnread = false)
        coEvery { repo.findArticleById("a2") } returns article

        val vm = createViewModel()
        vm.initData("a2")
        advanceUntilIdle()

        vm.recordOpened(6_000L)
        advanceUntilIdle()

        coVerify(exactly = 1) { articleDao.markOpened(3, "a2", 6_000L) }
    }

    @Test
    fun recordOpened_doesNothingWhenNoArticleLoaded() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.recordOpened(7_000L)
        advanceUntilIdle()

        coVerify(exactly = 0) { articleDao.markOpened(any(), any(), any()) }
    }

    @Test
    fun recordOpened_withoutDaoIsNoOp() = runTest(testDispatcher) {
        val article = sampleArticle("a3", accountId = 1, isUnread = true)
        coEvery { repo.findArticleById("a3") } returns article

        val vm = createViewModel(dao = null)
        vm.initData("a3")
        advanceUntilIdle()

        vm.recordOpened(8_000L)
        advanceUntilIdle()

        coVerify(exactly = 0) { articleDao.markOpened(any(), any(), any()) }
    }

    @Test
    fun recordOpened_swallowsDaoFailureWithoutCrashing() = runTest(testDispatcher) {
        val article = sampleArticle("a4", accountId = 1, isUnread = true)
        coEvery { repo.findArticleById("a4") } returns article
        coEvery { articleDao.markOpened(any(), any(), any()) } throws IOException("disk I/O error")

        val vm = createViewModel()
        vm.initData("a4")
        advanceUntilIdle()

        vm.recordOpened(9_000L)
        advanceUntilIdle()

        // Không crash, state bài viết còn nguyên
        org.junit.Assert.assertEquals("a4", vm.readingUiState.value.articleWithFeed?.article?.id)
    }

    @Test
    fun markUnread_doesNotCallMarkOpened() = runTest(testDispatcher) {
        val article = sampleArticle("a5", accountId = 1, isUnread = true)
        coEvery { repo.findArticleById("a5") } returns article

        val vm = createViewModel()
        vm.initData("a5")
        advanceUntilIdle()

        vm.markUnread(false)
        advanceUntilIdle()

        coVerify(exactly = 0) { articleDao.markOpened(any(), any(), any()) }
    }

    private fun sampleArticle(id: String, accountId: Int, isUnread: Boolean) = ArticleWithFeed(
        article = Article(
            id = id,
            date = Date(),
            title = "Title $id",
            rawDescription = "",
            shortDescription = "",
            link = "https://example.com/$id",
            feedId = "feed-1",
            accountId = accountId,
            isUnread = isUnread,
        ),
        feed = Feed(id = "feed-1", name = "Feed", url = "https://example.com/rss", groupId = "g", accountId = accountId),
    )
}
