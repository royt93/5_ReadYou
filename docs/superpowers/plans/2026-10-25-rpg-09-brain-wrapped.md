# [RPG-09] Brain Wrapped 9:16 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Brain RPG all-time text share with an accurate weekly 1080×1920 Neon celebratory PNG, shared through Android Sharesheet and FileProvider.

**Architecture:** Room v19 records each new XP award as an event in the same transaction that claims the reward and updates profile/category totals. A pure local-week calculator plus DAO aggregate builds weekly XP/topic summary with the current streak; `BrainRpgViewModel` owns loading/share state. A pure Android Canvas renderer draws the Story card, and an IO sharer writes a cache PNG and builds a FileProvider chooser intent, following existing Quote Card patterns.

**Tech Stack:** Kotlin 2.1.0, Room, Hilt, Coroutines/StateFlow, `java.time`, Android `Canvas`/`Bitmap`, `FileProvider`, Compose, JUnit4, MockK, Robolectric 4.14.1, Android instrumentation tests.

## Global Constraints
- No new Gradle dependency.
- Room v19 adds `brain_rpg_xp_event`; no historical event backfill.
- Week is Monday 00:00 through next Monday 00:00 in device-local timezone; use half-open `[weekStartMillis, weekEndMillis)`.
- Streak is current profile streak at share time, not reconstructed weekly streak.
- Weekly topic derives only from weekly XP events; ties resolve by ascending `categoryId`; never use all-time category totals.
- Disable sharing when weekly XP is zero; show localized guidance.
- PNG is exactly 1080×1920. Renderer accepts localized text and no `Context`.
- Render on `Dispatchers.Default`; file output and cleanup on `Dispatchers.IO`.
- Reuse existing FileProvider authority and cache-path; chooser grants URI read access only.
- Add visible copy to English base plus every maintained `values-*` locale; pass `StringResourceParityTest`.
- Before device install/run, list adb devices and use only Pixel 7 Pro serial `2B051FDH3006MU`; back up Room DB. If interstitial covers UI, stop immediately and wait for owner’s `done` (R4).
- Preserve Pixel DB changes unless owner explicitly asks restoration; never restore over newer data.

---

## File Map

- Create `domain/model/rpg/BrainRpgWeek.kt`: pure local-week interval calculation.
- Create `domain/model/rpg/WeeklyBrainWrapped.kt`: immutable summary and category aggregate projection.
- Create `domain/model/rpg/BrainRpgXpEventEntity.kt`: persisted XP event row.
- Modify `domain/repository/BrainRpgDao.kt`: atomic event insert and weekly aggregate queries.
- Modify `domain/repository/BrainRpgRepository.kt`: award event wiring and weekly summary orchestration.
- Modify `infrastructure/db/AndroidDb.kt`: entity registration, v19, migration 18→19.
- Export `app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/19.json`.
- Create `infrastructure/rpg/BrainWrappedRenderer.kt`: Canvas-only renderer.
- Create `infrastructure/rpg/BrainWrappedSharer.kt`: PNG cache output and chooser intent.
- Modify `ui/page/rpg/BrainRpgViewModel.kt`, `BrainRpgPage.kt`: summary state, asynchronous sharing, error/loading/empty UI.
- Modify base + all locale `strings.xml`: localized copy.
- Add JVM and Android tests for migration, award atomicity, aggregate, renderer, sharer, ViewModel, Compose UI and FileProvider URI.
- Finish docs: `doc/task/done/RPG-09_wrapped-full-spec-mvp_DONE.md`, `doc/feature.md`, `doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md`.

---

