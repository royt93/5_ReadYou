package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.rss.RssDescriptionParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class RssDescriptionPersistenceIntegrationTest {
    private lateinit var db: AndroidDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fallbackSummaryPersistsAndRemainsAttachedToArticleFeed() = runBlocking {
        val group = Group(id = "rss-group", name = "News", accountId = 1)
        val feed = Feed(
            id = "guardian-feed",
            name = "The Guardian",
            url = "https://www.theguardian.com/world/rss",
            groupId = group.id,
            accountId = 1,
        )
        val html = """<p>The judge who helped convict Jair Bolsonaro is caught up in a crisis.</p><ul><li><p><a href="https://example.test/related">Related report</a></p></li></ul><p>Thundering to a crowd of thousands, the senator focused on his opponent.</p> <a href="https://example.test/article">Continue reading...</a>"""
        val article = Article(
            id = "guardian-article",
            date = Date(1_000L),
            title = "Guardian report",
            rawDescription = html,
            shortDescription = RssDescriptionParser.shortDescription(html),
            link = "https://example.test/article",
            feedId = feed.id,
            accountId = 1,
        )
        db.groupDao().insert(group)
        db.feedDao().insert(feed)
        db.articleDao().insert(article)

        val persisted = db.articleDao().queryById(article.id)

        assertEquals(article.shortDescription, persisted?.article?.shortDescription)
        assertTrue(persisted?.article?.shortDescription.orEmpty().contains("judge who helped convict Jair Bolsonaro"))
        assertEquals(feed.id, persisted?.feed?.id)
    }
}
