# EXC-12 — AI Feed Autopilot (DONE)

**Audit score: 9/10.** Feed Autopilot identifies high-volume, rarely engaged feeds from local Room aggregates and offers unsubscribe only after explicit confirmation. Despite the feature name, scoring is transparent deterministic logic, not a learned model or generative AI.

## Behavior and limits

- Weekly, silent WorkManager analysis; no network request, cloud inference, push notification, Room migration, or dependency added. Room remains v20.
- Engagement means opened in Reader (`readAt`), starred, or saved for later. Bulk mark-read alone does not count as opened.
- A feed is suggested only with at least 20 articles in the analysis window, age of at least 14 days, and engagement strictly below 5%.
- Window is `min(30 days, keepArchived)`, bounded below by the date `readAt` was introduced. Retention under 14 days suppresses analysis.
- Decision records are JSON in DataStore: snooze for 30 days or keep forever. Orphan decisions are pruned.
- Unsubscribe is available only to Local accounts. The confirmation dialog warns about starred articles; no feed is deleted until the user confirms.
- Settings shows pending count as text, not a dot badge.
- “AI” is a product label: this version is a fixed heuristic. It does not model skips, dwell time, or user-specific learned preferences.

## Changes

- Domain models/scorer, defensive decisions codec, Room engagement and starred-count queries, processor, weekly worker, ViewModel/state, Compose page/dialog, route and Settings row.
- Added `feed_autopilot_error`; all new and updated strings present in 38 locale resource files, with English fallback except hand-authored English and Vietnamese.
- Added unit, Compose, and Android instrumentation coverage. No changes to database schema.

## Verification

- `./gradlew testDevDebugUnitTest --rerun-tasks`: **356 suites, 2,443 tests, 0 skipped, 0 failures, 0 errors**. XML results were current for this run.
- Focused Compose page tests: **12/12**; ViewModel tests: **15/15**; decision codec: **13/13**; scorer: **21/21**; route test: **5/5**; locale parity: **6/6**.
- Mutation checks: removing the feed deletion call failed the expected ViewModel tests; swapping Keep/Snooze callback failed the expected Compose test; changing route string failed the route test. Sources restored after each check.
- `./gradlew lintDevDebug`: **0 errors, 2,463 warnings**, equal to documented baseline; **0 EXC-12 lint hits** after wording fixes.
- `./gradlew assembleDevDebug assembleDevDebugAndroidTest`: successful. APKs: `RSS Cat Hub-2026.09.06-658bbb65.apk`, `app-dev-debug-androidTest.apk`.
- `FeedAutopilotDeviceTest` on Tecno BG6 `118743744X002560`: **OK (1 test)** in 0.651 seconds. It exercised Room aggregation on Android SQLite, 4% engagement scoring, starred count, and short-retention handling. An initial run stalled while a full-screen ad had just appeared during startup; app was force-stopped, then clean rerun passed. No instrumentation remains active.
- Smoke UI on Tecno BG6 `118743744X002560`: Settings row visible; page showed enabled switch and “Your feeds look tidy. No noisy feeds detected.” Toggling off displayed the disabled message; toggling on restored checked state; Back returned to Settings. The pre-test database had **0 feeds and 0 articles**, so no suggestion or unsubscribe dialog was smoke-tested against real feed data.
- Room and DataStore were backed up before install. After smoke, restored `Reader` and `settings.preferences_pb`; `cmp` confirmed both match backups byte-for-byte. Restored Room `PRAGMA integrity_check` returned `ok`. In-memory instrumentation left on-disk Room unchanged.

## Commits

- `2ff17f08` scorer and models
- `d8a90420` codec and DataStore values
- `a2f5e9ef` engagement DAO queries
- `d3433b65` processor and weekly worker
- `76a6f146` localized strings
- `807af3b8` ViewModel and UI state
- `2771b7f2` Compose page/dialog and error string
- `7abb04c2` route, Settings row, enabled state
- `658bbb65` lint-clean string wording
- `e4647706` on-device Room integration test

All commits remain local on `dev`; nothing pushed.
