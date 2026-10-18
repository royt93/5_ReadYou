# [IDEA-01] Reading Habits & Heatmap Design Specification

- **Status:** Approved
- **Date:** 2026-10-18
- **Priority:** P2 (Medium) — 5 Story Points
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng

---

## 1. Problem & Goal

Room stores article publication `date` and `isUnread`, but no moment at which the user read an article. The app has no screen that shows reading habits. IDEA-01 adds a Statistics screen with a 365-day activity heatmap, totals, reading streaks and the top five feeds.

## 2. Accepted Decisions

| Decision | Rationale |
|---|---|
| Add nullable `article.readAt` (epoch millis) in Room v17 via `MIGRATION_16_17`. | The heatmap must show the day the user read, not the publication day. |
| Record `readAt` only when the user opens an article in the Reader. | Reflects real reading. Does not touch the four existing `isUnread` queries, so "mark all as read" and Fever/Google Reader sync never write `readAt`. |
| `readAt` keeps the first read time (`COALESCE(readAt, :now)`). | Reopening an article must not move or double-count it. |
| No backfill. Articles read before the update keep `readAt = NULL`. | Backfilling with publication dates would be fake data and would corrupt streaks. The heatmap fills in from the update date. |
| Drop words-per-minute. | The Reader does not time the user; adding a timer is out of scope. |
| Count by device-local calendar day. | Same rule as IDEA-07: `strftime('%Y-%m-%d', readAt / 1000, 'unixepoch', 'localtime')`. |
| Scope everything to the current account. | Prevents cross-account leakage. |

## 3. Data

### 3.1 Schema

- `Article` gains `@ColumnInfo var readAt: Long? = null` as the last constructor parameter, so existing constructor calls keep compiling.
- `MIGRATION_16_17`: `ALTER TABLE article ADD COLUMN readAt INTEGER DEFAULT NULL`. Registered in the migration list; `AndroidDatabase` version becomes 17. Room schema `17.json` is exported to `app/schemas`.
- Existing data is untouched. No new index: the day aggregate runs over `article` rows with `readAt IS NOT NULL` for one account and is bounded to 365 days.

### 3.2 DAO

All in `ArticleDao`:

- `markOpened(accountId: Int, articleId: String, readAt: Long)`: `UPDATE article SET readAt = COALESCE(readAt, :readAt) WHERE id = :articleId AND accountId = :accountId`.
- `queryReadingDayCounts(accountId: Int, sinceMillis: Long): List<ReadingDayCount>` where `ReadingDayCount(day: String, count: Int)` and day is `yyyy-MM-dd` local. Filters `readAt >= :sinceMillis`, groups by day.
- `queryTotalRead(accountId: Int): Int`: `COUNT(*)` where `readAt IS NOT NULL`.
- `queryTopFeeds(accountId: Int, limit: Int = 5): List<FeedReadCount>` where `FeedReadCount(feedId, feedName, count)`: join `feed`, group by feed, `ORDER BY count DESC, feedName ASC`.

`readAt` is not exposed for editing anywhere else.

### 3.3 Write point

`ReadingViewModel` gets `recordOpened()`, called from the same `LaunchedEffect(articleId)` block in `ReadingPage` that already runs when an article is displayed (`ReadingPage.kt`, next to the unread auto-mark). It runs on `@IODispatcher`, reads the account from the loaded article, and calls `markOpened`. It runs for already-read articles too (first open after update records the date). Toggling read/unread in the BottomBar does not call it.

## 4. Domain Logic (pure, no Android)

- `ReadingStreakCalculator.compute(days: Set<LocalDate>, today: LocalDate): Streaks(current: Int, longest: Int)`.
  - `current` counts consecutive days ending today; if today has no read but yesterday does, the streak ending yesterday still counts (the user has until the end of today to continue).
  - `longest` is the longest run in the set.
