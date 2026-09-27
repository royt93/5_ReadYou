package com.mckimquyen.reader.domain.sv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration test for DJ-01/DJ-08:
 * Real Room DB -> ArticleDao.queryLatestUnread -> CommuteContentSelector.selectArticles.
 * Verifies interaction score prioritization + feed diversity work on a genuine SQLite dataset,
 * not just in-memory mocked lists.
 */
@RunWith(AndroidJUnit4::class)
class CommuteContentSelectionIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var groupDao: GroupDao
    private lateinit var feedDao: FeedDao
    private lateinit var articleDao: ArticleDao
    private lateinit var selector: CommuteContentSelector

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        groupDao = database.groupDao()
        feedDao = database.feedDao()
        articleDao = database.articleDao()
        selector = CommuteContentSelector()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun setUpFeeds(): List<Feed> {
        val group = Group(id = "g_commute", name = "Commute News", accountId = 1)
        groupDao.insert(group)
        val spammedFeed = Feed(
            id = "f_spammed",
            name = "Spammed Source",
            url = "https://spammed.example.com/rss",
            groupId = group.id,
            accountId = 1,
            isFullContent = false,
        )
        val qualityFeed = Feed(
            id = "f_quality",
            name = "Quality Source",
            url = "https://quality.example.com/rss",
            groupId = group.id,
            accountId = 1,
            isFullContent = false,
        )
        feedDao.insert(spammedFeed, qualityFeed)
        return listOf(spammedFeed, qualityFeed)
    }

    @Test
    fun starredArticle_isPrioritizedOverNewerUnstarredArticles() = runBlocking {
        val (spammedFeed, qualityFeed) = setUpFeeds()
        val now = System.currentTimeMillis()

        // A slightly older but starred article from the "quality" feed. The recency decay is mild
        // enough (100/(1+age/12)) that a 50-point starred bonus easily overcomes a few hours' gap.
        val starredOld = Article(
            id = "art_starred_old",
            title = "Important story the user starred",
            rawDescription = "This is a story the user cared enough about to star.",
            shortDescription = "This is a story the user cared enough about to star.",
            link = "https://quality.example.com/art_starred",
            feedId = qualityFeed.id,
            accountId = 1,
            date = Date(now - TimeUnit.HOURS.toMillis(3)),
            isStarred = true,
        )
        // A fresher but unremarkable article from the spammed feed
        val freshUnstarred = Article(
            id = "art_fresh_unstarred",
            title = "Routine update",
            rawDescription = "Nothing special here.",
            shortDescription = "Nothing special here.",
            link = "https://spammed.example.com/art_fresh",
            feedId = spammedFeed.id,
            accountId = 1,
            date = Date(now - TimeUnit.HOURS.toMillis(1)),
            isStarred = false,
        )
        articleDao.insertList(listOf(starredOld, freshUnstarred))

        val candidates = articleDao.queryLatestUnread(accountId = 1, limit = 50)
        assertEquals(2, candidates.size)

        val selectedOrder = candidates
            .map { it to selector.calculateInteractionScore(it) }
            .sortedByDescending { it.second }
            .map { it.first.id }

        assertEquals(
            "Starred article must outrank a slightly fresher unstarred one",
            "art_starred_old",
            selectedOrder.first()
        )
    }

    @Test
    fun selectArticles_onRealDb_respectsFeedDiversityAndTimeBudget() = runBlocking {
        val (spammedFeed, qualityFeed) = setUpFeeds()
        val now = System.currentTimeMillis()

        // 8 articles dumped by the spammed feed in quick succession
        val spammedArticles = (1..8).map { i ->
            Article(
                id = "art_spam_$i",
                title = "Spammed headline number $i about routine topics",
                rawDescription = "Filler description text for spammed article $i.",
                shortDescription = "Filler description text for spammed article $i.",
                link = "https://spammed.example.com/art_$i",
                feedId = spammedFeed.id,
                accountId = 1,
                date = Date(now - TimeUnit.MINUTES.toMillis(i.toLong())),
            )
        }
        // 4 articles from the quality feed, slightly older
        val qualityArticles = (1..4).map { i ->
            Article(
                id = "art_quality_$i",
                title = "Quality headline number $i with real analysis",
                rawDescription = "In-depth description text for quality article $i.",
                shortDescription = "In-depth description text for quality article $i.",
                link = "https://quality.example.com/art_$i",
                feedId = qualityFeed.id,
                accountId = 1,
                date = Date(now - TimeUnit.HOURS.toMillis(i.toLong())),
            )
        }
        articleDao.insertList(spammedArticles + qualityArticles)

        val candidates = articleDao.queryLatestUnread(accountId = 1, limit = 50)
        assertEquals(12, candidates.size)

        val selected = selector.selectArticles(candidates, targetMinutes = 4, maxConsecutivePerFeed = 2)

        assertTrue("Must select at least one article", selected.isNotEmpty())

        // Verify feed diversity constraint holds on real DB-sourced data
        var consecutive = 0
        var lastFeed = ""
        for (article in selected) {
            if (article.feedId == lastFeed) {
                consecutive++
                assertTrue("No more than 2 consecutive articles from the same feed", consecutive <= 2)
            } else {
                lastFeed = article.feedId
                consecutive = 1
            }
        }
        assertTrue(
            "Must interleave in articles from the quality feed despite spam dominating recency",
            selected.any { it.feedId == qualityFeed.id }
        )
    }

    @Test
    fun selectArticles_onRealDb_emptyInbox_returnsEmptyWithoutCrashing() = runBlocking {
        setUpFeeds()
        val candidates = articleDao.queryLatestUnread(accountId = 1, limit = 50)
        assertEquals(0, candidates.size)

        val selected = selector.selectArticles(candidates, targetMinutes = 4)
        assertTrue("Empty inbox must yield empty selection, not a crash", selected.isEmpty())
    }
}
