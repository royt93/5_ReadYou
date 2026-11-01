# RPG-03 — Streak Rescue Dialog & Cognitive Decay Design

- **Date:** 2026-11-01
- **Branch:** `dev`
- **Status:** Owner-approved design; implementation not started.
- **Device lock:** Google Pixel 7 Pro, serial `2B051FDH3006MU` only.
- **Builds on:** Epic 12 Brain RPG, `BrainRpgRepository`, `BrainRpgDao`, `CategoryDecay`, `RPG-06` (test clock audit), `RPG-07` (Room persistence & decay worker).

## 1. Goal and Honest Scope

The core of cognitive decay and streak counting already works in the codebase:
- Category XP decay is rendered dynamically via `CategoryDecay.effectiveXp` (`floor(base × 0.9^weeks)`).
- `BrainRpgDecayWorker` (with `BrainRpgDecayProcessor` and `BrainRpgDecayNotificationPolicy`) posts a daily notification.
- Rewarded recovery for decayed categories exists in `BrainRpgViewModel.recoverCategory`.
- `nextStreak` and `activateStreakShield` are tested (including 6 known behaviours documented in `RPG-06`).
- Ad/IAP/money tasks are deferred (~2026-11-18), but the owner explicitly chose to keep the rewarded ad on the rescue button to match the original Gherkin acceptance criteria.

What this task adds:

1. A **pure `StreakRescuePolicy`**: decides when an app open should prompt the user about a broken streak.
2. Two new repository methods on `BrainRpgRepository`:
   - `resetStreak(nowMillis)`: resets `streakDays` to 0 (saved to Room; the next read starts streak at 1).
   - `rescueStreak(nowMillis)`: re-validates eligibility and activates the streak shield so the next read bridges the gap.
3. `StreakRescueViewModel`: checks on startup, remembers the prompt date in DataStore (`DataStoreKeys.StreakRescueLastPromptEpochDay`), exposes the offer state, and delegates to `AdManager.showRewarded`.
4. `StreakRescueDialog` rendered in `HomeEntry`: shows the saved streak count, missed days, and two actions: "Xem video để giữ chuỗi" and "Bắt đầu lại từ đầu". Dismissing outside counts as "later today" and prompts again tomorrow if still unread.
5. Localization for about 6 new `streak_rescue_*` strings in all 38 locales.

Out of scope: no Room schema migration (Room stays version 20), no new ad formats (reuses `AdManager.showRewarded`), no undo history, no changes to `CategoryDecay` math or `nextStreak`.

