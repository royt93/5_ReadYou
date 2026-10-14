package com.mckimquyen.reader.domain.model.cluster

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SourceDnaAnalyzerTest {
    @Test fun uniqueEarliest_isMarkedAndNodesSortByTimestampThenArticleId() {
        val laterB = article("b", 20L, feed("B"))
        val earliest = article("a", 10L, feed("A"))
        val laterA = article("c", 20L, feed("C"))
        val result = analyze(listOf(laterB, earliest, laterA)) { _, _ -> 0.1f }

        assertEquals(listOf("a", "b", "c"), result.nodes.map { it.article.article.id })
        assertEquals(SourceDnaRelation.EARLIEST_IN_CLUSTER, result.nodes.first().relation)
        assertFalse(result.isEarliestAmbiguous)
    }

    @Test fun tiedEarliest_hasNoEarliestLabelAndNoEdgesBetweenTiedItems() {
        val a = article("a", 10L, feed("A"))
        val b = article("b", 10L, feed("B"))
        val c = article("c", 20L, feed("C"))
        val result = analyze(listOf(a, b, c)) { _, _ -> 0.9f }

        assertTrue(result.isEarliestAmbiguous)
        assertEquals(null, result.nodes.first().parentArticleId)
        assertEquals(SourceDnaRelation.NO_CLEAR_RELATIONSHIP, result.nodes.first().relation)
        assertEquals(SourceDnaRelation.NO_CLEAR_RELATIONSHIP, result.nodes[1].relation)
        assertEquals("a", result.nodes[2].parentArticleId)
    }

    @Test fun nodeChoosesHighestSimilarityStrictlyEarlierParent() {
        val a = article("a", 10L, feed("A"))
        val b = article("b", 20L, feed("B"))
        val c = article("c", 30L, feed("C"))
        val result = analyze(listOf(a, b, c)) { left, right ->
            when (left.article.id to right.article.id) {
                "a" to "b" -> 0.8f
                "a" to "c" -> 0.78f
                "b" to "c" -> 0.91f
                else -> 0f
            }
        }
        assertEquals("a", result.nodes[1].parentArticleId)
        assertEquals("b", result.nodes[2].parentArticleId)
        assertEquals(0.91f, result.nodes[2].similarityToParent)
    }

    @Test fun exactThresholdCreatesEdge_belowThresholdDoesNot() {
        val a = article("a", 1L, feed("A"))
        val b = article("b", 2L, feed("B"))
        val c = article("c", 3L, feed("C"))
        val result = analyze(listOf(a, b, c)) { left, right ->
            when (left.article.id to right.article.id) {
                "a" to "b" -> 0.75f
                "a" to "c" -> 0.749f
                "b" to "c" -> 0.2f
                else -> 0f
            }
        }
        assertEquals(SourceDnaRelation.HIGH_SIMILARITY, result.nodes[1].relation)
        assertEquals("a", result.nodes[1].parentArticleId)
        assertEquals(SourceDnaRelation.NO_CLEAR_RELATIONSHIP, result.nodes[2].relation)
        assertNull(result.nodes[2].parentArticleId)
    }

    @Test fun invalidAndOutOfRangeScoresAreIgnoredNotClampedIntoEdges() {
        val a = article("a", 1L, feed("A"))
        val b = article("b", 2L, feed("B"))
        val c = article("c", 3L, feed("C"))
        val scores = mapOf(
            ("a" to "b") to Float.NaN,
            ("a" to "c") to Float.POSITIVE_INFINITY,
            ("b" to "c") to 1.1f,
        )
        val result = analyze(listOf(a, b, c)) { x, y -> scores[x.article.id to y.article.id] ?: 0f }
        assertTrue(result.nodes.all { it.parentArticleId == null })
        assertFalse(result.hasAnySimilarityEdge)
    }

    @Test fun duplicateIdsKeepEarliestAndBlankIdsNeverBecomeParents() {
        val duplicateLate = article("same", 30L, feed("late"))
        val duplicateEarly = article("same", 10L, feed("early"))
        val blank = article(" ", 5L, feed("blank"))
        val later = article("later", 40L, feed("later"))
        val result = analyze(listOf(duplicateLate, duplicateEarly, blank, later)) { _, _ -> 0.9f }

        assertEquals(listOf("same", "later"), result.nodes.map { it.article.article.id })
        assertEquals("early", result.nodes.first().article.feed.id)
        assertEquals("same", result.nodes.last().parentArticleId)
    }

    @Test fun emptyAndSingleInputsReturnNoEdgeAndSingleEarliestWhenPresent() {
        assertTrue(analyze(emptyList()) { _, _ -> 1f }.nodes.isEmpty())
        val only = article("one", 1L, feed("A"))
        val result = analyze(listOf(only)) { _, _ -> 1f }
        assertEquals(1, result.nodes.size)
        assertEquals(SourceDnaRelation.EARLIEST_IN_CLUSTER, result.nodes.single().relation)
        assertFalse(result.hasAnySimilarityEdge)
    }

    @Test fun allEqualTimestampsProduceNoEdgesAndNoUniqueEarliest() {
        val articles = (1..4).map { article("a$it", 10L, feed("f$it")) }
        val result = analyze(articles) { _, _ -> 1f }
        assertTrue(result.isEarliestAmbiguous)
        assertFalse(result.hasAnySimilarityEdge)
        assertTrue(result.nodes.all { it.relation == SourceDnaRelation.NO_CLEAR_RELATIONSHIP })
    }

    @Test fun twentyOneItemsAnalyzesOldestTwentyAndMarksTruncation() {
        val articles = (1..21).map { article("a%02d".format(it), it.toLong(), feed("f$it")) }
        val result = analyze(articles) { _, _ -> 0f }
        assertEquals(20, result.analyzedArticleCount)
        assertTrue(result.wasTruncated)
        assertFalse(result.nodes.any { it.article.article.id == "a21" })
    }

    @Test fun twentyItemsCallSimilarityNoMoreThan190Times() {
        val articles = (1..20).map { article("a$it", it.toLong(), feed("f$it")) }
        var calls = 0
        SourceDnaAnalyzer.analyze(articles) { _, _ -> calls++; 0.2f }
        assertTrue("expected at most 20*19/2 calls, got $calls", calls <= 190)
    }

    @Test fun blankFeedNameUsesUrlHostThenUnknownFallback() {
        val host = article("host", 1L, feed("host", ""), "https://news.example/path")
        val noHost = article("unknown", 2L, feed("unknown", ""), "not a URL")
        val result = analyze(listOf(host, noHost)) { _, _ -> 0f }
        assertEquals("news.example", result.nodes[0].sourceLabel)
        assertEquals(null, result.nodes[1].sourceLabel)
    }

    private fun analyze(
        articles: List<ArticleWithFeed>,
        similarity: (ArticleWithFeed, ArticleWithFeed) -> Float,
    ) = SourceDnaAnalyzer.analyze(articles, similarity)

    private fun article(id: String, time: Long, source: Feed, link: String = "https://example.test/$id") = ArticleWithFeed(
        article = Article(
            id = id,
            date = Date(time),
            title = "Source DNA title $id",
            rawDescription = "description $id",
            shortDescription = "description $id",
            link = link,
            feedId = source.id,
            accountId = 1,
        ),
        feed = source,
    )

    private fun feed(id: String, name: String = id, url: String = "https://$id.example/rss") = Feed(
        id = id,
        name = name,
        url = url,
        groupId = "group",
        accountId = 1,
    )
}
