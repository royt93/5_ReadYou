package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
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
@Config(sdk = [34], application = Application::class)
class OracleMarketDaoTest {

    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleMarketDao

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "B", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.groupDao().insert(Group(id = "g2", name = "G2", accountId = 2))
        db.feedDao().insert(Feed(id = "f", name = "F", url = "https://e.com/f", groupId = "g", accountId = 1))
        db.feedDao().insert(Feed(id = "f2", name = "F2", url = "https://e.com/f2", groupId = "g2", accountId = 2))
        dao = db.oracleMarketDao()
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, date: Long, account: Int = 1) = Article(
        id = id, date = Date(date), title = "T-$id", rawDescription = "RAW-$id", shortDescription = "S-$id",
        link = "https://e.com/$id", feedId = if (account == 1) "f" else "f2", accountId = account,
    )

    private fun market(id: String, status: OracleMarketStatus, attempts: Int = 0, createdAt: Long = 1L, expiresAt: Long? = null) =
        OracleMarket(
            articleId = id, status = status, attempts = attempts,
            question = "Q?".takeIf { status == OracleMarketStatus.HAS_CLAIM },
            expiresAt = expiresAt,
            resolutionCriteria = "c".takeIf { status == OracleMarketStatus.HAS_CLAIM },
            createdAt = createdAt,
        )

    private fun count(): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM oracle_market").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun candidates_areNewestFirst_withinTheWindow_andLimited() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("old", 100), article("a", 1_000), article("b", 2_000), article("c", 3_000))
        val rows = dao.candidates(accountId = 1, sinceMillis = 500, limit = 2)
        assertEquals(listOf("c", "b"), rows.map { it.id })
        assertEquals("T-c", rows.first().title)
        assertEquals("S-c", rows.first().shortDescription)
        assertEquals(listOf("c", "b", "a"), dao.candidates(1, 500, 10).map { it.id })
    }

    @Test
    fun candidates_skipDecidedArticles_butKeepPendingOnesWithTheirAttempts() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("has", 1_000), article("none", 1_000), article("pend", 1_000), article("new", 1_000))
        dao.upsert(market("has", OracleMarketStatus.HAS_CLAIM, expiresAt = 9))
        dao.upsert(market("none", OracleMarketStatus.NO_CLAIM))
        dao.upsert(market("pend", OracleMarketStatus.PENDING, attempts = 1))

        val rows = dao.candidates(1, 0, 10).associateBy { it.id }
        assertEquals(setOf("pend", "new"), rows.keys)
        assertEquals(1, rows.getValue("pend").attempts)
        assertEquals(0, rows.getValue("new").attempts)
    }

    @Test
    fun candidates_onlyTheGivenAccount() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("mine", 1_000, account = 1), article("theirs", 1_000, account = 2))
        assertEquals(listOf("mine"), dao.candidates(1, 0, 10).map { it.id })
    }

    @Test
    fun upsert_replacesTheRowOfTheSameArticle() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("a", 1_000))
        dao.upsert(market("a", OracleMarketStatus.PENDING, attempts = 1))
        dao.upsert(market("a", OracleMarketStatus.HAS_CLAIM, expiresAt = 50))
        val row = dao.byArticle("a")
        assertNotNull(row)
        assertEquals(OracleMarketStatus.HAS_CLAIM, row?.status)
        assertEquals("Q?", row?.question)
        assertEquals(1, count())
    }

    @Test
    fun observeClaims_returnsOnlyRealClaims() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("has", 1_000), article("none", 1_000), article("pend", 1_000))
        dao.upsert(market("has", OracleMarketStatus.HAS_CLAIM, expiresAt = 9))
        dao.upsert(market("none", OracleMarketStatus.NO_CLAIM))
        dao.upsert(market("pend", OracleMarketStatus.PENDING, attempts = 1))
        assertEquals(listOf("has"), dao.observeClaims().first().map { it.articleId })
    }

    @Test
    fun deletingTheArticleCascades() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("a", 1_000))
        dao.upsert(market("a", OracleMarketStatus.NO_CLAIM))
        db.openHelper.writableDatabase.execSQL("DELETE FROM article WHERE id='a'")
        assertNull(dao.byArticle("a"))
    }

    @Test
    fun deleteStale_dropsExpiredClaimsAndOldUndecidedRowsOnly() = runBlocking {
        val ids = listOf("expired", "live", "oldNone", "edgeNone", "freshNone", "oldPend")
        db.articleDao().insertOnConflictIgnore(*ids.map { article(it, 1_000) }.toTypedArray())
        dao.upsert(market("expired", OracleMarketStatus.HAS_CLAIM, createdAt = 500, expiresAt = 900))
        dao.upsert(market("live", OracleMarketStatus.HAS_CLAIM, createdAt = 500, expiresAt = 5_000))
        dao.upsert(market("oldNone", OracleMarketStatus.NO_CLAIM, createdAt = 900))
        dao.upsert(market("edgeNone", OracleMarketStatus.NO_CLAIM, createdAt = 1_000))
        dao.upsert(market("freshNone", OracleMarketStatus.NO_CLAIM, createdAt = 2_000))
        dao.upsert(market("oldPend", OracleMarketStatus.PENDING, attempts = 1, createdAt = 100))

        val removed = dao.deleteStale(cutoffMillis = 1_000)

        assertEquals(3, removed)
        assertEquals(listOf("live"), dao.observeClaims().first().map { it.articleId })
        assertNotNull("a row created exactly at the cutoff is kept", dao.byArticle("edgeNone"))
        assertNotNull(dao.byArticle("freshNone"))
        assertNull(dao.byArticle("expired"))
    }
}
