package com.mckimquyen.reader.ui.page.home.read

import android.app.Application
import com.mckimquyen.reader.domain.model.article.Article
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

/** TTS starts at the paragraph nearest the saved reading position instead of always from the top (ZEN-08). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingViewModelPositionHandoffTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val workerDispatcher = StandardTestDispatcher(mainDispatcher.scheduler)

    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)
    private val zenAudioManager = mockk<ZenAudioManager>(relaxed = true)

    private val article = Article(
        id = ARTICLE_ID,
        date = Date(),
        title = "Handoff Article",
        author = "Author",
        rawDescription = FULL_TEXT,
        shortDescription = "preview",
        fullContent = FULL_TEXT,
        link = "https://example.com/handoff",
        feedId = "feed_1",
        accountId = 1,
        isUnread = false,
    )
    private val feed = Feed(
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
            every { spanned.toString() } returns html
            spanned
        }

        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
        coEvery { repo.findArticleById(ARTICLE_ID) } returns ArticleWithFeed(article, feed)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(androidx.core.text.HtmlCompat::class)
    }

    private fun newVm() = ReadingViewModel(
        rssService = rssService,
        rssHelper = rssHelper,
        ttsManager = ttsManager,
        summaryService = summaryService,
        zenAudioManager = zenAudioManager,
        defaultDispatcher = workerDispatcher,
    )

    @Test
    fun togglePlayAudio_defaultFraction_playsFullText() = runTest(mainDispatcher) {
        val vm = newVm()
        vm.initData(ARTICLE_ID, autoTtsEnabled = false)
        advanceUntilIdle()

        vm.togglePlayAudio()
        advanceUntilIdle()

        verify(exactly = 1) { ttsManager.play(FULL_TEXT, any(), any()) }
    }

    @Test
    fun togglePlayAudio_midFraction_playsFromNearestParagraph() = runTest(mainDispatcher) {
        val vm = newVm()
        vm.initData(ARTICLE_ID, autoTtsEnabled = false)
        advanceUntilIdle()

        vm.togglePlayAudio(0.5f)
        advanceUntilIdle()

        verify(exactly = 1) { ttsManager.play("Second paragraph.\n\nThird paragraph.", any(), any()) }
    }

    @Test
    fun togglePlayAudio_nearEndOrStart_restartsFromBeginning() = runTest(mainDispatcher) {
        val vm = newVm()
        vm.initData(ARTICLE_ID, autoTtsEnabled = false)
        advanceUntilIdle()

        vm.togglePlayAudio(0.99f)
        advanceUntilIdle()
        vm.togglePlayAudio(0.01f)
        advanceUntilIdle()

        verify(exactly = 2) { ttsManager.play(FULL_TEXT, any(), any()) }
    }

    private companion object {
        const val ARTICLE_ID = "art_handoff_1"
        const val FULL_TEXT = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
    }
}
