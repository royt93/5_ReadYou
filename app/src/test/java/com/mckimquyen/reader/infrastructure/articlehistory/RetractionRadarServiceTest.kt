package com.mckimquyen.reader.infrastructure.articlehistory

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/** Stable-id path used by Google Reader and Fever, against a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RetractionRadarServiceTest {

    private class Service(
        dao: com.mckimquyen.reader.domain.repository.ArticleRadarDao,
        history: ArticleHistoryRepository,
        var enabled: Boolean = true,
    ) : RetractionRadarService(ApplicationProvider.getApplicationContext(), dao, history) {
        override fun isEnabled() = enabled
    }

    private lateinit var db: AndroidDatabase
    private lateinit var service: Service

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.feedDao().insert(Feed(id = "f", name = "F", url = "https://e.com/f", groupId = "g", accountId = 1))
        service = Service(db.articleRadarDao(), ArticleHistoryRepository(db.articleRadarDao()))
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, title: String, body: String, date: Long = 1_000L) = Article(
        id = id, date = Date(date), title = title, rawDescription = body, shortDescription = body,
        link = "https://e.com/$id", feedId = "f", accountId = 1,
    )

    @Test
    fun recordEdits_ignoresArticlesNotStoredYet_andReportsNone() = runBlocking {
        assertEquals(emptySet<String>(), service.recordEdits(listOf(article("new", "T", "B"))))
        assertEquals(0, db.articleRadarDao().snapshotCount("new"))
    }

    @Test
    fun baselineThenEditedCopy_flagsOnlyTheEditedOne() = runBlocking {
        val a = article("a", "Title", "<p>Body</p>")
        val b = article("b", "Other", "<p>Same</p>")
        db.articleDao().insertOnConflictIgnore(a, b)
        service.recordBaselines(listOf(a, b))

        val handled = service.recordEdits(listOf(a.copy(title = "Title corrected"), b))

        assertEquals(setOf("a", "b"), handled)
        assertEquals(1, db.articleRadarDao().state("a")?.editCount)
        assertNull(db.articleRadarDao().state("b"))
        assertEquals(2, db.articleRadarDao().snapshotCount("a"))
        assertEquals(1, db.articleRadarDao().snapshotCount("b"))
    }

    @Test
    fun disabled_doesNothingAndReportsNothingHandled() = runBlocking {
        val a = article("a", "Title", "Body")
        db.articleDao().insertOnConflictIgnore(a)
        service.enabled = false

        service.recordBaselines(listOf(a))
        val handled = service.recordEdits(listOf(a.copy(title = "Changed")))

        assertEquals(emptySet<String>(), handled)
        assertEquals(0, db.articleRadarDao().snapshotCount("a"))
    }

    @Test
    fun largeBatchesAreChunkedWithoutExceedingSqlVariableLimit() = runBlocking {
        val many = (1..1200).map { article("id$it", "T$it", "B$it") }
        db.articleDao().insertOnConflictIgnore(*many.toTypedArray())

        assertEquals(1200, service.recordEdits(many).size)
    }

    // ---- recheckRecent: Fever's with_ids re-check of already stored items ----

    private suspend fun seed(n: Int): List<Article> {
        val list = (1..n).map { article("r$it", "Title $it", "<p>Body $it</p>", date = it * 1_000L) }
        db.articleDao().insertOnConflictIgnore(*list.toTypedArray())
        service.recordBaselines(list)
        return list
    }

    private suspend fun recheck(returned: List<Article>?, limit: Int = 50) =
        service.recheckRecent(accountId = 1, limit = limit) { returned ?: throw java.io.IOException("offline") }

    @Test
    fun recheck_detectsAnEditOfAStoredItem() = runBlocking {
        val items = seed(3)

        recheck(listOf(items[0].copy(title = "Title 1 corrected"), items[1], items[2]))

        assertEquals(1, db.articleRadarDao().state("r1")?.editCount)
        assertNull(db.articleRadarDao().state("r2"))
    }

    @Test
    fun recheck_itemMissingThreeTimesInARow_isFlaggedRemoved_notBefore() = runBlocking {
        val items = seed(5)
        val withoutLast = items.take(4)

        repeat(2) { recheck(withoutLast) }
        assertNull(db.articleRadarDao().state("r5")?.removedAt)
        assertEquals(2, db.articleRadarDao().state("r5")?.missedSyncs)

        recheck(withoutLast)
        assertNotNullAt("r5")
    }

    private suspend fun assertNotNullAt(id: String) {
        assertEquals(true, db.articleRadarDao().state(id)?.removedAt != null)
    }

    @Test
    fun recheck_reappearingItemClearsItsMisses() = runBlocking {
        val items = seed(5)
        repeat(2) { recheck(items.take(4)) }

        recheck(items)

        assertEquals(0, db.articleRadarDao().state("r5")?.missedSyncs)
        assertNull(db.articleRadarDao().state("r5")?.removedAt)
    }

    @Test
    fun recheck_emptyResponse_countsNothing() = runBlocking {
        seed(5)
        repeat(4) { recheck(emptyList()) }
        assertNull(db.articleRadarDao().state("r5"))
    }

    @Test
    fun recheck_serverIgnoringWithIds_returningUnrelatedItems_countsNothing() = runBlocking {
        seed(5)
        val unrelated = listOf(article("other1", "X", "Y"), article("other2", "X", "Y"))

        repeat(4) { recheck(unrelated) }

        assertNull(db.articleRadarDao().state("r5"))
        assertNull(db.articleRadarDao().state("r1"))
    }

    @Test
    fun recheck_fetchFailure_isSwallowedAndCountsNothing() = runBlocking {
        seed(5)
        repeat(4) { recheck(null) }
        assertNull(db.articleRadarDao().state("r5"))
    }

    @Test
    fun recheck_mostItemsVanishingTogether_isTreatedAsAServerResetNotRetractions() = runBlocking {
        val items = seed(5)
        repeat(4) { recheck(listOf(items[0])) }
        assertNull(db.articleRadarDao().state("r5"))
        assertNull(db.articleRadarDao().state("r4"))
    }

    @Test
    fun recheck_asksOnlyForTheNewestItems_inChunkLimit() = runBlocking {
        seed(60)
        var asked: List<String> = emptyList()

        service.recheckRecent(accountId = 1, limit = 50) { ids -> asked = ids; emptyList() }

        assertEquals(50, asked.size)
        assertEquals((11..60).map { "r$it" }.toSet(), asked.toSet())
    }

    @Test
    fun recheck_disabled_neverCallsTheServer() = runBlocking {
        seed(3)
        service.enabled = false
        var called = false

        service.recheckRecent(accountId = 1, limit = 50) { called = true; emptyList() }

        assertEquals(false, called)
    }

    @Test
    fun recheck_otherAccountsItemsAreNeverAsked() = runBlocking {
        seed(3)
        db.accountDao().insert(Account(id = 2, name = "B", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g2", name = "G2", accountId = 2))
        db.feedDao().insert(Feed(id = "f2", name = "F2", url = "https://e.com/f2", groupId = "g2", accountId = 2))
        db.articleDao().insertOnConflictIgnore(article("foreign", "T", "B", 9_000L).copy(feedId = "f2", accountId = 2))
        var asked: List<String> = emptyList()

        service.recheckRecent(accountId = 1, limit = 50) { ids -> asked = ids; emptyList() }

        assertEquals(false, "foreign" in asked)
    }

    @Test
    fun recheck_unrelatedItemsMixedIn_areIgnoredAndNeverRecordedAsEdits() = runBlocking {
        val items = seed(10)
        // Nine real items come back, one is missing; two foreign items ride along (server ignoring with_ids partly).
        val answer = items.take(9) + listOf(article("foreign-1", "Z", "Z"), article("foreign-2", "Z", "Z"))

        repeat(3) { recheck(answer) }

        assertEquals(true, db.articleRadarDao().state("r10")?.removedAt != null)
        assertEquals(0, db.articleRadarDao().snapshotCount("foreign-1"))
        assertNull(db.articleRadarDao().state("foreign-1"))
    }
}
