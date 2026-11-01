# RPG-03 Streak Rescue Dialog & Cognitive Decay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prompt the user on startup when their reading streak of >= 3 days is broken, offering a rewarded ad to arm the Streak Shield or restart from scratch.

**Architecture:** Pure `StreakRescuePolicy` determines when a broken streak deserves an offer. `BrainRpgRepository` adds mutex-protected `resetBrokenStreak()` and `rescueBrokenStreak()` methods without changing the Room schema. `StreakRescueViewModel` runs on startup, stores the prompt day in DataStore (`DataStoreKeys.StreakRescueLastPromptDay`), delegates the ad to `AdManager.showRewarded`, and exposes UI state to `StreakRescueDialog` inside `HomeEntry`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 1.0.1 (`AlertDialog`), Room v20 (unchanged), DataStore, Hilt, JUnit4, Robolectric, Android Compose instrumentation. No new dependencies.

## Global Constraints

- Branch `dev` only; no worktrees. Never mention, suggest or remind about pull requests.
- Pixel 7 Pro serial `2B051FDH3006MU` is the only device (`adb -s 2B051FDH3006MU ...`). Never use `connectedDevDebugAndroidTest`.
- Smoke MUST back up BOTH `databases/Reader*` AND `files/datastore/settings.preferences_pb` (non-empty, `PRAGMA integrity_check` ok) and restore BOTH with `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local`, verifying with `cmp`. Never delete the datastore on device.
- Standing owner rule: if a full-screen `com.google.android.gms.ads.AdActivity` appears, close it via its labelled control ("Tiếp tục đến ứng dụng"), re-check `topResumedActivity`, never tap ad creative, and report that ads appeared.
- Constants (exact): `MIN_STREAK_TO_OFFER = 3`, `MILLIS_PER_DAY = 86_400_000L` (UTC epoch day, matching existing `BrainRpgRepository.epochDay`).
- Ad mechanism: reuse `com.roy.sdkadbmob.AdManager.showRewarded(activity) { earned -> }`; do not add new ad formats, networks or payment code.
- Room DB stays at version 20; no migration, no new table. `BrainRpgProfileEntity` already has `streakDays`, `lastReadEpochDay` and `streakShieldActive`.
- Compose pitfalls: `by` needs `androidx.compose.runtime.getValue` and `setValue`; `stringResource` is `@Composable`, hoist out of lambdas; separate composable parameters with defaults last; read real failing assertion from test XML before guessing.
- Exactly 6 new strings in all 38 `values*/strings.xml`: `streak_rescue_title`, `streak_rescue_message` (%1$d missed, %2$d streak), `streak_rescue_action_ad`, `streak_rescue_action_restart`, `streak_rescue_action_later`, `streak_rescue_ad_failed`. English and Vietnamese hand-written, others English fallback. Added in the task that first uses them; `StringResourceParityTest` must stay 6/6.
- Honest limits: UTC calendar days; shield bridges gaps of any length (existing behaviour); ad availability depends on network/SDK; restart is not undoable.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/StreakRescuePolicy.kt`: pure policy evaluation.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`: add `DataStoreKeys.StreakRescueLastPromptDay`.
- Modify `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt`: add `resetBrokenStreak()` and `rescueBrokenStreak()`.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/StreakRescueViewModel.kt`: state machine, prompt gate, ad call.
- Create `app/src/main/java/com/mckimquyen/reader/ui/component/rpg/StreakRescueDialog.kt`: `AlertDialog` composable.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`: host `StreakRescueDialog`.
- Modify the 38 `app/src/main/res/values*/strings.xml`.
- Tests: `StreakRescuePolicyTest` (JVM), `BrainRpgRepositoryStreakRescueTest` (Robolectric), `StreakRescueViewModelTest` (Robolectric), `StreakRescueDialogTest` (Robolectric Compose), `StreakRescueDeviceTest` (Pixel instrumentation).
- Docs: `doc/task/done/RPG-03_cognitive-decay-streak-shield_DONE.md`, `doc/feature.md`, update `doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md`, delete `doc/task/todo/RPG-03_cognitive-decay-streak-shield.md`.

