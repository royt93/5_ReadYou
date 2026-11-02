package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.toListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GalaxySvTest {
    private val articleDao = mockk<ArticleDao>()
    private val engine = SemanticSearchEngine(mockk(relaxed = true))
    private val service = GalaxySv(articleDao, engine, Dispatchers.Unconfined)
    private val feed = Feed("f1", "BBC News", url = "https://x.test", groupId = "g", accountId = 1)

    private fun row(id: String, title: String, text: String, day: Long = 1, summary: String? = null) =
        ArticleWithFeed(
            Article(
                id = id, date = Date(day * DAY_MS), title = title, rawDescription = "BODY-$id", shortDescription = text,
                link = "https://x.test/$id", feedId = "f1", accountId = 1, isUnread = false, aiSummary = summary,
            ),
            feed,
        ).toListItemWithFeed()

    @Test
    fun load_asksForReadArticlesOfTheAccountWithTheCap() = runBlocking {
        coEvery { articleDao.queryReadArticleListWithFeed(7, any()) } returns emptyList()

        service.load(accountId = 7)

        coVerify { articleDao.queryReadArticleListWithFeed(7, GalaxySv.MAX_READ_ARTICLES) }
    }

    @Test
    fun load_mapsMetadataAndLinksArticlesThatShareAConcept() = runBlocking {
        coEvery { articleDao.queryReadArticleListWithFeed(1, any()) } returns listOf(
            row("a1", "AI chips", "nvidia machine learning semiconductor", day = 1),
            row("a2", "More AI", "openai machine learning model", day = 2),
            row("a3", "Bitcoin", "bitcoin crypto", day = 3),
        )

        val graph = service.load(accountId = 1)

        val a1 = graph.stars.single { it.id == "a1" }
        assertEquals("AI chips", a1.title)
        assertEquals("BBC News", a1.feedName)
        assertEquals(DAY_MS, a1.date)
        assertTrue("artificial_intelligence" in a1.concepts)
        assertTrue(graph.lines.any { setOf(it.a, it.b) == setOf("a1", "a2") })
        assertTrue(graph.lines.none { "a3" in setOf(it.a, it.b) })
    }

    @Test
    fun load_addsTagsFromSavedSummariesWithoutAnyAiCall() = runBlocking {
        val tags = ArticleHighlightsExtractor.serialize(ArticleHighlights(tldr = "t", tags = listOf("Politics")))
        coEvery { articleDao.queryReadArticleListWithFeed(1, any()) } returns listOf(row("a1", "Vote", "ballot", summary = tags))

        val star = service.load(accountId = 1).stars.single()

        assertTrue("politics" in star.concepts)
    }

    @Test
    fun load_appliesTheRequestedDateWindow() = runBlocking {
        coEvery { articleDao.queryReadArticleListWithFeed(1, any()) } returns listOf(
            row("old", "AI", "machine learning", day = 1),
            row("new", "AI", "machine learning", day = 10),
        )

        val graph = service.load(accountId = 1, from = 5 * DAY_MS, to = 20 * DAY_MS)

        assertEquals(listOf("new"), graph.stars.map { it.id })
    }

    @Test
    fun load_neverCarriesTheArticleBodyIntoAStar() = runBlocking {
        coEvery { articleDao.queryReadArticleListWithFeed(1, any()) } returns listOf(row("a1", "AI", "machine learning"))

        val star = service.load(accountId = 1).stars.single()

        assertTrue(star.toString().contains("BODY-").not())
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
