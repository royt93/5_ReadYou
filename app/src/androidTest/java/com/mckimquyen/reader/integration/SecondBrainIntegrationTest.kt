package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.SecondBrainSv
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.page.secondbrain.SecondBrainStateReducer
import com.mckimquyen.reader.ui.page.secondbrain.SecondBrainUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SecondBrainIntegrationTest {
    private lateinit var db: AndroidDatabase
    private lateinit var service: SecondBrainSv

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).build()
        service = SecondBrainSv(db.articleDao(), SemanticSearchEngine(db.articleEmbeddingDao()), Dispatchers.Unconfined)
        runBlocking {
            db.groupDao().insert(Group("g1", "News", 1))
            db.feedDao().insert(Feed("f1", "Feed", url = "https://x.test/rss", groupId = "g1", accountId = 1))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(id: String, title: String, body: String, day: Long, accountId: Int = 1, summary: String? = null) {
        db.articleDao().insert(
            Article(
                id = id, date = Date(day * DAY), title = title, rawDescription = body, shortDescription = body,
                link = "https://x.test/$id", feedId = "f1", accountId = accountId, aiSummary = summary,
            ),
        )
    }

    @Test
    fun emptyDatabase_producesEmptyState() = runBlocking {
        val graph = service.load(accountId = 1)
        assertEquals(SecondBrainUiState.Empty, SecondBrainStateReducer.fromGraph(graph, null, 0L, 0L))
    }

    @Test
    fun realArticles_becomeConnectedConceptNodes() = runBlocking {
        insert("a1", "Nvidia AI chips", "machine learning semiconductor wafer", day = 1)
        insert("a2", "Bitcoin rally", "bitcoin crypto blockchain", day = 2)

        val graph = service.load(accountId = 1)

        val ids = graph.nodes.map { it.id }
        assertTrue("artificial_intelligence" in ids && "semiconductor" in ids && "cryptocurrency" in ids)
        assertTrue("one article spans AI and semiconductor", graph.edges.any { it.from == "artificial_intelligence" && it.to == "semiconductor" })
        assertTrue("no edge between concepts that never co-occur", graph.edges.none { it.from == "artificial_intelligence" && it.to == "cryptocurrency" })
        assertEquals("Nvidia AI chips", graph.articleInfo.getValue("a1").title)
    }

    @Test
    fun savedSummaryTags_areMergedIntoTheGraph() = runBlocking {
        val tags = ArticleHighlightsExtractor.serialize(ArticleHighlights(tldr = "t", tags = listOf("Geopolitics")))
        insert("a1", "Plain title", "nothing special here", day = 1, summary = tags)

        val graph = service.load(accountId = 1)

        assertTrue(graph.nodes.any { it.id == "geopolitics" })
    }

    @Test
    fun otherAccountsArticles_areNeverIncluded() = runBlocking {
        insert("mine", "Bitcoin", "bitcoin crypto", day = 1, accountId = 1)
        insert("theirs", "Tesla", "tesla battery electric vehicle", day = 1, accountId = 2)

        val graph = service.load(accountId = 1)

        assertTrue(graph.articleInfo.keys == setOf("mine"))
        assertTrue(graph.nodes.none { it.id == "electric_vehicles" })
    }

    @Test
    fun timeWindow_filtersNodesButKeepsFullSliderBounds() = runBlocking {
        insert("old", "Bitcoin", "bitcoin crypto", day = 1)
        insert("new", "Tesla", "tesla battery electric vehicle", day = 9)

        val graph = service.load(accountId = 1, fromTime = 5 * DAY, toTime = 10 * DAY)

        assertEquals(listOf("electric_vehicles"), graph.nodes.map { it.id })
        assertEquals(1 * DAY, graph.minTime)
        assertEquals(9 * DAY, graph.maxTime)
    }

    @Test
    fun selectionSurvivesRefilter_onlyWhileTheNodeStillExists() = runBlocking {
        insert("old", "Bitcoin", "bitcoin crypto", day = 1)
        insert("new", "Tesla", "tesla battery electric vehicle", day = 9)

        val wide = service.load(1)
        val kept = SecondBrainStateReducer.fromGraph(wide, "cryptocurrency", wide.minTime, wide.maxTime) as SecondBrainUiState.Ready
        assertEquals("cryptocurrency", kept.selectedNodeId)

        val narrow = service.load(1, fromTime = 5 * DAY)
        val dropped = SecondBrainStateReducer.fromGraph(narrow, "cryptocurrency", 5 * DAY, wide.maxTime) as SecondBrainUiState.Ready
        assertNull(dropped.selectedNodeId)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
