package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFetchResult
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ACCOUNT = 7
private const val OLD_ADDRESS = "bsky:alice.bsky.social"
private val DID_ADDRESS = "bsky:${BlueskyTestPayloads.DID}"

/** The real Room and the real LocalRssSv: an old source is upgraded by a real sync, once, touching nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRssSvBlueskyUpgradeRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
    private val api = mockk<BlueskyApiClient>()
    private var originalAccountId = 1
    private lateinit var service: LocalRssSv // built in @Before, after the account is set

    @Before
    fun setUp() = runBlocking {
        originalAccountId = context.currentAccountId
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, ACCOUNT)
        db.groupDao().insert(Group("g1", "group", ACCOUNT))
        db.feedDao().insert(
            Feed(
                id = "f1", name = "My own name", icon = "icon", url = OLD_ADDRESS, groupId = "g1", accountId = ACCOUNT,
                isNotification = true, isFullContent = false, sourceType = Feed.BLUESKY_SOURCE_TYPE, sourceUrl = OLD_ADDRESS,
            ),
        )
        service = LocalRssSv(
            context = context, articleDao = db.articleDao(), feedDao = db.feedDao(), rssHelper = mockk(relaxed = true),
            notificationHelper = mockk(relaxed = true), accountDao = mockk(relaxed = true), groupDao = db.groupDao(),
            feedHealthDao = mockk(relaxed = true), ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
            workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true), smartFilterManager = mockk(relaxed = true),
            webFeedScraper = mockk(), smartCollectionMatcher = mockk(relaxed = true), widgetRefreshNotifier = mockk(relaxed = true),
            retractionRadar = mockk(relaxed = true), nostrRelayClient = mockk(relaxed = true), blueskyApiClient = api,
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, originalAccountId)
    }

    private fun answer(vararg items: String) = BlueskyOutcome.Found(
        BlueskyFetchResult(BlueskyTestPayloads.array(*items), BlueskyTestPayloads.DID, "alice.bsky.social", "Someone New", "https://cdn.bsky.app/x"),
    )

    private suspend fun stored(): Feed = requireNotNull(db.feedDao().queryById("f1"))

    @Test fun aSyncThatWorks_writesTheDid_andNothingElse() = runBlocking {
        coEvery { api.fetch("alice.bsky.social") } returns answer(BlueskyTestPayloads.post("3k1", "one"))
        assertNotNull(service.syncSingleFeed(stored()))
        val after = stored()
        assertEquals(DID_ADDRESS, after.sourceUrl)
        assertEquals(OLD_ADDRESS, after.url)
        assertEquals("My own name", after.name)
        assertEquals("icon", after.icon)
        assertTrue(after.isNotification)
        assertEquals("g1", after.groupId)
    }

    @Test fun afterTheUpgrade_aRenamedHandleIsStillRecognisedByTheStoredDid() = runBlocking {
        coEvery { api.fetch("alice.bsky.social") } returns answer(BlueskyTestPayloads.post("3k1", "one"))
        service.syncSingleFeed(stored())
        assertTrue(service.isFeedSourceExist(DID_ADDRESS))
        assertFalse("the shown address is still the old handle", service.isFeedSourceExist(OLD_ADDRESS))
    }

    @Test fun aSecondSync_writesNothingMore() = runBlocking {
        coEvery { api.fetch("alice.bsky.social") } returns answer(BlueskyTestPayloads.post("3k1", "one"))
        service.syncSingleFeed(stored())
        coEvery { api.fetch(BlueskyTestPayloads.DID) } returns answer(BlueskyTestPayloads.post("3k2", "two"))
        service.syncSingleFeed(stored())
        assertEquals(DID_ADDRESS, stored().sourceUrl)
    }

    @Test fun aSyncThatFails_leavesTheSourceAlone() = runBlocking {
        coEvery { api.fetch(any()) } returns BlueskyOutcome.Failed
        service.syncSingleFeed(stored())
        assertEquals(OLD_ADDRESS, stored().sourceUrl)
    }

    @Test fun anAddressEditedMeanwhile_isNotOverwritten() = runBlocking {
        val read = stored()
        db.feedDao().updateSourceUrlIfUnchanged("f1", OLD_ADDRESS, "bsky:bob.bsky.social")
        coEvery { api.fetch("alice.bsky.social") } returns answer(BlueskyTestPayloads.post("3k1", "one"))
        service.syncSingleFeed(read)
        assertEquals("the sync had read the old value, the user changed it first", "bsky:bob.bsky.social", stored().sourceUrl)
    }
}