## Interfaces

Task 1 produces (`com.mckimquyen.reader.domain.model.rpg`):

```kotlin
data class StreakRescueOffer(val streakDays: Int, val missedDays: Int)

object StreakRescuePolicy {
    const val MIN_STREAK_TO_OFFER = 3
    const val MILLIS_PER_DAY = 86_400_000L
    fun evaluate(
        streakDays: Int,
        lastReadEpochDay: Long,
        shieldActive: Boolean,
        todayEpochDay: Long,
        lastPromptEpochDay: Long,
    ): StreakRescueOffer?
    fun epochDay(nowMillis: Long): Long
}
```

Task 2 produces (`com.mckimquyen.reader.domain.repository.BrainRpgRepository`):

```kotlin
suspend fun resetBrokenStreak(nowMillis: Long = System.currentTimeMillis()): Boolean
suspend fun rescueBrokenStreak(nowMillis: Long = System.currentTimeMillis()): Boolean
```

Task 4 produces (`com.mckimquyen.reader.ui.page.rpg`):

```kotlin
sealed interface StreakRescueUiState {
    object Idle : StreakRescueUiState
    data class Prompt(
        val offer: StreakRescueOffer,
        val isLoadingAd: Boolean = false,
        val error: String? = null,
    ) : StreakRescueUiState
}

class StreakRescueViewModel @Inject constructor(
    private val repository: BrainRpgRepository,
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<StreakRescueUiState>
    fun checkOnStartup()
    fun onRescueWithAd(activity: Activity)
    fun onRestartFromScratch()
    fun onDismissLater()
}
```

Task 5 produces (`com.mckimquyen.reader.ui.component.rpg`):

```kotlin
const val STREAK_RESCUE_DIALOG_TAG = "streak_rescue_dialog"
const val STREAK_RESCUE_AD_TAG = "streak_rescue_ad_btn"
const val STREAK_RESCUE_RESTART_TAG = "streak_rescue_restart_btn"
const val STREAK_RESCUE_LATER_TAG = "streak_rescue_later_btn"

@Composable
fun StreakRescueDialog(
    offer: StreakRescueOffer,
    isLoadingAd: Boolean,
    errorMessage: String?,
    onRescueWithAd: () -> Unit,
    onRestartFromScratch: () -> Unit,
    onDismissLater: () -> Unit,
)
```

---

### Task 1: Pure `StreakRescuePolicy`