### Task 1: Local Week Boundaries and Summary Model

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgWeek.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/WeeklyBrainWrapped.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgWeekTest.kt`

**Interfaces:**
- `BrainRpgWeek.interval(now: Instant, zone: ZoneId): WeekInterval`.
- `WeekInterval(startMillis: Long, endMillis: Long)` represents `[startMillis, endMillis)`.
- `WeeklyBrainWrapped(weekStartMillis: Long, weekEndMillis: Long, weeklyXp: Long, currentStreakDays: Int, topCategoryId: String?)`.

- [ ] **Step 1: Add failing boundary tests**

```kotlin
@Test fun mondayAtLocalStart_usesThisMondayAndNextMonday() {
    val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    val interval = BrainRpgWeek.interval(Instant.parse("2026-10-26T01:00:00Z"), zone)
    assertEquals(Instant.parse("2026-10-25T17:00:00Z").toEpochMilli(), interval.startMillis)
    assertEquals(Instant.parse("2026-11-01T17:00:00Z").toEpochMilli(), interval.endMillis)
}

@Test fun sundayLastMillisecond_isIncludedAndNextMondayExcluded() {
    val interval = BrainRpgWeek.interval(
        Instant.parse("2026-10-25T23:59:59.999Z"), ZoneId.of("UTC"),
    )
    assertEquals(Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(), interval.endMillis)
    assertTrue(Instant.parse("2026-10-25T23:59:59.999Z").toEpochMilli() < interval.endMillis)
}

@Test fun sameInstant_usesSuppliedLocalTimezone() {
    val instant = Instant.parse("2026-10-25T23:30:00Z")
    val utc = BrainRpgWeek.interval(instant, ZoneId.of("UTC"))
    val auckland = BrainRpgWeek.interval(instant, ZoneId.of("Pacific/Auckland"))
    assertNotEquals(utc.startMillis, auckland.startMillis)
}
```

- [ ] **Step 2: Run focused test to verify RED**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.rpg.BrainRpgWeekTest"`
Expected: compile failure because types do not exist.

- [ ] **Step 3: Implement pure week calculation using java.time**

```kotlin
package com.mckimquyen.reader.domain.model.rpg

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeekInterval(val startMillis: Long, val endMillis: Long)

data class WeeklyBrainWrapped(
    val weekStartMillis: Long,
    val weekEndMillis: Long,
    val weeklyXp: Long,
    val currentStreakDays: Int,
    val topCategoryId: String?,
)

object BrainRpgWeek {
    fun interval(now: Instant, zone: ZoneId): WeekInterval {
        val date = now.atZone(zone).toLocalDate()
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return WeekInterval(
            monday.atStartOfDay(zone).toInstant().toEpochMilli(),
            monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}
```

- [ ] **Step 4: Rerun focused test to verify PASS**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.rpg.BrainRpgWeekTest"`
Expected: 3 tests pass, including local timezone boundaries.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgWeek.kt app/src/main/java/com/mckimquyen/reader/domain/model/rpg/WeeklyBrainWrapped.kt app/src/test/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgWeekTest.kt
git commit -m "feat(rpg): define local week boundaries for Brain Wrapped [RPG-09]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Room v19 XP Event Entity and Migration

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgXpEventEntity.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt`
- Create/export: `app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/19.json`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration18To19Test.kt`

**Interfaces:**
- `BrainRpgXpEventEntity(id: Long = 0L, categoryId: String, xpDelta: Long, rewardType: BrainRpgRewardType, createdAtMillis: Long)`.
- `MIGRATION_18_19 : Migration(18, 19)` registered after `MIGRATION_17_18`.

- [ ] **Step 1: Add migration schema/data tests**

Create a v18 `SupportSQLiteOpenHelper` DB with profile/category/reward data, run migration, then assert event table exists/empty and old rows survive. Use PRAGMA to compare column types/nullability, primary key, and indexes. Assert no SQL backfills any event rows.

- [ ] **Step 2: Run migration test to verify RED**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.db.RoomMigration18To19Test"`
Expected: compile failure due missing entity/migration.

- [ ] **Step 3: Add entity and migration**

```kotlin
@Entity(
    tableName = "brain_rpg_xp_event",
    indices = [
        Index(value = ["createdAtMillis"]),
        Index(value = ["categoryId", "createdAtMillis"]),
    ],
)
data class BrainRpgXpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: String,
    val xpDelta: Long,
    val rewardType: BrainRpgRewardType,
    val createdAtMillis: Long,
)
```

