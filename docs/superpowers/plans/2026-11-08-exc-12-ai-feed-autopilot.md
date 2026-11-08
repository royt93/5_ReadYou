# EXC-12 AI Feed Autopilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a 100% on-device, privacy-preserving feed clean-up assistant that identifies noisy, unread feeds, explains the reason with real numbers, and presents three user-confirmed choices (unsubscribe, snooze for 30 days, keep forever) without ever automatically deleting a feed.

**Architecture:** Pure Kotlin domain scorer calculates noise score based on article engagement ratio (`readAt IS NOT NULL`, `isStarred = 1`, or `isReadLater = 1`) over a safe window bounded by the account's `keepArchived` retention period. User decisions are persisted as a JSON map in DataStore (Room stays at v20). A weekly WorkManager periodic worker updates a silent badge count for the Settings row, and the dedicated Compose page recomputes fresh stats on open, presenting clean confirmation dialogs with starred-article deletion warnings before calling `AbstractRssRepository.deleteFeed` on Local accounts.

**Tech Stack:** Kotlin, Room v20 (no schema change), DataStore (Preferences), WorkManager, Jetpack Compose / Material You, Hilt DI, JUnit4 / MockK / Robolectric, AndroidX test runner on Tecno KJ7.

## Global Constraints

- Device target: **Tecno KJ7 `115333744A005844` ONLY**. S24U is broken; BG6 not attached. Never address another serial.
- Never run `connectedDevDebugAndroidTest`.
- Fresh Room + DataStore backup before device smoke, restore and `cmp` after, never delete DataStore.
- Close full-screen ads only after a fresh uiautomator dump and foreground check via the labelled control; skip banners.
- Room database version stays at **v20** (no migration, no schema JSON change).
- No new external dependency; no network calls; no cloud AI or Gemini API; no system push notifications.
- Local accounts only for one-tap unsubscribe; remote accounts (Fever, Google Reader) show an explanatory message.
- All new strings in all 38 maintained `strings.xml` files; English and Vietnamese authored by hand, other 36 locales use English fallback.
- TDD: write test first, verify failure for the expected reason, write minimal implementation, verify pass, focused commit.

---

## File Map

### New Domain Files (`app/src/main/java/com/mckimquyen/reader/domain/autopilot/`)
- `FeedAutopilotModels.kt` — Data classes: `FeedNoiseDecision` (enum `SNOOZE_30_DAYS`, `KEEP_FOREVER`), `FeedDecisionRecord`, `FeedNoiseScore`, `FeedAutopilotSuggestion`, `FeedAutopilotState`.
- `FeedAutopilotScorer.kt` — Pure Kotlin scoring engine: calculates engagement ratio, checks feed age and article count, filters snoozed/kept feeds, formats objective explanation strings.
- `FeedAutopilotDecisionsCodec.kt` — JSON serializer/deserializer for user decisions map stored in DataStore string pref, with automatic expired-snooze pruning and orphan feed cleanup.

### New Infrastructure & Worker Files
- `domain/sv/FeedAutopilotWorker.kt` — 7-day periodic `CoroutineWorker` registered in `RApp.workerInit()`, computing pending noisy feed count and updating silent badge pref.
- `domain/sv/FeedAutopilotProcessor.kt` — Domain service orchestrating DAO stats query, decisions map, and scorer execution.

### Modified Production Files
- `domain/repository/ArticleDao.kt` — Add `queryFeedEngagementStats(accountId, sinceMillis)` and `queryStarredCountByFeedId(accountId, feedId)`.
- `ui/ext/ExtDataStore.kt` — Add `DataStoreKeys.FeedAutopilotEnabled`, `FeedAutopilotDecisions`, `FeedAutopilotPendingCount`, with getters and flow helpers.
- `ui/page/common/RouteName.kt` — Add `FEED_AUTOPILOT = "feed_autopilot"`.
- `ui/page/common/HomeEntry.kt` — Add destination route for `FEED_AUTOPILOT`.
- `ui/page/setting/SettingsPage.kt` — Add a "Feed Autopilot" `SelectableSettingGroupItem` under `ARCHIVE_QUERY`. That composable has NO badge parameter (only `title`, `desc`, `icon`, `onClick`, `enable`, `selected`), so the pending count is shown by appending it to `desc` instead of adding a new UI component.
- `RApp.kt` — Enqueue `FeedAutopilotWorker.enqueueWeeklyWork(workManager)` in `workerInit()`.
- `app/src/main/res/values/strings.xml` and 37 `values-*/strings.xml` — Add 16 localized resource keys.

