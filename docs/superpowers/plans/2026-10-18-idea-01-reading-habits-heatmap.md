# [IDEA-01] Reading Habits & Heatmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Statistics screen with a 365-day reading heatmap, totals, streaks and top-5 feeds, backed by a new nullable `article.readAt` recorded when the user opens an article in the Reader.

**Architecture:** Room v17 adds `article.readAt` (epoch millis, nullable). The Reader writes it once (`COALESCE`) when an article is displayed. Pure domain helpers (`ReadingStreakCalculator`, `HeatLevel`, `ReadingStatsAssembler`) turn DAO aggregates into `ReadingStats`. A Hilt `ReadingStatisticsViewModel` runs queries on IO and exposes `Loading/Empty/Content/Error`. A Compose page draws a `Canvas` heatmap and is reached from Settings.

**Tech Stack:** Kotlin 2.1.0, Room (KAPT), Jetpack Compose, Material 3, Hilt, JUnit4, MockK, Robolectric 4.14.1, Compose UI test.

## Global Constraints

- Add nullable `article.readAt` (epoch millis) in Room v17 via `MIGRATION_16_17`.
- Record `readAt` only when the user opens an article in the Reader; never in "mark all as read", per-item list marking or Fever/Google Reader sync.
- `readAt` keeps the first read time: `COALESCE(readAt, :now)`.
- No backfill: articles read before the update keep `readAt = NULL`.
- No words-per-minute and no reading-duration tracking.
- Count by device-local calendar day: `strftime('%Y-%m-%d', readAt / 1000, 'unixepoch', 'localtime')`.
- Scope every query to the current account (`Context.currentAccountId`).
- Queries run on `@IODispatcher`; ViewModels never hold `Activity` or Compose state.
- Localize every new string across all 38 maintained locale files (`values` + 37 `values-*`); keep `StringResourceParityTest` green; Vietnamese keeps full diacritics.
- No magic numbers: thresholds, window length, limits are named constants.
- Run live Android work only on `emulator-5554` via `adb -s emulator-5554`; never run Gradle `connectedDevDebugAndroidTest`.
- Stop the smoke test immediately if an advertisement covers the UI and wait for the user's "done" (a small non-blocking banner does not count; the user said to skip banners).
- Keep `.idea/workspace.xml` and `.idea/deviceManager.xml` untouched. No ad, VIP or IAP behaviour (deferred by the owner).

## Known limits (record in the completion report)

- `ArticleDao.insert` uses `OnConflictStrategy.REPLACE` and Fever sync inserts parsed articles with it, so re-fetching an already-stored Fever article resets that row's `readAt` to NULL. Local and Google Reader accounts are not affected (`insertListIfNotExist`, `insertOnConflictIgnore`). Do not change Fever sync in this task.
- `ReadLaterWorker` calls `articleDao.update(article)` with an article loaded earlier; a Reader open that lands between its load and update could be overwritten. Rare, accepted.
- Streaks are computed inside the 365-day window, so a streak longer than 365 days is capped.

---

### Task 1: Room v17 `readAt`, migration and DAO queries (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/article/Article.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/statistics/ReadingDayCount.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/statistics/FeedReadCount.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration16To17Test.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoReadingStatsTest.kt`
- Generated: `app/schemas/<db class>/17.json` (by the build; commit it)

**Interfaces:**
- Produces:
  ```kotlin
  // Article.kt — new LAST constructor parameter
  @ColumnInfo
  var readAt: Long? = null,

  data class ReadingDayCount(val day: String, val count: Int)           // day = yyyy-MM-dd local
  data class FeedReadCount(val feedId: String, val feedName: String, val count: Int)

  // ArticleDao
  @Query("UPDATE article SET readAt = COALESCE(readAt, :readAt) WHERE id = :articleId AND accountId = :accountId")
  suspend fun markOpened(accountId: Int, articleId: String, readAt: Long)

  @Query("""
      SELECT strftime('%Y-%m-%d', readAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
      FROM article
      WHERE accountId = :accountId AND readAt IS NOT NULL AND readAt >= :sinceMillis
      GROUP BY day
      ORDER BY day ASC
  """)
  suspend fun queryReadingDayCounts(accountId: Int, sinceMillis: Long): List<ReadingDayCount>

  @Query("SELECT COUNT(*) FROM article WHERE accountId = :accountId AND readAt IS NOT NULL")
  suspend fun queryTotalRead(accountId: Int): Int

  @Query("""
      SELECT f.id AS feedId, f.name AS feedName, COUNT(*) AS count
      FROM article a JOIN feed f ON a.feedId = f.id
      WHERE a.accountId = :accountId AND a.readAt IS NOT NULL
      GROUP BY f.id
      ORDER BY count DESC, f.name ASC
      LIMIT :limit
  """)
  suspend fun queryTopFeeds(accountId: Int, limit: Int = 5): List<FeedReadCount>
  ```
  `MIGRATION_16_17`, and `AndroidDatabase` `version = 17`.

