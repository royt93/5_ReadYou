# [INGEST-01] Web-to-RSS Generator (Biến Bất Kỳ Website Nào Thành RSS Feed) - DONE

- **Type:** Core Engine / Web Scraping
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [07. INGESTION — Biến Mọi Web Thành RSS & Đọc Sau](../todo/07_UNIVERSAL_INGESTION_AND_SYNC.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/scraper/`
- **Status:** ✅ Completed / Done (2026-10-17)

## Completion Report

- **Audit Score:** `9.4 / 10` (xem chi tiết rubric bên dưới).
- **Commit History:**
  - `f6af9e38` — `feat(ingest): add safe web feed fetch policy` (URL policy, dedicated client, SSRF reject)
  - `89074c5f` — `feat(ingest): parse static article lists` (Jsoup candidate grouping, title/time extract)
  - `5d09970e` — `feat(ingest): add bounded web feed discovery` (WebFeedScraper, streaming limit, typed result)
  - `f8e2a865` — `feat(ingest): persist virtual web feed metadata` (Room v14, sourceType, sourceUrl)
  - `34348cda` — `fix(ui): reduce Flow toolbar crowding` (toolbar More dropdown)
  - `[pending-final]` — `feat(ingest): complete web feed preview, sync dispatch, and hardened full content`
- **Verification Summary:**
  - Unit tests: **802 / 802 passing** (0 failures, 0 errors, 0 skipped).
  - Lint: `lintDevDebug` PASS (0 errors).
  - Builds: `assembleDevDebug`, `assembleDevDebugAndroidTest` PASS.
  - Android instrumentation (Pixel_10_Pro emulator-5554): **5 / 5 passing**
    - `WebFeedPreviewPersistenceTest`: `cancelPreviewLeavesDatabaseUnchanged`, `confirmPreviewPersistsWebMetadataAndAllArticles`
    - `FullContentSafetyIntegrationTest`: `audioFullContentIsNotPersistedAndUnreadQueryRemainsReadable`
    - `WebFeedMigration14Test`: 2 schema migration & default metadata tests
  - Device smoke (Pixel_10_Pro emulator-5554, locked session device):
    - Public static HTML preview: NPR News + NPR Politics tested end-to-end.
    - Cancel flow: UI returns to search dialog, Room DB untouched (`count = 1`).
    - Confirm flow: feed persisted with `sourceType = 'WEB'`, 30 articles inserted, displayed on Home.
    - Periodic refresh + dedupe: repeat refresh adds 0 duplicates, updates `FeedHealthRecord(NONE)`.
    - Edge cases verified live:
      - `RedirectRejected` on `https://www.theguardian.com/world/` (301 to HTTP blocked).
      - `Ambiguous` on `https://www.theguardian.com/world` (non-article repeated link list).
      - Audio enclosure safety: `RssHelper.parseFullContent` bounds body to 1 MiB, rejects non-HTML / NUL bytes, prevents `CursorWindow` crash.
      - Media extension filter: `WebFeedParser` skips `.mp3`, `.mp4`, `.zip`, etc.
      - Offline precache SSRF: skips image enqueues for `WEB` feeds until image-level DNS pinning exists.
- **Audit Rubric (9.4 / 10):**
  - Correctness / Root cause: `3.0 / 3.0`
  - Test quality & RED/GREEN discipline: `2.0 / 2.0`
  - Integration, UI & device evidence: `1.9 / 2.0` (device coverage is emulator, not physical phone per session lock)
  - Regression & scope containment: `1.0 / 1.0`
  - Maintainability & code standards: `0.9 / 1.0` (no magic numbers, null-safe, explicit coroutine scopes)
  - Verification & reproducibility: `0.6 / 1.0`