### New UI Files (`app/src/main/java/com/mckimquyen/reader/ui/page/autopilot/`)
- `FeedAutopilotUiState.kt` — Sealed UI state: `Loading`, `Clean`, `HasSuggestions`, `NotLocalAccount`, `RetentionTooShort`, `Disabled`.
- `FeedAutopilotViewModel.kt` — ViewModel owning calculation, snooze/keep decisions, and delete feed confirmation.
- `FeedAutopilotPage.kt` — Compose screen with `BaseScaffold`, Back button, summary card, suggestion cards, and starred-warning dialog.

### Test Files
- `app/src/test/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotScorerTest.kt`
- `app/src/test/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotDecisionsCodecTest.kt`
- `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoFeedEngagementTest.kt`
- `app/src/test/java/com/mckimquyen/reader/domain/sv/FeedAutopilotProcessorTest.kt`
- `app/src/test/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotViewModelTest.kt`
- `app/src/test/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotPageTest.kt`
- `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/autopilot/FeedAutopilotDeviceTest.kt`

---

## Task 1: Domain Models and Pure Scorer Logic

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotModels.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotScorer.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotScorerTest.kt`

**Interfaces:**
- `enum class FeedDecisionType { SNOOZE_30_DAYS, KEEP_FOREVER }`
- `data class FeedDecisionRecord(val type: FeedDecisionType, val decidedAtMillis: Long)`
- `data class FeedAutopilotSuggestion(val feedId: String, val feedName: String, val windowTotal: Int, val windowEngaged: Int, val engagementRatio: Float, val windowDays: Int)`
- `FeedAutopilotScorer.evaluate(...)`: pure function taking stats, decisions, feed metadata, current timestamp, and retention settings.

- [ ] **Step 1: Write failing unit tests for FeedAutopilotScorer.**
  - Feed with < 20 articles in window: not flagged as noisy.
  - Feed with ≥ 20 articles but feed age (`MIN(updateAt)`) < 14 days: not flagged.
  - Feed with engagement ratio = 5.0% (e.g. 1 / 20): not flagged.
  - Feed with engagement ratio < 5.0% (e.g. 0 / 20 or 1 / 21): flagged as noisy.
  - Starred or readLater articles count towards engagement.
  - Feed with active `SNOOZE_30_DAYS` decision (< 30 days old): excluded.
  - Feed with expired `SNOOZE_30_DAYS` decision (≥ 30 days old): evaluated normally.
  - Feed with `KEEP_FOREVER` decision: always excluded.
  - Retention mapping: `KeepArchivedPreference.Always` (value `0L`) is treated as 30 days, NOT as 0 (otherwise it would be rejected as too short); `For1Day`/`For2Days`/`For3Days`/`For1Week` (< 14 days) return `RetentionTooShort`; `For2Weeks` (14 days) and `For1Month` are accepted, with window = min(30 days, retention).
  - Articles with `updateAt < READ_AT_INTRODUCED` (2026-10-18T00:00Z) are excluded from window evaluation.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.autopilot.FeedAutopilotScorerTest'
  ```
  Expected: FAIL with compilation error (unresolved references).

- [ ] **Step 3: Implement FeedAutopilotModels.kt and FeedAutopilotScorer.kt.**
  - Define `READ_AT_INTRODUCED = 1792281600000L` (2026-10-18T00:00:00Z).
  - Define `MIN_ARTICLES = 20`, `MIN_FEED_AGE_DAYS = 14`, `MAX_ENGAGEMENT_RATIO = 0.05f`.
  - Implement evaluation logic strictly matching spec section 4.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.autopilot.FeedAutopilotScorerTest'
  ```
  Expected: PASS (all cases green).

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/autopilot/ app/src/test/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotScorerTest.kt
  git commit -m "feat(exc-12): add feed autopilot domain models and scoring logic"
  ```

