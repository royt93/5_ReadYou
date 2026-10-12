package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SecondBrainSvTest {
    private val articleDao = mockk<ArticleDao>()
    private val engine = SemanticSearchEngine(mockk(relaxed = true))
    private val service = SecondBrainSv(articleDao, engine, Dispatchers.Unconfined)
    private val feed = Feed("f1", "Feed", url = "https://x.test", groupId = "g", accountId = 1)

    private fun article(id: String, title: String, text: String, summary: String? = null, day: Long = 1) =
        ArticleWithFeed(
            Article(
                id = id, date = Date(day * 86_400_000L), title = title, rawDescription = text, shortDescription = text,
                link = "https://x.test/$id", feedId = "f1", accountId = 1, aiSummary = summary,
            ),
            feed,
        )

    @Test
    fun toBrainArticle_combinesDetectedConceptsAndSavedSummaryTags() {
        val tags = ArticleHighlightsExtractor.serialize(ArticleHighlights(tldr = "t", tags = listOf("Politics")))
        val brain = service.toBrainArticle(article("a1", "Bitcoin rally", "bitcoin and ethereum", tags))

        assertTrue("cryptocurrency" in brain.concepts)
        assertTrue("Politics" in brain.concepts)
    }

    @Test
    fun toBrainArticle_tolerantOfNullOrCorruptSummary() {
        val corrupt = service.toBrainArticle(article("a1", "Tesla", "tesla battery", summary = "not-json"))
        val none = service.toBrainArticle(article("a2", "Tesla", "tesla battery", summary = null))

        assertTrue(corrupt.concepts.isNotEmpty())
        assertEquals(none.concepts, corrupt.concepts)
    }

    @Test
    fun toBrainArticle_doesNotMatchShortKeywordsInsideOtherWords() {
        // "blockchain" contains "ai" and "chain"; "raised" contains "ai"/"se". Neither is about AI.
        val brain = service.toBrainArticle(article("a1", "Bitcoin rally", "Prices raised on blockchain news"))

        assertTrue("cryptocurrency" in brain.concepts)
        assertTrue("artificial_intelligence" !in brain.concepts)
    }

    @Test
    fun toBrainArticle_stillMatchesShortKeywordAsAWholeWord() {
        val brain = service.toBrainArticle(article("a1", "New AI model", "The AI lab shipped it"))
        assertTrue("artificial_intelligence" in brain.concepts)
    }

    @Test
    fun load_buildsGraphFromRecentArticles_forTheGivenAccount() = runBlocking {
        coEvery { articleDao.queryRecentArticlesWithFeed(1, any()) } returns listOf(
            article("a1", "AI chips", "nvidia machine learning semiconductor"),
            article("a2", "Bitcoin", "bitcoin crypto", day = 2),
        )

        val graph = service.load(accountId = 1)

        assertTrue(graph.nodes.any { it.id == "artificial_intelligence" })
        assertTrue(graph.nodes.any { it.id == "cryptocurrency" })
        assertTrue(graph.edges.isNotEmpty())
    }

    @Test
    fun load_noArticles_returnsEmptyGraph() = runBlocking {
        coEvery { articleDao.queryRecentArticlesWithFeed(1, any()) } returns emptyList()
        assertEquals(0, service.load(1).nodes.size)
    }
}
