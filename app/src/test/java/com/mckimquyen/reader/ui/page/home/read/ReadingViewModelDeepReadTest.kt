package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelDeepReadTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)
    private val zenAudioManager = mockk<ZenAudioManager>(relaxed = true)

    private val dummyArticle = Article(
        id = "art_deep_1",
        date = Date(),
        title = "AI Deep Read Capabilities",
        author = "Researcher",
        rawDescription = "<p>Interactive conversational AI allows readers to query complex articles directly.</p>",
        shortDescription = "AI Q&A Feature",
        fullContent = "<p>Interactive conversational AI allows readers to query complex articles directly. The system extracts key evidence and cites sources.</p>",
        link = "https://readyou.app",
        feedId = "feed_1",
        accountId = 1,
        isUnread = false,
    )
    private val dummyFeed = Feed(
        id = "feed_1",
        name = "ReadYou Tech",
        url = "https://readyou.app/feed",
        groupId = "group_1",
        accountId = 1,
        isFullContent = false
    )
    private val dummyArticleWithFeed = ArticleWithFeed(dummyArticle, dummyFeed)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        io.mockk.mockkStatic(androidx.core.text.HtmlCompat::class)
        val mockSpanned = mockk<android.text.Spanned>()
        every { mockSpanned.toString() } returns "Interactive conversational AI allows readers to query complex articles directly. The system extracts key evidence and cites sources."
        every { androidx.core.text.HtmlCompat.fromHtml(any(), any()) } returns mockSpanned

        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(com.mckimquyen.reader.infrastructure.audio.TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
        coEvery { repo.findArticleById("art_deep_1") } returns dummyArticleWithFeed
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    private fun createViewModel(): ReadingViewModel {
        return ReadingViewModel(
            rssService = rssService,
            rssHelper = rssHelper,
            ttsManager = ttsManager,
            summaryService = summaryService,
            zenAudioManager = zenAudioManager,
        )
    }

    @Test
    fun openDeepRead_initializesActiveSessionWithChips() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.initData("art_deep_1")
        advanceUntilIdle()

        vm.openDeepRead()
        advanceUntilIdle()

        val state = vm.readingUiState.value
        assertTrue("Sheet should be shown", state.showDeepReadSheet)
        assertTrue("State should be Active", state.deepReadState is DeepReadState.Active)

        val active = state.deepReadState as DeepReadState.Active
        assertEquals("art_deep_1", active.session.articleId)
        assertEquals("AI Deep Read Capabilities", active.session.articleTitle)
        assertTrue("Suggested chips should be generated", active.session.suggestedChips.isNotEmpty())
        assertEquals("Should have 1 initial welcome message", 1, active.session.messages.size)
        assertEquals(DeepReadSender.ASSISTANT, active.session.messages.first().sender)
    }

    @Test
    fun dismissDeepRead_hidesSheet() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.initData("art_deep_1")
        advanceUntilIdle()

        vm.openDeepRead()
        assertTrue(vm.readingUiState.value.showDeepReadSheet)

        vm.dismissDeepRead()
        assertFalse(vm.readingUiState.value.showDeepReadSheet)
    }

    @Test
    fun sendDeepReadQuestion_success_appendsAssistantReply() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.initData("art_deep_1")
        advanceUntilIdle()
        vm.openDeepRead()
        advanceUntilIdle()

        val mockReply = DeepReadMessage(
            sender = DeepReadSender.ASSISTANT,
            content = "This article discusses interactive conversational AI.",
            isOfflineFallback = false,
            isGrounded = true
        )
        coEvery {
            summaryService.askArticleQuestion(any(), any(), any(), any(), any())
        } returns mockReply

        vm.sendDeepReadQuestion("What is this article about?")
        advanceUntilIdle()

        val state = vm.readingUiState.value
        val active = state.deepReadState as DeepReadState.Active
        assertFalse("Should not be sending anymore", active.isSending)
        assertEquals("Should have welcome + user + assistant message", 3, active.session.messages.size)

        val userMsg = active.session.messages[1]
        assertEquals(DeepReadSender.USER, userMsg.sender)
        assertEquals("What is this article about?", userMsg.content)

        val assistantMsg = active.session.messages[2]
        assertEquals(DeepReadSender.ASSISTANT, assistantMsg.sender)
        assertEquals("This article discusses interactive conversational AI.", assistantMsg.content)
        assertFalse("Should not be offline", assistantMsg.isOfflineFallback)
    }

    @Test
    fun sendDeepReadQuestion_whenServiceThrows_usesOfflineFallback() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.initData("art_deep_1")
        advanceUntilIdle()
        vm.openDeepRead()
        advanceUntilIdle()

        coEvery {
            summaryService.askArticleQuestion(any(), any(), any(), any(), any())
        } throws RuntimeException("Network down")

        vm.sendDeepReadQuestion("Summary please")
        advanceUntilIdle()

        val state = vm.readingUiState.value
        val active = state.deepReadState as DeepReadState.Active
        assertFalse("Should finish sending", active.isSending)
        assertEquals("Should have 3 messages", 3, active.session.messages.size)

        val fallbackMsg = active.session.messages[2]
        assertEquals(DeepReadSender.ASSISTANT, fallbackMsg.sender)
        assertTrue("Must be marked offline fallback", fallbackMsg.isOfflineFallback)
        assertTrue("Must contain content", fallbackMsg.content.isNotBlank())
    }

    @Test
    fun clearDeepReadChat_resetsMessagesPreservingChips() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.initData("art_deep_1")
        advanceUntilIdle()
        vm.openDeepRead()
        advanceUntilIdle()

        coEvery {
            summaryService.askArticleQuestion(any(), any(), any(), any(), any())
        } returns DeepReadMessage(sender = DeepReadSender.ASSISTANT, content = "Answer")

        vm.sendDeepReadQuestion("Question")
        advanceUntilIdle()

        val beforeClear = (vm.readingUiState.value.deepReadState as DeepReadState.Active).session
        assertEquals(3, beforeClear.messages.size)

        vm.clearDeepReadChat()

        val afterClear = (vm.readingUiState.value.deepReadState as DeepReadState.Active).session
        assertEquals(1, afterClear.messages.size)
        assertEquals(beforeClear.suggestedChips, afterClear.suggestedChips)
    }

    @Test
    fun sendDeepReadQuestion_blankQuestion_isIgnored() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.initData("art_deep_1")
        advanceUntilIdle()
        vm.openDeepRead()
        advanceUntilIdle()

        vm.sendDeepReadQuestion("   ")
        advanceUntilIdle()

        val active = vm.readingUiState.value.deepReadState as DeepReadState.Active
        assertEquals(1, active.session.messages.size)
    }
}
