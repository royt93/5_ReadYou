# [IDEA-01] Reading Habits & Heatmap — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed.
- **Completed:** 2026-10-19
- **Audit:** 9.2/10. Room v17 migration, Reader write path, aggregate queries, pure streak/heat logic, all UI states, localization and edge-to-edge insets are covered by tests. Live smoke verified first-open counting and no double-count on re-open. Known limitations: `readAt` only starts accumulating after upgrade; Fever `REPLACE` may reset it if a previously stored article is fetched again; rare stale `ReadLaterWorker` entity update can overwrite a concurrent `readAt`; streak history is limited to the 365-day window. No golden/performance profiling was run.
- **Commits:**
  - `4f5daf2f` — docs: add IDEA-01 Reading Habits & Heatmap design
  - `cc83873a` — docs: add IDEA-01 Reading Habits & Heatmap implementation plan
  - `b46a6bc9` — feat(db): add article readAt (Room v17) and reading statistics queries
  - `ea203011` — feat(domain): add reading streak, heat level and stats assembler
  - `3c6da554` — feat(reader): record first read time when an article is opened
  - `8dc4296e` — feat(ui): add ReadingStatisticsViewModel and state
  - `8f2eb9f7` — feat(i18n): localize reading statistics across all 38 maintained locales
  - `f2d33450` — feat(ui): add reading heatmap component
  - `e61e9491` — feat(ui): add reading statistics page and Settings entry
  - `cad6b231` — test(statistics): add edge-to-edge instrumentation test

## Implementation

- Room schema is now v17. `Article.readAt: Long?` is added via `MIGRATION_16_17`; no backfill. `ArticleDao.markOpened()` writes the first epoch-millisecond timestamp with `COALESCE`, scoped to article and account. DAO also provides day counts, total read count and ordered top feeds.
- Only opening an article in `ReadingPage` calls `ReadingViewModel.recordOpened()`. Existing mark-read toggles, mark-all-as-read and sync paths do not write `readAt`.
- Pure domain helpers calculate local-day streaks, heat levels, averages and the 365-day window. The streak remains active through today if yesterday was the latest read day. Words-per-minute and reading duration are not included.
- `ReadingStatisticsViewModel` exposes Loading/Empty/Content/Error, runs DB work on `@IODispatcher`, scopes by current account and refreshes on resume.
- `ReadingHeatmap` renders a 53-week × 7-row grid. `ReadingStatisticsPage` shows summary cards, heatmap, streak badges and top five feeds; Settings contains the entry. Empty/error states include copy and retry behavior. Explicit system bar insets and lifecycle observer disposal are covered.
- All 38 maintained locale directories include 14 new strings and three plural resources. Vietnamese retains full diacritics; plural categories cover the language-specific forms in Arabic, Czech, Hebrew, Polish, Romanian, Russian, Serbian, Slovenian and Ukrainian.

## Verification

- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`: **PASS**, 999 unit tests, 0 failures, 0 errors, 0 skipped; build and Android-test Kotlin compilation pass.
- New tests cover migration 16→17, DAO account/day/top-feed queries and first-write semantics, calendar zone/window logic, streak edge cases, heat-level thresholds, stats assembly, Reader persistence/no-op/failure behavior, ViewModel state and cancellation, heatmap layout/accessibility, page empty/error/content/badges, and UI navigation state rendering.
- `StringResourceParityTest`: **6/6 PASS**; all locale XML files parse; all 38 contain every new key and plural.
- `emulator-5554`, isolated `EInkUiTestRunner`: `ReadingStatisticsEdgeToEdgeTest` **2/2 PASS** (empty/content body and title clear system bar insets; heatmap rendered for content).
- Live smoke on `emulator-5554`, screenshots captured from the actual device:
  - Settings shows “Reading Statistics”; opening before any `readAt` shows empty state.
  - Opened “Toxic algorithms wouldn't let them go - now Australia wants an off switch” in Reader. Room then contained exactly one non-null `readAt` for this article. Statistics showed Total read 1, Today 1, Current streak 1 day, Longest streak 1 day, average 1.0, one heatmap cell and BBC News as top source.
  - Re-opened the same article. DB still contained exactly one non-null `readAt` row; total did not increase.
  - Test ads appeared as full-screen interstitials on back navigation. Smoke paused until the user said “done”; no further interaction occurred while an interstitial covered the UI. A small Reader banner did not cover content and was skipped as the user directed.
  - Restored `/tmp/stats_db_backup/Reader`, relaunched app, verified the app migrated DB to version 17, all 21 original articles remained, `readAt IS NOT NULL` count was zero, and no smoke-test rows remained.
- Every live Android command targeted `emulator-5554` using `adb -s emulator-5554`; `connectedDevDebugAndroidTest` was not run.
- `lintDevDebug` was not run; known local Android SDK 37 metadata issue remains. No lint pass is claimed.

## Limits and known issues

- No historical backfill. Articles read before v17 have `readAt = NULL`; heatmap and streak history accumulate from this upgrade onward.
- Fever sync inserts through `ArticleDao.insert` (`OnConflictStrategy.REPLACE`); fetching a previously stored Fever article can replace its row and reset `readAt` to NULL. This is documented but not changed in IDEA-01.
- `ReadLaterWorker` updates an `Article` entity read before fetching full content. A rare concurrent Reader open between that read and update can overwrite the new `readAt` with the stale null. This remains an accepted edge case.
- Current/longest streaks are calculated from the 365-day heatmap window; streaks longer than the window are capped.
- No words-per-minute, reading-duration timer, golden screenshots, Macrobenchmark/Perfetto profiling, network conditioning or monetization testing (out of scope).