- [ ] **Step 1: Write the migration test (RED)**

`RoomMigration16To17Test.kt` — same pattern as `RoomMigration15To16Test` (open a raw DB at version 16 with a hand-made `article` table, run `MIGRATION_16_17.migrate(db)`):
  - create `article` with the v16 columns that matter (`id TEXT PRIMARY KEY, accountId INTEGER NOT NULL, isUnread INTEGER NOT NULL`) and insert a row `('a1', 1, 0)`;
  - after migrating, `SELECT readAt FROM article WHERE id='a1'` returns a NULL value (`cursor.isNull(col)` is true) and the original row is intact;
  - `UPDATE article SET readAt = 1700000000000 WHERE id='a1'` then reading it back returns `1700000000000`.

- [ ] **Step 2: Write the DAO test (RED)**

`ArticleDaoReadingStatsTest.kt` — Robolectric in-memory Room, same setup as `ArticleDaoOnThisDayTest` (account 7 + other account 8, groups, feeds `feed-1` "Alpha" and `feed-2` "Beta" for account 7, `feed-9` for account 8; `.addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)`). Use a `localMillis(year, month, day)` helper = noon in `ZoneId.systemDefault()` (avoids midnight skew between JVM and SQLite `localtime`). Tests:
  - `markOpened_writesOnceAndKeepsFirstValue`: insert article with `readAt = null`; `markOpened(7, id, 1_000L)` then `markOpened(7, id, 2_000L)`; reading the row gives `readAt == 1_000L`.
  - `markOpened_ignoresOtherAccount`: `markOpened(8, id, ...)` on an account-7 article leaves `readAt == null`.
  - `queryReadingDayCounts_groupsByLocalDayAndExcludesNullOtherAccountAndOld`: seed 2 articles on 2026-10-10, 1 on 2026-10-11, 1 with `readAt = null`, 1 for account 8, 1 older than `sinceMillis`; expect `[ReadingDayCount("2026-10-10", 2), ReadingDayCount("2026-10-11", 1)]` for a `sinceMillis` of 2026-10-01 noon.
  - `queryTotalRead_countsOnlyReadAtNotNullForAccount`: the seed above has 4 account-7 rows with non-null `readAt` (2 + 1 + the old one), so expect exactly 4 (the NULL row and the account-8 row are excluded).
  - `queryTopFeeds_ordersByCountThenNameAndHonoursLimit`: Alpha 3 reads, Beta 3 reads, `feed-3` "Gamma" 1 read → order Alpha, Beta, Gamma (tie broken by name); `limit = 2` returns two rows.

- [ ] **Step 3: Run and confirm RED**

`./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.db.RoomMigration16To17Test" --tests "com.mckimquyen.reader.domain.repository.ArticleDaoReadingStatsTest"` → compilation failure (`MIGRATION_16_17`, `readAt`, DAO methods missing).

- [ ] **Step 4: Implement**
  - `Article.kt`: add `@ColumnInfo var readAt: Long? = null,` after `aiSummary` (last parameter).
  - Create `ReadingDayCount` and `FeedReadCount` plain data classes (`@Keep`, no Room annotations).
  - `ArticleDao.kt`: add the four methods above after `queryOnThisDay`.
  - `AndroidDb.kt`: `version = 17`; append `MIGRATION_16_17` to `allMigrations`; add
    ```kotlin
    @Suppress("ClassName")
    object MIGRATION_16_17 : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE article ADD COLUMN readAt INTEGER DEFAULT NULL")
        }
    }
    ```
  - Fix `RoomMigration15To16Test`-style builders elsewhere only if the build breaks (`rg -n "MIGRATION_15_16" app/src/test`): tests that build the real DB with `.addMigrations(...)` need `MIGRATION_16_17` only if they open an older file DB; in-memory builders do not.

- [ ] **Step 5: Run and confirm GREEN**

Same command as Step 3 → all pass. Then `./gradlew assembleDevDebug -q` and confirm `app/schemas/**/17.json` exists and contains a `readAt` column.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/article/Article.kt \
  app/src/main/java/com/mckimquyen/reader/domain/model/statistics \
  app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt \
  app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt \
  app/schemas app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration16To17Test.kt \
  app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoReadingStatsTest.kt