Register entity, set database version 19, add migration `CREATE TABLE IF NOT EXISTS` and indexes matching Room naming. Direct migration changes schema, not SQLite `user_version`; validate actual version through Room’s registered migration runner in Task 7.

- [ ] **Step 4: Generate schema and rerun migration tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.db.RoomMigration18To19Test"`
Expected: pass; exported v19 schema matches PRAGMA; old profile/category/reward preserved; event table empty.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgXpEventEntity.kt app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/19.json app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration18To19Test.kt
git commit -m "feat(db): add Brain RPG XP event schema v19 [RPG-09]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Atomic Event Recording for READ, QUIZ, DOUBLE and TRIAGE

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgDao.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRoomDaoTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRepositoryTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgTriageTest.kt`

**Interfaces:**
- DAO `insertXpEvent(event: BrainRpgXpEventEntity)`.
- `claimAndApplyReward(..., xpEvent: BrainRpgXpEventEntity, ...)` inserts event iff claim succeeds inside transaction.
- `claimDoubleRewardAndApplyXp(..., xpEvent: BrainRpgXpEventEntity)` inserts event iff quiz prerequisite and unique DOUBLE claim both succeed.

- [ ] **Step 1: Add DAO tests for event contents and rollback**

Assert first READ/QUIZ award inserts one row with exact fields; repeated claim inserts none; wrong quiz inserts none; `failBeforeCommit=true` rolls back profile/category/reward/event; DOUBLE without QUIZ inserts none; valid DOUBLE inserts exactly one event. TRIAGE event uses category `QuizGeneratorService.CATEGORY_GENERAL`, 5 XP, `TRIAGE`.

- [ ] **Step 2: Run focused tests to verify RED**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.BrainRpgRoomDaoTest" --tests "com.mckimquyen.reader.domain.repository.BrainRpgRepositoryTest" --tests "com.mckimquyen.reader.domain.repository.BrainRpgTriageTest"`
Expected: compile/assertion failures because event API and inserts are missing.

- [ ] **Step 3: Insert XP events within award transactions**

In `claimAndApplyReward`, insert only in `if (claimed)` alongside profile/category XP update. In `claimDoubleRewardAndApplyXp`, return before writes if quiz prerequisite fails or DOUBLE claim is duplicate; otherwise update profile/category and insert event before transaction returns. Use the same timestamp as reward ledger; do not call wall clock independently inside DAO. In `BrainRpgRepository.awardTriageXpOnce`, replace current literal `"GENERAL"` with `QuizGeneratorService.CATEGORY_GENERAL` so event/category key matches category labels.

- [ ] **Step 4: Rerun DAO/repository tests**

Run the focused Gradle command from Step 2.
Expected: all tests pass; duplicate claim adds no event, injected failure rolls back ledger/profile/category/event.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgDao.kt app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRoomDaoTest.kt app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRepositoryTest.kt app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgTriageTest.kt
git commit -m "feat(rpg): record XP events atomically for Brain Wrapped [RPG-09]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Weekly DAO Aggregate and Repository Summary

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgDao.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/WeeklyBrainWrapped.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgWeeklySummaryTest.kt`

**Interfaces:**
- Projection `CategoryXpTotal(categoryId: String, xp: Long)`.
- DAO totals/category queries accept `startMillis`, `endMillis` and use `createdAtMillis >= startMillis AND createdAtMillis < endMillis`.
- Repository `getWeeklyBrainWrapped(now: Instant, zone: ZoneId): WeeklyBrainWrapped` captures one interval and reads current streak.

- [ ] **Step 1: Add failing weekly aggregate tests**

Seed profile streak `6`; seed events immediately before Monday, at Monday start, Sunday final millisecond and next Monday. Assert only the half-open interval counts. Add category tie case (same XP; lexically first category ID wins), and empty week (0 XP, null topic). Verify returned streak equals profile value.

- [ ] **Step 2: Run focused test to verify RED**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.BrainRpgWeeklySummaryTest"`
Expected: compile failure for missing DAO/repository methods.

- [ ] **Step 3: Implement DAO queries and repository orchestration**

