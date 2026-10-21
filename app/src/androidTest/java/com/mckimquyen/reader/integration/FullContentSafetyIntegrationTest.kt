package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class FullContentSafetyIntegrationTest {
    private var db: AndroidDatabase? = null

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java).build()
    }

    @After
    fun tearDown() { db?.close() }

    @Test
    fun audioFullContentIsNotPersistedAndUnreadQueryRemainsReadable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(AUDIO_BODY.toResponseBody("audio/mpeg".toMediaType())).build()
        }.build()
        try {
            val database = requireNotNull(db)
            val helper = RssHelper(context, Dispatchers.IO, client)
            val group = Group(id = "audio-group", name = "Test", accountId = ACCOUNT_ID)
            val feed = Feed(id = "audio-feed", name = "Audio", url = "https://example.test/news", groupId = group.id,
                accountId = ACCOUNT_ID, sourceType = Feed.WEB_SOURCE_TYPE, sourceUrl = "https://example.test/news")
            val article = Article(id = "audio-article", date = Date(), title = "Audio segment", rawDescription = "",
                shortDescription = "Audio segment", link = "https://example.test/segment.mp3", feedId = feed.id, accountId = ACCOUNT_ID)
            database.groupDao().insert(group)
            database.feedDao().insert(feed)
            database.articleDao().insert(article)

            val content = helper.parseFullContent(article.link, article.title)
            if (content.isNotBlank()) database.articleDao().update(article.apply { fullContent = content })

            assertEquals("", content)
            val rows = database.articleDao().queryUnreadArticleList(ACCOUNT_ID, QUERY_LIMIT)
            assertEquals(listOf(article.id), rows.map { it.id })
            assertNull(database.articleDao().queryArticleBody(ACCOUNT_ID, rows.single().id)?.fullContent)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private companion object {
        const val ACCOUNT_ID = 1
        const val QUERY_LIMIT = 50
        const val AUDIO_BYTES = 4_207_639
        val AUDIO_BODY = ByteArray(AUDIO_BYTES) { if (it % 2 == 0) 0 else 0xff.toByte() }
    }
}
