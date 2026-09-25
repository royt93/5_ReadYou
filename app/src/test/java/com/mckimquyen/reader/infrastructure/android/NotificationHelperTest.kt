package com.mckimquyen.reader.infrastructure.android

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.ui.ext.needsNotificationPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationHelperTest {

    private lateinit var context: Context
    private lateinit var systemManager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        systemManager = context.getSystemService(NotificationManager::class.java)
        helper = NotificationHelper(context)
    }

    @Test
    fun notificationIds_areDeterministicForSameArticle() {
        assertEquals(
            NotificationHelper.articleNotificationId("1\$abc"),
            NotificationHelper.articleNotificationId("1\$abc"),
        )
        assertEquals(
            NotificationHelper.watchdogNotificationId("1\$abc"),
            NotificationHelper.watchdogNotificationId("1\$abc"),
        )
    }

    @Test
    fun notificationIds_areUniqueAcrossSampleArticlesAndKinds() {
        val articleIds = (1..500).map { "1\$article_$it" }
        val ids = articleIds.map(NotificationHelper::articleNotificationId) +
            articleIds.map(NotificationHelper::watchdogNotificationId) +
            NotificationHelper.feedSummaryNotificationId("1\$feed")

        assertEquals(ids.size, ids.toSet().size)
        // Summary for a feed must not collide with an article that happens to share its id string.
        assertNotEquals(
            NotificationHelper.articleNotificationId("1\$feed"),
            NotificationHelper.feedSummaryNotificationId("1\$feed"),
        )
    }

    @Test
    fun needsNotificationPermission_onlyOnAndroid13PlusWhenNotGranted() {
        assertFalse(needsNotificationPermission(Build.VERSION_CODES.S_V2, isGranted = false))
        assertFalse(needsNotificationPermission(Build.VERSION_CODES.TIRAMISU, isGranted = true))
        assertTrue(needsNotificationPermission(Build.VERSION_CODES.TIRAMISU, isGranted = false))
        assertTrue(needsNotificationPermission(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, isGranted = false))
    }

    @Test
    fun notify_whenNotificationsDisabled_postsNothing() {
        shadowOf(systemManager).setNotificationsEnabled(false)

        helper.notify(feedWithArticles("a1", "a2"))

        assertFalse(helper.canPostNotifications())
        assertTrue(shadowOf(systemManager).allNotifications.isEmpty())
    }

    @Test
    fun notify_sameArticleTwice_replacesInsteadOfDuplicating() {
        shadowOf(systemManager).setNotificationsEnabled(true)

        helper.notify(feedWithArticles("a1"))
        helper.notify(feedWithArticles("a1"))

        val posted = shadowOf(systemManager).allNotifications
        assertEquals(1, posted.size)
        assertTrue(shadowOf(systemManager).getNotification(NotificationHelper.articleNotificationId("1\$a1")) != null)
    }

    @Test
    fun notify_multipleArticles_postsOneEachPlusGroupSummary() {
        shadowOf(systemManager).setNotificationsEnabled(true)

        helper.notify(feedWithArticles("a1", "a2"))

        assertEquals(3, shadowOf(systemManager).allNotifications.size)
        assertTrue(
            shadowOf(systemManager).getNotification(NotificationHelper.feedSummaryNotificationId(FEED_ID)) != null
        )
    }

    @Test
    fun notifyWatchdogAlert_doesNotReplaceRegularArticleNotification() {
        shadowOf(systemManager).setNotificationsEnabled(true)
        val feedWithArticle = feedWithArticles("a1")

        helper.notify(feedWithArticle)
        helper.notifyWatchdogAlert(feedWithArticle.articles.single(), keyword = "kw", feedName = "Feed")

        assertEquals(2, shadowOf(systemManager).allNotifications.size)
    }

    private fun feedWithArticles(vararg ids: String) = FeedWithArticle(
        feed = Feed(id = FEED_ID, name = "Feed", url = "https://feed.test/rss", groupId = "1\$g", accountId = 1),
        articles = ids.map { id ->
            Article(
                id = "1\$$id",
                date = Date(),
                title = "Title $id",
                rawDescription = "",
                shortDescription = "Short $id",
                link = "https://feed.test/$id",
                feedId = FEED_ID,
                accountId = 1,
            )
        },
    )

    private companion object {
        const val FEED_ID = "1\$feed"
    }
}
