package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelContrastTest {
    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val articleDao: ArticleDao = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
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

    @After fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    private fun createViewModel() = ReadingViewModel(
        rssService = rssService,
        rssHelper = rssHelper,
        ttsManager = ttsManager,
        summaryService = summaryService,
        zenAudioManager = zenAudioManager,
        defaultDispatcher = dispatcher,
        articleDao = articleDao,
        ioDispatcher = dispatcher,
    )

    private fun item(id: String, title: String, feedId: String = "f1", feedName: String = "Feed 1") =
        ArticleListItemWithFeed(
            article = ArticleListItem(
                id = id, date = Date(1_000L), title = title, author = null, shortDescription = "", img = null,
                link = "https://e.com/$id", feedId = feedId, accountId = 7, isUnread = true, isStarred = false,
                isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
                audioMime = null, audioDurationSec = null,
            ),
            feed = Feed(id = feedId, name = feedName, url = "https://e.com", groupId = "g", accountId = 7),
        )

    @Test
    fun loadArticle_withContrastingCandidates_setsReadyState() = runTest(dispatcher) {
        val current = item("cur", "SpaceX expands Starlink coverage rapidly", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", any(), 40) } returns listOf(
            item("opp1", "Astronomers warn Starlink brightness disrupts observations", feedId = "f2", feedName = "ScienceFeed"),
        )

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        val state = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Starlink", state.selectedPhrase) // longest phrase of "SpaceX" and "Starlink"
        assertEquals(1, state.items.size)
        assertEquals("opp1", state.items.first().articleId)
        assertEquals("warn", state.items.first().cue)
    }

    @Test
    fun loadArticle_withoutContrastingCandidates_setsNoneState() = runTest(dispatcher) {
        val current = item("cur", "SpaceX expands Starlink coverage rapidly", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }

    @Test
    fun loadArticle_withNoExtractablePhrases_setsNoneState() = runTest(dispatcher) {
        val current = item("cur", "all lowercase headline with no proper nouns", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }

    @Test
    fun selectContrastPhrase_switchesToAnotherExtractedPhrase() = runTest(dispatcher) {
        val current = item("cur", "Elon Musk announces new Starlink deployment plan", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        // Extracted phrases, longest first: "Elon Musk", "Starlink", "Musk". The candidate contains all of them.
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", any(), 40) } returns listOf(
            item("opp1", "Critics warn Elon Musk Starlink plan poses risks", feedId = "f2", feedName = "OtherFeed"),
        )

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        val first = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Elon Musk", first.selectedPhrase)
        assertTrue(first.phrases.containsAll(listOf("Elon Musk", "Starlink")))

        vm.selectContrastPhrase("Starlink")
        advanceUntilIdle()

        val updated = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Starlink", updated.selectedPhrase)
        assertEquals(first.phrases, updated.phrases)
    }

    @Test
    fun switchingArticle_resetsContrastStateToIdleThenSearches() = runTest(dispatcher) {
        val cur1 = item("c1", "Starlink expands", feedId = "f1", feedName = "F1")
        val cur2 = item("c2", "nothing here", feedId = "f1", feedName = "F1")
        coEvery { repo.findArticleById("c1") } returns cur1.toArticleWithFeed()
        coEvery { repo.findArticleById("c2") } returns cur2.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        vm.initData("c1")
        advanceUntilIdle()

        vm.initData("c2")
        // Stale state from c1 is cleared
        advanceUntilIdle()
        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }
}
