# [KNOW-07] Semantic Smart Collections — Completion Report

- **Status:** Implemented; changes pushed to `origin/dev` on 2026-10-18 after fixing the pre-push matcher result-limit regression.
- **Completed:** 2026-10-18
- **Commits:**
  - `eda5b7a0` — Room v16 entities, DAO, migration, DAO/migration tests, spec and implementation plan.
  - `ee0e29a8` — SmartCollectionMatcher and incremental matching after actual article inserts in `AbstractRssRepository.sync()`.
  - `e1a49a70` — Smart Collection screens/routes, create/edit/delete UI, quick-save from semantic results, localization.
  - `119877d7` — Query-edit re-evaluation, cancellation-safe replace, edge-to-edge insets and tests.
  - `ab5bb7b0` — Bounded, account-scoped pagination for re-evaluation.
  - `e05eb231` — Update repository service test fixtures for mandatory matcher.
  - `2a879808` — Rollback and account-isolation tests.
- **Audit:** 9.3/10. Room CRUD/cascades and migration, matcher scoring, incremental repository insertion hook, query edits and interruption safety, account scoping, locale parity, UI flow and edge-to-edge insets are covered. Golden screenshot baseline, dedicated profiling/memory-leak/jank measurements and full automated end-to-end creation→sync coverage remain absent; lint is blocked by local Android SDK API metadata and full connected Gradle suite was accidentally run across unlocked devices, so no pass is claimed for those checks.

## Implementation

- Room schema v16 adds `smart_collection` and `smart_collection_article`, with account/article/collection cascade rules and indexes.
- `SmartCollectionMatcher` uses `SemanticSearchEngine` and the persistent embedding cache. New article candidates are matched at repository insertion time, using exact `insertedArticles`; matching errors are logged and isolated from feed sync, while coroutine cancellation is rethrown.
- Editing a collection query re-evaluates that account's articles in bounded pages. Matches replace atomically; interruption before replacement preserves previous memberships. Account mismatch is rejected.
- Compose screens provide list/detail, create/edit/delete, semantic-search quick-save and Flow overflow navigation. Status/navigation inset padding is explicit. UI strings added for all shipped locales.

## Pre-push review follow-up

- Reviewed `origin/dev..ddeebd07` (30 commits, KNOW-07 and IDEA-06) directly after independent reviewers could not start because of API rate limits. Review covered migration/entity/DAO wiring, sync insertion hook, matcher and query re-evaluation, collection UI/routes, E-Ink preference/theme/input lifecycle, and tests.
- Confirmed and fixed one blocking defect: `SemanticSearchEngine.rank()` defaults to 50 search results; collection matching must not truncate eligible memberships. Both incremental matching and paged re-evaluation now pass the candidate count explicitly as `limit`.
- Regression tests use the real semantic engine: before fix, 60 candidates produced 50 memberships and 260 candidates across two pages produced 100; after fix, every expected ID is retained.
- Latest `testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`: **PASS**, 937 tests, 0 failures/errors/skipped. No live device tests were repeated for this two-line matching fix.
- Removed one trailing whitespace line in this feature's implementation plan. Unrelated `.idea` changes are excluded from commits/push.
- Previously recorded lint and dedicated profiling/golden limitations remain; this review does not claim exhaustive concurrency or full end-to-end sync verification.

## Verification

- `./gradlew testDevDebugUnitTest` — **PASS**, 893 tests at original completion (latest combined suite: 937; see review follow-up above).
- `SmartCollectionReviewRegressionTest` uses 60 incremental and 260 paged candidate matches; all matches persist beyond the semantic search default limit of 50.
- `./gradlew assembleDevDebug` — **PASS**.
- `./gradlew compileDevDebugAndroidTestKotlin` — **PASS**.
- `adb -s emulator-5554 shell am instrument -w -e class com.mckimquyen.reader.ui.page.collection.SmartCollectionEdgeToEdgeTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner` — **PASS**, 1 test on locked Pixel emulator.
- Robolectric widget tests cover collection empty/list/count and save dialog validation; unit tests cover Room CRUD/cascades/rollback, migration, matching, account isolation, query re-evaluation and cancellation.
- `./gradlew lintDevDebug` — **BLOCKED by local toolchain metadata**: `/Users/loitran/Library/Android/sdk/platforms/android-37/data/api-versions.xml` contains `since="37.0"`; lint parser throws `NumberFormatException: For input string: "37.0"`.
- `./gradlew connectedDevDebugAndroidTest` — **NOT A VALID FEATURE RESULT**: Gradle ran on TECNO BG6 and SM-S928B as well as Pixel emulator despite the locked-device rule; suite had unrelated pre-existing device failures and instrumentation process crash. The feature's edge-to-edge instrumentation test was run directly via adb on emulator-5554 instead.
- Manual smoke check on latest build: app install/start worked on emulator, but final UI navigation/collection creation was not fully verified; no claim that an RSS sync created matching links live.
- No IAP or ad mediation tests: feature does not invoke purchases or ad APIs.
