package com.mckimquyen.reader.infrastructure.autopilot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotEvaluation
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotScorer
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.pref.KeepArchivedPreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class FeedAutopilotDeviceTest {

    private lateinit var db: AndroidDatabase

    @Before fun setUp() {
        // Prevent known class-init cycle in KeepArchivedPreference before Room hits Account
        KeepArchivedPreference.values
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun liveDatabaseRunsAggregationAndFlagsNoisyFeed() = runBlocking {
        val now = 1_794_052_800_000L // 2026-11-08T00:00:00Z
        val day = 86_400_000L

        db.accountDao().insert(Account(id = ACCOUNT, name = "Local", type = AccountType.Local))
        db.groupDao().insert(Group(id = "grp", name = "Group", accountId = ACCOUNT))
        val feed = Feed(id = "feed_noisy", name = "Noisy Feed", url = "https://example.com/rss", groupId = "grp", accountId = ACCOUNT)
        db.feedDao().insert(feed)

        // Seed 25 articles, all in the last 20 days (well within 30-day window), feed age 20 days, none read
        for (i in 1..25) {
            val articleTime = now - (20L - (i % 5)) * day
            db.articleDao().insert(
                Article(
                    id = "art_$i",
                    title = "Article $i",
                    date = Date(articleTime),
                    rawDescription = "desc",
                    fullContent = "content",
                    shortDescription = "short",
                    link = "https://example.com/art_$i",
                    feedId = feed.id,
                    accountId = ACCOUNT,
                    isUnread = true,
                    isStarred = (i == 1), // 1 article starred, used to verify queryStarredCountByFeedId
                    isReadLater = false,
                    updateAt = Date(articleTime),
                    readAt = null,
                ),
            )
        }

        // Verify starred count query
        val starredCount = db.articleDao().queryStarredCountByFeedId(ACCOUNT, feed.id)
        assertEquals(1, starredCount)

        // Aggregate on live Android SQLite
        val since = FeedAutopilotScorer.windowStartMillis(KeepArchivedPreference.For1Month, now)
        val stats = db.articleDao().queryFeedEngagementStats(ACCOUNT, since)
        assertEquals(1, stats.size)
        assertEquals(25, stats[0].windowTotal)
        assertEquals(1, stats[0].windowEngaged) // only the 1 starred article is engaged (4% < 5%)

        val evaluation = FeedAutopilotScorer.evaluate(
            stats = stats,
            feeds = listOf(feed),
            decisions = emptyMap(),
            keepArchived = KeepArchivedPreference.For1Month,
            nowMillis = now,
        )

        assertTrue(evaluation is FeedAutopilotEvaluation.Ready)
        val ready = evaluation as FeedAutopilotEvaluation.Ready
        assertEquals(1, ready.suggestions.size)
        assertEquals("feed_noisy", ready.suggestions[0].feedId)
        assertEquals(4, ready.suggestions[0].engagementPercent)

        // Short retention returns RetentionTooShort on device
        val shortEval = FeedAutopilotScorer.evaluate(
            stats = stats,
            feeds = listOf(feed),
            decisions = emptyMap(),
            keepArchived = KeepArchivedPreference.For1Week,
            nowMillis = now,
        )
        assertEquals(FeedAutopilotEvaluation.RetentionTooShort, shortEval)
    }

    private companion object {
        const val ACCOUNT = 1
    }
}
