package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/**
 * The cut that keeps an archived old article from coming back is by date, not by position: position hid the entries after a pinned old one for good.
 * AbstractRssRepository is abstract, so the rule lives in a pure function that is reached here through a tiny subclass.
 */
class OnlyNotOlderThanStoredTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val feed = Feed(id = "f", name = "F", url = "https://e/f", groupId = "g", accountId = 1)

    private fun a(n: Int, daysAgo: Long) = Article(
        id = "$n", date = Date(now - daysAgo * day), title = "t$n", rawDescription = "", shortDescription = "", link = "https://e/$n", feedId = "f", accountId = 1,
    )

    private fun cut(latest: Article?, fetched: List<Article>, feed: Feed = this.feed) = RssCut.onlyNotOlderThanStored(feed, latest, fetched, now)

    @Test fun aFeedThatPinsAnOldEntryOnTopStillGivesTheNewOnesAfterIt() {
        val stored = a(0, daysAgo = 5)
        val fetched = listOf(a(1, daysAgo = 400), a(2, daysAgo = 1), a(3, daysAgo = 2))
        assertEquals(listOf("2", "3"), cut(stored, fetched).map { it.id })
    }

    @Test fun anOldArticleTheUserArchivedAwayDoesNotComeBack() {
        val stored = a(0, daysAgo = 1)
        assertEquals(emptyList<String>(), cut(stored, listOf(a(1, daysAgo = 30), a(2, daysAgo = 10))).map { it.id })
    }

    @Test fun anEntryAsNewAsTheNewestStoredOneIsKeptAndTheDatabaseDropsTheDuplicateLink() {
        val stored = a(0, daysAgo = 3)
        assertEquals(listOf("9"), cut(stored, listOf(a(9, daysAgo = 3))).map { it.id })
    }

    @Test fun aStoredDateInTheFutureIsCappedAtNowSoItCannotHideEveryNewEntry() {
        val stored = a(0, daysAgo = -365) // a wrong pubDate one year ahead
        assertEquals(listOf("1"), cut(stored, listOf(a(1, daysAgo = 0), a(2, daysAgo = 2))).map { it.id })
    }

    @Test fun nothingStoredYetKeepsEverything() {
        assertEquals(listOf("1", "2"), cut(null, listOf(a(1, 50), a(2, 400))).map { it.id })
    }

    @Test fun nonRssSourcesAreNotCut() {
        val web = feed.copy(sourceType = Feed.WEB_SOURCE_TYPE)
        assertEquals(listOf("1"), cut(a(0, 1), listOf(a(1, 50)), web).map { it.id })
    }
}
