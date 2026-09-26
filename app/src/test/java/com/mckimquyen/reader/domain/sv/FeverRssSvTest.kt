package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.account.sec.FeverSecurityKey
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.provider.fever.FeverAPI
import com.mckimquyen.reader.infrastructure.rss.provider.fever.FeverDTO
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
class FeverRssSvTest {

    // Read the real value: the DataStore singleton may keep an account id written by another test.
    private val accountId by lazy { ApplicationProvider.getApplicationContext<Context>().currentAccountId }

    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val feedDao = mockk<FeedDao>(relaxed = true)
    private val groupDao = mockk<GroupDao>(relaxed = true)
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val feverApi = mockk<FeverAPI>(relaxed = true)
    private val worker = mockk<CoroutineWorker>(relaxed = true)
    private lateinit var feverRssSv: FeverRssSv

    @Before
    fun setUp() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkObject(FeverAPI.Companion)
        every { FeverAPI.getInstance(any(), any(), any(), any(), any()) } returns feverApi
        feverRssSv = FeverRssSv(
            context = context,
            articleDao = articleDao,
            feedDao = feedDao,
            rssHelper = mockk<RssHelper>(relaxed = true),
            notificationHelper = mockk<NotificationHelper>(relaxed = true),
            accountDao = accountDao,
            groupDao = groupDao,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined,
            workManager = mockk<WorkManager>(relaxed = true),
            watchdogManager = mockk<WatchdogManager>(relaxed = true),
            feedHealthDao = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        unmockkObject(FeverAPI.Companion)
    }

    private fun feverAccount(securityKey: FeverSecurityKey) = Account(
        id = accountId,
        name = "Fever",
        type = AccountType.Fever,
        securityKey = securityKey.toString(),
    )

    @Test
    fun validCredentials_whenAccountMissing_throwsIllegalStateInsteadOfNpe() {
        coEvery { accountDao.queryById(any()) } returns null

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { feverRssSv.validCredentials() }
        }

        assertEquals("Account not found", error.message)
    }

    @Test
    fun validCredentials_whenServerUrlMissing_throwsIllegalStateInsteadOfNpe() {
        coEvery { accountDao.queryById(any()) } answers {
            feverAccount(FeverSecurityKey(serverUrl = null, username = "user", password = "pass")).copy(id = firstArg())
        }

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { feverRssSv.validCredentials() }
        }

        assertEquals("Fever server URL not configured", error.message)
    }

    @Test
    fun sync_whenAccountMissing_returnsFailureInsteadOfCrashing() {
        coEvery { accountDao.queryById(any()) } returns null

        val result = runBlocking { feverRssSv.sync(worker) }

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { groupDao.insertOrUpdate(any()) }
    }

    @Test
    fun sync_skipsIncompleteRemoteEntities_andKeepsValidOnes() {
        coEvery { accountDao.queryById(any()) } answers {
            feverAccount(FeverSecurityKey(serverUrl = "https://fever.test", username = "user", password = "pass")).copy(id = firstArg())
        }
        coEvery { feverApi.getGroups() } returns FeverDTO.Groups(
            api_version = null, auth = 1, last_refreshed_on_time = null,
            groups = listOf(
                FeverDTO.GroupItem(id = null, title = "no id"),
                FeverDTO.GroupItem(id = 1, title = "Tech"),
            ),
            feeds_groups = null,
        )
        coEvery { feverApi.getFeeds() } returns FeverDTO.Feeds(
            api_version = null, auth = 1, last_refreshed_on_time = null,
            feeds = listOf(
                feedItem(id = null, url = "https://a.test/rss"),
                feedItem(id = 10, url = null),
                feedItem(id = 11, url = "https://orphan.test/rss"), // no group mapping
                feedItem(id = 12, url = "https://ok.test/rss"),
            ),
            feeds_groups = listOf(FeverDTO.FeedsGroupsItem(group_id = 1, feed_ids = "10,12")),
        )
        coEvery { feverApi.getItemsSince(any()) } returns FeverDTO.Items(
            api_version = null, auth = 1, last_refreshed_on_time = null, total_items = null,
            items = listOf(
                item(id = null, feedId = 12),
                item(id = "100", feedId = null),
                item(id = "101", feedId = 12),
            ),
        )
        coEvery { feverApi.getUnreadItems() } returns
            FeverDTO.ItemsByUnread(null, 1, null, unread_item_ids = "101")
        coEvery { feverApi.getSavedItems() } returns
            FeverDTO.ItemsByStarred(null, 1, null, saved_item_ids = "")
        every { articleDao.queryArticleMetadataAll(any()) } returns emptyList()
        val groups = slot<List<Group>>()
        val feeds = slot<List<Feed>>()
        val articles = mutableListOf<Article>()
        coEvery { groupDao.insertOrUpdate(capture(groups)) } returns Unit
        coEvery { feedDao.insertOrUpdate(capture(feeds)) } returns Unit
        coEvery { articleDao.insert(*anyVararg()) } answers {
            @Suppress("UNCHECKED_CAST")
            articles.addAll(args.first() as Array<Article>)
        }
        val updatedAccount = slot<Account>()
        coEvery { accountDao.update(capture(updatedAccount)) } returns Unit

        val result = runBlocking { feverRssSv.sync(worker) }

        assertTrue(result is ListenableWorker.Result.Success)
        val resolvedAccountId = updatedAccount.captured.id ?: accountId
        assertEquals(listOf("$resolvedAccountId\$1"), groups.captured.map { it.id })
        assertEquals(listOf("$resolvedAccountId\$12"), feeds.captured.map { it.id })
        assertEquals(listOf("$resolvedAccountId\$101"), articles.map { it.id })
        assertEquals("$resolvedAccountId\$12", articles.single().feedId)
        assertEquals("$resolvedAccountId\$101", updatedAccount.captured.lastArticleId)
    }

    private fun feedItem(id: Int?, url: String?) = FeverDTO.FeedItem(
        id = id, favicon_id = null, title = "feed $id", url = url,
        site_url = null, is_spark = null, last_refreshed_on_time = null,
    )

    private fun item(id: String?, feedId: Int?) = FeverDTO.Item(
        id = id, feed_id = feedId, title = "title $id", author = null, html = "<p>body</p>",
        url = "https://ok.test/$id", is_saved = 0, is_read = 0, created_on_time = null,
    )
}
