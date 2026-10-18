# [IDEA-07] On This Day Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface read articles published on today's calendar date in previous years as a FeedsPage card (max 5) and a dedicated overflow screen with empty/error states.

**Architecture:** Pure `OnThisDayCalendar` domain helper extracts zero-padded `monthDay` (MM-dd) and `currentYear` from an injected Clock and ZoneId. `ArticleDao` queries read articles (`isUnread = 0`) using SQLite `strftime('%m-%d', date/1000, 'unixepoch', 'localtime')` matching `monthDay` and year `< currentYear`, scoped by `accountId` and capped at 5. `OnThisDayViewModel` runs the query on `Dispatchers.IO`, exposes `OnThisDayUiState`, and refreshes on calendar changes. A compact card is composed in `FeedsPage` (hidden when empty/error or in AddSources mode) and a dedicated screen is routed through `RouteName.ON_THIS_DAY` via `FlowPage` overflow menu.

**Tech Stack:** Kotlin 2.1.0, AndroidX Room, Jetpack Compose, Material 3, Hilt, JUnit4, MockK, Robolectric 4.14.1, AndroidX Compose test.

## Global Constraints

- Match on the device's current local calendar date.
- Match `isUnread = false` articles by publication `date` month/day.
- Include every earlier year, not only last year.
- Order by `date DESC`, then `id DESC`, and return at most 5.
- Scope to the current account only.
- Do not map Feb 29 to Feb 28 or Mar 1.
- No Room migration, `readAt`, AI, FTS, notification, share action or setting toggle.
- Queries run on `@IODispatcher`; ViewModels never hold `Context` or `Activity`.
- Localize all user-facing strings across all 38 maintained `values-*/strings.xml` directories; keep `StringResourceParityTest` green.
- Run live Android tests only on `emulator-5554` via `adb -s emulator-5554`.
- Keep `.idea/workspace.xml` and `.idea/deviceManager.xml` untouched.

---

### Task 1: Domain Calendar Helper & Room DAO Query (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/onthisday/OnThisDayCalendar.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/onthisday/OnThisDayCalendarTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoOnThisDayTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class OnThisDayTarget(val monthDay: String, val currentYear: String)
  class OnThisDayCalendar(private val clock: java.time.Clock = java.time.Clock.systemDefaultZone(), private val zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()) {
      fun currentTarget(): OnThisDayTarget
  }
  ```
- Produces in `ArticleDao`:
  ```kotlin
  @Transaction
  @Query(
      """
      SELECT * FROM article
      WHERE accountId = :accountId
        AND isUnread = 0
        AND strftime('%m-%d', date / 1000, 'unixepoch', 'localtime') = :monthDay
        AND strftime('%Y', date / 1000, 'unixepoch', 'localtime') < :currentYear
      ORDER BY date DESC, id DESC
      LIMIT :limit
      """
  )
  suspend fun queryOnThisDay(
      accountId: Int,
      monthDay: String,
      currentYear: String,
      limit: Int = 5,
  ): List<ArticleWithFeed>
  ```

- [ ] **Step 1: Write calendar and DAO tests (RED)**
  - `OnThisDayCalendarTest`: test zero-padded `MM-dd` format (e.g. `02-05`), current year extraction, leap day `02-29`, timezone boundaries around midnight.
  - `ArticleDaoOnThisDayTest` (Robolectric with in-memory Room):
    - Seeds read (`isUnread = 0`) articles on today-last-year, today-two-years-ago, today-this-year (excluded), today-unread (excluded), yesterday-last-year (excluded), another-account (excluded).
    - Asserts matches returned newest first, capped at 5, tie-broken by id.
    - Asserts Feb 29 behavior without fallback.
- [ ] **Step 2: Run targeted tests; confirm RED**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.onthisday.OnThisDayCalendarTest" --tests "com.mckimquyen.reader.domain.repository.ArticleDaoOnThisDayTest"`
  Expected: compilation failure (missing types/methods).
- [ ] **Step 3: Implement `OnThisDayCalendar` and add `ArticleDao.queryOnThisDay`**
  - Create `OnThisDayCalendar.kt` with pure `java.time` conversions.
  - Add query to `ArticleDao.kt`.
  - Confirm with the DAO test that stored `Date` epoch milliseconds and SQLite `localtime` agree under the test default time zone; if a time-zone assumption fails, change the query before continuing.
- [ ] **Step 4: Run targeted tests; confirm GREEN**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.onthisday.OnThisDayCalendarTest" --tests "com.mckimquyen.reader.domain.repository.ArticleDaoOnThisDayTest"`
  Expected: all tests pass.
