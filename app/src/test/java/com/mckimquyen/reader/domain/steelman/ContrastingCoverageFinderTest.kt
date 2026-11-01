package com.mckimquyen.reader.domain.steelman

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ContrastingCoverageFinderTest {

    private fun item(
        id: String,
        title: String,
        feedId: String = "f1",
        feedName: String = "Feed 1",
        short: String = "",
        date: Long = 1_000L,
    ) = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id, date = Date(date), title = title, author = null, shortDescription = short, img = null,
            link = "https://e.com/$id", feedId = feedId, accountId = 1, isUnread = true, isStarred = false,
            isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
            audioMime = null, audioDurationSec = null,
        ),
        feed = Feed(id = feedId, name = feedName, url = "https://e.com", groupId = "g", accountId = 1),
    )

    @Test
    fun returnsOtherFeedArticles_withTopicAndContrarianCue_newestFirst() {
        val candidates = listOf(
            item("1", "Starlink expands rapidly across Europe", feedId = "f2", feedName = "Wired", date = 2_000L), // no cue
            item("2", "Astronomers warn Starlink satellites disrupt night sky", feedId = "f2", feedName = "Wired", date = 3_000L),
            item("3", "Government warns of Starlink monopoly risks", feedId = "f3", feedName = "Reuters", date = 4_000L),
            item("4", "Military warns of Starlink reliance", feedId = "f1", feedName = "BBC", date = 5_000L), // same feed
        )

        val results = ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates)

        assertEquals(listOf("3", "2"), results.map { it.articleId })
        assertEquals("warns", results[0].cue)
        assertEquals("Reuters", results[0].feedName)
        assertEquals("warn", results[1].cue)
    }

    @Test
    fun excludesTheCurrentArticle() {
        val candidates = listOf(item("curr", "Critics warn of Starlink flaws", feedId = "f2"))
        assertTrue(ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates).isEmpty())
    }

    @Test
    fun excludesCandidatesWithoutThePhrase() {
        val candidates = listOf(item("1", "Critics warn of satellite internet risks", feedId = "f2"))
        assertTrue(ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates).isEmpty())
    }

    @Test
    fun phraseMustMatchAsAWholePhrase_notAsASubstring() {
        val candidates = listOf(item("1", "Critics warn of Starlinks everywhere", feedId = "f2"))
        assertTrue(ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates).isEmpty())
    }

    @Test
    fun capsAtMaxResults_newestFirst() {
        val candidates = (1..10).map { i ->
            item("a$i", "Critics warn about Starlink issue $i", feedId = "feed_$i", feedName = "Source $i", date = i * 1_000L)
        }
        val results = ContrastingCoverageFinder.find("curr", "f0", "Starlink", candidates)
        assertEquals(ContrastingCoverageFinder.MAX_RESULTS, results.size)
        assertEquals("a10", results.first().articleId)
    }

    @Test
    fun phraseInShortDescription_countsWhenTheTitleHasTheCue() {
        val candidates = listOf(
            item("1", "Experts warn of new orbital danger", feedId = "f2", short = "Concerns grow over Starlink constellation"),
        )
        val results = ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates)
        assertEquals(listOf("1"), results.map { it.articleId })
    }

    @Test
    fun cueInTheDescriptionOnly_doesNotCount() {
        val candidates = listOf(
            item("1", "Starlink launches new batch", feedId = "f2", short = "Critics warn about the risks"),
        )
        assertTrue(ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates).isEmpty())
    }

    @Test
    fun blankPhraseOrNoCandidates_returnEmpty() {
        assertTrue(ContrastingCoverageFinder.find("curr", "f1", "  ", listOf(item("1", "Critics warn", feedId = "f2"))).isEmpty())
        assertTrue(ContrastingCoverageFinder.find("curr", "f1", "Starlink", emptyList()).isEmpty())
    }
}
