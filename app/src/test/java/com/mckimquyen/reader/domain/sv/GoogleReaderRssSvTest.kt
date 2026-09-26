package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.account.sec.GoogleReaderSecurityKey
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.provider.googleReader.GoogleReaderApi
import com.mckimquyen.reader.infrastructure.rss.provider.googleReader.GoogleReaderApiDto
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import com.mckimquyen.reader.ui.ext.currentAccountId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GoogleReaderRssSvTest {

    private val accountId by lazy {
        ApplicationProvider.getApplicationContext<Context>().run { currentAccountId }
    }

    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val feedDao = mockk<FeedDao>(relaxed = true)
    private val groupDao = mockk<GroupDao>(relaxed = true)
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val api = mockk<GoogleReaderApi>(relaxed = true)
    private val worker = mockk<CoroutineWorker>(relaxed = true)
    private lateinit var googleReaderRssSv: GoogleReaderRssSv

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkObject(GoogleReaderApi.Companion)
        every { GoogleReaderApi.getInstance(any(), any(), any()) } returns api
        googleReaderRssSv = GoogleReaderRssSv(
            context = context,
            articleDao = articleDao,
            feedDao = feedDao,
            rssHelper = mockk<RssHelper>(relaxed = true),
            notificationHelper = mockk<NotificationHelper>(relaxed = true),
            accountDao = accountDao,
            groupDao = groupDao,
            feedHealthDao = mockk<FeedHealthDao>(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined,
            workManager = mockk<WorkManager>(relaxed = true),
            watchdogManager = mockk<WatchdogManager>(relaxed = true),
            smartFilterManager = mockk<SmartFilterManager>(relaxed = true) {
                every { applyRules(any()) } answers { firstArg() }
            },
        )
    }

    @After
    fun tearDown() {
        unmockkObject(GoogleReaderApi.Companion)
    }

    private fun account(securityKey: GoogleReaderSecurityKey) = Account(
        id = accountId,
        name = "Self-hosted",
        type = AccountType.GoogleReader,
        securityKey = securityKey.toString(),
    )

    @Test
    fun validCredentials_whenAccountMissing_throwsIllegalState() {
        coEvery { accountDao.queryById(any()) } returns null

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { googleReaderRssSv.validCredentials() }
        }

        assertEquals("Account not found", error.message)
    }

    @Test
    fun sync_whenAccountMissing_returnsFailure() {
        coEvery { accountDao.queryById(any()) } returns null

        val result = runBlocking { googleReaderRssSv.sync(worker) }

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun sync_mapsCategoriesFeedsAndArticles_skippingIncompleteEntries() {
        coEvery { accountDao.queryById(any()) } answers {
            account(GoogleReaderSecurityKey(serverUrl = "https://rss.test", username = "u", password = "p"))
                .copy(id = firstArg())
        }

        coEvery { api.getSubscriptionList() } returns GoogleReaderApiDto.SubscriptionList(
            subscriptions = listOf(
                GoogleReaderApiDto.SubscriptionItem(
                    id = "feed/1", title = "Tech News", url = "https://a.test/rss",
                    categories = listOf(GoogleReaderApiDto.CategoryItem(id = "user/-/label/Tech", label = "Tech")),
                ),
                GoogleReaderApiDto.SubscriptionItem(id = "feed/2", title = "No URL Feed", url = null),
            )
        )

        coEvery { api.getStreamContents(continuation = null) } returns GoogleReaderApiDto.ReadingList(
            items = listOf(
                GoogleReaderApiDto.Item(
                    id = "item/1",
                    title = "Breaking News",
                    published = 1_700_000_000L,
                    summary = GoogleReaderApiDto.Summary(content = "<p>Body text</p>"),
                    categories = listOf("user/-/label/Tech"), // unread, not starred
                    origin = listOf(GoogleReaderApiDto.OriginItem(streamId = "feed/1")),
                ),
                GoogleReaderApiDto.Item(
                    id = "item/2",
                    title = "Starred article",
                    published = 1_700_000_100L,
                    summary = GoogleReaderApiDto.Summary(content = "<p>Other body</p>"),
                    categories = listOf("user/-/state/com.google/read", "user/-/state/com.google/starred"), // read + starred
                    origin = listOf(GoogleReaderApiDto.OriginItem(streamId = "feed/1")),
                ),
                GoogleReaderApiDto.Item(
                    id = null, // missing id -> skipped
                    origin = listOf(GoogleReaderApiDto.OriginItem(streamId = "feed/1")),
                ),
                GoogleReaderApiDto.Item(
                    id = "item/3",
                    origin = listOf(GoogleReaderApiDto.OriginItem(streamId = "feed/unknown")), // unmapped feed -> skipped
                ),
            ),
            continuation = null,
        )

        val groups = slot<List<Group>>()
        val feeds = slot<List<Feed>>()
        val articlesInserted = mutableListOf<Article>()
        coEvery { groupDao.insertOrUpdate(capture(groups)) } returns Unit
        coEvery { feedDao.insertOrUpdate(capture(feeds)) } returns Unit
        coEvery { articleDao.insertOnConflictIgnore(*anyVararg()) } answers {
            @Suppress("UNCHECKED_CAST")
            articlesInserted.addAll(args.first() as Array<Article>)
        }

        val result = runBlocking { googleReaderRssSv.sync(worker) }

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf("Tech"), groups.captured.map { it.name })
        assertEquals(1, feeds.captured.size) // feed/2 skipped (no url)
        assertEquals("Tech News", feeds.captured.single().name)
        assertEquals(2, articlesInserted.size) // item/1 + item/2; item without id and unmapped feed skipped
        assertEquals(true, articlesInserted[0].isUnread)
        assertEquals(false, articlesInserted[0].isStarred)
        assertEquals(false, articlesInserted[1].isUnread)
        assertEquals(true, articlesInserted[1].isStarred)
    }

    @Test
    fun markAsRead_callsEditTagWithReadTag() {
        coEvery { accountDao.queryById(any()) } answers {
            account(GoogleReaderSecurityKey(serverUrl = "https://rss.test", username = "u", password = "p"))
                .copy(id = firstArg())
        }

        runBlocking { googleReaderRssSv.markAsRead(null, null, "1\$item_5", null, isUnread = false) }

        coVerify(exactly = 1) { api.editTag(itemId = "item_5", tag = GoogleReaderApi.TAG_READ, add = true) }
    }

    @Test
    fun markAsStarred_callsEditTagWithStarredTag() {
        coEvery { accountDao.queryById(any()) } answers {
            account(GoogleReaderSecurityKey(serverUrl = "https://rss.test", username = "u", password = "p"))
                .copy(id = firstArg())
        }

        runBlocking { googleReaderRssSv.markAsStarred("1\$item_9", isStarred = true) }

        coVerify(exactly = 1) { api.editTag(itemId = "item_9", tag = GoogleReaderApi.TAG_STARRED, add = true) }
    }
}
