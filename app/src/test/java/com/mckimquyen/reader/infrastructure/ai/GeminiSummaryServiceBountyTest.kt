package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GeminiSummaryServiceBountyTest {

    private val context: Context = mockk(relaxed = true)
    private val gateway: AiRequestGateway = mockk()
    private lateinit var service: GeminiSummaryService

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        service = GeminiSummaryService(context, OkHttpClient(), gateway)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun related(id: String, title: String, short: String = "") = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id, date = Date(0), title = title, author = null, shortDescription = short, img = null,
            link = "https://e.com/$id", feedId = "f", accountId = 1, isUnread = true, isStarred = false,
            isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
            audioMime = null, audioDurationSec = null,
        ),
        feed = Feed(id = "f", name = "Feed", url = "https://e.com", groupId = "g", accountId = 1),
    )

    @Test
    fun blankCustomKey_throwsMissingApiKey_andNeverCallsGateway() = runBlocking {
        service = serviceWithCustomKey("   ")

        try {
            service.summarizeBountyContext("DeepSeek", listOf(related("1", "DeepSeek ships")))
            fail("expected MissingApiKey")
        } catch (e: GeminiSummaryService.SummaryException.MissingApiKey) {
            // expected
        }
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun customKey_isTheOnlyKeyPassedToGateway_noDefaultKeyFallback() = runBlocking {
        service = serviceWithCustomKey("  USER-KEY  ")
        val keys = slot<List<String>>()
        val useCase = slot<String>()
        coEvery { gateway.execute(capture(useCase), any(), capture(keys)) } returns "  context text  "

        val answer = service.summarizeBountyContext("DeepSeek", listOf(related("1", "DeepSeek ships")))

        assertEquals("context text", answer)
        assertEquals(listOf("USER-KEY"), keys.captured)
        assertEquals("bounty_context", useCase.captured)
    }

    @Test
    fun gatewayFailure_isNotRetriedWithDefaultKeys() = runBlocking {
        service = serviceWithCustomKey("USER-KEY")
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.RateLimited

        try {
            service.summarizeBountyContext("DeepSeek", listOf(related("1", "DeepSeek ships")))
            fail("expected RateLimited")
        } catch (e: GeminiSummaryService.SummaryException.RateLimited) {
            // expected
        }
        coVerify(exactly = 1) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun emptyGatewayAnswer_isReportedAsEmptyResponse() = runBlocking {
        service = serviceWithCustomKey("USER-KEY")
        coEvery { gateway.execute(any(), any(), any()) } returns "   "

        try {
            service.summarizeBountyContext("DeepSeek", listOf(related("1", "DeepSeek ships")))
            fail("expected EmptyResponse")
        } catch (e: GeminiSummaryService.SummaryException.EmptyResponse) {
            // expected
        }
    }

    @Test
    fun promptContainsOnlyTitlesAndShortDescriptions_andIsBounded() = runBlocking {
        service = serviceWithCustomKey("USER-KEY")
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns "ok"
        val many = (1..50).map { related("$it", "Title $it", "Short $it ".repeat(100)) }

        service.summarizeBountyContext("DeepSeek", many)

        val request = body.captured
        assertTrue(request.contains("DeepSeek"))
        assertTrue(request.contains("Title 1"))
        assertTrue("prompt must be bounded", request.length < 20_000)
    }

    private fun serviceWithCustomKey(key: String): GeminiSummaryService {
        val keyReader = mockk<(Context) -> String>()
        every { keyReader.invoke(any()) } returns key
        return GeminiSummaryService(context, OkHttpClient(), gateway, customKeyReader = keyReader)
    }
}
