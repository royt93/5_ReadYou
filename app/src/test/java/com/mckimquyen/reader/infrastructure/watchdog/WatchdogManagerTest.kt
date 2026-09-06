package com.mckimquyen.reader.infrastructure.watchdog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.watchdog.WatchdogEngine
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import io.mockk.mockk
import io.mockk.verify
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WatchdogManagerTest {

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
        manager = WatchdogManager(context, engine, notificationHelper)
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
        val newManager = WatchdogManager(context, engine, notificationHelper)
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
}