**Files:** Create `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/StreakRescuePolicy.kt`, `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/StreakRescuePolicyTest.kt`.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakRescuePolicyTest {
    private val today = 20_000L

    @Test
    fun returnsNull_whenStreakIsBelowMinimum() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 2, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
        assertNull(StreakRescuePolicy.evaluate(streakDays = 0, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsNull_whenShieldIsAlreadyActive() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 3, shieldActive = true, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsNull_whenNeverReadBefore() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = 0L, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = -1L, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsNull_whenStreakIsStillAlive() {
        // Read today: no break
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
        // Read yesterday: alive, reading today would increment
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 1, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsOffer_whenGapIsTwoDaysOrMore() {
        val offer = StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 2, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L)
        assertEquals(StreakRescueOffer(streakDays = 5, missedDays = 2), offer)

        val longGap = StreakRescuePolicy.evaluate(streakDays = 14, lastReadEpochDay = today - 10, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L)
        assertEquals(StreakRescueOffer(streakDays = 14, missedDays = 10), longGap)
    }

    @Test
    fun returnsNull_whenAlreadyPromptedToday() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = today))
    }

    @Test
    fun returnsOffer_whenPromptedYesterday_andStillUnreadToday() {
        val offer = StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = today - 1)
        assertEquals(StreakRescueOffer(streakDays = 5, missedDays = 3), offer)
    }

    @Test
    fun returnsNull_whenClockSkewGoesBackward() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today + 2, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun epochDay_matchesMillisPerDay() {
        assertEquals(0L, StreakRescuePolicy.epochDay(0L))
        assertEquals(1L, StreakRescuePolicy.epochDay(StreakRescuePolicy.MILLIS_PER_DAY))
        assertEquals(20_000L, StreakRescuePolicy.epochDay(20_000L * StreakRescuePolicy.MILLIS_PER_DAY + 12_345L))
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*StreakRescuePolicyTest"`. Expected: unresolved `StreakRescuePolicy`.

- [ ] **Step 3: Implement**

```kotlin
package com.mckimquyen.reader.domain.model.rpg

data class StreakRescueOffer(
    val streakDays: Int,
    val missedDays: Int,
)

/**
 * Pure policy for prompting users about a broken reading streak.
 * Only prompts when saved streak is >= 3 days, missed days >= 2, no shield armed, and not yet prompted today (UTC).
 */
object StreakRescuePolicy {
    const val MIN_STREAK_TO_OFFER = 3
    const val MILLIS_PER_DAY = 86_400_000L

    fun evaluate(
        streakDays: Int,
        lastReadEpochDay: Long,
        shieldActive: Boolean,
        todayEpochDay: Long,
        lastPromptEpochDay: Long,
    ): StreakRescueOffer? {
        if (streakDays < MIN_STREAK_TO_OFFER) return null
        if (shieldActive) return null
        if (lastReadEpochDay <= 0L) return null
        val gap = (todayEpochDay - lastReadEpochDay).toInt()
        if (gap < 2) return null
        if (lastPromptEpochDay == todayEpochDay) return null
        return StreakRescueOffer(streakDays = streakDays, missedDays = gap)
    }

    fun epochDay(nowMillis: Long): Long = nowMillis / MILLIS_PER_DAY
}
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*StreakRescuePolicyTest" --rerun`; expected 9/9.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/rpg/StreakRescuePolicy.kt app/src/test/java/com/mckimquyen/reader/domain/model/rpg/StreakRescuePolicyTest.kt
git commit -m "feat(rpg-03): add pure streak rescue prompt policy"
```

---

### Task 2: Repository mutations (`resetBrokenStreak` & `rescueBrokenStreak`)

**Files:** Modify `BrainRpgRepository.kt`; create `app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRepositoryStreakRescueTest.kt`.

- [ ] **Step 1: Write failing tests** (Robolectric with in-memory Room, matching `BrainRpgRepositoryTest`).

```kotlin
package com.mckimquyen.reader.domain.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.StreakRescuePolicy
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainRpgRepositoryStreakRescueTest {
    private val day = StreakRescuePolicy.MILLIS_PER_DAY
    private val now = 20_000L * day
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        repository = BrainRpgRepository(context, database, dao, importer, newScope(), Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        database.close()
    }

    private suspend fun seedBrokenStreak(streak: Int = 5, daysAgo: Long = 3) {
        val lastRead = (now / day) - daysAgo
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = streak, lastReadEpochDay = lastRead, streakShieldActive = false))
        repository.refresh(now)
    }

    @Test fun resetBrokenStreak_setsStreakDaysToZero_andNextReadStartsAtOne() = runTest {
        seedBrokenStreak(streak = 5, daysAgo = 3)

        assertTrue(repository.resetBrokenStreak(now))
        assertEquals(0, repository.getProgress().streakDays)

        // Reading today resets streak to 1
        repository.awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = now)
        assertEquals(1, repository.getProgress().streakDays)
    }

    @Test fun resetBrokenStreak_rejectsWhenStreakIsAliveOrShieldArmed() = runTest {
        // Read yesterday: alive, not broken
        val yesterday = (now / day) - 1
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 5, lastReadEpochDay = yesterday, streakShieldActive = false))
        repository.refresh(now)

        assertFalse(repository.resetBrokenStreak(now))
        assertEquals(5, repository.getProgress().streakDays)

        // Shield already active: not broken
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 5, lastReadEpochDay = (now / day) - 4, streakShieldActive = true))
        repository.refresh(now)

        assertFalse(repository.resetBrokenStreak(now))
    }

    @Test fun rescueBrokenStreak_armsShield_andNextReadIncrementsStreak() = runTest {
        seedBrokenStreak(streak = 5, daysAgo = 3)

        assertTrue(repository.rescueBrokenStreak(now))
        assertTrue(repository.getProgress().streakShieldActive)
        assertEquals(5, repository.getProgress().streakDays)

        // Next read bridges the gap, increments to 6, consumes the shield
        repository.awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = now)
        assertEquals(6, repository.getProgress().streakDays)
        assertFalse(repository.getProgress().streakShieldActive)
    }

    @Test fun rescueBrokenStreak_isIdempotent_secondCallReturnsFalse() = runTest {
        seedBrokenStreak(streak = 5, daysAgo = 3)

        assertTrue(repository.rescueBrokenStreak(now))
        assertFalse("second call when shield already armed returns false", repository.rescueBrokenStreak(now))
    }

    @Test fun rescueBrokenStreak_rejectsWhenStreakIsStillAlive() = runTest {
        val yesterday = (now / day) - 1
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 5, lastReadEpochDay = yesterday, streakShieldActive = false))
        repository.refresh(now)

        assertFalse(repository.rescueBrokenStreak(now))
        assertFalse(repository.getProgress().streakShieldActive)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*BrainRpgRepositoryStreakRescueTest"`. Expected: unresolved `resetBrokenStreak` / `rescueBrokenStreak`.