```kotlin
@Query("SELECT COALESCE(SUM(xpDelta), 0) FROM brain_rpg_xp_event WHERE createdAtMillis >= :startMillis AND createdAtMillis < :endMillis")
suspend fun queryWeeklyXpTotal(startMillis: Long, endMillis: Long): Long

@Query("SELECT categoryId, SUM(xpDelta) AS xp FROM brain_rpg_xp_event WHERE createdAtMillis >= :startMillis AND createdAtMillis < :endMillis GROUP BY categoryId ORDER BY xp DESC, categoryId ASC")
suspend fun queryWeeklyCategoryXp(startMillis: Long, endMillis: Long): List<CategoryXpTotal>
```

`getWeeklyBrainWrapped` calculates interval once, sends same boundaries to both DAO queries, reads profile streak (default profile value if no row), and uses the first ordered category if any. Never use category decay projections for historical weekly totals.

- [ ] **Step 4: Rerun weekly aggregate tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.BrainRpgWeeklySummaryTest"`
Expected: boundary, total, tie, empty and streak tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgDao.kt app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt app/src/main/java/com/mckimquyen/reader/domain/model/rpg/WeeklyBrainWrapped.kt app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgWeeklySummaryTest.kt
git commit -m "feat(rpg): aggregate Brain Wrapped weekly XP and topic [RPG-09]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Neon Canvas Renderer and PNG FileProvider Sharer

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedRenderer.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedSharer.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedRendererTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedSharerTest.kt`

**Interfaces:**
- `BrainWrappedRenderer.render(model: BrainWrappedRenderModel): Bitmap`.
- `BrainWrappedSharer.writePng(bitmap: Bitmap, nowMillis: Long = System.currentTimeMillis()): File`.
- `BrainWrappedSharer.buildShareIntent(file: File, chooserTitle: String): Intent`.
- `BrainWrappedRenderModel(weekLabel: String, header: String, weeklyXpLabel: String, weeklyXp: Long, streakLabel: String, streakValue: String, topicLabel: String, topicValue: String?, topicEmptyLabel: String, tagline: String, watermark: String)`.

- [ ] **Step 1: Add renderer tests**

Render summary with topic and without topic; assert 1080×1920 and PNG compression succeeds. Include long localized strings and verify bounded text layout does not crash.

- [ ] **Step 2: Add sharer tests**

Assert PNG decodes, parent is `cacheDir/brain_wrapped`, pruning removes only expired Wrapped files and leaves `quote_cards` untouched. Assert chooser nested intent is `ACTION_SEND`, type `image/png`, has stream URI with `${packageName}.fileprovider`, `ClipData`, and read grant.

- [ ] **Step 3: Run focused tests to verify RED**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.rpg.BrainWrappedRendererTest" --tests "com.mckimquyen.reader.infrastructure.rpg.BrainWrappedSharerTest"`
Expected: compile errors because classes do not exist.

- [ ] **Step 4: Implement Canvas renderer**

Use named constants for canvas bounds, safe margins, palette and typography. Draw navy/purple gradient, localized header/week label, large XP hero with lavender glow, current streak/topic cards, localized empty topic, tagline and `RSS Cat Hub` wordmark. Use `TextPaint` and bounded `StaticLayout`; no `Context`, resource access, Room, Compose or filesystem in renderer.

- [ ] **Step 5: Implement sharer following QuoteCardSharer**

Write into dedicated `brain_wrapped` cache directory; use named retention duration, unique filename, `FileOutputStream.use`, check `bitmap.compress`, and isolate cleanup to that directory. Build `ACTION_SEND`, MIME `image/png`, stream extra, `ClipData`, read URI grant; return chooser without launching it.

- [ ] **Step 6: Run focused renderer/sharer tests**

Run the focused command from Step 3.
Expected: all tests pass; tests delete only their own generated files.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedRenderer.kt app/src/main/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedSharer.kt app/src/test/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedRendererTest.kt app/src/test/java/com/mckimquyen/reader/infrastructure/rpg/BrainWrappedSharerTest.kt
git commit -m "feat(rpg): render and share Neon Brain Wrapped PNG [RPG-09]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Brain RPG ViewModel, Weekly Card UI and Localization

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModel.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt`
- Modify: `app/src/main/res/values/strings.xml` and all 37 `values-*` string files.
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModelWrappedTest.kt`
- Test: existing `StringResourceParityTest`.