git commit -m "feat(db): add article readAt (Room v17) and reading statistics queries

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Pure domain logic (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/statistics/ReadingStreakCalculator.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/statistics/HeatLevel.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/statistics/ReadingStats.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/statistics/ReadingStatsAssembler.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/statistics/ReadingStatisticsCalendar.kt`
- Create tests in `app/src/test/java/com/mckimquyen/reader/domain/model/statistics/`: `ReadingStreakCalculatorTest.kt`, `HeatLevelTest.kt`, `ReadingStatsAssemblerTest.kt`, `ReadingStatisticsCalendarTest.kt`

**Interfaces:**
- Consumes: `ReadingDayCount`, `FeedReadCount` (Task 1).
- Produces:
  ```kotlin
  data class Streaks(val current: Int, val longest: Int)
  object ReadingStreakCalculator { fun compute(days: Set<LocalDate>, today: LocalDate): Streaks }

  object HeatLevel {
      const val LEVEL_NONE = 0
      const val LEVEL_MAX = 4
      fun of(count: Int): Int      // 0 -> 0; 1 -> 1; 2..3 -> 2; 4..6 -> 3; >=7 -> 4
  }

  data class ReadingStats(
      val totalRead: Int,
      val readToday: Int,
      val avgPerActiveDay: Double,        // one decimal, 0.0 when no active day
      val currentStreak: Int,
      val longestStreak: Int,
      val activeDays: Int,
      val heatmap: Map<LocalDate, Int>,
      val topFeeds: List<FeedReadCount>,
  )
  object ReadingStatsAssembler {
      fun assemble(dayCounts: List<ReadingDayCount>, totalRead: Int, topFeeds: List<FeedReadCount>, today: LocalDate): ReadingStats
  }

  class ReadingStatisticsCalendar(clock: Clock = Clock.systemDefaultZone(), zoneId: ZoneId = ZoneId.systemDefault()) {
      @Inject constructor() : this(Clock.systemDefaultZone(), ZoneId.systemDefault())
      fun today(): LocalDate
      fun windowStartMillis(): Long   // start of day (today - (WINDOW_DAYS - 1)) in zoneId
      companion object { const val WINDOW_DAYS = 365 }
  }
  ```

- [ ] **Step 1: Write tests (RED)**
  - `ReadingStreakCalculatorTest` (today = 2026-10-18): empty → `Streaks(0,0)`; only today → `(1,1)`; today and the 3 previous days → current 4; no read today but yesterday and the day before → current 2 (streak still alive); last read 2 days ago → current 0, longest kept; two runs of 3 and 5 ending long ago → longest 5, current 0; days after `today` are ignored; a run that crosses a month/year boundary (2026-12-30..2027-01-02 with today 2027-01-02) counts as 4.
  - `HeatLevelTest`: `of(0)=0, of(1)=1, of(2)=2, of(3)=2, of(4)=3, of(6)=3, of(7)=4, of(100)=4, of(-1)=0`.
  - `ReadingStatsAssemblerTest`: day rows `("2026-10-17",2), ("2026-10-18",4)` with total 10 → `readToday=4`, `activeDays=2`, `avgPerActiveDay=3.0`, `currentStreak=2`, heatmap keys parsed as `LocalDate`; empty input → all zeros and empty maps; `avgPerActiveDay` rounds `7/3` to `2.3`; unparsable day strings are skipped (not thrown).
  - `ReadingStatisticsCalendarTest`: with `Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), UTC)` and zone `Asia/Tokyo`, `today()` is `2027-01-01`; `windowStartMillis()` equals `LocalDate.of(2027,1,1).minusDays(364).atStartOfDay(Tokyo).toInstant().toEpochMilli()`.

- [ ] **Step 2: Run, confirm RED** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.statistics.*"` → compilation failure.

- [ ] **Step 3: Implement**

`ReadingStreakCalculator.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.statistics

import java.time.LocalDate

data class Streaks(val current: Int, val longest: Int)

object ReadingStreakCalculator {
    fun compute(days: Set<LocalDate>, today: LocalDate): Streaks {
        val valid = days.filterNot { it.isAfter(today) }.toSortedSet()
        if (valid.isEmpty()) return Streaks(0, 0)
        var longest = 1
        var run = 1
        var previous: LocalDate? = null
        for (day in valid) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            if (run > longest) longest = run
            previous = day
        }
        val anchor = when {
            today in valid -> today
            today.minusDays(1) in valid -> today.minusDays(1)
            else -> null
        }
        var current = 0
        var cursor = anchor
        while (cursor != null && cursor in valid) {
            current++
            cursor = cursor.minusDays(1)
        }
        return Streaks(current, longest)
    }
}
```

