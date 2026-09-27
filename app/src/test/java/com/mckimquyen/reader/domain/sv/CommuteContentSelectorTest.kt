package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class CommuteContentSelectorTest {

    private lateinit var selector: CommuteContentSelector
    private val now = Date(1700000000000L) // Fixed reference time

    @Before
    fun setUp() {
        selector = CommuteContentSelector()
    }

    private fun createArticle(
        id: String,
        feedId: String = "feed_default",
        hoursAgo: Long = 0,
        isStarred: Boolean = false,
        isReadLater: Boolean = false,
        title: String = "Sample Title for Article $id",
        description: String = "Short summary of the article content here.",
        aiSummary: String? = null,
    ): Article {
        val articleDate = Date(now.time - hoursAgo * 3600 * 1000)
        return Article(
            id = id,
            date = articleDate,
            title = title,
            rawDescription = description,
            shortDescription = description,
            link = "https://example.com/$id",
            feedId = feedId,
            accountId = 1,
            isStarred = isStarred,
            isReadLater = isReadLater,
            aiSummary = aiSummary
        )
    }

    @Test
    fun calculateInteractionScore_starredArticle_scoresHigherThanUnstarred() {
        val unstarred = createArticle(id = "1", hoursAgo = 2, isStarred = false)
        val starred = createArticle(id = "2", hoursAgo = 2, isStarred = true)

        val unstarredScore = selector.calculateInteractionScore(unstarred, now)
        val starredScore = selector.calculateInteractionScore(starred, now)

        assertTrue(
            "Starred article should have a higher score ($starredScore > $unstarredScore)",
            starredScore > unstarredScore
        )
        assertEquals(50f, starredScore - unstarredScore, 0.01f)
    }

    @Test
    fun calculateInteractionScore_readLater_scoresHigherThanRegular() {
        val regular = createArticle(id = "1", hoursAgo = 4, isReadLater = false)
        val readLater = createArticle(id = "2", hoursAgo = 4, isReadLater = true)

        val regularScore = selector.calculateInteractionScore(regular, now)
        val readLaterScore = selector.calculateInteractionScore(readLater, now)

        assertTrue(
            "Read later article should have a higher score ($readLaterScore > $regularScore)",
            readLaterScore > regularScore
        )
        assertEquals(30f, readLaterScore - regularScore, 0.01f)
    }

    @Test
    fun calculateInteractionScore_freshArticle_scoresHigherThanOldArticle() {
        val fresh = createArticle(id = "fresh", hoursAgo = 1)
        val old = createArticle(id = "old", hoursAgo = 24)

        val freshScore = selector.calculateInteractionScore(fresh, now)
        val oldScore = selector.calculateInteractionScore(old, now)

        assertTrue(
            "Fresh article should score higher than 24h old article ($freshScore > $oldScore)",
            freshScore > oldScore
        )
    }

    @Test
    fun estimateSpokenDurationSeconds_boundsDurationCorrectly() {
        val shortArticle = createArticle(id = "short", title = "Hi", description = "")
        val shortDuration = selector.estimateSpokenDurationSeconds(shortArticle)
        assertEquals(
            "Duration should be bounded to minimum 20 seconds",
            CommuteContentSelector.MIN_ARTICLE_DURATION_SECONDS,
            shortDuration
        )

        val mediumText = "word ".repeat(40)
        val mediumArticle = createArticle(id = "medium", title = "Long", description = mediumText)
        val mediumDuration = selector.estimateSpokenDurationSeconds(mediumArticle)
        assertTrue(
            "Content-rich article should exceed the minimum duration",
            mediumDuration > CommuteContentSelector.MIN_ARTICLE_DURATION_SECONDS
        )
        assertTrue(
            "Duration must stay within the maximum bound",
            mediumDuration <= CommuteContentSelector.MAX_ARTICLE_DURATION_SECONDS
        )
    }

    @Test
    fun selectArticles_respectsTimeBudget() {
        // Create 20 candidate articles
        val candidates = (1..20).map { i ->
            createArticle(
                id = "art_$i",
                feedId = "feed_${i % 4}",
                hoursAgo = i.toLong(),
                description = "Word ".repeat(40) // ~30s each
            )
        }

        val selectedStandard = selector.selectArticles(candidates, targetMinutes = 4, now = now)
        // 4 minutes = 240s. Available = 205s. Each ~30s -> approx 5-7 articles
        assertTrue("Standard 4-min budget should select between 4 and 8 articles", selectedStandard.size in 4..8)

        val selectedDeepDive = selector.selectArticles(candidates, targetMinutes = 15, now = now)
        // 15 minutes should select more articles
        assertTrue(
            "Deep Dive 15-min should select more articles than standard (${selectedDeepDive.size} > ${selectedStandard.size})",
            selectedDeepDive.size > selectedStandard.size
        )
    }

    @Test
    fun selectArticles_interleavesFeedsWhenSpammed() {
        // Feed 1 has 10 very fresh articles (spammed)
        // Feed 2 has 5 slightly older articles
        val spammedFeed1 = (1..10).map { i ->
            createArticle(
                id = "f1_$i",
                feedId = "feed_spammed",
                hoursAgo = 1,
                title = "Spam news $i"
            )
        }
        val diverseFeed2 = (1..5).map { i ->
            createArticle(
                id = "f2_$i",
                feedId = "feed_other",
                hoursAgo = 2,
                title = "Quality news $i"
            )
        }

        val allCandidates = spammedFeed1 + diverseFeed2
        val selected = selector.selectArticles(allCandidates, targetMinutes = 4, maxConsecutivePerFeed = 2, now = now)

        // Verify that no more than 2 consecutive articles come from feed_spammed
        var consecutive = 0
        var lastFeed = ""
        for (article in selected) {
            if (article.feedId == lastFeed) {
                consecutive++
                assertTrue("Consecutive articles from same feed must be <= 2", consecutive <= 2)
            } else {
                lastFeed = article.feedId
                consecutive = 1
            }
        }
        // Verify feed_other was chosen to interleave
        assertTrue("Must include articles from other feed", selected.any { it.feedId == "feed_other" })
    }

    @Test
    fun selectArticles_fewArticles_returnsAllWithoutCrashing() {
        val emptyList = emptyList<Article>()
        val selectedEmpty = selector.selectArticles(emptyList, targetMinutes = 4, now = now)
        assertTrue("Empty candidates should return empty list", selectedEmpty.isEmpty())

        val singleArticle = listOf(createArticle(id = "single"))
        val selectedSingle = selector.selectArticles(singleArticle, targetMinutes = 4, now = now)
        assertEquals("Single article candidates should return 1 article", 1, selectedSingle.size)
        assertEquals("single", selectedSingle.first().id)

        val twoArticles = listOf(createArticle(id = "1"), createArticle(id = "2"))
        val selectedTwo = selector.selectArticles(twoArticles, targetMinutes = 4, now = now)
        assertEquals("Two articles should return both", 2, selectedTwo.size)
    }
}
