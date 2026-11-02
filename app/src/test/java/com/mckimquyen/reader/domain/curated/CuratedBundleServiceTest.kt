package com.mckimquyen.reader.domain.curated

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.addedsource.AddedRssSource
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.group.GroupWithFeed
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CuratedBundleServiceTest {

    private val accountService = mockk<AccountSv>()
    private val rssService = mockk<RssSv>()
    private val repository = mockk<LocalRssSv>(relaxed = true)
    private val rssHelper = mockk<RssHelper>()
    private val addedDao = mockk<AddedRssSourceDao>(relaxed = true)

    private fun service() = CuratedBundleService(
        context = ApplicationProvider.getApplicationContext(),
        accountService = accountService,
        rssService = rssService,
        rssHelper = rssHelper,
        addedRssSourceDao = addedDao,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun account(type: AccountType, id: Int = 1) = Account(id = id, name = "acc", type = type)

    @Test
    fun load_readsTheShippedAssetFromTheAndroidAssetManager() = runTest {
        val bundles = service().load()

        assertEquals(listOf("tech_ai", "finance", "vn_news", "design", "dev"), bundles.map { it.id })
    }

    @Test
    fun canSubscribe_isTrueOnlyForTheLocalAccount() = runTest {
        coEvery { accountService.getCurrentAccount() } returns account(AccountType.Local)
        assertTrue(service().canSubscribe())

        coEvery { accountService.getCurrentAccount() } returns account(AccountType.Fever)
        assertFalse(service().canSubscribe())

        coEvery { accountService.getCurrentAccount() } returns account(AccountType.GoogleReader)
        assertFalse(service().canSubscribe())
    }

    @Test
    fun existingUrls_unionsTheTrackedSourcesWithEveryFeedInTheAccount() = runTest {
        coEvery { accountService.getCurrentAccount() } returns account(AccountType.Local, id = 7)
        every { addedDao.getAddedSourcesByAccount(7) } returns flowOf(listOf(AddedRssSource("https://tracked.example/rss", "T", accountId = 7)))
        every { rssService.get(AccountType.Local.id) } returns repository
        val feed = Feed(id = "f", name = "F", url = "https://subscribed.example/rss", groupId = "g", accountId = 7)
        every { repository.pullFeeds() } returns flowOf(mutableListOf(GroupWithFeed(Group("g", "G", 7), mutableListOf(feed))))

        val urls = service().existingUrls()

        assertEquals(setOf("https://tracked.example/rss", "https://subscribed.example/rss"), urls)
    }

    @Test
    fun subscribe_refusesANonLocalAccountWithoutTouchingTheNetwork() = runTest {
        coEvery { accountService.getCurrentAccount() } returns account(AccountType.Fever)
        val bundle = CuratedBundle("b", "B", "B", "2026-11-02", listOf(CuratedSource("A", "https://a.example/rss")))

        val result = service().subscribe(bundle, "Group") { _, _ -> }

        assertEquals(BundleSubscribeResult(added = 0, skippedExisting = 0, failed = 0), result)
        io.mockk.coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }
}
