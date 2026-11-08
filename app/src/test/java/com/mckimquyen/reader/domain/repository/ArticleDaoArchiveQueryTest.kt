package com.mckimquyen.reader.domain.repository

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ArticleDaoArchiveQueryTest {
    // Room được tạo trong @Before, chỉ dùng sau khi setUp hoàn tất.
    private lateinit var db: AndroidDatabase

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        for (account in listOf(ACCOUNT, OTHER_ACCOUNT)) {
            db.accountDao().insert(Account(id = account, name = "Account $account", type = AccountType.Local))
            db.groupDao().insert(Group(id = "group$account", name = "Group", accountId = account))
            db.feedDao().insert(Feed(id = "feed$account", name = "Feed $account", url = "https://example.com/$account", groupId = "group$account", accountId = account))
        }
    }

    @After fun tearDown() { db.close() }

    private fun article(id: String, at: Long, account: Int = ACCOUNT, body: String = "") = Article(
        id = id, title = "Title $id", date = Date(at), rawDescription = body, fullContent = body,
        shortDescription = "Summary $id", link = "https://example.com/$id", feedId = "feed$account", accountId = account,
    )

    private suspend fun page(
        since: Long = 0L,
        until: Long = Long.MAX_VALUE,
        beforeDate: Long? = null,
        beforeId: String? = null,
        limit: Int = 100,
        account: Int = ACCOUNT,
    ) = db.articleDao().queryArticleMetadataPage(account, since, until, beforeDate, beforeId, limit)

    @Test fun dateRange_isStartInclusiveAndEndExclusive() = runBlocking {
        db.articleDao().insert(article("before", 999))
        db.articleDao().insert(article("start", 1_000))
        db.articleDao().insert(article("inside", 1_500))
        db.articleDao().insert(article("end", 2_000))
        assertEquals(listOf("inside", "start"), page(since = 1_000, until = 2_000).map { it.article.id })
    }

    @Test fun accountScope_neverReturnsAnotherAccountsRows() = runBlocking {
        db.articleDao().insert(article("mine", 10))
        db.articleDao().insert(article("theirs", 20, OTHER_ACCOUNT))
        assertEquals(listOf("mine"), page().map { it.article.id })
        assertEquals(listOf("theirs"), page(account = OTHER_ACCOUNT).map { it.article.id })
    }

    @Test fun orderingIsDateDescendingThenIdDescending() = runBlocking {
        db.articleDao().insert(article("a", 100))
        db.articleDao().insert(article("b", 100))
        db.articleDao().insert(article("c", 200))
        assertEquals(listOf("c", "b", "a"), page().map { it.article.id })
    }

    @Test fun keysetPaging_withEqualTimestamps_hasNoDuplicatesOrGaps() = runBlocking {
        listOf("a", "b", "c", "d", "e").forEach { db.articleDao().insert(article(it, 500)) }
        db.articleDao().insert(article("older", 100))
        db.articleDao().insert(article("newer", 900))
        val seen = mutableListOf<String>()
        var beforeDate: Long? = null
        var beforeId: String? = null
        while (true) {
            val rows = page(beforeDate = beforeDate, beforeId = beforeId, limit = 2)
            if (rows.isEmpty()) break
            seen += rows.map { it.article.id }
            beforeDate = rows.last().article.date.time
            beforeId = rows.last().article.id
        }
        assertEquals(listOf("newer", "e", "d", "c", "b", "a", "older"), seen)
    }

    @Test fun limit_isHonoured() = runBlocking {
        repeat(5) { db.articleDao().insert(article("a$it", it.toLong())) }
        assertEquals(2, page(limit = 2).size)
    }

    @Test fun oversizedBody_isNotMaterializedByMetadataPage() = runBlocking {
        db.articleDao().insert(article("huge", 10, body = "x".repeat(OVERSIZED_BODY_CHARS)))
        val rows = page()
        assertEquals(listOf("huge"), rows.map { it.article.id })
        assertEquals("Summary huge", rows.single().article.shortDescription)
        assertEquals("Feed $ACCOUNT", rows.single().feed.name)
    }

    private companion object {
        const val ACCOUNT = 7
        const val OTHER_ACCOUNT = 8
        const val OVERSIZED_BODY_CHARS = 1_200 * 1024
    }
}
