package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Probe, not a fix: ArticleDao.insert is OnConflictStrategy.REPLACE, which SQLite runs as DELETE then INSERT, and six tables reference article(id) with ON DELETE CASCADE.
 * An audit claimed that re-inserting an existing article therefore wipes its read position and the other child rows. These tests run the real Room + SQLite to find out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArticleReplaceCascadeTest {
    private lateinit var db: AndroidDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private fun article(id: String, title: String = "t") = Article(
        id = id, date = Date(1_000L), title = title, rawDescription = "", shortDescription = "", link = "https://x/$id", feedId = "f1", accountId = 1,
    )

    private suspend fun seed(): ArticleDao {
        val dao = db.articleDao()
        db.groupDao().insert(com.mckimquyen.reader.domain.model.group.Group(id = "g1", name = "G", accountId = 1))
        db.feedDao().insert(com.mckimquyen.reader.domain.model.feed.Feed(id = "f1", name = "Feed", url = "https://x/feed", groupId = "g1", accountId = 1))
        dao.insert(article("a1"))
        db.articleReadPositionDao().upsertPosition(ArticleReadPosition(articleId = "a1", accountId = 1, feedUrl = "u", articleLink = "https://x/a1", progress = 0.6f))
        return dao
    }

    @Test
    fun replaceDeletesTheReadPosition_thisIsWhyFeverMustNotUseIt() = runBlocking {
        // Documents the hazard with the real engine: if this ever stops being true, upsertKeepingLocalState is no longer needed.
        val dao = seed()
        dao.insert(article("a1", title = "edited"))
        assertNull(db.articleReadPositionDao().getPosition("a1"))
    }

    @Test
    fun upsertKeepsTheReadPositionAndRefreshesTheServerText() = runBlocking {
        val dao = seed()
        dao.upsertKeepingLocalState(article("a1", title = "edited"))
        assertNotNull("the position must survive a re-sent item", db.articleReadPositionDao().getPosition("a1"))
        assertEquals("edited", dao.queryArticleByLink("https://x/a1", "f1", 1)?.title)
    }

    @Test
    fun upsertKeepsTheReadersOwnState() = runBlocking {
        val dao = seed()
        dao.markAsStarredByArticleId(1, "a1", true)
        dao.markAsReadByArticleId(1, "a1", false)
        dao.upsertKeepingLocalState(article("a1", title = "edited"))
        val row = dao.queryArticleByLink("https://x/a1", "f1", 1)
        assertEquals(true, row?.isStarred)
        assertEquals(false, row?.isUnread)
    }

    @Test
    fun upsertInsertsAnArticleThatIsNew() = runBlocking {
        val dao = seed()
        dao.upsertKeepingLocalState(article("a2", title = "brand new"))
        assertEquals("brand new", dao.queryArticleByLink("https://x/a2", "f1", 1)?.title)
        assertNotNull(db.articleReadPositionDao().getPosition("a1"))
    }
}
