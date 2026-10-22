# [FIX-18] Investigate oversized RSS fullContent persistence — Design Specification

- **Status:** Approved
- **Date:** 2026-10-22
- **Priority:** P1 (High)
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit

---

## 1. Observed Problem

FIX-17 fixed bulk article lists by not loading body columns into list queries. Separately, the preserved emulator database `/Users/loitran/podcast_bak_emulator/Reader` contains an NPR article whose title is "Hit a midlife wall? This test identifies your strengths and what matters to you"; `rawDescription` is empty, `fullContent` is 12,324,490 characters and 15,890,764 UTF-8 bytes. The emulator log at the time showed `SQLiteBlobTooBigException` in `queryRecentArticlesWithFeed`; CommuteWorker also logged the same exception from `queryLatestUnread`.

`RssHelper.parseFullContent()` has a 1 MiB response/cleaned-content ceiling. That ceiling does not cover `RssHelper.article()`, which persists `SyndEntry.contents` after `NewsletterSanitizer.cleanArticleHtml()`. This is a potential bypass, not yet a proven source for the saved NPR body. Other writers exist (`FeverRssSv`, `GoogleReaderRssSv`, `ReadLaterWorker`, `OfflinePrecacheService.updateFullContent`). Do not assert ingestion cause without reproducing and tracing.

FIX-18 investigates how oversized content entered persisted `article.fullContent`, establishes a safe write-boundary policy, and prevents pathological bodies from consuming excessive storage or CPU while preserving ordinary article behavior. A separate safety question is needed before destructive cleanup of historical body data.

## 2. Goal and Non-Goals

**Goal:** Reproduce the 15.9 MB stored article from preserved metadata/source where possible, identify the exact writer and bypass, then enforce a documented size/format policy at every production write path, with tests proving no writer persists data above the selected limit and normal content remains intact.

**Not in scope:** Undoing FIX-17; hand-editing the preserved backup; silently deleting existing production bodies; arbitrary global pruning; changing article titles/descriptions/feed URLs; relaxing network security; or claiming the 2026-10-20 incident is fixed until source and write path are demonstrated.

## 3. Investigation / Design

1. Treat the saved emulator DB as read-only evidence. Record metadata only: source feed URL, article link, content length in characters/bytes, MIME/content markup shape indicators, entry origin and timestamps. Never print the 15.9 MB body into logs or chat.
2. Trace and test each body writer:
   - `RssHelper.article()` from `SyndEntry.contents`.
   - `RssHelper.parseFullContent()` network/Readability pipeline.
   - `FeverRssSv` and `GoogleReaderRssSv` provider-supplied `fullContent`.
   - `ReadLaterWorker` and `OfflinePrecacheService.updateFullContent()`.
3. Reproduce with a fixture that has small/empty `rawDescription`, short title/summary, and a very large provider `<content:encoded>` or equivalent. Verify which parser field maps into `fullContent` and which write commits it.
4. Choose a named UTF-8 byte cap at trusted ingestion/write boundaries; do not truncate by UTF-16 character count. Proposed starting ceiling: `MAX_STORED_FULL_CONTENT_BYTES = 1 MiB`, matching current `parseFullContent()` limit. Preserve content ≤ limit exactly; reject over-limit network enrichment; for provider RSS content over limit, keep `fullContent = null` while retaining safe title/summary/raw description. Never store an arbitrary prefix that may cut markup/UTF-8 incorrectly.
5. Add logging only of article ID, source type/provider, content byte count, and action (accepted/rejected); do not log body text or sensitive feed credentials.
6. Historical 15.9 MB content policy is separate: default behavior is no automatic destructive migration. If a cleanup action is desired, first ask owner whether to add a transactional migration/batched maintenance job or leave old rows untouched. FIX-17 bulk reads remain safe either way.

## 4. Error Handling and Compatibility

- Byte counts use `String.toByteArray(Charsets.UTF_8).size` at persistence boundary.
- Boundary behavior is explicit: `size <= cap` persists byte-identical content; `size > cap` does not persist full content and logs only metadata.
- Apply the same rule to every writer, including provider imports, ReadLaterWorker and OfflinePrecache; no path may bypass it. ReadLater/OfflinePrecache must use metadata + `queryArticleBody` and the narrow `updateFullContent`; never update an Article reconstructed without its other columns.
- Article summary/raw description must remain available for reading/search if full content is rejected.
- Cancellation, parser errors and IO failures keep existing behavior; over-size rejection is not treated as a network retry loop.
- No Room schema change unless investigation proves a separate persisted flag is necessary; prefer no new column.

## 5. Testing and Verification

### Unit / parser / provider tests
- UTF-8 boundaries: exactly cap, cap+1 using ASCII, multibyte character at boundary, emoji and combining sequence; assert accepted body is byte-identical and oversize body is not persisted.
- `RssHelper.article()` test with RSS `<content:encoded>` above cap and under cap; verify `rawDescription`/`shortDescription` survive and `fullContent` is null only for oversized content.
- `parseFullContent()` test for response-body cap and cleaned-content cap; ensure existing 1 MiB behavior still holds. Known-length rejection logs exact bytes, chunked rejection logs `>cap`, and neither log contains body text.
- Fever/Google Reader mapping tests with provider fullContent above/below cap.
- ReadLaterWorker and OfflinePrecache tests proving update path enforces cap and cannot replace an article with oversized content.
- No test logs or snapshots may contain the actual preserved article body.

### Room integration
- Verify an old oversized row can still be listed via FIX-17 metadata APIs without loading body.
- Verify no automatic destructive cleanup occurs on DB open unless owner separately approves a maintenance action.

### Build / device
- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin` passes.
- Smoke on owner-selected device with a controlled oversized test feed/import; check logcat for safe metadata-only rejection and verify ordinary ≤1 MiB articles retain full content.
- Test device target must be listed/locked under session device rules; backup before any data-changing subscription/import; stop if a full-screen ad covers UI.

## 6. Not In Scope

- Reopening or printing the original fullContent payload.
- SQL cleanup of Pixel/Tecno/emulator databases without owner approval.
- Content compression, content-addressed storage, streaming HTML parsing, or provider redesign.
- FIX-17 DAO list projections and 64 KiB safe body read path, already implemented.
