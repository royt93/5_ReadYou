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
class ArticleDaoLightweightReadTest {
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

    private fun article(id: String, body: String, account: Int = ACCOUNT, at: Long = 0) = Article(
        id = id, title = id, date = Date(at), rawDescription = body, fullContent = body,
        shortDescription = "Summary $id", link = "https://example.com/$id", feedId = "feed$account", accountId = account,
    )

    @Test fun recentList_doesNotMaterializeBodiesLargerThanCursorWindow() = runBlocking {
        db.articleDao().insert(article("huge", "x".repeat(OVERSIZED_BODY_CHARS)))
        val rows = db.articleDao().queryRecentArticleListWithFeed(ACCOUNT, LIST_LIMIT)
        assertEquals(listOf("huge"), rows.map { it.article.id })
        assertEquals("Feed $ACCOUNT", rows.single().feed.name)
    }

    @Test fun unreadList_doesNotMaterializeBodiesLargerThanCursorWindow() = runBlocking {
        db.articleDao().insert(article("huge", "x".repeat(OVERSIZED_BODY_CHARS)))
        assertEquals(listOf("huge"), db.articleDao().queryUnreadArticleList(ACCOUNT, LIST_LIMIT).map { it.id })
    }

    @Test fun manyMediumBodies_keepOrderLimitAndAccountScope() = runBlocking {
        repeat(LIST_LIMIT) { db.articleDao().insert(article("a$it", "m".repeat(MEDIUM_BODY_CHARS), at = it.toLong())) }
        db.articleDao().insert(article("other", "", OTHER_ACCOUNT, Long.MAX_VALUE))
        val expected = (0 until LIST_LIMIT).reversed().map { "a$it" }
        assertEquals(expected, db.articleDao().queryRecentArticleListWithFeed(ACCOUNT, LIST_LIMIT).map { it.article.id })
        assertEquals(expected.take(SMALL_LIMIT), db.articleDao().queryUnreadArticleList(ACCOUNT, SMALL_LIMIT).map { it.id })
    }

    @Test fun bodyLookup_roundTripsOversizedUnicodeWithoutTruncation() = runBlocking {
        val text = "漢🙂e\u0301".repeat(UNICODE_REPEATS)
        db.articleDao().insert(article("tag:google.com,2005", text))
        val body = db.articleDao().queryArticleBody(ACCOUNT, "tag:google.com,2005")
        assertEquals(text, body?.rawDescription)
        assertEquals(text, body?.fullContent)
    }

    @Test fun bodyLookup_preservesNullAndEmptyAndAccountScope() = runBlocking {
        db.articleDao().insert(article("null", "raw").copy(fullContent = null))
        db.articleDao().insert(article("empty", "raw").copy(fullContent = ""))
        assertEquals(null, db.articleDao().queryArticleBody(ACCOUNT, "null")?.fullContent)
        assertEquals("", db.articleDao().queryArticleBody(ACCOUNT, "empty")?.fullContent)
        assertEquals(null, db.articleDao().queryArticleBody(OTHER_ACCOUNT, "empty"))
        assertEquals(null, db.articleDao().queryArticleBody(ACCOUNT, "missing"))
    }

    private companion object {
        const val UNICODE_REPEATS = 500_000
        const val ACCOUNT = 7
        const val OTHER_ACCOUNT = 8
        const val OVERSIZED_BODY_CHARS = 3 * 1024 * 1024
        const val MEDIUM_BODY_CHARS = 140_000
        const val LIST_LIMIT = 20
        const val SMALL_LIMIT = 3
    }
}