- [ ] **Step 3: Implement in `BrainRpgRepository.kt`**

Under `mutationMutex`:

```kotlin
    /** Resets a broken streak to 0 in Room; returns false if streak is not actually broken or shield is already armed. */
    suspend fun resetBrokenStreak(nowMillis: Long = System.currentTimeMillis()): Boolean = mutationMutex.withLock {
        legacyImporter.ensureImported(nowMillis)
        val profile = dao.getProfile() ?: return@withLock false
        val today = epochDay(nowMillis)
        val gap = today - profile.lastReadEpochDay
        if (profile.streakShieldActive || profile.lastReadEpochDay <= 0L || gap < 2) return@withLock false
        database.withTransaction {
            dao.updateProfile(profile.copy(streakDays = 0))
        }
        refresh(nowMillis)
        true
    }

    /** Re-validates a broken streak under the mutex and arms the shield; returns false if no longer eligible. */
    suspend fun rescueBrokenStreak(nowMillis: Long = System.currentTimeMillis()): Boolean = mutationMutex.withLock {
        legacyImporter.ensureImported(nowMillis)
        val profile = dao.getProfile() ?: return@withLock false
        val today = epochDay(nowMillis)
        val gap = today - profile.lastReadEpochDay
        if (profile.streakShieldActive || profile.lastReadEpochDay <= 0L || gap < 2) return@withLock false
        database.withTransaction {
            dao.updateProfile(profile.copy(streakShieldActive = true))
        }
        refresh(nowMillis)
        true
    }
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*BrainRpgRepository*" --rerun`; expected all pass.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRepositoryStreakRescueTest.kt
git commit -m "feat(rpg-03): add resetBrokenStreak and rescueBrokenStreak to repository"
```

---

### Task 3: DataStore prompt day & 6 strings in 38 locales

**Files:** Modify `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`; modify all 38 `app/src/main/res/values*/strings.xml`.

- [ ] **Step 1: Add DataStoreKey in `ExtDataStore.kt`**

```kotlin
    object StreakRescueLastPromptDay : DataStoreKeys<Long>() {
        override val key: Preferences.Key<Long>
            get() = androidx.datastore.preferences.core.longPreferencesKey("streak_rescue_last_prompt_day")
    }