`HeatLevel.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.statistics

object HeatLevel {
    const val LEVEL_NONE = 0
    const val LEVEL_MAX = 4
    private const val LEVEL_1_MAX = 1
    private const val LEVEL_2_MAX = 3
    private const val LEVEL_3_MAX = 6

    fun of(count: Int): Int = when {
        count <= 0 -> LEVEL_NONE
        count <= LEVEL_1_MAX -> 1
        count <= LEVEL_2_MAX -> 2
        count <= LEVEL_3_MAX -> 3
        else -> LEVEL_MAX
    }
}
```

`ReadingStats.kt`: the data class from Interfaces.

`ReadingStatsAssembler.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.statistics

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.round

object ReadingStatsAssembler {
    private const val AVG_SCALE = 10.0

    fun assemble(
        dayCounts: List<ReadingDayCount>,
        totalRead: Int,
        topFeeds: List<FeedReadCount>,
        today: LocalDate,
    ): ReadingStats {
        val heatmap = LinkedHashMap<LocalDate, Int>()
        for (row in dayCounts) {
            val date = try { LocalDate.parse(row.day) } catch (_: DateTimeParseException) { continue }
            heatmap[date] = (heatmap[date] ?: 0) + row.count
        }
        val streaks = ReadingStreakCalculator.compute(heatmap.keys, today)
        val windowTotal = heatmap.values.sum()
        val active = heatmap.size
        val avg = if (active == 0) 0.0 else round(windowTotal.toDouble() / active * AVG_SCALE) / AVG_SCALE
        return ReadingStats(
            totalRead = totalRead,
            readToday = heatmap[today] ?: 0,
            avgPerActiveDay = avg,
            currentStreak = streaks.current,
            longestStreak = streaks.longest,
            activeDays = active,
            heatmap = heatmap,
            topFeeds = topFeeds,
        )
    }
}
```

`ReadingStatisticsCalendar.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.statistics

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ReadingStatisticsCalendar(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    @Inject
    constructor() : this(Clock.systemDefaultZone(), ZoneId.systemDefault())

    fun today(): LocalDate = LocalDate.now(clock.withZone(zoneId))

    fun windowStartMillis(): Long =
        today().minusDays((WINDOW_DAYS - 1).toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()

    companion object {
        const val WINDOW_DAYS = 365
    }
}
```

- [ ] **Step 4: Run, confirm GREEN** — same command; all pass.
- [ ] **Step 5: Commit** — `git add app/src/main/java/com/mckimquyen/reader/domain/model/statistics app/src/test/java/com/mckimquyen/reader/domain/model/statistics && git commit -m "feat(domain): add reading streak, heat level and stats assembler" ` with the Co-Authored-By trailer.

---

### Task 3: Record `readAt` when the Reader opens an article (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelReadAtTest.kt`

**Interfaces:**
- Consumes: `ArticleDao.markOpened(accountId, articleId, readAt)` (Task 1).
- Produces on `ReadingViewModel`:
  - new LAST constructor parameter `private val articleDao: ArticleDao? = null` (after `articleReadPositionDao`; existing tests keep compiling; Hilt injects it);
  - `fun recordOpened(nowMillis: Long = System.currentTimeMillis())`.

- [ ] **Step 1: Write the test (RED)**
  Reuse the setup of `ReadingViewModelDeepReadTest` / `ReadingViewModelSummaryTest` (mock `RssSv`, `RssHelper`, `TtsManager`, `GeminiSummaryService`, `ZenAudioManager`, `StandardTestDispatcher` as main). Pass `articleDao = mockk(relaxed = true)` as the last argument. Tests:
  - `recordOpened_callsMarkOpenedWithArticleAccountAndIdForUnreadArticle`: after `initData` loads an article (`accountId = 3`, `isUnread = true`), `recordOpened(5_000L)` + `advanceUntilIdle()` → `coVerify(exactly = 1) { articleDao.markOpened(3, id, 5_000L) }`.
  - `recordOpened_alsoRecordsAlreadyReadArticle`: `isUnread = false` → still one call.
  - `recordOpened_doesNothingWhenNoArticleLoaded`: no load → `coVerify(exactly = 0)`.
  - `recordOpened_withoutDaoIsNoOp`: constructed with `articleDao = null` → no exception.
  - `recordOpened_swallowsDaoFailureWithoutCrashing`: `coEvery { articleDao.markOpened(any(), any(), any()) } throws IOException()` → no exception, `readingUiState` unchanged.
  - `markUnread_doesNotCallMarkOpened`: `markUnread(false)` then `coVerify(exactly = 0) { articleDao.markOpened(any(), any(), any()) }`.

