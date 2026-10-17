# INGEST-01 Web-to-RSS Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add local virtual feeds from supported static public HTTPS article-list pages, with preview-before-save, bounded secure fetches, and periodic refresh through existing local sync.

**Architecture:** Preserve Rome RSS/Atom behavior. Add a separate scraper fetch/client and deterministic Jsoup discovery parser; keep feed source metadata on Room `Feed`, dispatch web feeds only in local RSS sync, and reuse current article dedupe/filter/notification pipeline. Add a narrow UI preview/confirm state to source subscription flow.

**Tech Stack:** Kotlin, OkHttp, Jsoup (already used in main source; verify dependency is declared transitively before adding a direct declaration), Room/KAPT, Hilt, WorkManager, Compose, MockWebServer, MockK, Robolectric, AndroidX instrumentation.

## Global Constraints

- `MVP supports static HTML only; JavaScript-rendered pages unsupported.`
- `Preview and explicit user confirmation required before persistence.`
- `Minimum threshold: 3 valid candidates with distinct canonical HTTPS URLs.`
- `Only public HTTPS destinations; private/local/redirects rejected.`
- `Refresh follows existing local sync cadence and network constraints.`
- `Existing RSS import and refresh remain behaviorally unchanged.`
- Do not broaden app-wide cleartext, weaken TLS, or alter shared `OkHttpClientModule` policy; scraper owns hardened client and injected DNS validation.
- Existing data migration must retain all RSS rows as ordinary RSS feeds.
- Session device override (2026-10-17): only Pixel_10_Pro `emulator-5554`; never send device commands to any physical device. Do not invoke `connected*`; stop on any ad in screenshot testing and wait for user `done`.
- No commit/push unless tests, lint, builds, emulator smoke pass and audit score >9/10; never stage `.idea/` or `.superpowers/`.

---

## Current Code Map

- Add-source path: `ui/page/home/addsources/AddSourcesViewModel.kt` calls `RssHelper.searchFeed()` then `RssSv.get().subscribe()`; the same method currently treats all search exceptions as “subscribe empty feed”, so distinguish unsupported HTML discovery from genuine RSS/network failures.
- RSS parser: `infrastructure/rss/RssHelper.kt` uses Rome for `searchFeed()` and `queryRssXml()`; preserve behavior for actual RSS/Atom.
- Local persistence/sync: `domain/sv/LocalRssSv.kt` inherits `AbstractRssRepository`; `AbstractRssRepository.sync()` queries `FeedDao.queryAll()`, calls `syncSingleFeed()`, filters/deduplicates/inserts articles, then notifications.
- Room: `domain/model/feed/Feed.kt`; database and `allMigrations` in `infrastructure/db/AndroidDb.kt`, currently schema version 13.
- Existing dedupe: `ArticleDao.insertListIfNotExist()` checks `(accountId, feedId, link)`.
- UI: `ui/page/home/addsources/AddSourcesPage.kt` currently hosts country/catalog list; add the manual URL + preview affordance through the smallest existing source UI seam. Existing standard subscription dialog is in `ui/page/home/feed/subs/SubscribeDialog.kt` and `SubscribeViewModel.kt`; choose one entry point consistently, not two duplicate workflows.

## Planned Files

- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/scraper/WebFeedUrlPolicy.kt`: HTTPS/authority validation and public address classification.
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/scraper/WebFeedHttpClient.kt`: dedicated OkHttp client with redirects disabled, platform TLS, bounded timeouts, bounded response streaming and a validating injected `Dns`.
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/scraper/WebFeedParser.kt`: HTML-to-site/candidate extraction, candidate grouping, canonical article URLs, dates, minimum threshold/ambiguity result.
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/scraper/WebFeedScraper.kt`: combine bounded client + parser into typed outcomes; no Room/UI dependency.
- Modify `Feed.kt`, `AndroidDb.kt`, schema snapshot: source type and optional original source URL, safe defaults and migration.
- Modify `AddSourcesViewModel.kt` and the selected UI entry (prefer current `SubscribeDialog` if manual URL entry already meets UX, otherwise add minimal URL field to `AddSourcesPage.kt`): states `loading`, `preview`, typed failure, confirm/cancel.
- Modify `LocalRssSv.kt` or narrow dispatch method in `AbstractRssRepository.kt`: send normal RSS through unchanged path and web feed through `WebFeedScraper`.
- Tests: parser/policy/scraper unit tests under `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/scraper/`; ViewModel + Compose test under corresponding UI test package; Room migration/sync integration under `app/src/androidTest/java/com/mckimquyen/reader/integration/`.

## Task 1: URL Policy and Dedicated Fetch Client

**Files:** create policy and client classes; tests under `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/scraper/`; inspect `app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt` but do not alter shared client.

