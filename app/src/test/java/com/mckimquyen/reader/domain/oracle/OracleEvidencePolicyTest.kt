package com.mckimquyen.reader.domain.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleEvidencePolicyTest {

    private val placedAt = 1_000L

    private fun c(id: String, date: Long, score: Float, title: String = "T-$id", feed: String = "F-$id") =
        OracleEvidenceCandidate(id, title, feed, date, score)

    private fun ids(list: List<OracleEvidence>) = list.map { it.articleId }

    @Test
    fun constants_areHardCodedNumbers() {
        assertEquals(3, OracleEvidencePolicy.MAX_EVIDENCE)
        assertEquals(200, OracleEvidencePolicy.RECENT_SCAN_LIMIT)
    }

    @Test
    fun emptyInput_isEmpty() {
        assertTrue(OracleEvidencePolicy.select(emptyList(), placedAt).isEmpty())
    }

    @Test
    fun onlyArticlesFromThePlacingMomentOn_areEvidence_andTheBoundaryIsKept() {
        val out = OracleEvidencePolicy.select(
            listOf(c("before", placedAt - 1, 0.9f), c("same", placedAt, 0.5f), c("after", placedAt + 1, 0.4f)),
            placedAt,
        )
        assertEquals(setOf("same", "after"), ids(out).toSet())
        assertTrue("an article older than the bet can never confirm it", "before" !in ids(out))
        assertTrue("an article published exactly when the bet was placed is kept", "same" in ids(out))
    }

    @Test
    fun orderedByScoreDescending() {
        val out = OracleEvidencePolicy.select(
            listOf(c("low", 2_000, 0.3f), c("high", 2_000, 0.9f), c("mid", 2_000, 0.6f)), placedAt,
        )
        assertEquals(listOf("high", "mid", "low"), ids(out))
    }

    @Test
    fun equalScore_newerFirst_thenByIdForADeterministicOrder() {
        val out = OracleEvidencePolicy.select(
            listOf(c("b", 2_000, 0.5f), c("a", 2_000, 0.5f), c("newer", 3_000, 0.5f)), placedAt,
        )
        assertEquals(listOf("newer", "a", "b"), ids(out))
    }

    @Test
    fun theOrderOfTheInputDoesNotChangeTheResult() {
        val list = listOf(c("a", 2_000, 0.5f), c("b", 2_500, 0.5f), c("c", 2_200, 0.8f), c("d", 2_100, 0.8f))
        assertEquals(ids(OracleEvidencePolicy.select(list, placedAt)), ids(OracleEvidencePolicy.select(list.reversed(), placedAt)))
    }

    @Test
    fun atMostThree_byDefault() {
        val many = (1..10).map { c("a$it", 2_000, it / 10f) }
        val out = OracleEvidencePolicy.select(many, placedAt)
        assertEquals(3, out.size)
        assertEquals(listOf("a10", "a9", "a8"), ids(out))
    }

    @Test
    fun theLimitIsHonoured_andNonPositiveLimitIsEmpty() {
        val many = (1..5).map { c("a$it", 2_000, it / 10f) }
        assertEquals(2, OracleEvidencePolicy.select(many, placedAt, limit = 2).size)
        assertTrue(OracleEvidencePolicy.select(many, placedAt, limit = 0).isEmpty())
        assertTrue(OracleEvidencePolicy.select(many, placedAt, limit = -1).isEmpty())
    }

    @Test
    fun duplicatesOfTheSameArticle_collapseToTheBestScore() {
        val out = OracleEvidencePolicy.select(
            listOf(c("x", 2_000, 0.4f), c("x", 2_000, 0.8f), c("y", 2_000, 0.5f)), placedAt,
        )
        assertEquals(listOf("x", "y"), ids(out))
    }

    @Test
    fun titleAndFeedNameSurvive() {
        val out = OracleEvidencePolicy.select(listOf(c("a", 2_000, 0.5f, title = "Hello", feed = "Wire")), placedAt)
        assertEquals("Hello", out.single().title)
        assertEquals("Wire", out.single().feedName)
        assertEquals(2_000L, out.single().dateMillis)
    }

    @Test
    fun whenNothingIsNewEnough_theResultIsEmpty_notFilledWithOldArticles() {
        val out = OracleEvidencePolicy.select(listOf(c("old1", 10, 0.99f), c("old2", 20, 0.98f)), placedAt)
        assertTrue(out.isEmpty())
    }
}
