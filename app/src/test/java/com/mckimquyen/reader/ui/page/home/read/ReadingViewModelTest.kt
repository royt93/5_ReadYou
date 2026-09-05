package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns kotlinx.coroutines.flow.MutableStateFlow(com.mckimquyen.reader.infrastructure.audio.TtsState.IDLE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initData_emitsScrollToTopEventAndLoadsArticle() = runTest(testDispatcher) {
        val dummyArticle = Article(
            id = "test_art_1",
            date = Date(),
            title = "Kotlin 2.1 Released",
            author = "JetBrains",
            rawDescription = "Full content here",
            shortDescription = "Short content",
            fullContent = "Full content here",
            link = "https://kotlinlang.org",
            feedId = "feed_1",
            accountId = 1,
            isUnread = true,
        )
        val dummyFeed = Feed(
            id = "feed_1",
            name = "Kotlin Blog",
            url = "https://blog.jetbrains.com/kotlin/feed",
            groupId = "group_1",
            accountId = 1,
            isFullContent = false
        )
        val dummyArticleWithFeed = ArticleWithFeed(dummyArticle, dummyFeed)

        coEvery { repo.findArticleById("test_art_1") } returns dummyArticleWithFeed

        val viewModel = ReadingViewModel(
            rssService = rssService,
            rssHelper = rssHelper,
            ttsManager = ttsManager,
            summaryService = summaryService
        )

        var receivedScrollEvent = false
        val job = launch {
            viewModel.scrollToTopEvent.collect {
                receivedScrollEvent = true
            }
        }

        viewModel.initData("test_art_1", autoTtsEnabled = false)
        advanceUntilIdle()

        val state = viewModel.readingUiState.value
        assertNotNull(state.articleWithFeed)
        assertEquals("Kotlin 2.1 Released", state.articleWithFeed?.article?.title)
        assertEquals(false, state.isLoading)
        assertTrue("scrollToTopEvent phải được phát", receivedScrollEvent)

        job.cancel()
    }
}
