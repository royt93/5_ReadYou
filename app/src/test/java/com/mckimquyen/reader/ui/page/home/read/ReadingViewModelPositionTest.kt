package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.ReadPositionConflictResolver
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleReadPositionDao
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelPositionTest {

    private val dispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val ttsState = kotlinx.coroutines.flow.MutableStateFlow(com.mckimquyen.reader.infrastructure.audio.TtsState.IDLE)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)
    private val zenAudioManager = mockk<ZenAudioManager>(relaxed = true)
    private val readPositionDao = mockk<ArticleReadPositionDao>()
    private lateinit var viewModel: ReadingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { ttsManager.ttsState } returns ttsState
        viewModel = ReadingViewModel(
            rssService = rssService,
            rssHelper = rssHelper,
            ttsManager = ttsManager,
            summaryService = summaryService,
            zenAudioManager = zenAudioManager,
            articleReadPositionDao = readPositionDao,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initData_loadsSavedPositionForArticle() = runTest(dispatcher) {
        val articleWithFeed = mockk<ArticleWithFeed>(relaxed = true) {
            every { article.id } returns ARTICLE_ID
            every { article.accountId } returns ACCOUNT_ID
        }
        val savedPosition = position(progress = SAVED_PROGRESS)
        every { rssService.get() } returns mockk {
            coEvery { findArticleById(ARTICLE_ID) } returns articleWithFeed
        }
        coEvery { readPositionDao.getPosition(ARTICLE_ID) } returns savedPosition
        viewModel.initData(ARTICLE_ID)
        advanceUntilIdle()

        assertEquals(savedPosition, viewModel.readingUiState.value.savedReadPosition)
        coVerify { readPositionDao.getPosition(ARTICLE_ID) }
    }

    @Test
    fun saveReadPosition_ignoresNearStartAndNearEndProgress() = runTest(dispatcher) {
        coEvery { readPositionDao.getPosition(ARTICLE_ID) } returns null
        coEvery { readPositionDao.upsertPosition(any()) } returns Unit

        viewModel.saveReadPosition(ARTICLE_ID, NEAR_START_PROGRESS)
        viewModel.saveReadPosition(ARTICLE_ID, NEAR_END_PROGRESS)
        advanceUntilIdle()

        coVerify(exactly = 0) { readPositionDao.upsertPosition(any()) }
    }

    @Test
    fun saveReadPosition_mergesWithExistingPositionAndKeepsFurtherProgress() = runTest(dispatcher) {
        val articleWithFeed = mockk<ArticleWithFeed>(relaxed = true) {
            every { article.id } returns ARTICLE_ID
            every { article.accountId } returns ACCOUNT_ID
        }
        every { rssService.get() } returns mockk {
            coEvery { findArticleById(ARTICLE_ID) } returns articleWithFeed
        }
        val existing = position(progress = SAVED_PROGRESS, lastReadTime = OLDER_TIMESTAMP)
        coEvery { readPositionDao.getPosition(ARTICLE_ID) } returns existing
        val saved = mutableListOf<ArticleReadPosition>()
        coEvery { readPositionDao.upsertPosition(capture(saved)) } returns Unit

        viewModel.initData(ARTICLE_ID)
        advanceUntilIdle()

        viewModel.saveReadPosition(ARTICLE_ID, LOWER_PROGRESS)
        advanceUntilIdle()

        assertEquals(1, saved.size)
        assertEquals(SAVED_PROGRESS, saved.single().progress)
        assertEquals(ARTICLE_ID, saved.single().articleId)
    }

    @Test
    fun saveReadPosition_persistsWhenInsideAllowedProgressRange() = runTest(dispatcher) {
        val articleWithFeed = mockk<ArticleWithFeed>(relaxed = true) {
            every { article.id } returns ARTICLE_ID
            every { article.accountId } returns ACCOUNT_ID
        }
        every { rssService.get() } returns mockk {
            coEvery { findArticleById(ARTICLE_ID) } returns articleWithFeed
        }
        coEvery { readPositionDao.getPosition(ARTICLE_ID) } returns null
        val saved = mutableListOf<ArticleReadPosition>()
        coEvery { readPositionDao.upsertPosition(capture(saved)) } returns Unit

        viewModel.initData(ARTICLE_ID)
        advanceUntilIdle()

        viewModel.saveReadPosition(ARTICLE_ID, VALID_PROGRESS)
        advanceUntilIdle()

        assertEquals(1, saved.size)
        assertEquals(VALID_PROGRESS, saved.single().progress)
    }

    private fun position(progress: Float, lastReadTime: Long = OLDER_TIMESTAMP) = ArticleReadPosition(
        articleId = ARTICLE_ID,
        accountId = ACCOUNT_ID,
        feedUrl = "https://example.com/feed",
        articleLink = "https://example.com/article",
        progress = progress,
        lastReadTime = lastReadTime,
        isSynced = false,
    )

    private companion object {
        const val ARTICLE_ID = "article-1"
        const val ACCOUNT_ID = 1
        const val SAVED_PROGRESS = 0.75f
        const val LOWER_PROGRESS = 0.25f
        const val VALID_PROGRESS = 0.45f
        const val NEAR_START_PROGRESS = 0.01f
        const val NEAR_END_PROGRESS = 0.99f
        const val OLDER_TIMESTAMP = 1_700_000_000_000L
    }
}