---

## Task 2: Decisions JSON Codec and DataStore Storage

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotDecisionsCodec.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotDecisionsCodecTest.kt`

**Interfaces:**
- `FeedAutopilotDecisionsCodec.encode(decisions: Map<String, FeedDecisionRecord>): String`
- `FeedAutopilotDecisionsCodec.decode(json: String?, nowMillis: Long, validFeedIds: Set<String>?): Map<String, FeedDecisionRecord>`
- DataStore keys: `DataStoreKeys.FeedAutopilotEnabled` (Boolean), `DataStoreKeys.FeedAutopilotDecisions` (String), `DataStoreKeys.FeedAutopilotPendingCount` (Int).

- [ ] **Step 1: Write failing unit tests for FeedAutopilotDecisionsCodec.**
  - Round-trip encode and decode for snooze and keep decisions.
  - Corrupt or empty JSON returns empty map safely without throwing.
  - Expired `SNOOZE_30_DAYS` (older than 30 days based on `nowMillis`) is automatically omitted on decode.
  - Orphan feed IDs not in `validFeedIds` are pruned on decode.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.autopilot.FeedAutopilotDecisionsCodecTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement Codec using org.json (standard Android/Java library).**
  - Serialize map to `JSONObject` with `type` and `decidedAtMillis`.
  - Deserialize safely with `optJSONObject`, checking expiration and valid feed IDs.
  - Add DataStore preference keys and context extensions in `ExtDataStore.kt`.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.autopilot.FeedAutopilotDecisionsCodecTest'
  ```
  Expected: PASS.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotDecisionsCodec.kt app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/test/java/com/mckimquyen/reader/domain/autopilot/FeedAutopilotDecisionsCodecTest.kt
  git commit -m "feat(exc-12): add feed autopilot decisions JSON codec and preferences"
  ```

---

## Task 3: DAO Engagement Aggregation Queries (FIX-17)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoFeedEngagementTest.kt`

**Interfaces:**
- `data class FeedEngagementStat(val feedId: String, val windowTotal: Int, val windowEngaged: Int, val oldestUpdateAtMillis: Long?)`
- `ArticleDao.queryFeedEngagementStats(accountId: Int, sinceMillis: Long): List<FeedEngagementStat>`
- `ArticleDao.queryStarredCountByFeedId(accountId: Int, feedId: String): Int`

- [ ] **Step 1: Write failing Room native SQLite integration tests.**
  - Calculate `windowTotal` and `windowEngaged` strictly within `updateAt >= sinceMillis`.
  - Articles outside the window (`updateAt < sinceMillis`) are excluded from window totals but contribute to `oldestUpdateAtMillis`.
  - `engagedArticles` includes articles where `readAt IS NOT NULL`, `isStarred = 1`, or `isReadLater = 1`.
  - Multi-account isolation: stats only return rows for the requested `accountId`.
  - `queryStarredCountByFeedId` correctly counts starred articles for a specific feed and account.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.repository.ArticleDaoFeedEngagementTest'
  ```
  Expected: FAIL with compilation error (missing queries).

- [ ] **Step 3: Add queries in ArticleDao.kt matching spec section 6.**
  - Ensure no body columns (`rawDescription`, `fullContent`) are selected.
  - Verify `Date` to `Long` converter behavior in Room.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.repository.ArticleDaoFeedEngagementTest'
  ```
  Expected: PASS on native SQLite.

- [ ] **Step 5: Verify Room schema is unchanged.**
  ```bash
  git diff -- app/schemas/
  ```
  Expected: empty output (Room remains at v20).

- [ ] **Step 6: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoFeedEngagementTest.kt
  git commit -m "feat(exc-12): add feed engagement aggregation queries to ArticleDao"
  ```

---

## Task 4: Autopilot Processor & Weekly Worker

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/FeedAutopilotProcessor.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/FeedAutopilotWorker.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/RApp.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/FeedAutopilotProcessorTest.kt`

