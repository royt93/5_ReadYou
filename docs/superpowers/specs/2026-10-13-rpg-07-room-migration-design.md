# RPG-07: Room Migration and Cognitive Decay

**Status:** Approved design, implementation plan not yet written.

## Goal

Move Brain RPG persistence from `SharedPreferences` to Room without losing existing progress; make reading rewards harder to farm; add inspectable category XP decay and recovery.

## Current State (verified 2026-10-13)

- `BrainRpgRepository` stores one device-wide RPG profile in `SharedPreferences("brain_rpg_prefs")`.
- RPG-08 stores per-article `READ`, `QUIZ`, and `DOUBLE` reward IDs in SharedPreferences. `commit()` makes each ID durable, but XP mutation and reward ledger mutation are not one atomic transaction.
- `Content.kt` awards reading XP when the last visible article item reaches 75%; no minimum dwell duration.
- XP categories and streaks exist. There is no cognitive-decay worker.
- Room database is version 12; schema snapshots exported under `app/schemas`.
- `RouteName.BRAIN_RPG` opens the Brain RPG page.

## Decisions

1. **Storage:** Room, not SharedPreferences. Keep one RPG profile per device, independent of RSS account.
2. **XP source of truth:** XP earned and level remain based on non-decaying base XP. Only category XP displayed as active/retained skill decays. Never delete or decrement earned XP.
3. **Decay:** after each complete 7-day period without practice in a category, multiply displayed category XP by `0.9`, cumulatively: `floor(baseXp * 0.9^fullWeeks)`. For example, week 1 = 90%, week 2 = 81%. A qualifying reading completion or correct quiz in that category resets the multiplier to 100% by updating `lastPracticedAt`.
4. **Practice events:** qualifying article completion and a correct quiz count as practice. Opening an article, scrolling without meeting the completion gate, and an incorrect answer do not.
5. **Reading reward gate:** award once per article only when both conditions hold: continuous time in the article `>= 30 seconds` and the last visible item index is at least 80% through the index range appended by `Reader`. Progress is item-index based, not pixel-height based; body blocks count equally. The denominator includes only items appended by `Reader` (prose, images, code/video content); it excludes `Content.kt` metadata, loading spacer, quiz card, and trailing spacer/ad. `Reader` must expose the body start/end item indices (or an equivalent explicit range)—`LazyListState.layoutInfo.totalItemsCount` is not a valid denominator. Time accumulates only while the app is foreground; leaving the article or switching to another article resets that article's timer. A fling under 30 seconds cannot earn XP.
6. **Quiz/reward idempotency:** a correct quiz reward remains once per article; wrong answer increments attempts but does not consume the correct-answer reward or reset decay. Rewarded double-XP remains once per article and only after a correct quiz.
7. **Schema migration:** add Room migration `12 -> 13` to create the three RPG tables and their indices. A Room `Migration` receives a `SupportSQLiteDatabase`, not an Android `Context`, so it must not try to read SharedPreferences directly.
8. **Legacy import:** after Room has opened, a repository-level importer reads `brain_rpg_prefs` and imports the profile, category XP, streak, shield and quiz counters into Room in one Room transaction. It also imports legacy `READ`/`QUIZ`/`DOUBLE` reward ID sets into the ledger. A persistent import-complete marker is written in the same transaction. If the process dies before commit, the transaction and marker both roll back; startup retries the import. Until import succeeds, RPG mutations wait for the import instead of awarding against default Room values. The importer does not delete or rewrite the old preferences during the first release; Room becomes the source of truth after marker commit.
9. Set imported categories' `lastPracticedAt` to import time so existing XP does not decay immediately. Thereafter, only qualifying practice updates it.
10. **Reward ledger:** unique `(articleId, rewardType)` constraint, where reward type is `READ`, `QUIZ`, or `DOUBLE`. Atomically insert reward claim and update XP/profile within one Room transaction; a duplicate claim returns zero XP and does not increment quiz counters.
11. **Durability/rollback:** Room transaction is the only write path after import. A failed transaction cannot leave a reward row without its XP mutation, or XP mutation without its reward row.
12. **Notifications:** one daily summary notification lists every category currently past its first 7-day decay boundary. Continue once daily until there is qualifying practice or recovery; cancel/update when no categories are decaying. A fixed notification ID prevents duplicates. Tapping opens `RouteName.BRAIN_RPG`. If notification permission is denied, decay calculation and app UI still work without crashing.
13. **Rewarded recovery:** the user chooses one decayed category and watches one rewarded ad to reset that category's `lastPracticedAt` to now. Failed, dismissed, unavailable, or unearned ad does not change decay state. Recovery does not add base XP and does not change total XP/level. No automatic recovery offer on notification tap; the RPG page exposes the action.
14. **Scheduling:** unique periodic WorkManager job (daily cadence) computes notification contents from Room state. Category XP decay itself is calculated from timestamps when read; it does not require a worker to mutate stored XP.
15. **UI:** Brain RPG shows base XP, effective/decayed category XP, and decay indication. Show recovery action only for a category with decay and no active reward flow. New user-facing strings localized for maintained locales.
16. **Privacy:** no network requests for XP, article IDs, or category practice. Data stays on device.

