package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionArticle
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.MIGRATION_14_15
import com.mckimquyen.reader.infrastructure.db.MIGRATION_15_16
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SmartCollectionDaoTest {

    private lateinit var database: AndroidDatabase
    private lateinit var dao: SmartCollectionDao

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java,
        ).allowMainThreadQueries()
            .addMigrations(MIGRATION_14_15, MIGRATION_15_16)
            .build()
        dao = database.smartCollectionDao()

        // Seed account -> group -> feed -> article for FK tests
        database.accountDao().insert(
            com.mckimquyen.reader.domain.model.account.Account(
                id = ACCOUNT_ID,
                name = "Account",
                type = com.mckimquyen.reader.domain.model.account.AccountType.Local,
            )
        )
        database.accountDao().insert(
            com.mckimquyen.reader.domain.model.account.Account(
                id = OTHER_ACCOUNT_ID,
                name = "Other account",
                type = com.mckimquyen.reader.domain.model.account.AccountType.Local,
            )
        )
        database.groupDao().insert(Group(id = GROUP_ID, name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(
            Feed(id = FEED_ID, name = "Feed", url = FEED_URL, groupId = GROUP_ID, accountId = ACCOUNT_ID)
        )
        database.articleDao().insert(
            Article(
                id = ARTICLE_ID,
                date = Date(),
                title = "Article",
                rawDescription = "",
                shortDescription = "AI article",
                link = ARTICLE_LINK,
                feedId = FEED_ID,
                accountId = ACCOUNT_ID,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Test
    fun upsertAndGetCollection_returnsInserted() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)

        val result = dao.getCollectionById(col.id)
        assertNotNull(result)
        assertEquals(col.name, result!!.name)
        assertEquals(col.query, result.query)
    }

    @Test
    fun getCollectionsByAccount_returnsOnlyOwnAccount() = runBlocking {
        dao.upsertCollection(smartCollection(accountId = ACCOUNT_ID))
        dao.upsertCollection(smartCollection(accountId = OTHER_ACCOUNT_ID))

        val results = dao.getCollectionsByAccount(ACCOUNT_ID)
        assertEquals(1, results.size)
        assertEquals(ACCOUNT_ID, results[0].accountId)
    }

    @Test
    fun deleteCollection_removesIt() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.deleteCollection(col.id)

        assertNull(dao.getCollectionById(col.id))
    }

    @Test
    fun updateCollection_changesNameAndQuery() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.updateCollection(col.id, "Renamed", "new query")

        val updated = dao.getCollectionById(col.id)!!
        assertEquals("Renamed", updated.name)
        assertEquals("new query", updated.query)
    }

    // ── Article count ─────────────────────────────────────────────────────────

    @Test
    fun insertMatches_countReflectsInsertedLinks() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.insertMatches(listOf(SmartCollectionArticle(col.id, ARTICLE_ID, score = 0.5f)))

        val count = dao.countArticles(col.id)
        assertEquals(1, count)
    }

    @Test
    fun observeCollectionsWithCount_emitsCorrectCount() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.insertMatches(listOf(SmartCollectionArticle(col.id, ARTICLE_ID, score = 0.4f)))

        val results = dao.observeCollectionsWithCount(ACCOUNT_ID).first()
        assertEquals(1, results.size)
        assertEquals(1, results[0].articleCount)
    }

    @Test
    fun observeCollectionsWithCount_countIsZeroWhenNoMatches() = runBlocking {
        dao.upsertCollection(smartCollection())

        val results = dao.observeCollectionsWithCount(ACCOUNT_ID).first()
        assertEquals(1, results.size)
        assertEquals(0, results[0].articleCount)
    }

    // ── Cascade: deleting collection removes junction rows, not articles ───────

    @Test
    fun deleteCollection_cascadesToJunctionButArticleRemains() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.insertMatches(listOf(SmartCollectionArticle(col.id, ARTICLE_ID, score = 0.5f)))

        dao.deleteCollection(col.id)

        // Junction row gone
        val count = dao.countArticles(col.id)
        assertEquals(0, count)
        // Article still exists
        val article = database.articleDao().queryById(ARTICLE_ID)
        assertNotNull(article)
    }

    // ── Cascade: deleting article removes junction rows ───────────────────────

    @Test
    fun deleteArticle_cascadesToJunctionRow() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.insertMatches(listOf(SmartCollectionArticle(col.id, ARTICLE_ID, score = 0.5f)))

        // Delete article via feed cascade
        database.articleDao().deleteByFeedId(ACCOUNT_ID, FEED_ID)

        val count = dao.countArticles(col.id)
        assertEquals(0, count)
    }

    // ── Idempotency: ON CONFLICT IGNORE prevents duplicate junction rows ───────

    @Test
    fun insertMatches_ignoreDuplicates() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        val match = SmartCollectionArticle(col.id, ARTICLE_ID, score = 0.5f)
        dao.insertMatches(listOf(match))
        dao.insertMatches(listOf(match)) // second insert should be ignored

        val count = dao.countArticles(col.id)
        assertEquals(1, count)
    }

    @Test
    fun replaceMatches_failedReplacementRollsBackAndKeepsPreviousMembership() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.insertMatches(listOf(SmartCollectionArticle(col.id, ARTICLE_ID, score = 0.5f)))

        runCatching {
            dao.replaceMatches(
                col.id,
                listOf(SmartCollectionArticle(col.id, "missing-article", score = 0.8f)),
            )
        }

        assertEquals(1, dao.countArticles(col.id))
        val retained = dao.observeArticlesInCollection(col.id).first()
        assertEquals(ARTICLE_ID, retained.single().article.id)
    }

    // ── Observe articles in collection ────────────────────────────────────────

    @Test
    fun observeArticlesInCollection_returnsLinkedArticles() = runBlocking {
        val col = smartCollection()
        dao.upsertCollection(col)
        dao.insertMatches(listOf(SmartCollectionArticle(col.id, ARTICLE_ID, score = 0.6f)))

        val articles = dao.observeArticlesInCollection(col.id).first()
        assertEquals(1, articles.size)
        assertEquals(ARTICLE_ID, articles[0].article.id)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun smartCollection(accountId: Int = ACCOUNT_ID) = SmartCollection(
        id = "col-${accountId}-${System.nanoTime()}",
        accountId = accountId,
        name = "Smart Collection",
        query = "AI news",
    )

    private companion object {
        const val ACCOUNT_ID = 1
        const val OTHER_ACCOUNT_ID = 2
        const val GROUP_ID = "group-1"
        const val FEED_ID = "feed-1"
        const val FEED_URL = "https://example.com/feed"
        const val ARTICLE_ID = "article-1"
        const val ARTICLE_LINK = "https://example.com/1"
    }
}
