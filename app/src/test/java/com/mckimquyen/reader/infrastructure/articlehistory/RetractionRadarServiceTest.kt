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

    private fun article(id: String, title: String, body: String) = Article(
        id = id, date = Date(1_000L), title = title, rawDescription = body, shortDescription = body,
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
}
