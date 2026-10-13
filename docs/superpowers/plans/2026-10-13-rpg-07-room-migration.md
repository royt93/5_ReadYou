# RPG-07 Room Migration and Cognitive Decay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate device-wide Brain RPG state and idempotency ledger from SharedPreferences to Room, require 80% of Reader body items plus 30 seconds for reading XP, and add category XP decay, daily summary notifications, and one-category rewarded recovery without deleting earned base XP.

**Architecture:** Room becomes the only RPG write source after a retry-safe, one-time SharedPreferences import. A singleton profile stores base XP/streak/quiz counters; category rows store base XP and practice/notification timestamps; a unique reward ledger makes read, quiz, and double-XP awards atomic and idempotent. Decay is derived from base category XP and `lastPracticedAt`, not persisted as XP loss; a daily unique WorkManager job only aggregates and posts notifications. Reader reports its own LazyList item range so metadata, quiz, and trailing UI cannot enter the 80% denominator.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.4 + KAPT, Hilt, WorkManager/HiltWorker, DataStore/SharedPreferences migration source, AndroidX notifications, JUnit4/MockK/Robolectric, Compose AndroidJUnit4 tests.

## Global Constraints

- Room is version 12 before this work; add exactly migration 12→13 and export `app/schemas/.../13.json`.
- Keep one RPG profile per device, independent of RSS account.
- Base total XP and levels never decay; only displayed category XP uses `floor(baseXp * 0.9^fullWeeks)` after each complete 7-day interval.
- Reading reward requires both `Reader` body-item progress `>= 0.80` and 30 continuous foreground seconds; metadata/loading/quiz/trailing spacer/ad are excluded from the denominator.
- Qualifying completed reading and a correct quiz reset only that category's `lastPracticedAt`; incorrect quiz/opening/flinging do not.
- Reward ledger uniqueness is `(articleId, rewardType)` for `READ`, `QUIZ`, and `DOUBLE`; claim + XP/profile update happen in one Room transaction.
- One daily summary notification lists all decayed categories; WorkManager work is unique and notification permission denial must not crash.
- Rewarded recovery resets one selected category only after earned-ad callback; failed/dismissed/unavailable ad changes no state or XP.
- No XP/article/category data leaves the device. New user-facing strings must be localized in maintained locales `en`, `vi`, `zh-rCN`, `ja`, `fr`, `de`.
- Keep Room annotation processing on KAPT and retain the existing KAPT JVM exports in `gradle.properties`.
- `room-testing` 2.8.4 currently conflicts with the app's kotlinx-serialization 1.7.3 constraint (`AbstractMethodError` on device); schema tests must use the existing SQLite/JSON comparison pattern unless that compatibility issue is separately reviewed and solved.
- Device target for install/UI/smoke: Tecno `115333744A005844` only, locked for this work; never run Gradle `connected*` (targets all attached devices). Stop on full-screen ad and wait for user `done`; banner ads are okay.
- Do not push. Owner handles push. Leave `.idea/workspace.xml` and all other unrelated/uncommitted user changes untouched.

---

## File Map