**Interfaces:**
- `class FeedAutopilotProcessor @Inject constructor(@ApplicationContext context: Context, accountDao: AccountDao, feedDao: FeedDao, articleDao: ArticleDao, @IODispatcher ioDispatcher: CoroutineDispatcher)`. Reads the account with `accountDao.queryById(context.currentAccountId)`; account type check uses `context.currentAccountType == AccountType.Local.id`. Feeds come from `feedDao.queryAll(accountId)`.
- `suspend fun computeSuggestions(nowMillis: Long = System.currentTimeMillis()): FeedAutopilotEvaluation` (account id comes from `context.currentAccountId`)
- `class FeedAutopilotWorker @AssistedInject constructor(...) : CoroutineWorker(...)`
- `FeedAutopilotWorker.enqueueWeeklyWork(workManager: WorkManager)`

- [ ] **Step 1: Write failing unit tests for FeedAutopilotProcessor.**
  - Non-Local account types return `NotLocalAccount` outcome.
  - Account with `keepArchived < For2Weeks` returns `RetentionTooShort`.
  - Disabled feature toggle returns `Disabled`.
  - Generates correct sorted suggestion list and updates pending count pref.
  - Prunes orphan decisions when feeds have been deleted.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.sv.FeedAutopilotProcessorTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement Processor, Worker, and register in RApp.kt.**
  - `FeedAutopilotProcessor` injects DAOs, Context, and CoroutineDispatcher.
  - `FeedAutopilotWorker` runs once every 7 days (`PeriodicWorkRequestBuilder<FeedAutopilotWorker>(7, TimeUnit.DAYS)`), unique name `FeedAutopilotWeeklyWorker`, policy `KEEP`.
  - Register `FeedAutopilotWorker.enqueueWeeklyWork(workManager)` in `RApp.workerInit()`.

- [ ] **Step 4: Run processor unit tests.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.sv.FeedAutopilotProcessorTest'
  ```
  Expected: PASS.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/sv/ app/src/main/java/com/mckimquyen/reader/RApp.kt app/src/test/java/com/mckimquyen/reader/domain/sv/FeedAutopilotProcessorTest.kt
  git commit -m "feat(exc-12): add feed autopilot background processor and weekly worker"
  ```

---

## Task 5: Localized Strings for 38 Locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: all 37 `app/src/main/res/values-*/strings.xml`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/StringResourceParityTest.java`

**Keys (16 keys from spec section 9):**
- `feed_autopilot_title`, `feed_autopilot_desc`, `feed_autopilot_enabled`, `feed_autopilot_summary_noisy`, `feed_autopilot_summary_clean`, `feed_autopilot_reason_opened`, `feed_autopilot_reason_zero`, `feed_autopilot_action_unsubscribe`, `feed_autopilot_action_snooze`, `feed_autopilot_action_keep`, `feed_autopilot_confirm_title`, `feed_autopilot_confirm_message`, `feed_autopilot_confirm_starred_warning`, `feed_autopilot_not_local_account`, `feed_autopilot_retention_too_short`, `feed_autopilot_disabled_message`.

- [ ] **Step 1: Add EN and VI strings into values/strings.xml and values-vi/strings.xml.**
  - Avoid nouns directly following number formats to prevent `PluralsCandidate` warnings.
  - Escape XML entities properly (`\'`, `&amp;`).

- [ ] **Step 2: Add English fallback strings to remaining 36 values-*/strings.xml files.**
  - Ensure identical placeholder signatures (`%1$d`, `%2$d`, `%1$s`).
  - Validate with `xmllint --noout`.

- [ ] **Step 3: Run StringResourceParityTest.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest'
  ```
  Expected: PASS (6/6 tests green, no missing or extra keys).

- [ ] **Step 4: Commit.**
  ```bash
  git add app/src/main/res/values*/strings.xml
  git commit -m "feat(exc-12): add feed autopilot strings across all 38 locales"
  ```

---

## Task 6: ViewModel and State Handling

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotUiState.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotViewModelTest.kt`

**Interfaces:**
- `FeedAutopilotViewModel.refresh()`
- `FeedAutopilotViewModel.toggleEnabled(enabled: Boolean)`
- `FeedAutopilotViewModel.snoozeFeed(feedId: String)`
- `FeedAutopilotViewModel.keepFeed(feedId: String)`
- `FeedAutopilotViewModel.confirmUnsubscribe(feed: Feed)` — calls `rssSv.get().deleteFeed(feed)`
- `FeedAutopilotViewModel.requestUnsubscribe(feed: Feed)` (queries starred count for dialog)

