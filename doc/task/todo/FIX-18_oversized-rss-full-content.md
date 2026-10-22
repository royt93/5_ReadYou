# [FIX-18] Investigate oversized RSS fullContent persistence

- **Type:** Bug / Data safety / Performance
- **Priority:** `P1 (High)`
- **Estimation:** `3 Story Points` (investigation plus bounded-write tests; re-estimate only if multiple provider paths need redesign)
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`RssHelper.kt`](../../app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt#L112), [`FeverRssSv.kt`](../../app/src/main/java/com/mckimquyen/reader/domain/sv/FeverRssSv.kt#L201), [`GoogleReaderRssSv.kt`](../../app/src/main/java/com/mckimquyen/reader/domain/sv/GoogleReaderRssSv.kt#L182), [`ReadLaterWorker.kt`](../../app/src/main/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorker.kt#L35), [`OfflinePrecacheService.kt`](../../app/src/main/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheService.kt#L46); design: `docs/superpowers/specs/2026-10-22-fix-18-oversized-rss-full-content-design.md`.

## Vấn đề thực tế

FIX-17 fixed CursorWindow over-read by using metadata-only list queries and 64 KiB body slices. Its preserved emulator DB revealed a separate malformed/pathological `article.fullContent` row: title "Hit a midlife wall? This test identifies your strengths and what matters to you", `rawDescription` empty, `fullContent` 12,324,490 characters / 15,890,764 UTF-8 bytes. The saved DB is `/Users/loitran/podcast_bak_emulator/Reader` and must be treated read-only evidence. Do not print the body or mutate the backup.

`RssHelper.parseFullContent()` has a 1 MiB response/cleaned-content cap (`MAX_FULL_CONTENT_BYTES`), but `RssHelper.article()` persists `SyndEntry.contents` through `NewsletterSanitizer` without that cap. This suggests a bypass but is not a proven cause. `FeverRssSv`, `GoogleReaderRssSv`, `ReadLaterWorker` and `OfflinePrecacheService.updateFullContent()` also write `fullContent`; investigation must find the actual path before stating root cause.

## User Story

> Là người đọc RSS,
> Tôi muốn nội dung HTML bất thường không chiếm quá nhiều bộ nhớ/đĩa hoặc phá các tác vụ đọc bài,
> Để các bài bình thường tiếp tục đọc được và dữ liệu bất thường được xử lý an toàn, có thể giải thích.

## Acceptance Criteria (Gherkin)

- **Given** preserved metadata and an oversized article row,
  **When** investigation records source type, feed/article identifiers, title, UTF-8 byte length and safe markup-shape indicators,
  **Then** it does not log or display body text, edit the saved backup, or claim an unproven writer as root cause.
- **Given** the article origin is established or explicitly remains unknown,
  **When** each production writer is tested,
  **Then** the completion report states what was reproduced, what writer path was verified, and what remains unverified.
- **Given** content of `MAX_STORED_FULL_CONTENT_BYTES` or less,
  **When** any writer persists `fullContent`,
  **Then** it persists byte-identical UTF-8 content and leaves summary/metadata unchanged.
- **Given** content larger than `MAX_STORED_FULL_CONTENT_BYTES`,
  **When** any writer attempts to persist it,
  **Then** the full body is rejected (stored as null / not updated), article title/description/summary remain available, and only metadata (article ID, source type, byte size, reject action) is logged.
- **Given** the prior response-size policy is 1 MiB,
  **When** task chooses the default cap,
  **Then** `MAX_STORED_FULL_CONTENT_BYTES = 1_048_576` (unless investigation shows a provider contract requiring a separately documented value; do not silently change the ceiling).
- **Given** an existing DB contains a 15.9 MB body,
  **When** the app opens the database,
  **Then** this task does not delete/rewrite that historic row automatically. Historical cleanup requires an explicit separate owner decision.
- **Given** normal RSS/provider feeds and ReadLater/precache flows,
  **When** content is within the cap,
  **Then** existing behavior and body precedence remain unchanged.

## Investigation checklist

- Read the preserved DB read-only. Record `fullContent` byte length (15,890,764), chars, rawDescription byte length (0), title, link/source metadata and text-shape counts; never print body bytes/chars to terminal/chat/log.
- Trace and write a failing/reproducing fixture for `RssHelper.article()` `<content:encoded>`, `parseFullContent()`, Fever provider, Google Reader provider, `ReadLaterWorker`, and OfflinePrecache update path.
- Verify RSS parser limits, HTTP response content-length check, Sanitizer output and any database update/replace path; distinguish source parser from repeated update/cache loops.
- If source cannot be reconstructed from preserved metadata, state this; do not fabricate a reproduction.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```text
Bạn đang thực hiện task FIX-18 "Investigate oversized RSS fullContent persistence" trong RSS Cat Hub. Đọc CLAUDE.md, FIX-18 spec và task file trước khi làm.

1. Root cause first: inspect the saved emulator DB read-only (never dump body text), trace every production fullContent writer, and reproduce each path with synthetic fixtures. Record exact byte counts and source path.
2. TDD every writer: test cap boundary at 1 MiB in UTF-8 bytes (including multibyte split boundary), then add one shared/narrow validator or equivalent boundary enforcement so no writer bypasses it.
3. Enforce `MAX_STORED_FULL_CONTENT_BYTES = 1_048_576` across RSS `SyndEntry.contents`, network Readability parse, Fever/Google Reader imports, ReadLaterWorker and OfflinePrecacheService. Preserve metadata/short description; reject over-cap body without truncating.
4. No historical SQL cleanup or migration. If cleanup seems necessary, stop and ask the owner before designing it.
5. Run all writer-focused tests then `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`.
6. Device test only on owner-selected, session-locked device. Backup before any feed import, interact only through app, and stop if a full-screen ad appears. Never log actual oversized body.
7. Update DONE report and `doc/feature.md` with verified source path or explicit unknown, test counts, byte policy and remaining limits. Commit; do not push unless explicitly requested.
```

## 🏁 Tín hiệu kết thúc loop

Task chỉ kết thúc khi:
1. Root cause được tái hiện qua test hoặc báo cáo trung thực là chưa xác định; mọi writer path được kiểm kê.
2. Tests prove exactly-at-cap accepted byte-identically; over-cap rejected at every writer; title/summary/raw metadata remain; null/empty fullContent semantics preserved.
3. Unit tests, build and androidTest compile pass; device test evidence is reported if device available.
4. No historical data deleted. Report and `doc/feature.md` updated; todo moved to DONE; local commits created. No push without owner request.