**Room/domain persistence**
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgProfile.kt` — Room row for singleton profile (`id=1`).
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgCategory.kt` — Room row for base category XP and practice/notification timestamps; category string key preserves unknown legacy category rows.
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgReward.kt` — Room row for `articleId`, `rewardType` (`READ`, `QUIZ`, `DOUBLE`), and creation timestamp; unique `(articleId, rewardType)`.
- Create `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgDao.kt` — transactional DAO, category/profile reads, atomic reward claims.
- Modify `.../infrastructure/db/AndroidDb.kt` — entities, DAO accessor, `version = 13`, migration 12→13.
- Modify `.../infrastructure/di/DbModule.kt` — provide DAO if existing provider pattern requires it.
- Modify `.../domain/repository/BrainRpgRepository.kt` — one-time legacy importer, Room-backed state/transactions, decay projection.

**Reader/worker/UI**
- Create `.../domain/model/rpg/ReadingCompletionGate.kt` — pure item-progress + foreground-duration state logic using injected clock.
- Modify `.../ui/component/reader/Reader.kt` and `HtmlToComposable.kt` — expose exact Reader body item range/count without changing displayed content.
- Modify `.../ui/page/home/read/Content.kt` — feed Reader body range and article lifecycle into the completion gate; call repository path only on qualifying completion.
- Modify `.../domain/sv/BrainRpgDecayWorker.kt` — Hilt periodic worker + unique daily scheduling/cancel helper.
- Modify `.../infrastructure/android/NotificationHelper.kt` (or existing RPG-appropriate notification helper after inspection) — post/cancel one summary notification with stable ID and deep link to Brain RPG.
- Modify `.../ui/page/rpg/BrainRpgViewModel.kt` and `BrainRpgPage.kt` — observe effective/base category XP, category selection, worker setup hook if owner pattern requires, recovery flow.
- Modify the existing rewarded-ad callback path in `BrainRpgViewModel.kt` / `BrainRpgPage.kt` — persist recovery only after earned callback.
- Modify maintained `app/src/main/res/values*/strings.xml` only for the exact new labels/messages.

**Tests/docs**
- Add unit tests under `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/` for gate/decay/legacy mapping.
- Add repository transaction/import tests under `app/src/test/java/com/mckimquyen/reader/domain/repository/`.
- Add Android instrumented schema migration test under `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/db/`, comparing `PRAGMA` against exported schema JSON (not `MigrationTestHelper` unless serialization compatibility is resolved).
- Add/update Compose widget tests for RPG category and recovery states, and Reader progress-range tests.
- Add instrumented integration test for migration + legacy import + reward idempotency + worker aggregation.
- Modify `doc/task/todo/RPG-07_room-migration-or-doc-alignment.md`, `doc/task/todo/RPG-01_xp-skill-tree-system.md`, `doc/task/todo/RPG-03_cognitive-decay-streak-shield.md`; on completion move RPG-07 to `doc/task/done/` with `_DONE` suffix and completion report per task end-signal.

## Task 1: Pure decay math and reading completion gate

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/CategoryDecay.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/ReadingCompletionGate.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/CategoryDecayTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/ReadingCompletionGateTest.kt`

**Interfaces produced:**
- `CategoryDecay.effectiveXp(baseXp: Long, lastPracticedAtMillis: Long, nowMillis: Long): Long`
- `ReadingCompletionGate(progressThreshold: Float = 0.80f, requiredForegroundMillis: Long = 30_000L)` with explicit `onArticleChanged(articleId)`, `onForegroundChanged(isForeground, nowMillis)`, and `isComplete(articleId, lastVisibleBodyIndex, bodyStartIndex, bodyItemCount, nowMillis): Boolean` semantics. Prefer a pure accumulator returning an updated immutable state so pause/resume and article change are deterministic.

- [ ] **Step 1: Write failing decay tests** for zero XP; 0/6d/7d/14d/21d; exact boundary; negative time delta; future timestamp; floor rounding; very large XP without overflow.
- [ ] **Step 2: Run `./gradlew testDevDebugUnitTest --tests "*CategoryDecayTest"` and verify RED** because `CategoryDecay` is missing.
- [ ] **Step 3: Implement only the pure decay formula.** Compute full weeks by integer division of nonnegative elapsed milliseconds; clamp XP at zero; use `BigDecimal`/safe repeated decimal multiplication or another overflow-safe strategy verified for `Long.MAX_VALUE` inputs. Avoid hardcoded timing values outside named constants.
- [ ] **Step 4: Run the decay tests and verify GREEN.**
- [ ] **Step 5: Write failing gate tests** for 79% vs 80%, 29,999ms vs 30,000ms, passing both conditions, wrong article ID, article switch resets timer, background time pauses the timer, foreground resume continues accumulated foreground time, body range excludes surrounding list items, zero body count never completes.
- [ ] **Step 6: Run `./gradlew testDevDebugUnitTest --tests "*ReadingCompletionGateTest"` and verify RED.**
- [ ] **Step 7: Implement the minimum immutable gate/accumulator** with injected `nowMillis` at every transition. Define body progress precisely as `(lastVisibleBodyIndex - bodyStartIndex + 1) / bodyItemCount`; clamp to `[0,1]`; require `lastVisibleBodyIndex >= bodyStartIndex`.
- [ ] **Step 8: Run both test classes and verify GREEN.**
- [ ] **Step 9: Commit** `feat(rpg): define decay math and reading completion gate`.

## Task 2: Room schema, migration, and durable legacy import

**Files:**
- Create 3 RPG entities and `BrainRpgDao.kt` as listed in File Map.
- Modify `AndroidDb.kt`, DB DI, `BrainRpgRepository.kt`.
- Test: repository unit test + SQLite schema-comparison migration test.