- [ ] **Step 2: Run, confirm RED** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.ReadingViewModelReadAtTest"` → compilation failure.

- [ ] **Step 3: Implement**
  - `ReadingViewModel`: add the constructor parameter and
    ```kotlin
    fun recordOpened(nowMillis: Long = System.currentTimeMillis()) {
        val dao = articleDao ?: return
        val article = _readingUiState.value.articleWithFeed?.article ?: return
        val accountId = article.accountId
        val articleId = article.id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.markOpened(accountId, articleId, nowMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ReadingViewModel", "markOpened failed", e)
            }
        }
    }
    ```
    (add `kotlinx.coroutines.CancellationException` import if missing; if the file already routes IO through the injected dispatcher, use that instead of `Dispatchers.IO`, but keep it off Main).
    If the test cannot control `Dispatchers.IO`, launch with `defaultDispatcher` (already injected, `testDispatcher` in tests) and use that — pick one and keep the test consistent.
  - `ReadingPage.kt`: in the existing `LaunchedEffect(readingUiState.articleWithFeed?.article?.id)` block add the call *outside* the `if (it.article.isUnread)` check:
    ```kotlin
    readingUiState.articleWithFeed?.let {
        readingViewModel.recordOpened()
        if (it.article.isUnread) {
            readingViewModel.markUnread(false)
        }
    }
    ```

- [ ] **Step 4: Run, confirm GREEN**, then run the existing Reader tests: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.*"` → all pass.
- [ ] **Step 5: Commit** — `git commit -m "feat(reader): record first read time when an article is opened"` (+ trailer) after `git add` of the three files.

---

### Task 4: `ReadingStatisticsViewModel` and state (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/statistics/ReadingStatisticsUiState.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/statistics/ReadingStatisticsViewModel.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/statistics/ReadingStatisticsViewModelTest.kt`

**Interfaces:**
- Consumes: `ArticleDao.queryReadingDayCounts/queryTotalRead/queryTopFeeds`, `ReadingStatisticsCalendar`, `ReadingStatsAssembler` (Tasks 1–2), `Context.currentAccountId`, `@IODispatcher`.
- Produces:
  ```kotlin
  sealed interface ReadingStatisticsUiState {
      data object Loading : ReadingStatisticsUiState
      data object Empty : ReadingStatisticsUiState
      data class Content(val stats: ReadingStats) : ReadingStatisticsUiState
      data class Error(@StringRes val messageRes: Int) : ReadingStatisticsUiState
  }

  @HiltViewModel
  class ReadingStatisticsViewModel @Inject constructor(
      @ApplicationContext private val context: Context,
      private val articleDao: ArticleDao,
      private val calendar: ReadingStatisticsCalendar,
      @IODispatcher private val ioDispatcher: CoroutineDispatcher,
  ) : ViewModel() {
      val state: StateFlow<ReadingStatisticsUiState>
      fun refresh()
  }
  ```
  `Error` uses `R.string.reading_stats_error` (added in Task 5; until then add the English string to `values/strings.xml` in this task so the build compiles — Task 5 only adds the translations and the remaining keys).

- [ ] **Step 1: Write tests (RED)** — same style as `OnThisDayViewModelTest` (`StandardTestDispatcher`, Robolectric `Application`, mocked `ArticleDao`, a mocked `ReadingStatisticsCalendar` returning `today() = LocalDate.of(2026,10,18)` and `windowStartMillis() = 1_000L`):
  - initial state is `Loading`;
  - `totalRead == 0` → `Empty` (and the day/top queries are not required to return anything);
  - rows present → `Content` with `readToday`, `currentStreak`, `topFeeds` from the fakes; `queryReadingDayCounts` is called with `(1, 1_000L)` (account from `context.currentAccountId`, default 1);
  - DAO throws `IOException` → `Error(R.string.reading_stats_error)` and no crash;
  - `refresh()` after the calendar's `today()` changes re-queries and updates `readToday`;
  - a second `refresh()` while the first is running cancels the first (only the latest result is published).

