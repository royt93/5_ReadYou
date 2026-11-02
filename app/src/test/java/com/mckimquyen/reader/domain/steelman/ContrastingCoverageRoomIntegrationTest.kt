package com.mckimquyen.reader.domain.steelman

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.bounty.BountyQuerySanitizer
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ContrastingCoverageRoomIntegrationTest {

    private lateinit var db: AndroidDatabase

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "Local", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g1", name = "Default", accountId = 1))
        db.feedDao().insert(Feed(id = "f_bbc", name = "BBC News", url = "https://e.com/bbc", groupId = "g1", accountId = 1))
        db.feedDao().insert(Feed(id = "f_wired", name = "Wired", url = "https://e.com/wired", groupId = "g1", accountId = 1))
    }

    @After fun tearDown() = db.close()

    private suspend fun insert(id: String, title: String, feedId: String, short: String = "", date: Long = 1_000L) {
        db.articleDao().insert(
            Article(
                id = id, date = Date(date), title = title, author = null, rawDescription = "",
                shortDescription = short, fullContent = "", img = null, link = "https://e.com/$id",
                feedId = feedId, accountId = 1, isUnread = true, isStarred = false, isReadLater = false,
            )
        )
    }

    @Test
    fun queryAndFinder_cooperateToSurfaceOtherSourceWithContrarianCue() = runBlocking {
        insert("cur", "Elon Musk expands Starlink satellite service", "f_bbc", date = 1_000L)
        insert("opp_wired", "Scientists warn Starlink disrupts deep-space research", "f_wired", date = 2_000L)
        insert("opp_same_feed", "Critics warn Starlink costs rise", "f_bbc", date = 3_000L) // same feed: excluded by finder
        insert("unrelated_wired", "Weather service warns of autumn storm", "f_wired", date = 4_000L) // no Starlink

        val candidates = db.articleDao().queryRelatedArticleListWithFeed(
            accountId = 1,
            currentArticleId = "cur",
            escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral("Starlink"),
            limit = ContrastingCoverageFinder.CANDIDATE_LIMIT,
        )
        val contrasting = ContrastingCoverageFinder.find("cur", "f_bbc", "Starlink", candidates)

        assertEquals(1, contrasting.size)
        val match = contrasting.single()
        assertEquals("opp_wired", match.articleId)
        assertEquals("Wired", match.feedName)
        assertEquals("warn", match.cue)
    }

    @Test
    fun queryPagesOtherFeedsAndAppliesTheContrarianFilterBeforeTakingFiveResults() = runBlocking {
        insert("cur", "OpenAI outlines new research plans", "f_bbc", date = 10_000L)
        // First page is deliberately full of same-topic articles without a cue. Old SQL LIMIT 40 prevents the
        // relevant old article from ever reaching the Kotlin cue filter.
        (1..45).forEach { i ->
            insert("no_cue_$i", "OpenAI publishes research update $i", "f_wired", date = 9_000L - i)
        }
        insert("older_relevant", "Critics warn OpenAI safety review is incomplete", "f_wired", date = 1_000L)

        val firstPage = db.articleDao().queryRelatedContrastingArticlePage(
            accountId = 1,
            currentArticleId = "cur",
            currentFeedId = "f_bbc",
            escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral("OpenAI"),
            beforeDate = null,
            beforeId = null,
            limit = ContrastingCoverageFinder.CANDIDATE_LIMIT,
        )
        val firstFiltered = firstPage.filter { ContrarianCueLexicon.find(it.article.title) != null }
        val nextPage = db.articleDao().queryRelatedContrastingArticlePage(
            accountId = 1,
            currentArticleId = "cur",
            currentFeedId = "f_bbc",
            escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral("OpenAI"),
            beforeDate = firstPage.last().article.date.time,
            beforeId = firstPage.last().article.id,
            limit = ContrastingCoverageFinder.CANDIDATE_LIMIT,
        )
        val matching = firstFiltered + nextPage.filter { ContrarianCueLexicon.find(it.article.title) != null }
        val contrasting = ContrastingCoverageFinder.find("cur", "f_bbc", "OpenAI", matching)

        assertEquals(40, firstPage.size)
        assertTrue(firstFiltered.isEmpty())
        assertEquals(6, nextPage.size) // 5 no-cue rows plus the older contradictory headline.
        assertEquals(listOf("older_relevant"), contrasting.map { it.articleId })
        // The lexicon returns the first cue in its own list order ("critics" precedes "warn").
        assertEquals("critics", contrasting.single().cue)
    }

    @Test
    fun queryAndFinder_safelyHandleWildcardCharactersInPhrase() = runBlocking {
        insert("cur", "Company promises 100%_growth this quarter", "f_bbc")
        insert("opp_wired", "Economists warn 100%_growth claim is unrealistic", "f_wired")
        insert("false_hit", "Economists warn 100X_growth claim is unrealistic", "f_wired")

        val candidates = db.articleDao().queryRelatedArticleListWithFeed(
            accountId = 1,
            currentArticleId = "cur",
            escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral("100%_growth"),
            limit = ContrastingCoverageFinder.CANDIDATE_LIMIT,
        )
        val contrasting = ContrastingCoverageFinder.find("cur", "f_bbc", "100%_growth", candidates)

        assertEquals(listOf("opp_wired"), contrasting.map { it.articleId })
    }
}