**Interfaces produced:**
- `enum class BrainRpgRewardType { READ, QUIZ, DOUBLE }`
- `BrainRpgDao.claimReward(articleId: String, rewardType: BrainRpgRewardType, createdAt: Long): Boolean` implemented as `INSERT OR IGNORE` and return true iff inserted.
- `BrainRpgDao.recordPractice(categoryId: String, practicedAtMillis: Long)` updates only `lastPracticedAt`; invoke it for a qualifying completed read even if the READ ledger already exists, and for a correct quiz even if the QUIZ ledger already exists.
- `BrainRpgDao` transaction method(s) that claim and update profile/category rows atomically; reward duplicate suppresses XP/counters but does not suppress practice timestamp updates for an actual qualifying practice event.
- `BrainRpgProfile.legacyImportComplete: Boolean` is the durable migration marker; do not add a fourth table solely for the marker.
- `BrainRpgRepository.ensureImported()` that serializes first access, imports current `brain_rpg_prefs` profile/category/reward sets once, sets `legacyImportComplete=true` in the same DB transaction, and blocks mutations until import finishes.
- `BrainRpgRepository.userProgress: StateFlow<UserProgress>` derived from Room profile; no parallel SharedPreferences write path after import.

- [ ] **Step 1: Write failing tests for legacy mapping**: map all six known category XP keys, profile totals/streak/shield/quiz counters, READ/QUIZ/DOUBLE string sets; missing keys use existing defaults; every key matching `brain_rpg_cat_*` is preserved as a category row (do not discard accrued XP for a category the current UI no longer lists).
- [ ] **Step 2: Run focused repository tests and verify RED.**
- [ ] **Step 3: Add the three entities and DAO with Room constraints.** Use a stable profile primary key `1`; category primary key category ID; reward unique index `(articleId, rewardType)`; timestamp stored as `Long` epoch millis.
- [ ] **Step 4: Register entities/DAO and add `MIGRATION_12_13`.** Migration creates only the RPG tables/indexes and does not touch previous tables.
- [ ] **Step 5: Add failing migration schema test first.** Build a version-12 SQLite database, apply migration, compare each new table's columns/types/nullability/PK/indexes against `13.json`; verify all old entities in 12.json are unchanged. Run and confirm RED against the current migration.
- [ ] **Step 6: Implement the Room-open importer as a single repository-owned transaction.** Serialize concurrent callers (mutex/single-flight); migration itself must not read `Context`/SharedPreferences. On first RPG access, read legacy prefs, write profile/category/reward rows, then write import marker in same transaction. No RPG mutation may race ahead of import. Leave old prefs untouched for this release.
- [ ] **Step 7: Add transaction failure/retry tests**: exception before commit leaves marker absent and no partial rows; second attempt imports successfully; concurrent initial calls import once; completed marker skips re-import on repository recreation.
- [ ] **Step 8: Add repository tests**: every old progress field survives import; old reward IDs suppress duplicate READ/QUIZ/DOUBLE; new claims are unique; claim + XP write is atomic; duplicate quiz does not increment attempts/passes; incorrect quiz records attempt but not reward; streak/shield behavior remains as before.
- [ ] **Step 9: Build and test** `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest`; resolve KAPT/schema issues without changing serialization versions unless separately approved.
- [ ] **Step 10: Commit** `feat(rpg): persist profile and reward ledger in Room`.

## Task 3: Route all RPG awards through atomic Room operations

**Files:**
- Modify: `BrainRpgRepository.kt`, `BrainRpgViewModel.kt`, `Content.kt`, quiz card integration caller.
- Tests: update `BrainRpgRepositoryTest`, `BrainRpgViewModelTest`, `BrainRpgIntegrationTest`.

**Interfaces consumed:** Task 2 DAO transactions and Task 1 gate.

**Interfaces produced:**
- `suspend fun awardReadingXpOnce(articleId, category, amount): AwardResult`
- `suspend fun submitQuizResultOnce(articleId, category, isCorrect): AwardResult` (wrong answers increment attempts; correct answer can claim once).
- `suspend fun awardDoubleXpForQuizOnce(articleId, category, bonusXp): AwardResult` (must verify correct-quiz ledger exists and DOUBLE not claimed).
- `AwardResult(progress: UserProgress, awardedXp: Long, wasDuplicate: Boolean)` (exact name can follow existing local naming but keep one result contract across the three flows).