- [ ] **Step 2: Run, confirm RED.**
- [ ] **Step 3: Implement** — mirror `OnThisDayViewModel` (`refreshJob?.cancel()`, `viewModelScope.launch(ioDispatcher)`, `try/catch` rethrowing `CancellationException`, `Log.e` then `Error`). Query order: `total = articleDao.queryTotalRead(accountId)`; if `total == 0` → `Empty`; else `dayCounts = queryReadingDayCounts(accountId, calendar.windowStartMillis())`, `top = queryTopFeeds(accountId, TOP_FEEDS_LIMIT)` with `private const val TOP_FEEDS_LIMIT = 5`; `state = Content(ReadingStatsAssembler.assemble(dayCounts, total, top, calendar.today()))`. `init { refresh() }`.
- [ ] **Step 4: Run, confirm GREEN.**
- [ ] **Step 5: Commit** — `feat(ui): add ReadingStatisticsViewModel and state` (+ trailer).

---

### Task 5: Localization across all 38 locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml` and every `app/src/main/res/values-*/strings.xml` that has a `strings.xml` (37 files; `values-night` has none).

**Keys (English source):**
- `reading_stats_title` = "Reading Statistics"
- `reading_stats_settings_desc` = "Heatmap, streaks and your most-read sources"
- `reading_stats_total` = "Total read"
- `reading_stats_today` = "Today"
- `reading_stats_current_streak` = "Current streak"
- `reading_stats_longest_streak` = "Longest streak"
- `reading_stats_avg_per_day` = "Avg per active day"
- `reading_stats_top_feeds` = "Top sources"
- `reading_stats_empty` = "No reading history yet. Statistics start counting from this update."
- `reading_stats_error` = "Could not load statistics. Tap to retry."
- `reading_stats_badge_week` = "7-day streak"
- `reading_stats_badge_month` = "30-day streak"
- `reading_stats_less` = "Less"
- `reading_stats_more` = "More"
- plurals `reading_stats_days` — `one`: "%1$d day", `other`: "%1$d days"
- plurals `reading_stats_heatmap_desc` — `one`: "Reading heatmap: %1$d active day in the last year", `other`: "Reading heatmap: %1$d active days in the last year"
- plurals `reading_stats_articles` — `one`: "%1$d article", `other`: "%1$d articles"

(`values/strings.xml` already has `back` and `retry`; reuse them.)

- [ ] **Step 1:** Add the keys to `values/strings.xml` (the Task 4 `reading_stats_error` line moves into this block; do not duplicate it).
- [ ] **Step 2:** Write a throw-away Python script (not committed; same approach as the IDEA-07 i18n script): a dict `locale -> (all string values, plural dict)` with real translations for each of the 37 locale directories, covering each plural quantity the language needs (`ar`: zero/one/two/few/many/other; `cs`, `pl`, `ru`, `uk`, `sl`, `sr`, `ro`: the extra `few`/`many`/`two` forms; `ja`, `zh-rCN`, `zh-rTW`, `in-rID`, `my`, `lzh`: `other` only; others: `one`/`other`). Assert the locale set equals the directories on disk and that no key already exists; escape `'` as `\'`; insert before `</resources>`. Vietnamese keeps full diacritics.
- [ ] **Step 3:** Validate every file parses (`python3 -c "import xml.dom.minidom as m; m.parse(path)"`) and that no locale lacks any plural: `rg -L "reading_stats_heatmap_desc" app/src/main/res/values*/strings.xml` prints nothing.
- [ ] **Step 4:** `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" processDevDebugResources -q` → PASS (the parity test only checks `<string>` keys; the `rg -L` check in Step 3 covers plurals).
- [ ] **Step 5: Commit** — `feat(i18n): localize reading statistics across all 38 maintained locales` (+ trailer).

---

### Task 6: Statistics page, route, Settings entry and widget tests (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/statistics/ReadingHeatmap.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/statistics/ReadingStatisticsPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt` (`const val READING_STATISTICS = "reading_statistics"`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/component/statistics/ReadingHeatmapTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/statistics/ReadingStatisticsPageTest.kt`

**Interfaces:**
- Consumes: `ReadingStats`, `HeatLevel`, `ReadingStatisticsViewModel` / `ReadingStatisticsUiState` (Tasks 2, 4), string keys (Task 5).
- Produces:
  ```kotlin
  const val READING_HEATMAP_TAG = "reading_heatmap"
  const val HEATMAP_WEEKS = 53
  const val HEATMAP_DAYS_PER_WEEK = 7

  /** Pure layout: column index (week) and row index (0 = Monday) for each date in the window. */
  internal data class HeatmapCell(val date: LocalDate, val week: Int, val row: Int, val level: Int)
  internal fun buildHeatmapCells(heatmap: Map<LocalDate, Int>, today: LocalDate): List<HeatmapCell>

  @Composable fun ReadingHeatmap(heatmap: Map<LocalDate, Int>, today: LocalDate, modifier: Modifier = Modifier)

  const val STATS_PAGE_TITLE_TAG = "reading_stats_title"
  const val STATS_EMPTY_TAG = "reading_stats_empty"
  const val STATS_ERROR_TAG = "reading_stats_error"
  const val STATS_RETRY_TAG = "reading_stats_retry"
  const val STATS_CONTENT_TAG = "reading_stats_content"
  const val STATS_TOP_FEED_TAG_PREFIX = "reading_stats_feed_"

  @Composable fun ReadingStatisticsPage(navController: NavHostController, viewModel: ReadingStatisticsViewModel = hiltViewModel())
  ```

