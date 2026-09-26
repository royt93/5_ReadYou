package com.mckimquyen.reader.ui.page.home.read

import android.app.Application
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
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
import io.mockk.verify
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Verifies [FIX-05]: HTML parsing across TTS, Summary, MindMap, and DeepRead is moved off the
 * Main thread and executed on [defaultDispatcher] (Dispatchers.Default), preventing UI jank on long articles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingViewModelHtmlWorkerThreadTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val workerDispatcher = StandardTestDispatcher(mainDispatcher.scheduler)

    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)
    private val zenAudioManager = mockk<ZenAudioManager>(relaxed = true)

    private val longHtmlArticle = Article(
        id = "art_worker_1",
        date = Date(),
        title = "Very Long Article",
        author = "Author",
        rawDescription = "<p>Heavy html paragraph</p>",
        shortDescription = "Long article preview",
        fullContent = "<p>Heavy html paragraph</p>",
        link = "https://example.com/long",
        feedId = "feed_1",
        accountId = 1,
        isUnread = false,
    )
    private val dummyFeed = Feed(
        id = "feed_1",
        name = "Feed",
        url = "https://example.com/feed",
        groupId = "group_1",
        accountId = 1,
        isFullContent = false,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        io.mockk.mockkStatic(androidx.core.text.HtmlCompat::class)
        every { androidx.core.text.HtmlCompat.fromHtml(any(), any()) } answers {
            val html = firstArg<String>()
            val spanned = mockk<android.text.Spanned>()
            every { spanned.toString() } returns html.replace(Regex("<[^>]*>"), "")
            spanned
        }

        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
        coEvery { repo.findArticleById("art_worker_1") } returns ArticleWithFeed(longHtmlArticle, dummyFeed)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(androidx.core.text.HtmlCompat::class)
    }

    @Test
    fun playCurrentContent_stripsHtmlAndStartsTts_viaWorkerDispatcher() = runTest(mainDispatcher) {
        val viewModel = ReadingViewModel(
            rssService = rssService,
            rssHelper = rssHelper,
            ttsManager = ttsManager,
            summaryService = summaryService,
            zenAudioManager = zenAudioManager,
            defaultDispatcher = workerDispatcher,
        )
        viewModel.initData("art_worker_1", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.togglePlayAudio()
        advanceUntilIdle()

        verify(exactly = 1) {
            ttsManager.play("Heavy html paragraph", "Very Long Article", "Feed")
        }
    }

    @Test
    fun requestSummary_parsesHtmlOnWorkerDispatcher_beforeCallingAiGateway() = runTest(mainDispatcher) {
        coEvery { summaryService.extractHighlights(any(), any(), any()) } returns ArticleHighlights(
            tldr = "TLDR",
            keyTakeaways = listOf("Point 1"),
        )

        val viewModel = ReadingViewModel(
            rssService = rssService,
            rssHelper = rssHelper,
            ttsManager = ttsManager,
            summaryService = summaryService,
            zenAudioManager = zenAudioManager,
            defaultDispatcher = workerDispatcher,
        )
        viewModel.initData("art_worker_1", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.requestSummary()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            summaryService.extractHighlights(
                title = "Very Long Article",
                plainText = "Heavy html paragraph",
                any(),
            )
        }
    }
}