```

- [ ] **Step 2: Add 6 strings in all 38 locales** using a Python script:
  - English:
    - `streak_rescue_title` = "Reading streak at risk!"
    - `streak_rescue_message` = "You missed %1$d days of reading. Your %2$d-day streak will be reset on your next read."
    - `streak_rescue_action_ad` = "Watch video to save streak"
    - `streak_rescue_action_restart` = "Start from scratch"
    - `streak_rescue_action_later` = "Later"
    - `streak_rescue_ad_failed` = "Could not load video. Please try again."
  - Vietnamese (`values-vi`):
    - `streak_rescue_title` = "Chuỗi đọc đang gặp nguy hiểm!"
    - `streak_rescue_message` = "Bạn đã lỡ %1$d ngày không đọc bài. Chuỗi %2$d ngày sẽ bị huỷ trong lần đọc tới."
    - `streak_rescue_action_ad` = "Xem video để giữ chuỗi"
    - `streak_rescue_action_restart` = "Bắt đầu lại từ đầu"
    - `streak_rescue_action_later` = "Để sau"
    - `streak_rescue_ad_failed` = "Không thể tải video. Vui lòng thử lại."

- [ ] **Step 3: Verify parity.** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun`; expected 6/6.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/main/res
git commit -m "feat(rpg-03): add streak rescue prompt day key and strings across 38 locales"
```

---

### Task 4: `StreakRescueViewModel`

**Files:** Create `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/StreakRescueViewModel.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/rpg/StreakRescueViewModelTest.kt`.

- [ ] **Step 1: Write failing tests** (Robolectric with fake ad launcher and fake clock).

```kotlin
package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer
import com.mckimquyen.reader.domain.model.rpg.StreakRescuePolicy
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StreakRescueViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val day = StreakRescuePolicy.MILLIS_PER_DAY
    private var now = 20_000L * day
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        repository = BrainRpgRepository(context, database, dao, importer, newScope(), Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        adLauncher: (Activity, (Boolean) -> Unit) -> Unit = { _, cb -> cb(true) }
    ) = StreakRescueViewModel(
        repository = repository,
        context = context,
        defaultDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        clock = { now },
        adLauncher = adLauncher,
    )

    private suspend fun seedBrokenStreak(streak: Int = 5, daysAgo: Long = 3) {
        val lastRead = (now / day) - daysAgo
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = streak, lastReadEpochDay = lastRead, streakShieldActive = false))
        repository.refresh(now)
    }

    @Test fun checkOnStartup_promptsWhenStreakIsBroken_andSavesPromptDay() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()

        vm.checkOnStartup()
        advanceUntilIdle()

        val prompt = vm.state.value as StreakRescueUiState.Prompt
        assertEquals(StreakRescueOffer(streakDays = 5, missedDays = 3), prompt.offer)
        assertNull(prompt.error)

        // Prompt day recorded in DataStore
        val recordedDay = context.dataStore.data.map { it[DataStoreKeys.StreakRescueLastPromptDay.key] }.first()
        assertEquals(now / day, recordedDay)
    }

    @Test fun checkOnStartup_staysIdleWhenStreakIsAlive() = runTest(testDispatcher) {
        val yesterday = (now / day) - 1
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 5, lastReadEpochDay = yesterday, streakShieldActive = false))
        repository.refresh(now)

        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
    }

    @Test fun checkOnStartup_staysIdleWhenAlreadyPromptedToday() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        context.dataStore.put(DataStoreKeys.StreakRescueLastPromptDay, now / day)

        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
    }

    @Test fun onRestartFromScratch_resetsStreakAndDismisses() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRestartFromScratch()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
        assertEquals(0, repository.getProgress().streakDays)
    }

    @Test fun onDismissLater_dismissesWithoutResettingStreak() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onDismissLater()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
        assertEquals(5, repository.getProgress().streakDays)
    }

    @Test fun onRescueWithAd_onSuccess_armsShieldAndDismisses() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel(adLauncher = { _, cb -> cb(true) })
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk())
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
        assertTrue(repository.getProgress().streakShieldActive)
        assertEquals(5, repository.getProgress().streakDays)
    }

    @Test fun onRescueWithAd_onFailure_retainsPromptWithErrorMessage() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel(adLauncher = { _, cb -> cb(false) })
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk())
        advanceUntilIdle()

        val prompt = vm.state.value as StreakRescueUiState.Prompt
        assertTrue(prompt.error != null)
        assertFalse(repository.getProgress().streakShieldActive)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*StreakRescueViewModelTest"`. Expected: unresolved `StreakRescueViewModel`.

- [ ] **Step 3: Implement `StreakRescueViewModel.kt`**

