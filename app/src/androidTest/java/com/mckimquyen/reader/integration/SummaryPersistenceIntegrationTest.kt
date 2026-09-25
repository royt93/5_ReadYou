package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SummaryPersistenceIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var articleDao: ArticleDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        articleDao = database.articleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fullSummaryPersistenceLifecycle_saveQueryRestoreUpdateInDatabase() = runBlocking {
        // 0. Setup Group and Feed for foreign key constraints
        val group = Group(id = "g_quantum", name = "Quantum Group", accountId = 1)
        database.groupDao().insert(group)

        val feed = Feed(
            id = "feed_quantum",
            name = "Quantum Feed",
            url = "https://quantum.google.com/feed",
            groupId = group.id,
            accountId = 1,
            isFullContent = false,
        )
        database.feedDao().insert(feed)

        val testArticle = Article(
            id = "persisted_art_001",
            date = Date(),
            title = "Understanding Quantum Computing with Android",
            author = "Google Research",
            rawDescription = "<p>Quantum computing harnesses quantum mechanics.</p>",
            shortDescription = "Quantum computing basics",
            fullContent = "<p>Quantum computing harnesses quantum mechanics to solve complex problems.</p>",
            link = "https://quantum.google.com/article",
            feedId = feed.id,
            accountId = 1,
            isUnread = true,
            aiSummary = null,
        )

        // 1. Insert article with null aiSummary
        articleDao.insert(testArticle)
        val initialSummary = articleDao.queryAiSummaryByArticleId(testArticle.id)
        assertNull("Initially aiSummary must be null", initialSummary)

        // 2. Serialize and persist highlights into Room DB
        val highlights = ArticleHighlights(
            tldr = "Quantum computing introduces unprecedented computational speedups.",
            keyTakeaways = listOf(
                "Superposition allows exploring combinatorial state spaces.",
                "Entanglement coordinates multi-qubit register interactions.",
                "Error mitigation techniques bring fault-tolerant NISQ closer."
            ),
            readingTimeSavedMin = 5,
            tags = listOf("Quantum", "Physics", "Computing"),
            isOfflineFallback = false,
        )
        val serialized = ArticleHighlightsExtractor.serialize(highlights)
        articleDao.updateAiSummary(testArticle.id, serialized)

        // 3. Query back from database and deserialize
        val savedRaw = articleDao.queryAiSummaryByArticleId(testArticle.id)
        assertNotNull("Persisted summary must exist in Room", savedRaw)

        val restored = ArticleHighlightsExtractor.deserialize(savedRaw)
        assertNotNull(restored)
        assertEquals(highlights.tldr, restored?.tldr)
        assertEquals(3, restored?.keyTakeaways?.size)
        assertEquals(highlights.keyTakeaways[0], restored?.keyTakeaways?.get(0))
        assertEquals(highlights.keyTakeaways[2], restored?.keyTakeaways?.get(2))
        assertEquals(5, restored?.readingTimeSavedMin)
        assertEquals(listOf("Quantum", "Physics", "Computing"), restored?.tags)

        // 4. Update with a new summary (e.g. refreshed with detailed mode)
        val updatedHighlights = highlights.copy(
            tldr = "Refreshed in-depth quantum analysis.",
            readingTimeSavedMin = 7,
        )
        val updatedSerialized = ArticleHighlightsExtractor.serialize(updatedHighlights)
        articleDao.updateAiSummary(testArticle.id, updatedSerialized)

        val secondRestored = ArticleHighlightsExtractor.deserialize(
            articleDao.queryAiSummaryByArticleId(testArticle.id)
        )
        assertEquals("Refreshed in-depth quantum analysis.", secondRestored?.tldr)
        assertEquals(7, secondRestored?.readingTimeSavedMin)

        // 5. Clean up / clear summary
        articleDao.updateAiSummary(testArticle.id, null)
        val cleared = articleDao.queryAiSummaryByArticleId(testArticle.id)
        assertNull("Summary should be successfully cleared", cleared)
    }
}