**Interface:** `WebFeedUrlPolicy.validate(url: String, resolvedAddresses: List<InetAddress>): WebFeedUrlValidation`; `WebFeedHttpClient` builds its own client with `followRedirects(false)`, normal TLS validation, request timeout and `Dns` bound to the same validated resolution result.

- [ ] Write tests first: reject `http`, `file`, unsupported scheme, missing host, userinfo, localhost, IPv4 loopback/private/link-local/multicast/reserved, IPv6 loopback/ULA/link-local/multicast, and mixed DNS answer sets containing any blocked address; accept public HTTPS host and public-only DNS answers.
- [ ] RED: `./gradlew --no-daemon --max-workers=1 testDevDebugUnitTest --tests "*WebFeedUrlPolicyTest"` fails because policy is absent.
- [ ] Implement URL validation with `java.net.URI`/`InetAddress` stdlib. Reject entire DNS answer set if any returned address is blocked. Reject IP literal if blocked. Do not separately validate one DNS lookup then let OkHttp perform another; injected `Dns` must return the validated address set to that request.
- [ ] Test client behavior with local `MockWebServer`: HTTPS-only, 3xx not followed, 4xx/5xx represented as typed HTTP failure; bounded timeout. For TLS policy, inspect built client configuration through behavior-oriented tests, not reflection-only tests.
- [ ] GREEN targeted test. No generic/global OkHttp policy edits.

## Task 2: Static HTML Candidate Parser

**Files:** create `WebFeedParser.kt`; unit fixture tests.

**Interface:** `parse(sourceUrl: HttpUrl, html: String): WebFeedParseResult`, where success carries `siteTitle: String`, `articles: List<WebArticleCandidate>` and result types include `Unsupported`, `Ambiguous`, `TooFewCandidates`.

- [ ] Write fixture tests for repeated `<article>` blocks, relative URL resolution, `<h1>/<h2>` title, `time[datetime]`, no date, duplicate canonical URLs, fragment removal, nav/sidebar false positives, single article, missing title/link, script-only HTML, malformed sibling candidate skip, and two equally-scored list groups.
- [ ] RED focused parser tests.
- [ ] Implement deterministic candidate extraction, preserving document order. Require 3 distinct canonical HTTPS article URLs with non-empty title. Never fetch article pages. Prefer Jsoup existing dependency; if Gradle declares it only transitively, add a direct declaration only when this source imports Jsoup directly and establish existing version from dependency tree first.
- [ ] GREEN parser tests, including stable error category and preview list.

## Task 3: Bounded Web Fetch + Typed Discovery

**Files:** create `WebFeedScraper.kt`; MockWebServer tests.

**Interface:** `suspend fun discover(url: String): WebFeedDiscoveryResult`; typed results include `Preview`, invalid/unsafe URL, redirect, non-HTML, HTTP error, timeout, oversized body, unsupported/ambiguous/too-few parser result. `suspend fun refresh(sourceUrl: String): WebFeedRefreshResult` can share private fetch/parse operations.

- [ ] Write MockWebServer tests first: valid HTML, RSS XML rejected from scraper branch, redirect rejected, non-success status, wrong content type, malformed HTML, timeout, compressed/large response capped before parse, parser threshold/ambiguity propagation.
- [ ] RED focused scraper test.
- [ ] Implement request with validated HTTPS URL; disable redirects; enforce connect/read/call timeout; stream response into bounded buffer and stop once maximum byte count exceeded; do not use unbounded `body.string()`; log host and typed result only.
- [ ] GREEN test suite; assert closing response body on every path.

## Task 4: Feed Source Type and Room Migration

**Files:** `Feed.kt`, `AndroidDb.kt`, schema JSON, `FeedDao` only if query helper needed; migration integration test.

**Interface:** Feed source type enum/string defaults to `RSS`; virtual web feed stores `sourceType=WEB` and `sourceUrl`; regular feed stores null/source default.

- [ ] Write migration test first using exported schema fixture: migrate v13 DB to v14; existing feed reads as RSS, existing fields remain identical, WEB feed metadata writes/reads; foreign keys remain valid.
- [ ] RED migration test.
- [ ] Add source discriminator and nullable source URL to Feed; update equals/hashCode if these are persisted constructor fields; add `MIGRATION_13_14` and append to `allMigrations`; regenerate/commit Room schema snapshot using project KAPT workflow.
- [ ] GREEN Android integration migration test on AndroidSQLite and existing unit schema verification.

## Task 5: Preview/Confirm UI and Subscription Persistence

**Files:** `AddSourcesViewModel.kt`, one Compose entry point (`SubscribeDialog.kt` or `AddSourcesPage.kt`, decided in implementation after reading nav wiring), localized strings in six maintained locales, ViewModel/widget tests.

