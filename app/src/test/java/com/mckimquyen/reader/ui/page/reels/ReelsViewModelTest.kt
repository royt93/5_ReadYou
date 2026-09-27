package com.mckimquyen.reader.ui.page.reels

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReelsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ReelsViewModel(
        context = context,
        articleDao = articleDao,
        ioDispatcher = testDispatcher,
        defaultDispatcher = testDispatcher,
    )

    private fun articleWithFeed(
        id: String,
        title: String = "Solar power hits a record high",
        html: String = LONG_BODY,
        img: String? = "https://cdn.example.com/cover.jpg",
        fullContent: String? = null,
    ) = ArticleWithFeed(
        article = Article(
            id = id,
            title = title,
            rawDescription = html,
            shortDescription = "",
            fullContent = fullContent,
            img = img,
            link = "https://news.example.com/$id",
            feedId = FEED_ID,
            accountId = ACCOUNT_ID,
            date = Date(FIXED_DATE_MILLIS),
        ),
        feed = Feed(
            id = FEED_ID,
            name = "Example Energy Daily",
            url = "https://news.example.com/rss",
            groupId = "g1",
            accountId = ACCOUNT_ID,
        ),
    )

    @Test
    fun `maps articles into cards with at most three offline bullets`() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), any()) } returns
            listOf(articleWithFeed("a1"))

        val state = viewModel().let { advanceUntilIdle(); it.uiState.value }

        assertFalse(state.isLoading)
        assertEquals(1, state.items.size)
        val item = state.items.first()
        assertEquals("a1", item.articleId)
        assertEquals("Solar power hits a record high", item.title)
        assertEquals("Example Energy Daily", item.feedName)
        assertEquals(FIXED_DATE_MILLIS, item.publishedAtMillis)
        assertEquals("https://cdn.example.com/cover.jpg", item.coverImageUrl)
        assertTrue("Bullets must be produced offline", item.bullets.isNotEmpty())
        assertTrue(
            "Expected at most ${ReelsViewModel.BULLET_COUNT} bullets, got ${item.bullets.size}",
            item.bullets.size <= ReelsViewModel.BULLET_COUNT,
        )
        assertTrue("Bullets must not be blank", item.bullets.none { it.isBlank() })
    }

    @Test
    fun `strips html so bullets never contain markup`() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), any()) } returns
            listOf(articleWithFeed("a1"))

        val state = viewModel().let { advanceUntilIdle(); it.uiState.value }

        val bullets = state.items.first().bullets
        assertTrue("Tags leaked into a bullet: $bullets", bullets.none { it.contains('<') })
    }

    @Test
    fun `flags articles that carry a playable video`() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), any()) } returns listOf(
            articleWithFeed("withVideo", html = """$LONG_BODY<video src="https://c.example/v.mp4"></video>"""),
            articleWithFeed("withIframe", html = """$LONG_BODY<iframe src="https://www.youtube.com/embed/x"></iframe>"""),
            articleWithFeed("plain"),
        )

        val items = viewModel().let { advanceUntilIdle(); it.uiState.value.items }

        assertTrue(items.first { it.articleId == "withVideo" }.hasVideo)
        assertFalse(
            "A YouTube iframe is not a native-playable video",
            items.first { it.articleId == "withIframe" }.hasVideo,
        )
        assertFalse(items.first { it.articleId == "plain" }.hasVideo)
    }

    @Test
    fun `prefers fullContent over rawDescription when summarising`() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), any()) } returns listOf(
            articleWithFeed(
                id = "a1",
                html = "<p>Stub teaser text that should be ignored entirely.</p>",
                fullContent = LONG_BODY,
            )
        )

        val bullets = viewModel().let { advanceUntilIdle(); it.uiState.value.items.first().bullets }

        assertTrue(
            "Summary must come from fullContent, got $bullets",
            bullets.any { it.contains("grid", ignoreCase = true) || it.contains("battery", ignoreCase = true) },
        )
    }

    @Test
    fun `reports empty state when there are no articles`() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), any()) } returns emptyList()

        val state = viewModel().let { advanceUntilIdle(); it.uiState.value }

        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertTrue("isEmpty must be true once loading finished", state.isEmpty)
    }

    @Test
    fun `survives articles with no cover image and no body`() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), any()) } returns
            listOf(articleWithFeed("bare", html = "", img = null))

        val item = viewModel().let { advanceUntilIdle(); it.uiState.value.items.first() }

        assertNull(item.coverImageUrl)
        assertTrue("An empty body yields no bullets rather than a crash", item.bullets.isEmpty())
    }

    @Test
    fun `isEmpty stays false while still loading`() {
        // A blank list mid-load must not render the "nothing here" message.
        assertFalse(ReelsUiState(items = emptyList(), isLoading = true).isEmpty)
    }

    private companion object {
        const val ACCOUNT_ID = 1
        const val FEED_ID = "f1"
        const val FIXED_DATE_MILLIS = 1_700_000_000_000L

        /**
         * Long enough for the heuristic extractor to find several scoring sentences; it discards
         * anything shorter than 25 characters.
         */
        val LONG_BODY = """
            <p>Solar generation set a new national record this quarter, regulators confirmed today.</p>
            <p>Output reached 42% of total demand during peak afternoon hours across the grid.</p>
            <p>Battery installations doubled year over year, smoothing the evening ramp considerably.</p>
            <p>Analysts expect the grid operator to raise its renewable target again next spring.</p>
            <p>Coal plants ran at their lowest capacity factor since records began in this region.</p>
        """.trimIndent()
    }
}