Honest limits:
- Days are calculated in UTC (per `RPG-06` owner decision; does not use device time zone).
- A streak shield bridges any gap length, not just 1 day (per existing `nextStreak` behaviour #2; kept per owner decision).
- Rewarded ad display depends on ad network availability; if no ad is available or the user cancels, the streak is not rescued and the dialog stays open with an error notice.

## 2. Owner Decisions (AskUserQuestion, 2026-11-01)

1. Scope: "Dialog mất streak + giữ nút xem rewarded ad như hiện tại" (dialog on startup with rewarded ad rescue).
2. Trigger: on app open, at most once per day, only when saved streak was `>= 3` days.
3. Restart action: resets `streakDays` to 0 immediately and persists to Room, so tomorrow the dialog does not re-prompt.
4. Dismissing / Back: records "prompted today" in DataStore without touching Room, giving the user another chance tomorrow.

## 3. Pure Logic: `StreakRescuePolicy`

Package: `com.mckimquyen.reader.domain.model.rpg`.

```kotlin
data class StreakRescueOffer(
    val streakDays: Int,
    val missedDays: Int,
)

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
        // today - lastRead == 0 (read today) or 1 (read yesterday, streak alive) -> no break
        val gap = (todayEpochDay - lastReadEpochDay).toInt()
        if (gap < 2) return null
        if (lastPromptEpochDay == todayEpochDay) return null
        return StreakRescueOffer(streakDays = streakDays, missedDays = gap)
    }

    fun epochDay(nowMillis: Long): Long = nowMillis / MILLIS_PER_DAY
}
```

Pure, deterministically testable on the JVM without Android dependencies.

## 4. Repository: `BrainRpgRepository`

Add two methods under `mutationMutex`:

- `suspend fun resetBrokenStreak(nowMillis: Long = System.currentTimeMillis()): Boolean`
  - Re-validates that streak is actually broken (`(epochDay(now) - lastRead) >= 2` and `!shieldActive`).
  - Updates `profile.copy(streakDays = 0)` in Room.
  - Returns `true` if reset, `false` if condition no longer holds.
  - Calls `refresh()`.

- `suspend fun rescueBrokenStreak(nowMillis: Long = System.currentTimeMillis()): Boolean`
  - Re-validates eligibility under the mutex (callback from rewarded ad arrives asynchronously).
  - Updates `profile.copy(streakShieldActive = true)` in Room.
  - Returns `true` if shield activated, `false` if condition was invalidated.
  - Calls `refresh()`.

Room DB schema is untouched; `BrainRpgProfileEntity` already has `streakDays` and `streakShieldActive`.

## 5. Persistence: Prompt Date in DataStore

Add `DataStoreKeys.StreakRescueLastPromptDay : DataStoreKeys<Long>()` with key `"streak_rescue_last_prompt_day"`.
Read synchronously off main thread on startup, updated on every prompt evaluation or user dismissal.

## 6. UI & State

### 6.1 `StreakRescueViewModel`
- Injected with `BrainRpgRepository`, `@ApplicationContext Context`, `@DefaultDispatcher`, `@IODispatcher`, and an optional `Clock` (default `Clock.systemUTC()`) for testability.
- State: `StateFlow<StreakRescueUiState>` (`Idle`, `Prompt(offer, isLoadingAd = false, error = null)`).
- `checkOnStartup()`: evaluates policy; if offer non-null, sets state to `Prompt` and writes `todayEpochDay` to DataStore.
- `onRescueWithAd(activity: Activity)`: calls `AdManager.showRewarded`. On success, calls `repository.rescueBrokenStreak()`; on success dismisses dialog; on ad cancel/fail sets `error` message.
- `onRestartFromScratch()`: calls `repository.resetBrokenStreak()`, dismisses dialog.
- `onDismissLater()`: dismisses dialog without mutating Room.

### 6.2 `StreakRescueDialog`
`AlertDialog` rendered in `HomeEntry`:
- Title: `streak_rescue_title` ("Chuỗi đọc đang gặp nguy hiểm!" / "Reading streak at risk!").
- Message: formatted with `streakDays` and `missedDays` (`streak_rescue_message`).
- Confirm button: "Xem video để giữ chuỗi" (`streak_rescue_action_ad`), tag `STREAK_RESCUE_AD_TAG`.
- Dismiss button: "Bắt đầu lại từ đầu" (`streak_rescue_action_restart`), tag `STREAK_RESCUE_RESTART_TAG`.
- Neutral action / outside click: "Để sau" (`streak_rescue_action_later`).
- Tags on buttons and dialog for testing.

## 7. Localization

Keys in 38 locales:
- `streak_rescue_title`: "Reading streak at risk!" / "Chuỗi đọc đang gặp nguy hiểm!"
- `streak_rescue_message`: "You missed %1$d days of reading. Your %2$d-day streak will be reset on your next read." / "Bạn đã lỡ %1$d ngày không đọc bài. Chuỗi %2$d ngày sẽ bị huỷ trong lần đọc tới."
- `streak_rescue_action_ad`: "Watch video to save streak" / "Xem video để giữ chuỗi"
- `streak_rescue_action_restart`: "Start from scratch" / "Bắt đầu lại từ đầu"
- `streak_rescue_action_later`: "Later" / "Để sau"
- `streak_rescue_ad_failed`: "Could not load video. Please try again." / "Không thể tải video. Vui lòng thử lại."

`StringResourceParityTest` must remain 6/6.

## 8. Testing Strategy

1. **JVM unit tests:**
   - `StreakRescuePolicyTest`: 8+ cases (healthy streak, 1-day gap, 2-day gap, gap >= 5, streak < 3, shield already active, already prompted today, zero lastRead, clock skew backward).
2. **Robolectric tests:**
   - `BrainRpgRepositoryStreakRescueTest`: `resetBrokenStreak` sets `streakDays=0`, re-read starts at 1; `rescueBrokenStreak` sets `streakShieldActive=true`, re-read increments streak; double calls; healthy streak rejection.
   - `StreakRescueViewModelTest`: policy integration, prompt state, ad success rescues and dismisses, ad failure retains prompt with error, restart calls reset.
   - `StreakRescueDialogTest`: Compose test asserting tags, buttons, callbacks.
3. **Instrumentation test (Pixel only):**
   - Dialog renders on real device, dismiss button fires.
4. **Device smoke test (Pixel `2B051FDH3006MU`):**
   - Backup Room + DataStore.
   - Insert temporary broken streak: `streakDays=5`, `lastReadEpochDay=today-3`.
   - Launch app -> dialog appears with correct counts -> test cancel -> test ad branch -> restore backups.

## 9. Risks and Mitigations

- **Ad collision on open:** full-screen ads may pop up on startup. Mitigation: dialog stays in Compose state; when ad activity closes, dialog is visible on `MainActivity`.
- **Clock manipulation:** user rolls clock back. Handled: `gap < 2` returns null, backward clock returns negative gap, no prompt.
- **Accidental reset:** user taps restart by mistake. Mitigation: clear confirmation copy stating streak will become 0.