- [ ] **Step 1: Update tests first** so all repository methods are suspend and assert award result; include duplicate callbacks concurrently and across a newly constructed repository.
- [ ] **Step 2: Run focused tests and verify expected compile/test RED** due signature mismatch/missing API.
- [ ] **Step 3: Implement repository transactions**; remove `brain_rpg_read_rewarded_ids`, `brain_rpg_quiz_rewarded_ids`, and `brain_rpg_double_rewarded_ids` as active write source, but keep importer reading them.
- [ ] **Step 4: Update BrainRpgViewModel callbacks** to show +XP only when `awardedXp > 0`; UI must not report a fake reward on duplicate.
- [ ] **Step 5: Update `BrainQuizCard`/caller** so a persisted claimed DOUBLE reward hides the button after process recreation; ad callback re-checks Room eligibility before claim (do not trust pre-ad UI state).
- [ ] **Step 6: Test wrong-answer retry, duplicate correct answer, double ad success/failure/duplicate, and a callback arriving after card dismissal. Also assert a qualifying repeat read or correct quiz refreshes `lastPracticedAt` even when its reward ledger claim is already present, without changing XP or attempt/pass counters.**
- [ ] **Step 7: Run focused unit + existing RPG Android integration tests, verify GREEN.**
- [ ] **Step 8: Commit** `fix(rpg): make all XP awards Room-idempotent`.

## Task 4: Reader body item range + 80%/30-second gate

**Files:**
- Modify `ui/component/reader/Reader.kt`, `HtmlToComposable.kt`, `ui/page/home/read/Content.kt`.
- Unit test: `ReadingCompletionGateTest`.
- Widget test: `ReadingContentWidgetTest` or new `ReadingCompletionWidgetTest`.
- Integration test: RPG integration.

- [ ] **Step 1: Add failing Reader range test** that constructs metadata + Reader body + quiz/trailing items and proves the range includes only `Reader`-appended items.
- [ ] **Step 2: Run focused test and verify RED.**
- [ ] **Step 3: Implement explicit Reader range reporting.** Capture `LazyListScope` item count before and after the single `Reader` call; return/emit `[bodyStartIndex, bodyEndIndexExclusive)`. Handle `content.isBlank()` / loading state as an empty range; never award on empty body.
- [ ] **Step 4: Replace `totalItemsCount * 0.75f` in `Content.kt`** with the gate from Task 1 using body range and only visible items inside that range. Start/reset timer by article ID; pause it when lifecycle is not foreground; reset on article change/dispose.
- [ ] **Step 5: Test item range excludes metadata, quiz, loading indicator, and trailing spacers.** Cover multiple paragraphs, images, code, optional video, empty content, and conditional quiz.
- [ ] **Step 6: Test 79%/80%, 29s/30s, quick fling, idle while foreground (meets spec because dwell is continuous article time), background pause, article switch reset, and repeated completion only one reward.**
- [ ] **Step 7: Run focused unit/widget/integration tests and verify GREEN.**
- [ ] **Step 8: Commit** `feat(rpg): require 80 percent and 30 seconds for reading XP`.

## Task 5: Category XP decay projection + RPG UI

**Files:**
- Modify `BrainRpgRepository.kt`, `BrainRpgViewModel.kt`, `BrainRpgPage.kt`, RPG category components.
- Add/update maintained locale strings.
- Unit + widget tests.

- [ ] **Step 1: Write failing projection tests** for base XP 0/1/100/Long.MAX_VALUE at 0/6/7/14/21 days, floor behavior, exact-boundary timestamps, future timestamp/clock rollback, and reset on practice.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement `CategoryDecay.effectiveXp(baseXp, lastPracticedAt, now)`** with full weeks by nonnegative elapsed duration and overflow-safe calculation; do not mutate base XP.
- [ ] **Step 4: Expose base/effective XP, decay ratio, and `isDecayed` from ViewModel**, using an injected clock for testability and lifecycle-aware collection.
- [ ] **Step 5: Update category UI** to label base/effective values and decay state; add localized strings for `en`, `vi`, `zh-rCN`, `ja`, `fr`, `de`; only show recovery action for decayed categories.
- [ ] **Step 6: Add widget tests** for fresh/decayed category, exact displayed XP, loading/error state, and recovery controls hidden when not decayed.
- [ ] **Step 7: Run unit/widget tests and verify GREEN.**
- [ ] **Step 8: Commit** `feat(rpg): project decayed category XP without deleting base XP`.

## Task 6: Daily summary notification worker

**Files:**
- Create `domain/sv/BrainRpgDecayWorker.kt` (`@HiltWorker`, `CoroutineWorker`).
- Modify existing `NotificationHelper.kt` or add one focused RPG notification method after inspection; update notification navigation/intent handling to `RouteName.BRAIN_RPG`.
- Modify scheduling owner (inspect `RApp`, `DailyEditionWorker`, and RPG entry lifecycle first); use unique work `BrainRpgDecayDailyWork` and daily cadence.
- Unit/integration tests; no device permissions are assumed.

