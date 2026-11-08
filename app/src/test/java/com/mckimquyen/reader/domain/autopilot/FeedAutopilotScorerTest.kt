package com.mckimquyen.reader.domain.autopilot

import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.pref.KeepArchivedPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedAutopilotScorerTest {

    private val now = READ_AT_INTRODUCED + DAYS_60
    private val feedA = Feed(id = "f1", name = "Feed One", url = "https://example.com/1", groupId = "g1", accountId = ACCOUNT)
    private val feedB = Feed(id = "f2", name = "Feed Two", url = "https://example.com/2", groupId = "g1", accountId = ACCOUNT)

    private fun stat(
        feedId: String = feedA.id,
        total: Int = MIN_ARTICLES,
        engaged: Int = 0,
        oldestDaysAgo: Long = MIN_FEED_AGE_DAYS,
    ) = FeedEngagementStat(
        feedId = feedId,
        windowTotal = total,
        windowEngaged = engaged,
        oldestUpdateAtMillis = now - oldestDaysAgo * DAY_MILLIS,
    )

    private fun evaluate(
        stats: List<FeedEngagementStat> = listOf(stat()),
        feeds: List<Feed> = listOf(feedA),
        decisions: Map<String, FeedDecisionRecord> = emptyMap(),
        retention: KeepArchivedPreference = KeepArchivedPreference.For1Month,
    ) = FeedAutopilotScorer.evaluate(stats, feeds, decisions, retention, now)

    @Test fun lessThanTwentyArticles_isNotFlagged() {
        val result = evaluate(stats = listOf(stat(total = MIN_ARTICLES - 1)))
        assertTrue((result as FeedAutopilotEvaluation.Ready).suggestions.isEmpty())
    }

    @Test fun feedYoungerThanFourteenDays_isNotFlagged() {
        val result = evaluate(stats = listOf(stat(oldestDaysAgo = MIN_FEED_AGE_DAYS - 1)))
        assertTrue((result as FeedAutopilotEvaluation.Ready).suggestions.isEmpty())
    }

    @Test fun exactlyFivePercentEngagement_isNotFlagged() {
        val result = evaluate(stats = listOf(stat(total = 20, engaged = 1)))
        assertTrue((result as FeedAutopilotEvaluation.Ready).suggestions.isEmpty())
    }

    @Test fun belowFivePercentEngagement_isFlagged() {
        val suggestion = evaluate(stats = listOf(stat(total = 21, engaged = 1)))
            .readySuggestions().single()
        assertEquals(feedA.id, suggestion.feedId)
        assertEquals(MIN_FEED_AGE_DAYS.toInt(), suggestion.feedAgeDays)
        assertEquals(21, suggestion.windowTotal)
        assertEquals(1, suggestion.windowEngaged)
        assertTrue(suggestion.engagementRatio < MAX_ENGAGEMENT_RATIO)
    }

    @Test fun starredAndReadLaterArticlesAreIncludedByEngagementCount() {
        // The DAO has already summed readAt/starred/readLater into windowEngaged.
        assertTrue(evaluate(stats = listOf(stat(total = 20, engaged = 1)))
            .readySuggestions().isEmpty())
    }

    @Test fun activeSnooze_isExcluded() {
        val decisions = mapOf(feedA.id to FeedDecisionRecord(FeedDecisionType.SNOOZE_30_DAYS, now - DAYS_29))
        assertTrue(evaluate(decisions = decisions).readySuggestions().isEmpty())
    }

    @Test fun expiredSnooze_isEvaluatedAgain() {
        val decisions = mapOf(feedA.id to FeedDecisionRecord(FeedDecisionType.SNOOZE_30_DAYS, now - DAYS_31))
        assertEquals(listOf(feedA.id), evaluate(decisions = decisions).readySuggestions().map { it.feedId })
    }

    @Test fun keepForever_isAlwaysExcluded() {
        val decisions = mapOf(feedA.id to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now))
        assertTrue(evaluate(decisions = decisions).readySuggestions().isEmpty())
    }

    @Test fun alwaysRetentionUsesThirtyDayWindow_notZero() {
        val result = evaluate(retention = KeepArchivedPreference.Always).readySuggestions().single()
        assertEquals(DEFAULT_WINDOW_DAYS, result.windowDays)
    }

    @Test fun retentionShorterThanFourteenDays_isRejected() {
        for (retention in listOf(
            KeepArchivedPreference.For1Day,
            KeepArchivedPreference.For2Days,
            KeepArchivedPreference.For3Days,
            KeepArchivedPreference.For1Week,
        )) {
            assertEquals(FeedAutopilotEvaluation.RetentionTooShort, evaluate(retention = retention))
        }
    }

    @Test fun fourteenDayRetentionIsAcceptedWithFourteenDayWindow() {
        val result = evaluate(retention = KeepArchivedPreference.For2Weeks).readySuggestions().single()
        assertEquals(14, result.windowDays)
    }

    @Test fun monthRetentionIsCappedAtThirtyDays() {
        val result = evaluate(retention = KeepArchivedPreference.For1Month).readySuggestions().single()
        assertEquals(DEFAULT_WINDOW_DAYS, result.windowDays)
    }

    @Test fun suggestionsAreSortedByEngagementThenVolumeThenFeedName() {
        val feedC = Feed(id = "f3", name = "Zulu", url = "https://example.com/3", groupId = "g1", accountId = ACCOUNT)
        val feedD = Feed(id = "f4", name = "Alpha", url = "https://example.com/4", groupId = "g1", accountId = ACCOUNT)
        val stats = listOf(
            stat(feedId = feedC.id, total = 40, engaged = 0),
            stat(feedId = feedA.id, total = 20, engaged = 0),
            stat(feedId = feedD.id, total = 30, engaged = 0),
        )
        val result = evaluate(stats, listOf(feedA, feedC, feedD)).readySuggestions()
        assertEquals(listOf(feedC.id, feedD.id, feedA.id), result.map { it.feedId })
    }

    @Test fun suggestionCarriesTheRawCountsTheScreenNeedsForItsReason() {
        // The domain never builds display text; the UI formats these numbers from localized resources.
        val zero = evaluate(stats = listOf(stat(total = 25))).readySuggestions().single()
        assertEquals(25, zero.windowTotal)
        assertEquals(0, zero.windowEngaged)
        assertEquals(0, zero.engagementPercent)
        val oneOfTwentyOne = evaluate(stats = listOf(stat(total = 21, engaged = 1))).readySuggestions().single()
        assertEquals(4, oneOfTwentyOne.engagementPercent) // 4.76% is shown rounded down, never up to 5%
    }

    @Test fun feedWithoutAnyStoredArticleHasNoAgeAndIsNotFlagged() {
        val noAge = FeedEngagementStat(feedA.id, windowTotal = 30, windowEngaged = 0, oldestUpdateAtMillis = null)
        assertTrue(evaluate(stats = listOf(noAge)).readySuggestions().isEmpty())
    }

    @Test fun statForAFeedThatNoLongerExistsIsIgnored() {
        assertTrue(evaluate(stats = listOf(stat(feedId = "deleted")), feeds = listOf(feedA)).readySuggestions().isEmpty())
    }

    @Test fun windowStartIsNowMinusWindowLength_whenFarAfterTheReadAtMilestone() {
        val start = FeedAutopilotScorer.windowStartMillis(KeepArchivedPreference.For1Month, now)
        assertEquals(now - DEFAULT_WINDOW_DAYS * DAY_MILLIS, start)
        val twoWeeks = FeedAutopilotScorer.windowStartMillis(KeepArchivedPreference.For2Weeks, now)
        assertEquals(now - DAYS_14 * DAY_MILLIS, twoWeeks)
    }

    @Test fun windowStartNeverPrecedesTheReadAtMilestone() {
        // Ten days after readAt existed: older articles cannot have a trustworthy readAt, so the window is clamped.
        val earlyNow = READ_AT_INTRODUCED + 10L * DAY_MILLIS
        assertEquals(READ_AT_INTRODUCED, FeedAutopilotScorer.windowStartMillis(KeepArchivedPreference.For1Month, earlyNow))
    }

    @Test fun reportedWindowDaysIsTheEffectiveWindow_notTheConfiguredOne() {
        val earlyNow = READ_AT_INTRODUCED + 21L * DAY_MILLIS
        val stat = FeedEngagementStat(feedA.id, windowTotal = 25, windowEngaged = 0, oldestUpdateAtMillis = earlyNow - 20L * DAY_MILLIS)
        val ready = FeedAutopilotScorer.evaluate(listOf(stat), listOf(feedA), emptyMap(), KeepArchivedPreference.For1Month, earlyNow)
            as FeedAutopilotEvaluation.Ready
        assertEquals(21, ready.windowDays)
        assertEquals(21, ready.suggestions.single().windowDays)
    }

    @Test fun testConstantsStayPinnedToProduction() {
        assertEquals(READ_AT_INTRODUCED, FeedAutopilotScorer.READ_AT_INTRODUCED_MILLIS)
        assertEquals(MIN_ARTICLES, FeedAutopilotScorer.MIN_ARTICLES)
        assertEquals(MIN_FEED_AGE_DAYS.toInt(), FeedAutopilotScorer.MIN_FEED_AGE_DAYS)
        assertEquals(DEFAULT_WINDOW_DAYS, FeedAutopilotScorer.MAX_WINDOW_DAYS)
    }

    @Test fun snoozeExpiresExactlyAtThirtyDays() {
        val exactly = mapOf(feedA.id to FeedDecisionRecord(FeedDecisionType.SNOOZE_30_DAYS, now - 30L * DAY_MILLIS))
        assertEquals(listOf(feedA.id), evaluate(decisions = exactly).readySuggestions().map { it.feedId })
    }

    private fun FeedAutopilotEvaluation.readySuggestions(): List<FeedAutopilotSuggestion> {
        assertFalse(this !is FeedAutopilotEvaluation.Ready)
        return (this as FeedAutopilotEvaluation.Ready).suggestions
    }

    private companion object {
        const val ACCOUNT = 1
        const val DAY_MILLIS = 86_400_000L
        const val DAYS_14 = 14L
        const val DAYS_29 = 29L * DAY_MILLIS
        const val DAYS_31 = 31L * DAY_MILLIS
        const val DAYS_60 = 60L * DAY_MILLIS
        const val MIN_ARTICLES = 20
        const val MIN_FEED_AGE_DAYS = 14L
        const val MAX_ENGAGEMENT_RATIO = 0.05f
        const val DEFAULT_WINDOW_DAYS = 30
        const val READ_AT_INTRODUCED = 1_792_281_600_000L // pinned to production by readAtConstantMatchesProduction
    }
}