**Interface:** Add explicit `discoverWebFeed(url)`, `confirmWebFeed()`, `cancelWebFeedPreview()` actions; UI state carries preview/error/loading. Do not overload `addRssSource` catch-all fallback to silently subscribe invalid web URLs.

- [ ] Write ViewModel tests first: RSS result still follows current RSS route; HTML result emits preview but writes no DAO rows; confirm persists feed and preview articles once; cancel writes none; invalid/private URL and unsupported HTML show recoverable error and persist none; double confirm is idempotent/disabled; exceptional request resets loading.
- [ ] RED view-model tests.
- [ ] Implement ViewModel orchestration using injected scraper and RSS helper; on confirm use existing default group/account and local service only; if current account backend is not Local, show explicit unsupported state and do not insert fake web feed.
- [ ] Add Compose widget tests for URL entry, loading, preview article title list, confirm/cancel, unsupported error, and unchanged RSS subscription behavior. Add localized labels/errors for en, vi, zh-rCN, ja, fr, de.
- [ ] GREEN ViewModel/widget tests. Ensure coroutine work cancelled/cleared with ViewModel lifecycle; no unmanaged scopes/listeners.

## Task 6: Local Sync Dispatch, Dedupe, and Feed Health

**Files:** `LocalRssSv.kt` or `AbstractRssRepository.kt` narrow seam; `FeedHealthDao` usage; repository unit tests.

**Interface:** normal RSS feed still calls `RssHelper.queryRssXml(feed, latestLink)`; WEB feed uses `WebFeedScraper.refresh(feed.sourceUrl)`; result converts candidates to `Article` linked to stored feed/account. Existing `ArticleDao.insertListIfNotExist()` handles dedupe.

- [ ] Write tests first: normal RSS uses prior parser unchanged; web first refresh inserts article candidates; repeated refresh with same article links inserts none; newly discovered candidate inserts once; malformed candidate skipped; page/network failure records `FeedHealthRecord` failure; processing failure for one feed does not block a normal RSS feed in same account sync.
- [ ] RED targeted repository tests with MockWebServer + in-memory Room or MockK DAOs according to smallest reliable harness.
- [ ] Implement source-type dispatch only in local service path. Preserve notifications, smart filters, watchdog and archive behavior by returning ordinary `FeedWithArticle` to existing sync pipeline.
- [ ] GREEN targeted tests; verify WorkManager schedule remains unchanged and no second scheduler/pref is added.

## Task 7: End-to-End Integration and Device Smoke

**Files:** `app/src/androidTest/java/com/mckimquyen/reader/integration/WebFeedIntegrationTest.kt`, UI integration test if real navigation needs coverage; no production changes unless test first fails against approved behavior.

- [ ] In-memory Room + MockWebServer test: add/confirm a virtual feed; persist source metadata and at least three articles; simulate next page with one new article; assert old links are not duplicated, new article appears, one bad candidate does not drop siblings.
- [ ] UI integration: tap manual source action, preview candidate set, cancel (DB unchanged), repeat and confirm (feed/articles persisted and shown in list). Avoid Hilt test runner changes unless existing manual construction cannot cover relevant path.
- [ ] Run all unit tests serially: `./gradlew --no-daemon --max-workers=1 testDevDebugUnitTest`.
- [ ] Run `./gradlew lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- [x] Device lock: target only Pixel_10_Pro `emulator-5554`; never use physical devices or `connected*`. Install `devDebug`, test public static article-list URL with at least 3 entries, verify preview before confirm, initial articles visible, refresh adds one newly available fixture candidate without duplicates. Stable NPR smoke confirmed preview, save and 34 WEB articles; second refresh produced zero duplicates and FeedHealth `NONE`. Controlled new-link proof is `WebFeedRoomEndToEndTest` (Robolectric + real Room + MockWebServer).
- [ ] If full-screen ad appears, stop immediately and wait for user's `done`; do not dismiss, relaunch, or continue flow unless user authorizes after ad closes.

## Task 8: Final Audit and Completion Gate

- [ ] Review diff against spec, Room migration, logs/privacy, URL/DNS boundary, every unit/widget/integration case; run `git diff --check`.
- [ ] Score objectively /10: secure fetch + correctness (3), parser determinism and candidate quality (2), test quality/RED-GREEN (2), Room/sync/UI integration (1.5), device evidence (1), maintainability and unchanged RSS behavior (.5). Push only if score >9 and all test/build/device gates pass.
- [ ] Stage exact INGEST-01 paths only; preserve `.idea/` and `.superpowers/`. Commit with Conventional Commit message. Push `dev` only after all gates.
- [ ] Move task document from `doc/task/todo/` to `doc/task/done/`, suffix `_DONE`, include completion report with score, commit SHA, tests and smoke evidence; update `doc/task/todo/07_UNIVERSAL_INGESTION_AND_SYNC.md` status only after completion.