**Precedence note:** importer completion marker is durable in Room, not in SharedPreferences. The old values remain untouched as a recovery copy for this release. A later cleanup task may remove them after observing stable migration in production.

## Room Schema

Three tables in one profile scope:

- `brain_rpg_profile`: singleton row (`id = 1`), base total XP, streak day count, last-read epoch day, shield state, quiz attempts/passes, and persisted level inputs needed to reconstruct the current `UserProgress`.
- `brain_rpg_category`: one row per category key, base XP, `lastPracticedAtEpochMillis`, and `lastDecayNotificationAtEpochDay` (or equivalent worker dedupe state).
- `brain_rpg_reward`: article ID, reward type, creation timestamp; unique index on `(articleId, rewardType)`.

`UserProgress` remains the UI-facing model. Level/title/progress fraction derive from base total XP; category UI derives effective XP from base XP and `lastPracticedAt` using an injected clock. Do not persist decayed XP as a new base.

## Failure and Edge-Case Rules

- Missing SharedPreferences keys migrate using existing defaults (0 XP, current streak defaults, zero quiz counts, empty reward IDs).
- Invalid/blank article IDs never receive any reward.
- Concurrent duplicate read/quiz/ad callbacks race through the Room unique constraint; exactly one transaction succeeds.
- Clock rollback yields zero completed decay weeks and never negative effective XP.
- `lastPracticedAt` in the future yields no decay until wall-clock time passes it; do not write a future timestamp.
- Worker retry or process death cannot create duplicate notification IDs or double-apply decay; worker only reads timestamp state and upserts one notification.
- A failed Room transaction must not leave a reward row without its XP mutation, or XP mutation without its reward row.
- Rewarded ad callback may arrive after the article/card is gone; repository still validates eligibility and idempotency by article ID, not ViewModel RAM.

## Testing and Acceptance

- **Unit:** decay math at 0/6d/7d/14d/21d, floor and large XP, clock rollback/future timestamp; item-based 80% + continuous 30s gate boundaries; switching article/resuming foreground resets or pauses timer as specified; blank IDs; duplicate/concurrent ledger claims; wrong quiz retry; double reward eligibility; notification aggregation and daily dedupe; migration mapping and import-marker behavior.
- **Room/instrumented:** create a version-12 DB from the exported schema, populate SharedPreferences with every profile field, all category-prefixed XP keys (including an unknown category key), and all three legacy reward-ID sets; migrate to 13; validate against `13.json`; assert base XP/streak/category values preserved, legacy reward sets copied, unique index works, and no unrelated tables changed. Assert the importer is exactly-once after process recreation; exercise failure before transaction commit, concurrent first-access attempts, and retry after rollback; confirm the marker and data commit atomically.
- **Widget:** category base/effective XP and decay label; no recovery button when fresh; category chooser/recovery action; notification permission denied state; controls hidden/disabled during pending ad; no false success after failed ad.
- **Integration:** article read passes threshold -> one READ reward + category practice reset; process/repository recreation does not duplicate; correct quiz -> one QUIZ reward, wrong answer does not consume it; rewarded double success/failure; recovery for one category does not reset others; daily worker aggregates and cancels summary notification.
- **Practice vs reward:** a qualifying read of an already-rewarded article still updates `lastPracticedAt` (no XP is re-awarded); a correct repeat quiz for an already-rewarded article still updates practice time but does not increment counters or award XP again.
- **Docs:** update RPG-01/RPG-03 and RPG-07 completion report to state the shipped Room schema, item-range 80% + 30s gate, base-vs-effective XP rule, notification cadence, and recovery behavior; remove the obsolete SharedPreferences/75% claims.
- **StoryCluster/reader progress coupling:** if Reader is changed to report appended item count/range, test multiple paragraphs, code, images, and optional video so metadata/quiz/trailing spacers are excluded and empty/loading content cannot award XP.
- **Smoke:** on Tecno only (serial `115333744A005844`), verify under-30s/fast-fling earns no XP, qualifying read earns once, correct quiz earns once, repeated/restarted flow earns none, and one-category recovery resets only that category. Stop immediately if a full-screen ad appears and wait for owner `done`.
- Build `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest`; run instrumented classes explicitly with `adb -s 115333744A005844 shell am instrument ...`, never Gradle `connected*`.

## Scope Exclusions

- No account-specific RPG profile; this preserves current device-wide behavior.
- No decay of total XP, level, streak, or ledger history.
- No new server/cloud sync for RPG data.
- No gamification/prediction/leaderboard additions.

## Open Implementation Notes

- Room API versions and migration helper must match the existing Room 2.8.4 + kotlinx-serialization pin. Prefer current project-compatible migration/schema test approach; do not bump the app-wide serialization version solely to satisfy test infrastructure without review.
- Use existing Hilt WorkManager and notification permission patterns; inspect actual project implementation before reusing paths from older task docs.
- Rewarded ad provider callbacks are asynchronous: add a repository eligibility check at claim time, not only a UI check before showing the ad.