```kotlin
package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer
import com.mckimquyen.reader.domain.model.rpg.StreakRescuePolicy
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import com.roy.sdkadbmob.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface StreakRescueUiState {
    object Idle : StreakRescueUiState
    data class Prompt(
        val offer: StreakRescueOffer,
        val isLoadingAd: Boolean = false,
        val error: String? = null,
    ) : StreakRescueUiState
}

@HiltViewModel
class StreakRescueViewModel @Inject constructor(
    private val repository: BrainRpgRepository,
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val adLauncher: (Activity, (Boolean) -> Unit) -> Unit = { act, cb -> AdManager.showRewarded(act) { cb(it) } },
) : ViewModel() {

    private val _state = MutableStateFlow<StreakRescueUiState>(StreakRescueUiState.Idle)
    val state: StateFlow<StreakRescueUiState> = _state.asStateFlow()

    fun checkOnStartup() {
        viewModelScope.launch {
            val nowMillis = clock()
            val today = StreakRescuePolicy.epochDay(nowMillis)
            val progress = withContext(ioDispatcher) { repository.getProgress() }
            val lastPromptDay = withContext(ioDispatcher) {
                context.dataStore.data.map { it[DataStoreKeys.StreakRescueLastPromptDay.key] ?: -1L }.first()
            }
            val offer = StreakRescuePolicy.evaluate(
                streakDays = progress.streakDays,
                lastReadEpochDay = progress.lastReadEpochDay,
                shieldActive = progress.streakShieldActive,
                todayEpochDay = today,
                lastPromptEpochDay = lastPromptDay,
            )
            if (offer != null) {
                withContext(ioDispatcher) { context.dataStore.put(DataStoreKeys.StreakRescueLastPromptDay, today) }
                _state.value = StreakRescueUiState.Prompt(offer)
            }
        }
    }

    fun onRescueWithAd(activity: Activity) {
        val current = _state.value as? StreakRescueUiState.Prompt ?: return
        _state.value = current.copy(isLoadingAd = true, error = null)
        adLauncher(activity) { earned ->
            viewModelScope.launch {
                if (earned) {
                    val rescued = withContext(ioDispatcher) { repository.rescueBrokenStreak(clock()) }
                    if (rescued) {
                        _state.value = StreakRescueUiState.Idle
                        return@launch
                    }
                }
                _state.value = current.copy(
                    isLoadingAd = false,
                    error = context.getString(R.string.streak_rescue_ad_failed),
                )
            }
        }
    }

    fun onRestartFromScratch() {
        viewModelScope.launch {
            withContext(ioDispatcher) { repository.resetBrokenStreak(clock()) }
            _state.value = StreakRescueUiState.Idle
        }
    }

    fun onDismissLater() {
        _state.value = StreakRescueUiState.Idle
    }
}
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*StreakRescueViewModelTest" --rerun`; expected 7/7.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rpg/StreakRescueViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/rpg/StreakRescueViewModelTest.kt
git commit -m "feat(rpg-03): add StreakRescueViewModel managing prompt and rewarded ad lifecycle"
```

---

### Task 5: `StreakRescueDialog` & `HomeEntry` integration

**Files:** Create `app/src/main/java/com/mckimquyen/reader/ui/component/rpg/StreakRescueDialog.kt`, `app/src/test/java/com/mckimquyen/reader/ui/component/rpg/StreakRescueDialogTest.kt`; modify `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`.

- [ ] **Step 1: Write failing Compose tests in `StreakRescueDialogTest.kt`**

```kotlin
package com.mckimquyen.reader.ui.component.rpg

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreakRescueDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val offer = StreakRescueOffer(streakDays = 5, missedDays = 3)

    @Test
    fun dialog_rendersFormattedMessage_andInvokesAdCallback() {
        var adClicked = 0
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = null,
                onRescueWithAd = { adClicked++ },
                onRestartFromScratch = {},
                onDismissLater = {},
            )
        }

        compose.onNodeWithTag(STREAK_RESCUE_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText("You missed 3 days of reading. Your 5-day streak will be reset on your next read.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(STREAK_RESCUE_AD_TAG).performClick()
        assertEquals(1, adClicked)
    }

    @Test
    fun restartButton_invokesRestartCallback() {
        var restarted = 0
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = null,
                onRescueWithAd = {},
                onRestartFromScratch = { restarted++ },
                onDismissLater = {},
            )
        }

        compose.onNodeWithTag(STREAK_RESCUE_RESTART_TAG).performClick()
        assertEquals(1, restarted)
    }

    @Test
    fun laterButton_invokesDismissCallback() {
        var dismissed = 0
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = null,
                onRescueWithAd = {},
                onRestartFromScratch = {},
                onDismissLater = { dismissed++ },
            )
        }

        compose.onNodeWithTag(STREAK_RESCUE_LATER_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun errorState_showsMessage() {
        compose.setContent {
            StreakRescueDialog(
                offer = offer,
                isLoadingAd = false,
                errorMessage = "Ad failed to load",
                onRescueWithAd = {},
                onRestartFromScratch = {},
                onDismissLater = {},
            )
        }

        compose.onNodeWithText("Ad failed to load").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*StreakRescueDialogTest"`. Expected: unresolved `StreakRescueDialog`.

- [ ] **Step 3: Implement `StreakRescueDialog.kt`**

```kotlin
package com.mckimquyen.reader.ui.component.rpg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer

const val STREAK_RESCUE_DIALOG_TAG = "streak_rescue_dialog"
const val STREAK_RESCUE_AD_TAG = "streak_rescue_ad_btn"
const val STREAK_RESCUE_RESTART_TAG = "streak_rescue_restart_btn"
const val STREAK_RESCUE_LATER_TAG = "streak_rescue_later_btn"

@Composable
fun StreakRescueDialog(
    offer: StreakRescueOffer,
    isLoadingAd: Boolean,
    errorMessage: String?,
    onRescueWithAd: () -> Unit,
    onRestartFromScratch: () -> Unit,
    onDismissLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.streak_rescue_title)
    val message = stringResource(R.string.streak_rescue_message, offer.missedDays, offer.streakDays)
    val actionAd = stringResource(R.string.streak_rescue_action_ad)
    val actionRestart = stringResource(R.string.streak_rescue_action_restart)
    val actionLater = stringResource(R.string.streak_rescue_action_later)

    AlertDialog(
        onDismissRequest = onDismissLater,
        modifier = modifier.testTag(STREAK_RESCUE_DIALOG_TAG),
        title = { Text("🔥 $title", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRescueWithAd,
                enabled = !isLoadingAd,
                modifier = Modifier.testTag(STREAK_RESCUE_AD_TAG),
            ) {
                Text("🎬 $actionAd")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onRestartFromScratch,
                enabled = !isLoadingAd,
                modifier = Modifier.testTag(STREAK_RESCUE_RESTART_TAG),
            ) {
                Text(actionRestart)
            }
        },
    )
}
```

- [ ] **Step 4: Integrate in `HomeEntry.kt`**

In `HomeEntry.kt`:
- Add imports: `import com.mckimquyen.reader.ui.page.rpg.StreakRescueViewModel`, `import com.mckimquyen.reader.ui.page.rpg.StreakRescueUiState`, `import com.mckimquyen.reader.ui.component.rpg.StreakRescueDialog`.
- Inject viewModel: `val streakRescueViewModel: StreakRescueViewModel = hiltViewModel()`
- Collect state: `val streakRescueState by streakRescueViewModel.state.collectAsState()`
- Add startup hook: `LaunchedEffect(Unit) { streakRescueViewModel.checkOnStartup() }`
- Below `CommuteCastDialog`:
  ```kotlin
  val rescue = streakRescueState
  if (rescue is StreakRescueUiState.Prompt) {
      StreakRescueDialog(
          offer = rescue.offer,
          isLoadingAd = rescue.isLoadingAd,
          errorMessage = rescue.error,
          onRescueWithAd = { streakRescueViewModel.onRescueWithAd(activity) },
          onRestartFromScratch = { streakRescueViewModel.onRestartFromScratch() },
          onDismissLater = { streakRescueViewModel.onDismissLater() },
      )
  }
  ```

- [ ] **Step 5: Run green.** `./gradlew testDevDebugUnitTest --tests "*StreakRescue*" --rerun`; all pass. Then `./gradlew compileDevDebugKotlin`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/component/rpg/StreakRescueDialog.kt app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt app/src/test/java/com/mckimquyen/reader/ui/component/rpg/StreakRescueDialogTest.kt
git commit -m "feat(rpg-03): add StreakRescueDialog and wire into HomeEntry startup"
```

---

### Task 6: Full verification, Pixel smoke, documentation

- [ ] **Step 1: Write instrumentation test `StreakRescueDeviceTest.kt`**

In `app/src/androidTest/java/com/mckimquyen/reader/ui/component/rpg/StreakRescueDeviceTest.kt`:
Using `createAndroidComposeRule<ComponentActivity>()`, render `StreakRescueDialog`, assert rendered on real device display, click later button, assert callback fired.

- [ ] **Step 2: Full verification.**
  - `./gradlew testDevDebugUnitTest --rerun`
  - `./gradlew lintDevDebug --rerun-tasks`
  - `./gradlew assembleDevDebug assembleDevDebugAndroidTest`
  - Record exact unit test counts and lint warnings from reports.

- [ ] **Step 3: Device instrumentation.**
  - Confirm `adb devices -l` lists `2B051FDH3006MU` and model is `Pixel 7 Pro`.
  - Install debug and test APKs.
  - Run: `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.component.rpg.StreakRescueDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`.
  - Uninstall test package.

- [ ] **Step 4: Smoke test on the Pixel.**
  - Backup `Reader*` and `settings.preferences_pb`. Check non-empty and `PRAGMA integrity_check` ok on copy.
  - Insert temporary broken streak: `streakDays=5`, `lastReadEpochDay=today-3`. Clear `streak_rescue_last_prompt_day` in DataStore so prompt triggers.
  - Launch app. Check `topResumedActivity`; close ad if present via "Tiếp tục đến ứng dụng".
  - Dialog appears with "🔥 Chuỗi đọc đang gặp nguy hiểm!", "lỡ 3 ngày", "Chuỗi 5 ngày".
  - Screenshot.
  - Test "Để sau": dialog closes, streak stays 5 in Room.
  - Re-launch with cleared prompt day, test "Bắt đầu lại từ đầu": streak reset to 0 in Room.
  - Restore BOTH backups with quoted `run-as` script.
  - Verify with `cmp` and `PRAGMA integrity_check`.

- [ ] **Step 5: Completion docs.**
  - Write `doc/task/done/RPG-03_cognitive-decay-streak-shield_DONE.md` with honest limits (UTC calendar day, shield bridges any gap, ad dependency, restart not undoable), exact commits, test counts.
  - Update `doc/feature.md` with RPG-03 entry.
  - Update `doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md` row for RPG-03 to Done.
  - Delete `doc/task/todo/RPG-03_cognitive-decay-streak-shield.md` with `git rm`.

- [ ] **Step 6: Commit.**

```bash
git add app/src doc docs/superpowers/plans/2026-11-01-rpg-03-streak-rescue.md
git commit -m "feat(rpg-03): complete streak rescue dialog and cognitive decay"
```

## Final Verification Checklist

- [ ] `StreakRescuePolicy`: edge cases covered (gap < 2, streak < 3, shield active, prompted today, clock backward).
- [ ] `BrainRpgRepository`: `resetBrokenStreak` and `rescueBrokenStreak` mutate Room atomically, return false when not broken, idempotent.
- [ ] DataStore: `StreakRescueLastPromptDay` persists prompt day, prevents repeat on same UTC day.
- [ ] Dialog: rendered in `HomeEntry`, tags on all buttons, dismiss/confirm/cancel wired.
- [ ] 6 strings in all 38 locales; `StringResourceParityTest` 6/6.
- [ ] Room stays version 20; no schema change.
- [ ] Full unit suite, lint, builds pass; instrumentation passes on Pixel `2B051FDH3006MU`.
- [ ] Smoke verified broken streak dialog on device; backups restored and verified with `cmp`.