- `HeatLevel.of(count: Int): Int` returns 0 for 0, then 1..4 by named thresholds (`1`, `2-3`, `4-6`, `7+`) held in constants.
- `ReadingStatsAssembler` maps DAO rows into `ReadingStats(totalRead, readToday, avgPerActiveDay, currentStreak, longestStreak, heatmap: Map<LocalDate, Int>, topFeeds)`. `avgPerActiveDay` is total in the 365-day window divided by the number of days with at least one read, rounded to one decimal; 0 when empty.
- A `Clock` and `ZoneId` are injected so "today" and midnight boundaries are testable.

## 5. State and ViewModel

`ReadingStatisticsViewModel` (Hilt) injects `ArticleDao`, `@ApplicationContext Context` (only for `currentAccountId`), the clock/zone provider and `@IODispatcher`. State: `Loading`, `Empty` (no `readAt` rows at all), `Content(ReadingStats)`, `Error(messageRes)`. Queries run on IO. Refresh on screen start and `ON_RESUME`. Query failures are logged and produce `Error`; cancellation is rethrown.

## 6. UI

- Route `RouteName.READING_STATISTICS`, registered in `HomeEntry` with `animatedComposable(eInk = eInk)`. Entry row in `SettingsPage` next to Brain RPG.
- `ReadingStatisticsPage`: header with back button and title; summary cards (Total read, Today, Current streak, Longest streak, Average per active day); heatmap; Top 5 feeds; streak badges at 7 and 30 days.
- Heatmap: 53 weeks × 7 rows, Monday-first, drawn with `Canvas` using `colorScheme` tokens (primary at graded alpha) so E-Ink monochrome works automatically. Each cell has no individual semantics; the heatmap exposes one `contentDescription` summarising the active-day count for accessibility.
- Empty state explains that statistics start counting from this update.
- Error state with retry.
- Explicit status-bar and navigation-bar insets, same approach as `SmartCollectionsPage` / `OnThisDayPage`.
- No decorative animation.

## 7. Localization

All new user-visible strings (title, card labels, badge names, empty/error text, heatmap description, plural for days/articles) are added to `values/strings.xml` and all 37 maintained locale directories. `StringResourceParityTest` stays green. Vietnamese keeps full diacritics.

## 8. Testing and Verification

- **Unit:** `ReadingStreakCalculator` (empty, today only, run ending yesterday, broken run, longest vs current, midnight/timezone boundary), `HeatLevel` thresholds, `ReadingStatsAssembler`.
- **Room:** migration 16→17 keeps data and adds a NULL `readAt`; `markOpened` writes once and keeps the first value; day counts exclude `readAt IS NULL`, other accounts and days older than 365; top feeds ordering and limit; local-day grouping under two default time zones.
- **ViewModel:** Loading, Empty, Content, Error, refresh, account scoping.
- **Reader hook:** opening an unread and an already-read article records `readAt` once; BottomBar toggle does not.
- **Compose widget:** summary cards, heatmap description, Top 5, empty and error states.
- **Instrumentation (`emulator-5554` only, isolated `EInkUiTestRunner`):** title and last list item clear status/navigation insets.
- **Real-app smoke on `emulator-5554`:** screenshot after every step; stop immediately if an ad covers the UI and wait for "done" from the user. Open an article, then Statistics shows it; empty state before any read.
- Full unit suite, `assembleDevDebug` and `compileDevDebugAndroidTestKotlin` must pass. Lint is affected by a known unrelated SDK 37 metadata failure; do not claim lint passed unless that changes.
- Golden screenshots, Macrobenchmark/Perfetto, network conditioning and IAP/ad mediation are out of scope.

## 9. Not In Scope

- Words-per-minute or reading-duration tracking.
- Backfilling `readAt` for existing read articles.
- Counting "mark all as read", per-item list marking or backend sync as reading.
- Sharing, export, notifications, widgets, per-topic breakdowns or goals.
- Any ad, VIP or IAP behaviour (deferred by the owner).
