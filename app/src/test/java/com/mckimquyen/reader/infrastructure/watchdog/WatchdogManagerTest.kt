package com.mckimquyen.reader.infrastructure.watchdog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.watchdog.WatchdogEngine
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WatchdogManagerTest {

    // Unconfined: the async initial load runs inline, so tests see persisted state right away.
    private val eagerScope = CoroutineScope(Dispatchers.Unconfined)

    private lateinit var context: Context
    private lateinit var engine: WatchdogEngine
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var manager: WatchdogManager

    private val sampleFeed = Feed(
        id = "feed_1",
        name = "VnExpress Kinh Doanh",
        url = "https://vnexpress.net/rss/kinh-doanh.rss",
        groupId = "group_1",
        accountId = 1,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear preferences before each test
        context.getSharedPreferences("watchdog_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        engine = WatchdogEngine()
        notificationHelper = mockk(relaxed = true)
        manager = WatchdogManager(context, engine, notificationHelper, eagerScope, Dispatchers.Unconfined)
    }

    private fun createArticle(
        id: String,
        title: String,
        description: String = "",
    ): Article {
        return Article(
            id = id,
            title = title,
            rawDescription = description,
            shortDescription = description,
            link = "https://vnexpress.net/$id",
            feedId = sampleFeed.id,
            accountId = 1,
            date = Date(),
        )
    }

    @Test
    fun addKeyword_success() {
        val result = manager.addKeyword("\$VIC")
        assertTrue(result)
        assertEquals(1, manager.keywords.value.size)
        assertEquals("\$VIC", manager.keywords.value.first().keyword)
        assertTrue(manager.keywords.value.first().isEnabled)
    }

    @Test
    fun addKeyword_duplicateCaseInsensitive_returnsFalse() {
        manager.addKeyword("Bitcoin")
        val duplicate = manager.addKeyword("bitcoin")
        assertFalse(duplicate)
        assertEquals(1, manager.keywords.value.size)
    }

    @Test
    fun addKeyword_blank_returnsFalse() {
        assertFalse(manager.addKeyword("   "))
        assertTrue(manager.keywords.value.isEmpty())
    }

    @Test
    fun removeKeyword_success() {
        manager.addKeyword("Lãi suất")
        val keywordId = manager.keywords.value.first().id

        manager.removeKeyword(keywordId)
        assertTrue(manager.keywords.value.isEmpty())
    }

    @Test
    fun toggleKeyword_success() {
        manager.addKeyword("Giá vàng")
        val keywordId = manager.keywords.value.first().id
        assertTrue(manager.keywords.value.first().isEnabled)

        manager.toggleKeyword(keywordId, false)
        assertFalse(manager.keywords.value.first().isEnabled)

        manager.toggleKeyword(keywordId, true)
        assertTrue(manager.keywords.value.first().isEnabled)
    }

    @Test
    fun incrementMatchCount_success() {
        manager.addKeyword("\$FPT")
        val keywordId = manager.keywords.value.first().id
        assertEquals(0, manager.keywords.value.first().matchCount)

        manager.incrementMatchCount(keywordId)
        assertEquals(1, manager.keywords.value.first().matchCount)

        manager.incrementMatchCount(keywordId)
        assertEquals(2, manager.keywords.value.first().matchCount)
    }

    @Test
    fun persistence_reloadsCorrectlyAcrossInstances() {
        manager.addKeyword("\$VNINDEX")
        manager.addKeyword("Bão lũ")
        val id1 = manager.keywords.value[0].id
        manager.incrementMatchCount(id1)

        // Instantiate new manager instance reading from same SharedPreferences
        val newManager = WatchdogManager(context, engine, notificationHelper, eagerScope, Dispatchers.Unconfined)
        assertEquals(2, newManager.keywords.value.size)
        assertEquals("\$VNINDEX", newManager.keywords.value[0].keyword)
        assertEquals(1, newManager.keywords.value[0].matchCount)
        assertEquals("Bão lũ", newManager.keywords.value[1].keyword)
    }

    @Test
    fun checkAndNotify_matchesAndNotifiesOnlyMatchingArticles() {
        manager.addKeyword("\$VIC")
        manager.addKeyword("Bitcoin")

        val articles = listOf(
            createArticle("art_1", "Thị trường hôm nay: cổ phiếu \$VIC tăng vọt"),
            createArticle("art_2", "Công nghệ bán dẫn đang thu hút dòng vốn"),
            createArticle("art_3", "Dự báo giá Bitcoin sắp tới"),
        )

        val alertCount = manager.checkAndNotify(articles, sampleFeed)
        assertEquals(2, alertCount)

        verify(exactly = 1) {
            notificationHelper.notifyWatchdogAlert(
                article = match { it.id == "art_1" },
                keyword = "\$VIC",
                feedName = sampleFeed.name,
            )
        }
        verify(exactly = 1) {
            notificationHelper.notifyWatchdogAlert(
                article = match { it.id == "art_3" },
                keyword = "Bitcoin",
                feedName = sampleFeed.name,
            )
        }
    }

    @Test
    fun checkAndNotify_ignoresDisabledKeyword() {
        manager.addKeyword("\$VIC")
        val kwId = manager.keywords.value.first().id
        manager.toggleKeyword(kwId, false)

        val articles = listOf(
            createArticle("art_1", "Cổ phiếu \$VIC tăng mạnh"),
        )

        val alertCount = manager.checkAndNotify(articles, sampleFeed)
        assertEquals(0, alertCount)

        verify(exactly = 0) {
            notificationHelper.notifyWatchdogAlert(any(), any(), any())
        }
    }

    @Test
    fun constructor_loadsPersistedKeywordsAsynchronously() {
        manager.addKeyword("Bitcoin")
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)

        val lazyManager = WatchdogManager(context, engine, notificationHelper, scope, dispatcher)

        // Nothing loaded in the constructor: the flow still holds its default.
        assertTrue(lazyManager.keywords.value.isEmpty())

        scope.testScheduler.advanceUntilIdle()

        assertEquals(listOf("Bitcoin"), lazyManager.keywords.value.map { it.keyword })
    }

    @Test
    fun addKeyword_beforeAsyncLoad_keepsPersistedKeywords() {
        manager.addKeyword("Bitcoin")
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val lazyManager = WatchdogManager(context, engine, notificationHelper, scope, dispatcher)

        assertTrue(lazyManager.addKeyword("Vàng"))
        scope.testScheduler.advanceUntilIdle()

        assertEquals(listOf("Bitcoin", "Vàng"), lazyManager.keywords.value.map { it.keyword })
        val reloaded = WatchdogManager(context, engine, notificationHelper, eagerScope, Dispatchers.Unconfined)
        assertEquals(listOf("Bitcoin", "Vàng"), reloaded.keywords.value.map { it.keyword })
    }

    @Test
    fun checkArticle_beforeAsyncLoad_matchesPersistedKeywords() {
        manager.addKeyword("Bitcoin")
        val dispatcher = StandardTestDispatcher()
        val lazyManager = WatchdogManager(context, engine, notificationHelper, TestScope(dispatcher), dispatcher)

        val matched = lazyManager.checkArticle(createArticle("art_btc", "Giá Bitcoin tăng mạnh"))

        assertNotNull(matched)
        assertEquals("Bitcoin", matched?.keyword)
    }

    // ---- REEL-04: atomic persistence, no lost updates under concurrency ----

    @Test
    fun concurrentIncrementMatchCount_fromMultipleThreads_doesNotLoseUpdates() {
        manager.addKeyword("\$VIC")
        val keywordId = manager.keywords.value.first().id

        val threadCount = 20
        val incrementsPerThread = 10
        val threads = (1..threadCount).map {
            Thread {
                repeat(incrementsPerThread) {
                    manager.incrementMatchCount(keywordId)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Every increment must be reflected: no lost update from interleaved read-modify-write.
        assertEquals(threadCount * incrementsPerThread, manager.keywords.value.first().matchCount)

        // Reloading from disk must agree with the in-memory result (write path is also atomic).
        val reloaded = WatchdogManager(context, engine, notificationHelper, eagerScope, Dispatchers.Unconfined)
        assertEquals(threadCount * incrementsPerThread, reloaded.keywords.value.first().matchCount)
    }

    @Test
    fun concurrentAddAndIncrement_simulatingUiEditVsBackgroundSync_doesNotLoseEither() {
        manager.addKeyword("Bitcoin")
        val bitcoinId = manager.keywords.value.first().id

        // Simulates SyncWorker on a background thread incrementing matchCount for existing
        // keywords while the UI thread concurrently adds a brand-new keyword.
        val syncThread = Thread {
            repeat(50) { manager.incrementMatchCount(bitcoinId) }
        }
        val uiThread = Thread {
            repeat(5) { i -> manager.addKeyword("keyword_$i") }
        }
        syncThread.start()
        uiThread.start()
        syncThread.join()
        uiThread.join()

        assertEquals(6, manager.keywords.value.size) // Bitcoin + 5 new keywords, none lost
        assertEquals(50, manager.keywords.value.first { it.id == bitcoinId }.matchCount)
    }

    // ---- REEL-04: corrupt JSON must not silently wipe existing data ----

    @Test
    fun load_whenPrimaryJsonCorrupt_recoversFromBackup() {
        manager.addKeyword("\$VIC")
        manager.addKeyword("Bitcoin")
        val prefs = context.getSharedPreferences("watchdog_prefs", Context.MODE_PRIVATE)
        // Second save() call promotes the first save's JSON to the backup key.
        val backupJson = prefs.getString("watchdog_keywords_json_backup", null)
        assertNotNull("Backup must exist after 2 saves", backupJson)

        // Simulate primary corruption (e.g. process killed mid-write).
        prefs.edit().putString("watchdog_keywords_json", "{not valid json!!").commit()

        val recovered = WatchdogManager(context, engine, notificationHelper, eagerScope, Dispatchers.Unconfined)

        assertFalse("Must recover keywords from backup, not wipe to empty", recovered.keywords.value.isEmpty())
        assertEquals(listOf("\$VIC"), recovered.keywords.value.map { it.keyword })
    }

    @Test
    fun load_whenBothPrimaryAndBackupCorrupt_returnsEmptyWithoutCrashing() {
        val prefs = context.getSharedPreferences("watchdog_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("watchdog_keywords_json", "not json")
            .putString("watchdog_keywords_json_backup", "also not json")
            .commit()

        val recovered = WatchdogManager(context, engine, notificationHelper, eagerScope, Dispatchers.Unconfined)

        assertTrue(recovered.keywords.value.isEmpty())
    }

    @Test
    fun load_whenNoDataEverSaved_returnsEmptyWithoutError() {
        val fresh = WatchdogManager(context, engine, notificationHelper, eagerScope, Dispatchers.Unconfined)
        assertTrue(fresh.keywords.value.isEmpty())
    }
}
