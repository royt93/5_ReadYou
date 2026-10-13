package com.mckimquyen.reader.domain.model.rpg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BrainRpgLegacyImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun importsProfileFieldsAndEveryKnownCategory() {
        val prefs = context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 1_250L)
            .putInt(BrainRpgLegacyPreferences.KEY_STREAK_DAYS, 4)
            .putLong(BrainRpgLegacyPreferences.KEY_LAST_READ_EPOCH_DAY, 20L)
            .putBoolean(BrainRpgLegacyPreferences.KEY_STREAK_SHIELD_ACTIVE, true)
            .putInt(BrainRpgLegacyPreferences.KEY_QUIZZES_ATTEMPTED, 10)
            .putInt(BrainRpgLegacyPreferences.KEY_QUIZZES_PASSED, 7)
            .putLong(BrainRpgLegacyPreferences.categoryKey("tech"), 111L)
            .putLong(BrainRpgLegacyPreferences.categoryKey("business"), 222L)
            .putLong(BrainRpgLegacyPreferences.categoryKey("science"), 333L)
            .putLong(BrainRpgLegacyPreferences.categoryKey("health"), 444L)
            .putLong(BrainRpgLegacyPreferences.categoryKey("philosophy"), 555L)
            .putLong(BrainRpgLegacyPreferences.categoryKey("general"), 666L)
            .commit()

        val imported = BrainRpgLegacyPreferences.read(prefs)

        assertEquals(1_250L, imported.profile.baseTotalXp)
        assertEquals(4, imported.profile.streakDays)
        assertEquals(20L, imported.profile.lastReadEpochDay)
        assertTrue(imported.profile.streakShieldActive)
        assertEquals(10, imported.profile.quizzesAttempted)
        assertEquals(7, imported.profile.quizzesPassed)
        assertEquals(
            mapOf("tech" to 111L, "business" to 222L, "science" to 333L, "health" to 444L, "philosophy" to 555L, "general" to 666L),
            imported.categoryBaseXp,
        )
    }

    @Test
    fun missingValuesUseTheExistingBrainRpgDefaults() {
        val imported = BrainRpgLegacyPreferences.read(
            context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE),
        )

        assertEquals(0L, imported.profile.baseTotalXp)
        assertEquals(1, imported.profile.streakDays)
        assertEquals(0L, imported.profile.lastReadEpochDay)
        assertEquals(false, imported.profile.streakShieldActive)
        assertEquals(0, imported.profile.quizzesAttempted)
        assertEquals(0, imported.profile.quizzesPassed)
        assertTrue(imported.categoryBaseXp.isEmpty())
    }

    @Test
    fun unknownCategoryPrefixIsPreservedInsteadOfDiscardingEarnedXp() {
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(BrainRpgLegacyPreferences.categoryKey("legacy_future_category"), 987L).commit()

        val imported = BrainRpgLegacyPreferences.read(
            context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE),
        )

        assertEquals(987L, imported.categoryBaseXp["legacy_future_category"])
    }

    @Test
    fun importsAllThreeLegacyRewardSets() {
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(BrainRpgLegacyPreferences.KEY_READ_REWARDED, setOf("a1", "a2"))
            .putStringSet(BrainRpgLegacyPreferences.KEY_QUIZ_REWARDED, setOf("a2"))
            .putStringSet(BrainRpgLegacyPreferences.KEY_DOUBLE_REWARDED, setOf("a2"))
            .commit()

        val imported = BrainRpgLegacyPreferences.read(
            context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE),
        )

        assertEquals(setOf(BrainRpgLegacyReward("a1", BrainRpgRewardType.READ), BrainRpgLegacyReward("a2", BrainRpgRewardType.READ)), imported.rewardLedger.filter { it.rewardType == BrainRpgRewardType.READ }.toSet())
        assertEquals(setOf(BrainRpgLegacyReward("a2", BrainRpgRewardType.QUIZ)), imported.rewardLedger.filter { it.rewardType == BrainRpgRewardType.QUIZ }.toSet())
        assertEquals(setOf(BrainRpgLegacyReward("a2", BrainRpgRewardType.DOUBLE)), imported.rewardLedger.filter { it.rewardType == BrainRpgRewardType.DOUBLE }.toSet())
    }
}
