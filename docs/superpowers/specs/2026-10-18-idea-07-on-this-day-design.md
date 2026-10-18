# [IDEA-07] On This Day Design Specification

- **Status:** Approved
- **Date:** 2026-10-18
- **Priority:** P3 (Low) — 3 Story Points
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng

---

## 1. Problem & Goal

Room already stores every article's publication `date` and `isUnread` state, but the app does not use this history to bring back older reading. IDEA-07 adds an "On This Day" experience for articles that the user has read and that were published on today's month/day in earlier years.

The schema has no read timestamp. This design therefore uses publication date plus read state. Product copy must not claim that the article was read on the same day in a prior year.

## 2. Accepted Decisions

| Decision | Rationale |
|---|---|
| Match on the device's current local calendar date. | "Today" matches what the user sees on the device. |
| Match `isUnread = false` articles by publication `date` month/day. | Uses the existing schema; no migration or sync change. |
| Include every earlier year, not only last year. | Long-lived archives remain useful; current-year articles are excluded. |
| Order by `date DESC`, then `id DESC`, and return at most 5. | Newest first with a stable tie-break. |
| Scope to the current account only. | Prevents cross-account leakage. |
| Do not map Feb 29 to Feb 28 or Mar 1. | Feb 29 appears only when today is Feb 29 and the article was published on that date. |
| No Room migration, `readAt`, AI, FTS, notification, share action or setting toggle. | YAGNI; keeps this as a small read-only feature. |

## 3. Data Access

Add `ArticleDao.queryOnThisDay(accountId: Int, monthDay: String, currentYear: String, limit: Int = 5): List<ArticleWithFeed>`.

The query is read-only and uses SQLite local-time date formatting on the millisecond epoch stored by `AndroidDatabase.DateConverters`:

```sql
SELECT * FROM article
WHERE accountId = :accountId
  AND isUnread = 0
  AND strftime('%m-%d', date / 1000, 'unixepoch', 'localtime') = :monthDay
  AND strftime('%Y', date / 1000, 'unixepoch', 'localtime') < :currentYear
ORDER BY date DESC, id DESC
LIMIT :limit
```

`monthDay` is zero-padded (`MM-dd`) and `currentYear` is a four-digit string. DAO tests on in-memory Room must verify SQLite `localtime` behavior under a controlled default time zone; if a time-zone assumption is false, correct the query before building UI.

No schema or index change is planned. The result set is capped, and existing account/date indexes are acceptable for this P3 feature unless measurement shows a real problem.

## 4. Domain and State

- `OnThisDayCalendar` is a small pure/domain helper that converts an injected clock/time zone into `monthDay` and `currentYear`.
- `OnThisDayViewModel` is a Hilt ViewModel injected with `ArticleDao`, `AccountSv`/current-account access, the calendar helper and `@IODispatcher`.
- Queries run on the IO dispatcher. The ViewModel never stores `Context`, `Activity`, navigation or Compose state.
- UI state is one of: `Loading`, `Empty`, `Content(articles)` and `Error`.
- Refresh on screen start, app resume and calendar-date change. A date change must not require a worker.
- Query failure logs and shows `Error` on the dedicated screen. The FeedsPage card renders nothing for `Loading`, `Empty` or `Error`.

## 5. UI

### 5.1 FeedsPage card

Insert a compact card after the filter banner and before the Feeds subtitle when the result is `Content`.

- Show title, short explanatory text and up to 5 article rows with title/feed/year.
- Tapping a row opens `RouteName.READING/{articleId}` with `launchSingleTop = true`.
- Hide it in AddSources mode and when the result is not `Content`.
- Reuse existing typography/theme tokens so E-Ink theme and grayscale behavior remain automatic.
- Do not add decorative animations.

### 5.2 Dedicated screen

Add `RouteName.ON_THIS_DAY`, registered in `HomeEntry` with `animatedComposable` and an `eInk`-aware transition.

- Open it from the existing `FlowPage` overflow menu.
- Show the same article list, a clear empty state and an error state with retry.
- Apply explicit status-bar and navigation-bar insets using the same edge-to-edge approach as `SmartCollectionsPage`.
- Article click behavior matches the card.

## 6. Localization

Add title, card description, empty text, error/retry text and row-year label strings to `values/strings.xml` and all maintained locale directories. Preserve `StringResourceParityTest`. Do not use ASCII-folded Vietnamese.

## 7. Testing and Verification

### Unit and DAO

- Selects only `isUnread = false` articles with the same local month/day and an earlier year.
- Excludes current-year articles, other accounts and other dates.
- Orders newest first and uses `id` as a stable tie-breaker.
- Enforces the limit of 5.
- Feb 29 matches only on Feb 29; common-year Feb 28/Mar 1 do not fall back to it.
- Local-time boundary tests cover at least two time zones around midnight.
- ViewModel tests cover loading, empty, content, error, retry and date-change refresh.

### Compose and instrumentation

- FeedsPage card appears only for content and routes the tapped article ID correctly.
- Dedicated screen renders content, empty and error states, and keeps title/list clear of status and navigation insets.
- Run live instrumentation only with `adb -s emulator-5554`; use the isolated UI runner where Hilt/ad startup is not part of the behavior under test.

### Verification limits

- Full unit suite, `assembleDevDebug` and `compileDevDebugAndroidTestKotlin` must pass before completion.
- Lint is currently affected by a known unrelated SDK 37 metadata failure; do not claim lint passed unless that changes.
- Golden screenshots, Macrobenchmark/Perfetto profiling, network conditioning and IAP/ad mediation are not part of this feature unless the implementation unexpectedly touches those systems.
- A real-app smoke test must use screenshots after each step and stop immediately if an advertisement covers the UI.

## 8. Not In Scope

- Read timestamp storage or migration.
- Exact "read on this date" semantics.
- Settings toggle for hiding the card.
- Notifications, widgets, sharing, statistics or heatmaps.
- AI summarisation or semantic ranking.
