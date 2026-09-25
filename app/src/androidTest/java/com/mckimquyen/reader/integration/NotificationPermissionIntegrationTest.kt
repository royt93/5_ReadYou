package com.mckimquyen.reader.integration

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.ext.isNotificationPermissionGranted
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * End-to-end on a real device: NotificationHelper -> system NotificationManager, with whatever
 * POST_NOTIFICATIONS state the device currently has. Both outcomes are asserted: posted when the
 * permission is granted, silently skipped (no crash, nothing active) when it is not.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionIntegrationTest {

    private lateinit var context: Context
    private lateinit var systemManager: NotificationManager
    private lateinit var helper: NotificationHelper
    private val article = Article(
        id = "1\$fix07_it_article",
        date = Date(),
        title = "FIX-07 integration",
        rawDescription = "",
        shortDescription = "body",
        link = "https://feed.test/fix07",
        feedId = FEED_ID,
        accountId = 1,
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        systemManager = context.getSystemService(NotificationManager::class.java)
        helper = NotificationHelper(context)
        systemManager.cancelAll()
    }

    @After
    fun tearDown() {
        systemManager.cancelAll()
    }

    @Test
    fun notify_respectsPermission_andUsesStableArticleId() {
        val feedWithArticle = FeedWithArticle(
            feed = Feed(id = FEED_ID, name = "Feed", url = "https://feed.test/rss", groupId = "1\$g", accountId = 1),
            articles = listOf(article),
        )

        helper.notify(feedWithArticle)
        helper.notify(feedWithArticle)

        val activeIds = systemManager.activeNotifications.map { it.id }
        if (context.isNotificationPermissionGranted() && helper.canPostNotifications()) {
            // Posted twice with the same stable id -> exactly one notification.
            assertEquals(listOf(NotificationHelper.articleNotificationId(article.id)), activeIds)
        } else {
            assertTrue("Nothing may be posted without permission", activeIds.isEmpty())
        }
    }

    private companion object {
        const val FEED_ID = "1\$fix07_feed"
    }
}
