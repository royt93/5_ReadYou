package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRpgDecayNotificationPolicyTest {
    @Test fun beforeFirstSevenDayBoundary_doesNotNotifyOrCancel() {
        val result = BrainRpgDecayNotificationPolicy.evaluate(listOf(CategoryDecayState("tech", 100L, 0L, 0L)), 6L * DAY)
        assertFalse(result.shouldNotify)
        assertTrue("existing summary should be cleared before first decay week", result.shouldCancel)
        assertTrue(result.decaying.isEmpty())
    }

    @Test fun firstDecayDay_notifiesAllDecayingCategoriesButNotFreshCategories() {
        val result = BrainRpgDecayNotificationPolicy.evaluate(
            listOf(
                CategoryDecayState("tech", 100L, 0L, 0L),
                CategoryDecayState("science", 100L, DAY, 0L),
                CategoryDecayState("empty", 0L, 0L, 0L),
            ),
            8L * DAY,
        )
        assertTrue(result.shouldNotify)
        assertEquals(listOf("science" to 1, "tech" to 1), result.decaying.map { it.categoryId to it.weeks })
    }

    @Test fun atMostOneSummaryPerEpochDay() {
        val row = CategoryDecayState("tech", 100L, 0L, 8L)
        assertFalse(BrainRpgDecayNotificationPolicy.evaluate(listOf(row), 8L * DAY + 1L).shouldNotify)
        assertTrue(BrainRpgDecayNotificationPolicy.evaluate(listOf(row), 9L * DAY).shouldNotify)
    }

    @Test fun whenNothingDecays_cancelExistingSummary() {
        assertTrue(BrainRpgDecayNotificationPolicy.evaluate(emptyList(), 8L * DAY).shouldCancel)
        assertTrue(BrainRpgDecayNotificationPolicy.evaluate(listOf(CategoryDecayState("tech", 100L, 7L * DAY, 0L)), 8L * DAY).shouldCancel)
    }

    @Test fun futurePracticeTimestamp_neverProducesNegativeDecay() {
        val result = BrainRpgDecayNotificationPolicy.evaluate(listOf(CategoryDecayState("future", 100L, 10L * DAY, 0L)), 8L * DAY)
        assertFalse(result.shouldNotify)
        assertTrue(result.shouldCancel)
    }

    private companion object { const val DAY = 86_400_000L }
}