- [ ] **Step 1: Write tests (RED)**
  - `ReadingHeatmapTest` (pure + Compose): `buildHeatmapCells` for `today = 2026-10-18` (Sunday) places today in the last week at row 6, the cell 364 days earlier in week 0 or week 1 consistently (assert the first cell's `date` is the Monday on or before `today - 364 days` and that cell count is `HEATMAP_WEEKS * HEATMAP_DAYS_PER_WEEK` minus the trailing days after today); levels equal `HeatLevel.of(count)`; days with no count have level 0. Compose test: the node tagged `READING_HEATMAP_TAG` exists and its `contentDescription` is "Reading heatmap: 2 active days in the last year" for a map with two days.
  - `ReadingStatisticsPageTest` (Robolectric, `createComposeRule`, `@Config(sdk = [34], application = Application::class)`, view model built with a mocked `ArticleDao` and `Dispatchers.Unconfined`, mocked calendar): Empty shows `STATS_EMPTY_TAG` and the empty text; Error shows `STATS_ERROR_TAG`, and clicking `STATS_RETRY_TAG` re-queries (`coVerify(atLeast = 2)`); Content shows total/today/streak values as text ("12", "3", "5"), the badge "7-day streak" only when `longestStreak >= 7`, and a row per top feed (`STATS_TOP_FEED_TAG_PREFIX + feedId`) in order.

- [ ] **Step 2: Run, confirm RED** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.component.statistics.*" --tests "com.mckimquyen.reader.ui.page.statistics.*"`.

- [ ] **Step 3: Implement**
  - `RouteName.READING_STATISTICS`; in `HomeEntry` add `animatedComposable(route = RouteName.READING_STATISTICS, eInk = eInk) { ReadingStatisticsPage(navController = navController) }` next to `ON_THIS_DAY`; in `SettingsPage` add a `SelectableSettingGroupItem(title = stringResource(R.string.reading_stats_title), desc = stringResource(R.string.reading_stats_settings_desc), icon = Icons.Outlined.BarChart)` right after the Brain RPG item, navigating with `launchSingleTop = true`.
  - `buildHeatmapCells`: `start = today.minusDays(364)` moved back to the Monday on or before it (`start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))`); for each date from `start` to `today`, `week = ChronoUnit.DAYS.between(start, date) / 7`, `row = date.dayOfWeek.value - 1`, `level = HeatLevel.of(heatmap[date] ?: 0)`.
  - `ReadingHeatmap`: a `Canvas` inside a `Box` with `Modifier.semantics { contentDescription = pluralStringResource(R.plurals.reading_stats_heatmap_desc, activeDays, activeDays) }.testTag(READING_HEATMAP_TAG)`, wrapped in `horizontalScroll(rememberScrollState(), reverseScrolling = true)` so the newest weeks are visible first; cell size and gap are named `Dp` constants; level 0 uses `MaterialTheme.colorScheme.surfaceVariant`, levels 1–4 use `MaterialTheme.colorScheme.primary` at alpha `0.25f/0.5f/0.75f/1f` (named constants) so E-Ink monochrome works automatically. Below it a Less → More legend row using the same colors.
  - `ReadingStatisticsPage`: `Scaffold` + top row exactly like `OnThisDayPage` (explicit `WindowInsets.statusBars` top padding, back `FeedbackIconButton`, title with `STATS_PAGE_TITLE_TAG`); body by state: `Loading` → centered `CircularProgressIndicator`; `Empty` → centered text (`STATS_EMPTY_TAG`); `Error` → text + `Button` (`STATS_RETRY_TAG`, `viewModel.refresh()`); `Content` → `LazyColumn` (`STATS_CONTENT_TAG`, bottom padding `WindowInsets.navigationBars`) with a 2-column grid of stat cards (Total read, Today, Current streak = `pluralStringResource(reading_stats_days, n, n)`, Longest streak, Avg per active day formatted with one decimal), then `ReadingHeatmap`, then the badges (`reading_stats_badge_week` when `longestStreak >= STREAK_BADGE_WEEK_DAYS`, `reading_stats_badge_month` when `>= STREAK_BADGE_MONTH_DAYS`; constants 7 and 30), then the "Top sources" section with one row per `FeedReadCount` (`feedName` + `pluralStringResource(reading_stats_articles, count, count)`, tag `STATS_TOP_FEED_TAG_PREFIX + feedId`). Refresh on `ON_RESUME` with a `DisposableEffect` lifecycle observer that is removed in `onDispose` (same pattern as `FeedsPage` for On This Day).
  - `@OptIn(ExperimentalMaterial3Api::class)` on the page, as `OnThisDayPage` needs it.

- [ ] **Step 4: Run, confirm GREEN** (same command), then `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — two commits: `feat(ui): add reading heatmap component` and `feat(ui): add reading statistics page and Settings entry` (+ trailer).

---

### Task 7: Instrumentation, full verification, smoke test and docs

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/statistics/ReadingStatisticsEdgeToEdgeTest.kt`
- Create: `doc/task/done/IDEA-01_reading-habits-heatmap_DONE.md`
- Modify: `doc/feature.md`
- Delete: `doc/task/todo/IDEA-01_reading-habits-heatmap.md`

- [ ] **Step 1: Edge-to-edge instrumentation test** — copy `OnThisDayEdgeToEdgeTest` (same `ActivityScenario`, `setDecorFitsSystemWindows(false)`, real insets polling): build the VM with `database.articleDao()` of an in-memory `AndroidDatabase`, `ReadingStatisticsCalendar()`, `Dispatchers.Unconfined`; with an empty DB it shows the Empty state, so assert `STATS_PAGE_TITLE_TAG` top `>=` status inset and `STATS_EMPTY_TAG` bottom `<=` decor height minus navigation inset. Add a second test that inserts one article with `readAt = now` (create account/group/feed rows first, as the Robolectric DAO tests do) and asserts the `STATS_CONTENT_TAG` bounds clear the same insets and `READING_HEATMAP_TAG` exists.
- [ ] **Step 2: Run on `emulator-5554` only**
  ```bash
  ./gradlew -PtestRunner=com.mckimquyen.reader.EInkUiTestRunner assembleDevDebug assembleDevDebugAndroidTest
  adb -s emulator-5554 install -r "app/build/outputs/apk/dev/debug/<apk>.apk"
  adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
  adb -s emulator-5554 shell am instrument -w -e class com.mckimquyen.reader.ui.page.statistics.ReadingStatisticsEdgeToEdgeTest com.mckimquyen.reader.test/com.mckimquyen.reader.EInkUiTestRunner
  ```
  Expected: `OK (2 tests)`. Do not run `connectedDevDebugAndroidTest`.
- [ ] **Step 3: Full suite and builds** — `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`; sum the XML results (958 before this feature, expect more, 0 failures/errors/skipped). Do not claim lint passed.
- [ ] **Step 4: Live smoke on `emulator-5554`** — install the normal debug APK (rebuild without `-PtestRunner`), launch `com.mckimquyen.reader` (the package has no `.dev` suffix), screenshot after every step with `adb -s emulator-5554 exec-out screencap -p`, confirm the foreground window with `dumpsys window | grep mCurrentFocus`:
  1. Settings → "Reading Statistics" entry visible and opens the page; with no `readAt` rows the empty state shows (back up the DB first if rows already exist: `run-as com.mckimquyen.reader` copy of `databases/Reader` and `Reader-wal`).
  2. Back, open any article in the Reader, return to Statistics: Total read ≥ 1, Today = 1, heatmap shows one filled cell, the feed appears in Top sources.
  3. Open the same article again: Total read does not increase (first-read semantics).
  4. If an ad covers the UI, stop, tell the user and wait for "done" (banners that do not cover the UI are skipped).
  After the smoke test restore the original DB from the backup (or delete only rows you added).
- [ ] **Step 5: Docs** — write `doc/task/done/IDEA-01_reading-habits-heatmap_DONE.md` (status, commit hashes from `git log`, implementation, measured verification numbers, and the three Known limits above plus "no backfill" and "WPM dropped"); in `doc/feature.md` move IDEA-01 into "Recently implemented" with the real test counts; `git rm doc/task/todo/IDEA-01_reading-habits-heatmap.md`.
- [ ] **Step 6: Commit** — `docs: finalize IDEA-01 Reading Habits & Heatmap completion report` (+ trailer). Do not push unless the user asks.