- [ ] **Step 1: Write failing unit tests for FeedAutopilotViewModel.**
  - Emits `Loading` initially, then transitions to `HasSuggestions` or `Clean`.
  - `snoozeFeed` adds decision to DataStore and removes item from current state.
  - `keepFeed` adds `KEEP_FOREVER` to DataStore and removes item.
  - `requestUnsubscribe` loads starred count and prepares confirmation state.
  - `confirmUnsubscribe` calls `deleteFeed(feed)`, updates UI state, and removes item.
  - Non-Local account emits `NotLocalAccount`.
  - Short retention period emits `RetentionTooShort`.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.autopilot.FeedAutopilotViewModelTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement ViewModel and State.**
  - Inject `@ApplicationContext Context`, `FeedAutopilotProcessor`, `AbstractRssRepository`, `ArticleDao`, and `@IODispatcher CoroutineDispatcher`.
  - Mark stored context with `@ApplicationContext` to avoid lint `StaticFieldLeak`.
  - Use Kotlin Coroutines `viewModelScope.launch(ioDispatcher)`.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.autopilot.FeedAutopilotViewModelTest'
  ```
  Expected: PASS.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/ui/page/autopilot/ app/src/test/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotViewModelTest.kt
  git commit -m "feat(exc-12): add feed autopilot ViewModel and UI state"
  ```

---

## Task 7: Compose UI Page & Dialogs

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotPage.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotPageTest.kt`

**Tags:**
- `AUTOPILOT_SWITCH_TAG`, `AUTOPILOT_CARD_TAG_PREFIX`, `AUTOPILOT_UNSUB_TAG_PREFIX`, `AUTOPILOT_SNOOZE_TAG_PREFIX`, `AUTOPILOT_KEEP_TAG_PREFIX`, `AUTOPILOT_CONFIRM_DIALOG_TAG`.

- [ ] **Step 1: Write Robolectric Compose UI tests for FeedAutopilotPage.**
  - Back button calls `onBack`.
  - Toggle switch reflects enabled state.
  - Shows empty/clean card when no suggestions exist.
  - Renders suggestion card with real stats and numbers.
  - Tapping "Unsubscribe" opens confirmation dialog with starred warning.
  - Dialog confirm button calls delete callback.
  - Shows warning screen for non-Local accounts.
  - Shows warning screen for short retention settings.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.autopilot.FeedAutopilotPageTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement FeedAutopilotPage composable.**
  - Use `BaseScaffold(navigationIcon = { FeedbackIconButton(imageVector = Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface, onClick = onBack) }, content = { ... })` exactly like `ArchiveQueryPage`; `content` is a named parameter, not a trailing lambda with the closing `}` of `Column` moved out.
  - Use `@OptIn(ExperimentalMaterial3Api::class)` on the page (Material3 1.0.1 marks `OutlinedTextField`/`TopAppBar` experimental; do not use `minLines`, it does not exist in this version).
  - Use `AlertDialog` for unsubscribe confirmation with prominent starred warning.
  - Support TalkBack labels and safe drawing insets.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.autopilot.FeedAutopilotPageTest'
  ```
  Expected: PASS.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/autopilot/FeedAutopilotPageTest.kt
  git commit -m "feat(exc-12): render feed autopilot suggestions screen and confirmation dialog"
  ```

---

## Task 8: Navigation Wiring & Settings Entry

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/common/HomeEntryNavigationTest.kt`