**Interfaces:**
- `WeeklyWrappedUiState(isLoading: Boolean, summary: WeeklyBrainWrapped?, isSharing: Boolean, error: WrappedShareError?)`.
- `BrainRpgViewModel.weeklyWrappedState: StateFlow<WeeklyWrappedUiState>`.
- `refreshWeeklyWrapped()` reloads summary.
- Share API accepts `Activity` as a call argument; ViewModel does not store it.

- [ ] **Step 1: Add ViewModel tests**

Test zero XP does not call renderer/sharer; positive XP passes exact summary/localized copy to renderer; duplicate taps while sharing create one file; render/write error sets UI error and never opens chooser; sharing flag resets in `finally`.

- [ ] **Step 2: Run test to verify RED**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.rpg.BrainRpgViewModelWrappedTest"`
Expected: missing state/API compile errors.

- [ ] **Step 3: Add localized strings**

Add keys: `brain_wrapped_share_story`, `brain_wrapped_share_title`, `brain_wrapped_week_header`, `brain_wrapped_xp_label`, `brain_wrapped_streak_label`, `brain_wrapped_current_streak`, `brain_wrapped_topic_label`, `brain_wrapped_topic_empty`, `brain_wrapped_tagline`, `brain_wrapped_no_week_xp`, `brain_wrapped_sharing`, `brain_wrapped_share_error`. Keep placeholders consistent and translate naturally in all maintained locales.

- [ ] **Step 4: Implement asynchronous share state**

Load one weekly summary on page entry. Use renderer on `Dispatchers.Default`, file write on `Dispatchers.IO`, then start chooser from Activity on Main. Reset `isSharing` in `finally`; do not convert `CancellationException` to an error. Do not retain Activity in ViewModel.

- [ ] **Step 5: Replace text-only share and bind UI states**

Remove old `ACTION_SEND text/plain` block in `BrainRpgPage.kt`. Disable share if loading/sharing/weekly XP is zero; show localized guidance, progress and retryable error states. Add stable Compose test tags for share state.