- [ ] **Step 1: Write failing aggregation/scheduling tests**: no decayed category cancels/does not post; 1+ categories create exactly one summary; fixed ID updates, not duplicates; re-running same day does not repost; next day can repost; practice removes category and cancels notification when empty; permission denied produces no crash.
- [ ] **Step 2: Run tests and verify RED.**
- [ ] **Step 3: Implement pure `DecayNotificationSummary` builder** from category rows and injected `now`; summary contains category names and daily cadence state.
- [ ] **Step 4: Implement unique WorkManager schedule/cancel** using existing Hilt WorkerFactory and application scope patterns; worker reads Room, posts at most one notification, and returns retry only for transient storage failure.
- [ ] **Step 5: Add stable PendingIntent/deep link** to `RouteName.BRAIN_RPG`; check Android notification permission before posting; do not crash if denied.
- [ ] **Step 6: Add notification channel with existing project conventions** and cancel/update on no decay; do not create duplicate channels/IDs.
- [ ] **Step 7: Run worker/repository integration tests** including repeated execution and process restart.
- [ ] **Step 8: Commit** `feat(rpg): notify once daily about decayed categories`.

## Task 7: Per-category rewarded recovery

**Files:**
- Modify `BrainRpgPage.kt`, `BrainRpgViewModel.kt`, `BrainRpgRepository.kt`, category component.
- Unit + widget + integration tests.

- [ ] **Step 1: Write failing repository tests** for success recovery resets only selected category's `lastPracticedAt`; another decayed category remains decayed; recovery leaves base XP, total XP, level, streak, and quiz counts unchanged; recovery on fresh category is rejected/no-op.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement transactional `recoverCategory(categoryId, now)`** with validation that category is decayed at claim time.
- [ ] **Step 4: Wire rewarded-ad flow:** pre-check only for UI; authoritative repository check after earned callback; callback false/dismissal/exception must not mutate Room. Guard duplicate taps/callbacks with the Room transaction.
- [ ] **Step 5: Add a category picker in BrainRpgPage** with pending/error/success states. Only display recovery for a category currently decayed. Add localized strings in all maintained locales.
- [ ] **Step 6: Widget tests:** open/close chooser, no action for fresh category, successful claim updates only chosen category, failed/not-earned ad leaves state unchanged, duplicate callback does not restore twice.
- [ ] **Step 7: Integration test with fake rewarded-ad result and real Room DAO.**
- [ ] **Step 8: Commit** `feat(rpg): restore one decayed category with rewarded ad`.

## Task 8: Full migration, integration, docs and device verification

**Files:** all RPG files above, `app/schemas/.../13.json`, RPG task docs.

- [ ] **Step 1: Run Room schema export and inspect `13.json`.** Verify only the three RPG tables/indices are added and all version-12 entities are unchanged.
- [ ] **Step 2: Write schema comparison instrumented test** using project-compatible SQLite + JSON `PRAGMA` comparison (do not add `room-testing` 2.8.4 unless kotlinx-serialization compatibility is resolved; 2.8.4 previously threw `AbstractMethodError` against the app's 1.7.3 constraint).
- [ ] **Step 3: Add migration+import integration test**: start with version-12 DB plus legacy SharedPreferences fixture; migrate; instantiate repository; assert all profile/category/reward values preserved, marker persisted, no XP award lost, and second startup skips import.
- [ ] **Step 4: Verify rollback/retry** by forcing importer transaction failure before marker commit; next startup re-imports exactly once; no partial profile/ledger rows remain.
- [ ] **Step 5: Run:** `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest`; count XML results, all must be 0 failure.
- [ ] **Step 6: Run `./gradlew lintDevDebug`; fix only RPG-07-introduced findings. Existing Android Auto voice-search lint findings are outside this task.
- [ ] **Step 7: Install/run tests only on Tecno `115333744A005844`** via explicit `adb -s` per class. Before every UI action verify `topResumedActivity`; if `AdActivity` full-screen appears, stop and wait for owner `done`. Banner ads may continue.
- [ ] **Step 8: Manual Tecno smoke:** pre-migration progress survives; under-30-second read/fling gets no XP; ≥80% body plus ≥30 foreground seconds gets one reward; repeated/restarted read does not; correct quiz gets once; wrong answer does not consume reward; double-XP rewarded once; category decay at 7-day boundary and 10% weekly compounding; correct quiz/read resets one category; recovery ad success resets selected category only; denial/failure leaves it unchanged; notification summary aggregates categories and opens RPG page.
- [ ] **Step 9: Update `doc/feature.md`, RPG-01, RPG-03, RPG-07 and completion report** to describe shipped behavior and remove obsolete SharedPreferences/75% claims.
- [ ] **Step 10: Run final `git diff --check`; audit each acceptance criterion against code/test output.**
- [ ] **Step 11: Commit all RPG-07 source/tests/docs as a focused commit. Do not push.**