- [ ] **Step 5: Commit**
  `git commit -m "feat(domain): add OnThisDay calendar helper and Room query"`

---

### Task 2: ViewModel & State Management (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/onthisday/OnThisDayUiState.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/onthisday/OnThisDayViewModel.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/onthisday/OnThisDayViewModelTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface OnThisDayUiState {
      object Loading : OnThisDayUiState
      object Empty : OnThisDayUiState
      data class Content(
          val articles: List<ArticleWithFeed>,
          val monthDay: String,
          val currentYear: Int,
      ) : OnThisDayUiState
      data class Error(val messageRes: Int) : OnThisDayUiState
  }
  ```
- Produces `OnThisDayViewModel`:
  ```kotlin
  @HiltViewModel
  class OnThisDayViewModel @Inject constructor(
      @ApplicationContext private val context: Context,
      private val articleDao: ArticleDao,
      private val calendar: OnThisDayCalendar,
      @IODispatcher private val ioDispatcher: CoroutineDispatcher,
  ) : ViewModel() {
      val state: StateFlow<OnThisDayUiState>
      fun refresh()
  }
  ```

- [ ] **Step 1: Write ViewModel tests (RED)**
  - Initial emission is `Loading`.
  - When articles exist, emits `Content(articles, monthDay)` on `ioDispatcher`.
  - When DAO returns empty, emits `Empty`.
  - When DAO throws, catches and emits `Error`, without crashing.
  - Calling `refresh()` triggers a re-query; a fresh calendar date (fake clock moved to next day) produces a new `monthDay`.
  - Uses the account from `context.currentAccountId` and never queries another account.
- [ ] **Step 2: Run targeted tests; confirm RED**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.onthisday.OnThisDayViewModelTest"`
- [ ] **Step 3: Implement `OnThisDayUiState` and `OnThisDayViewModel`**
  - Implement state machine with `StateFlow`.
  - Use `@ApplicationContext` only to read `context.currentAccountId` (the existing safe default-1 helper in `ExtDataStore.kt`); do not call `AccountSv.getCurrentAccount()` because it uses `!!`. Never store an Activity or Compose/Navigation object.
- [ ] **Step 4: Run targeted tests; confirm GREEN**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.onthisday.OnThisDayViewModelTest"`
- [ ] **Step 5: Commit**
  `git commit -m "feat(ui): add OnThisDayViewModel and state machine"`

---

### Task 3: Localization across All 38 Locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: every `app/src/main/res/values-*/strings.xml` (all 37 non-base locales)

**Required string keys:**
- `on_this_day_title`: "On This Day"
- `on_this_day_desc`: "Articles you've read that were published on this day in earlier years"
- `on_this_day_empty`: "No read articles found from this day in earlier years"
- `on_this_day_error`: "Could not load articles. Tap to retry."
- `on_this_day_years_ago`: plural resource with `one` = "1 year ago" and `other` = "%1$d years ago" (plurals, not two plain strings, so all locales can pluralize correctly)

- [ ] **Step 1: Add strings to `values/strings.xml`**
- [ ] **Step 2: Translate and append to all 37 `values-*/strings.xml`**
  Use Python script to ensure exact key set parity without manual typos.
- [ ] **Step 3: Run parity test**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`
  Expected: PASS.
- [ ] **Step 4: Commit**
  `git commit -m "feat(i18n): localize On This Day across all 38 maintained locales"`

---

### Task 4: FeedsPage Card & FlowPage Menu Navigation (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/onthisday/OnThisDayCard.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/FeedsPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowActionsMenu.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowPage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/component/onthisday/OnThisDayCardWidgetTest.kt`

**Interfaces:**
- `OnThisDayCard(articles: List<ArticleWithFeed>, currentYear: Int, onArticleClick: (String) -> Unit, onSeeAllClick: () -> Unit)`
- `OnThisDayUiState.Content` therefore also exposes `val currentYear: Int` (add it in Task 2 together with `monthDay`).
- `FlowActionsMenu` gains `onOnThisDay: () -> Unit` parameter.

- [ ] **Step 1: Write card widget test (RED)**
  - Renders title, subtitle and up to 5 article items.
  - Tapping an item triggers `onArticleClick(articleId)`.
  - Shows year badge from the `on_this_day_years_ago` plural for the article's publication year versus the injected current year (1 year ago, 2 years ago).
  - Never shows more than five rows even when passed more.