- [ ] **Step 6: Run ViewModel and parity tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.rpg.BrainRpgViewModelWrappedTest" --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`
Expected: all targeted tests and locale parity pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModel.kt app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt app/src/main/res/values*/strings.xml app/src/test/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModelWrappedTest.kt
git commit -m "feat(rpg): add weekly Brain Wrapped share UI [RPG-09]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: Room Migration Chain and FileProvider Instrumentation Tests

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/db/BrainRpgMigration19Test.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/rpg/BrainWrappedShareInstrumentationTest.kt`

- [ ] **Step 1: Test registered v18→v19 migration**

Create a v18 DB file with profile/category/reward rows; reopen through `AndroidDatabase`/`allMigrations`; assert `PRAGMA user_version = 19`, old rows preserved and event table empty. Close and delete only test DB in `finally`.

- [ ] **Step 2: Test actual FileProvider PNG decode**

Render and write PNG; extract nested stream URI from chooser; open with `contentResolver.openInputStream`; decode and assert 1080×1920, MIME and permission. Delete own test PNG in `finally`.

- [ ] **Step 3: Test Compose states**

Using stable test tags, assert zero XP shows localized guidance and disabled button; positive XP enables it; sharing disables repeat taps and shows progress.

- [ ] **Step 4: Run targeted instrumentation only on locked Pixel**

Run `adb devices` and send device commands only as `adb -s 2B051FDH3006MU ...`. Use a targeted test invocation; never run a task that broadcasts to all connected devices. Back up Room first; inspect foreground before UI actions. Stop immediately if interstitial covers UI and wait for owner’s `done`.

Expected: migration, URI decode and UI state instrumentation pass on Pixel 7 Pro.

- [ ] **Step 5: Commit tests**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/infrastructure/db/BrainRpgMigration19Test.kt app/src/androidTest/java/com/mckimquyen/reader/ui/page/rpg/BrainWrappedShareInstrumentationTest.kt
git commit -m "test(rpg): verify Brain Wrapped migration and share flow [RPG-09]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: Full Verification, Pixel Smoke, and Completion Documentation

**Files:**
- Create: `doc/task/done/RPG-09_wrapped-full-spec-mvp_DONE.md`
- Modify: `doc/feature.md`
- Modify: `doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md`
- Move: `doc/task/todo/RPG-09_wrapped-full-spec-mvp.md` → `doc/task/done/RPG-09_wrapped-full-spec-mvp_DONE.md`

- [ ] **Step 1: Run full suite and build**

Run `./gradlew testDevDebugUnitTest --rerun` and `./gradlew assembleDevDebug`.
Expected: `BUILD SUCCESSFUL`; report actual count and failures.

- [ ] **Step 2: Run lint**

Run `./gradlew lintDevDebug`.
Expected: `BUILD SUCCESSFUL`, 0 errors; record warnings accurately.

- [ ] **Step 3: Run Pixel instrumentation and smoke**

List devices, then use only `adb -s 2B051FDH3006MU`. Back up Room DB and record integrity/version/profile totals before install. Install exact devDebug APK with explicit serial. Before every screenshot/tap, inspect foreground; if full-screen interstitial appears, stop immediately and wait for owner’s `done`.

Open Brain RPG; verify weekly XP/current streak/topic; tap “Chia sẻ lên Story”; confirm Sharesheet PNG attachment; resolve/preview the PNG and verify 1080×1920 and visible fields. After share, verify profile totals unchanged, event rows unchanged, DB integrity `ok`. Preserve DB; do not restore unless owner requests.

- [ ] **Step 4: Update completion docs and status**

Report MVP only; list radar chart, QR, Sunday reminder and Top X% as excluded. Record migration/no-backfill behavior, test/build/lint output, Pixel smoke evidence and commit hashes. Move RPG-09 task to done; update Epic 12 and `doc/feature.md`.

- [ ] **Step 5: Check and commit docs**

Run `git diff --check`; expected exit 0/no output.

```bash
git add doc/feature.md doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md doc/task/todo/RPG-09_wrapped-full-spec-mvp.md doc/task/done/RPG-09_wrapped-full-spec-mvp_DONE.md
git commit -m "docs: complete RPG-09 Brain Wrapped MVP

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 6: Ask before push**

Do not push automatically. Present verified evidence and ask owner via `AskUserQuestion` before any remote push.

---

## Plan Self-Review

- **Spec coverage:** Room v19/no backfill (Task 2), all four atomic award paths (Task 3), local week/totals/topic/current streak/empty/tie (Tasks 1 and 4), Neon Canvas output (Task 5), PNG/FileProvider and cache cleanup (Tasks 5 and 7), disabled/loading/error/localized UI (Task 6), migration/unit/instrumentation/device smoke/docs (Tasks 2–8). Radar, QR, Sunday reminder and percentile badge remain out of scope.
- **Placeholder scan:** No empty implementation placeholders; every command, interface, test expectation and migration behavior is concrete.
- **Type consistency:** WeekInterval feeds DAO bounds; WeeklyBrainWrapped is returned by repository and consumed by ViewModel/renderer model; BrainRpgXpEventEntity uses current reward converter; share intent is launched by Activity, not sharer/ViewModel context.
- **Migration note:** Direct `Migration.migrate()` does not update SQLite `user_version`; registered Room migration test verifies v19.
- **Data/device safety:** Only Pixel 7 Pro serial `2B051FDH3006MU`; backup before smoke; R4 stop rule; no automatic DB restore; no automatic push.
- **Local untracked artifacts:** `.idea/deviceManager.xml` and `.superpowers/brainstorm/96396-1792910916/` are not part of this plan; do not stage them.

Plan complete and saved to `docs/superpowers/plans/2026-10-25-rpg-09-brain-wrapped.md`. Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task and review between tasks.
2. **Inline Execution** — execute tasks in this session with checkpoints.

Which approach? 