- [ ] **Step 1: Write failing navigation unit tests.**
  - Verify `RouteName.FEED_AUTOPILOT == "feed_autopilot"` and is unique among all routes.
  - Verify route integration in `HomeEntryNavigationTest`.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.common.HomeEntryNavigationTest'
  ```
  Expected: FAIL.

- [ ] **Step 3: Wire RouteName, HomeEntry, and SettingsPage.**
  - Add `const val FEED_AUTOPILOT = "feed_autopilot"` in `RouteName.kt`.
  - Register `animatedComposable(route = RouteName.FEED_AUTOPILOT)` in `HomeEntry.kt` with route-scoped `hiltViewModel()`.
  - Add an `item { SelectableSettingGroupItem(...) }` in `SettingsPage.kt` right after the `ARCHIVE_QUERY` item. Read the pending count with `LocalContext.current.dataStore` (as other Settings rows read preferences) and append it to `desc` when greater than 0. Use an icon already imported in the file or add one import after confirming it exists in `material-icons-extended:1.2.0-beta02`.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.common.HomeEntryNavigationTest'
  ```
  Expected: PASS.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/ui/page/common/ app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/common/HomeEntryNavigationTest.kt
  git commit -m "feat(exc-12): wire feed autopilot route and settings navigation"
  ```

---

## Task 9: Full Suite, Lint Baseline & APK Builds

**Files:**
- Verification only (no functional code changes unless fixing a regression).

- [ ] **Step 1: Run the full devDebug unit test suite from scratch.**
  ```bash
  ./gradlew testDevDebugUnitTest --rerun
  ```
  Expected: BUILD SUCCESSFUL, 0 failures, 0 errors.

- [ ] **Step 2: Run Android Lint.**
  ```bash
  ./gradlew lintDevDebug
  ```
  Expected: 0 errors, exactly 2,463 warnings (no new warnings introduced, 0 EXC-12 issues).

- [ ] **Step 3: Build debug APK and androidTest APK.**
  ```bash
  ./gradlew assembleDevDebug assembleDevDebugAndroidTest
  ```
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify git status is clean.**
  ```bash
  git status --short | grep -v '.idea'
  ```
  Expected: clean working directory.

---

## Task 10: On-Device Instrumentation & Smoke Test on Tecno KJ7

**Target device:** Tecno KJ7 (`115333744A005844`). Never touch S24U or other serials.

- [ ] **Step 1: Create on-device integration test.**
  - Create `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/autopilot/FeedAutopilotDeviceTest.kt`.
  - Test in-memory Room database on live Android SQLite runtime evaluating noisy feeds and retention limits.
  - Compile and run via `adb -s 115333744A005844 shell am instrument -w -r -e class com.mckimquyen.reader.infrastructure.autopilot.FeedAutopilotDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`.
  - Expected: `OK (1 test)`.

- [ ] **Step 2: Backup Room and DataStore on KJ7.**
  - Force-stop `com.mckimquyen.reader`.
  - Copy `databases/Reader*` and `files/datastore/settings.preferences_pb` to `/tmp/exc12-kj7-backup/`.
  - Verify sha256 checksums and `PRAGMA integrity_check`.

- [ ] **Step 3: Install APK and smoke UI on KJ7.**
  - Install the newest APK with `adb -s 115333744A005844 install --user 0 -r "app/build/outputs/apk/dev/debug/RSS Cat Hub-<version>-<hash>.apk"` (the file name contains spaces; take it with `ls -t ... | head -1`).
  - Launch with `adb -s 115333744A005844 shell am start --user 0 -n com.mckimquyen.reader/.infrastructure.android.SplashActivity` (the real launcher is `infrastructure.android.SplashActivity`; `ui.page.common.MainActivity` does not exist). If full-screen ad appears, dump UI, verify foreground `AdActivity`, and close via labelled button.
  - Navigate: Settings → "Feed Autopilot".
  - Verify screen displays correct state with real 152-article archive.
  - Test action buttons (snooze, keep, unsubscribe dialog warning).
  - Verify Back button returns cleanly to Settings.

- [ ] **Step 4: Restore Room and DataStore on KJ7.**
  - Force-stop app.
  - Stream original backup files back to device.
  - Verify with `cmp` that `Reader` and `settings.preferences_pb` match backup 100%.

- [ ] **Step 5: Write DONE completion report and update tracking docs.**
  - Create `doc/task/done/EXC-12_ai-feed-autopilot_DONE.md` with audit score, commits, test evidence, and limitations.
  - Move/delete `doc/task/todo/EXC-12_ai-feed-autopilot.md`.
  - Update `doc/feature.md` and `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md`.
  - Commit documentation on `dev`. Do not push.
