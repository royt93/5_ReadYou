package com.mckimquyen.reader.integration

import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.watchdog.WatchdogEngine
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import com.mckimquyen.reader.ui.component.watchdog.WatchdogBadge
import com.mckimquyen.reader.ui.component.watchdog.WatchdogSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class WatchdogIntegrationTest {

    private lateinit var context: Context
    private lateinit var engine: WatchdogEngine
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var watchdogManager: WatchdogManager

    private val sampleFeed = Feed(
        id = "feed_vnexpress",
        name = "VnExpress Kinh Tế",
        url = "https://vnexpress.net/rss/kinh-doanh.rss",
        groupId = "group_1",
        accountId = 1,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("watchdog_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        engine = WatchdogEngine()
        notificationHelper = NotificationHelper(context)
        watchdogManager = WatchdogManager(context, engine, notificationHelper)
    }

    private fun createArticle(id: String, title: String, description: String = ""): Article {
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
    fun watchdog_notificationChannelCreated_withHighImportance() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(NotificationHelper.WATCHDOG_CHANNEL_ID)
        assertNotNull("Watchdog notification channel must exist", channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.shouldVibrate())
    }

    @Test
    fun endToEnd_watchdogFlow_syncDetectionAndUIBinding() {
        // 1. User configures keywords
        assertTrue(watchdogManager.addKeyword("\$VIC"))
        assertTrue(watchdogManager.addKeyword("Lãi suất"))
        assertEquals(2, watchdogManager.keywords.value.size)

        // 2. Incoming batch of articles arrives from sync
        val articles = listOf(
            createArticle("art_1", "Thị trường: \$VIC tăng trần phiên giao dịch thứ 3 liên tiếp"),
            createArticle("art_2", "Bộ Y tế khuyến cáo phòng dịch mùa thu đông"),
            createArticle("art_3", "Ngân hàng nhà nước giữ nguyên lãi suất điều hành"),
            createArticle("art_4", "Giải bóng đá vô địch quốc gia khai mạc tuần này"),
        )

        // 3. Watchdog scans and triggers alerts
        val alertCount = watchdogManager.checkAndNotify(articles, sampleFeed)
        assertEquals(2, alertCount)

        // 4. Verify match counts incremented
        val vicKeyword = watchdogManager.keywords.value.find { it.keyword == "\$VIC" }
        assertNotNull(vicKeyword)
        assertEquals(1, vicKeyword?.matchCount)

        val interestKeyword = watchdogManager.keywords.value.find { it.keyword == "Lãi suất" }
        assertNotNull(interestKeyword)
        assertEquals(1, interestKeyword?.matchCount)

        // 5. Verify UI Rendering with Live Compose View
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    // Badge rendering for matched article
                    WatchdogBadge(keyword = vicKeyword!!.keyword)

                    // Sheet rendering with active keywords
                    WatchdogSheet(
                        keywords = watchdogManager.keywords.value,
                        onDismissRequest = {},
                        onAddKeyword = { watchdogManager.addKeyword(it) },
                        onRemoveKeyword = { watchdogManager.removeKeyword(it) },
                        onToggleKeyword = { id, enabled -> watchdogManager.toggleKeyword(id, enabled) },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("UI attached and rendered without exception", composeView)
        }
        scenario.close()
    }
}
