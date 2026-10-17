package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.room.withTransaction
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

class WebFeedPreviewPersistenceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private var database: AndroidDatabase? = null
    private val feed = Feed(id = "preview-feed", name = "Fixture news", url = SOURCE_URL, groupId = "preview-group",
        accountId = ACCOUNT_ID, sourceType = Feed.WEB_SOURCE_TYPE, sourceUrl = SOURCE_URL)
    private val articles = (1..ARTICLE_COUNT).map { index ->
        Article(id = "preview-$index", date = Date(), title = "Report $index", rawDescription = "",
            shortDescription = "Report $index", link = "https://example.test/story/$index", feedId = feed.id, accountId = ACCOUNT_ID)
    }

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        database = Room.inMemoryDatabaseBuilder(compose.activity, AndroidDatabase::class.java).build()
        requireNotNull(database).groupDao().insert(Group(id = feed.groupId, name = "News", accountId = ACCOUNT_ID))
    }

    @After
    fun tearDown() { database?.close() }

    @Test
    fun cancelPreviewLeavesDatabaseUnchanged() {
        var cancellations = 0
        compose.setContent {
            WebFeedPreviewContent(feed.name, articles, onConfirm = { persist() }, onCancel = { cancellations++ })
        }
        articles.forEach { compose.onNodeWithText(it.title).assertIsDisplayed() }
        assertEquals(0, feedCount())
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
        assertEquals(0, feedCount())
        assertEquals(0, articleCount())
    }

    @Test
    fun confirmPreviewPersistsWebMetadataAndAllArticles() {
        compose.setContent {
            WebFeedPreviewContent(feed.name, articles, onConfirm = { persist() }, onCancel = {})
        }
        assertEquals(0, feedCount())
        compose.onNodeWithText(compose.activity.getString(R.string.web_feed_add)).performClick()
        compose.waitForIdle()
        assertEquals(1, feedCount())
        assertEquals(ARTICLE_COUNT, articleCount())
        val stored = runBlocking(Dispatchers.IO) { requireNotNull(database).feedDao().queryById(feed.id) }
        assertEquals(Feed.WEB_SOURCE_TYPE, stored?.sourceType)
        assertEquals(SOURCE_URL, stored?.sourceUrl)
    }

    // Test boundary: real preview composable + Room; ViewModel/navigation tested separately.
    private fun persist() = runBlocking(Dispatchers.IO) {
        val db = requireNotNull(database)
        db.withTransaction {
            db.feedDao().insert(feed)
            db.articleDao().insertList(articles)
        }
    }

    private fun feedCount() = runBlocking(Dispatchers.IO) { requireNotNull(database).feedDao().queryAll(ACCOUNT_ID).size }
    private fun articleCount() = runBlocking(Dispatchers.IO) { requireNotNull(database).articleDao().queryLatestUnread(ACCOUNT_ID, ARTICLE_COUNT + 1).size }

    private companion object {
        const val ACCOUNT_ID = 1
        const val ARTICLE_COUNT = 3
        const val SOURCE_URL = "https://example.test/news"
    }
}