- [ ] **Step 2: Implement `OnThisDayCard`**
  - Style with MaterialTheme tokens; respect monochrome E-Ink automatically via `LocalTonalPalettes` and `colorScheme`.
- [ ] **Step 3: Insert into `FeedsPage`**
  - Inject `OnThisDayViewModel` with `hiltViewModel()`; observe state with the existing collection helper and refresh in a `Lifecycle.Event.ON_RESUME` observer (same pattern as `VipBadge`) so returning after midnight updates the card. Dispose the observer.
  - If state is `Content` and not in AddSources mode, display `OnThisDayCard`. If `Loading`, `Empty` or `Error`, compose nothing.
  - Tapping article routes to `RouteName.READING/{articleId}` (`launchSingleTop = true`).
- [ ] **Step 4: Add menu item in `FlowActionsMenu` & route in `FlowPage`**
  - Add "On This Day" item with calendar/history icon in `FlowActionsMenu`.
  - Wire click in `FlowPage` to navigate to `RouteName.ON_THIS_DAY`.
- [ ] **Step 5: Run widget and navigation tests**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.component.onthisday.OnThisDayCardWidgetTest"`
- [ ] **Step 6: Commit**
  `git commit -m "feat(ui): add On This Day card to FeedsPage and entry to FlowPage menu"`

---

### Task 5: Dedicated Screen & Navigation Route (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/onthisday/OnThisDayPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/onthisday/OnThisDayPageTest.kt`

**Interfaces:**
- `RouteName.ON_THIS_DAY = "on_this_day"`
- `HomeEntry.kt`: `animatedComposable(route = RouteName.ON_THIS_DAY, eInk = eInk) { OnThisDayPage(navController) }`

- [ ] **Step 1: Write dedicated page tests (RED)**
  - Verifies empty state displays string resource `on_this_day_empty`.
  - Verifies error state displays error message and retry button.
  - Verifies content state lists articles with year and feed name.
- [ ] **Step 2: Implement `OnThisDayPage`**
  - Header with Back button and localized title.
  - Status-bar and navigation-bar insets handled with `WindowInsets.statusBars` and `WindowInsets.navigationBars`.
  - Retry button triggers `viewModel.refresh()`.
- [ ] **Step 3: Register route in `RouteName` and `HomeEntry`**
  - Add route constant.
  - Register with `animatedComposable(eInk = eInk)`.
- [ ] **Step 4: Run unit & widget tests**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.onthisday.OnThisDayPageTest"`
- [ ] **Step 5: Commit**
  `git commit -m "feat(ui): add dedicated OnThisDayPage and navigation route"`

---

### Task 6: Instrumentation, Edge-to-Edge & Release Verification

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/onthisday/OnThisDayEdgeToEdgeTest.kt`
- Modify: `doc/feature.md`
- Create: `doc/task/done/IDEA-07_on-this-day_DONE.md`

- [ ] **Step 1: Write edge-to-edge instrumentation test**
  - Launch `OnThisDayPage` in `ComponentActivity` using `EInkUiTestRunner`.
  - Assert title sits below `statusBars` inset and list bottom clears `navigationBars` inset.
- [ ] **Step 2: Run live instrumentation on `emulator-5554`**
  ```bash
  ./gradlew -PtestRunner=com.mckimquyen.reader.EInkUiTestRunner assembleDevDebugAndroidTest
  adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
  adb -s emulator-5554 shell am instrument -w -e class com.mckimquyen.reader.ui.page.onthisday.OnThisDayEdgeToEdgeTest com.mckimquyen.reader.test/com.mckimquyen.reader.EInkUiTestRunner
  ```
- [ ] **Step 3: Run full unit test suite & builds**
  `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`
  Expected: 937+ unit tests pass, 0 failures, 0 skipped.
- [ ] **Step 4: Live smoke test on `emulator-5554`**
  - Install real debug APK.
  - Launch app, verify `FeedsPage` displays card when articles exist, hides when empty.
  - Open `FlowPage` overflow menu, tap "On This Day", verify screen opens with correct insets.
  - Take screenshot after each step; stop if advertisement covers UI.
- [ ] **Step 5: Document completion & update feature status**
  - Move task note to `doc/task/done/IDEA-07_on-this-day_DONE.md` with measured numbers.
  - Update `doc/feature.md` moving IDEA-07 to Recently implemented.
- [ ] **Step 6: Commit**
  `git commit -m "docs: finalize IDEA-07 On This Day completion report"`
