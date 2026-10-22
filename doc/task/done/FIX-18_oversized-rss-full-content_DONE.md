# [FIX-18] Investigate oversized RSS `fullContent` persistence — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed.
- **Completed:** 2026-10-22
- **Audit:** 8/10. Every verified production `fullContent` writer enforces 1 MiB UTF-8 cap; tests cover exact/over boundary, metadata preservation, safe logging, providers, ReadLater and OfflinePrecache. The synthetic RSS oversized-content path passed on Pixel. The historical 15.9 MB row’s exact writer/source remains unknown; preserved DB was not modified.
- **Commits:** `9e4f315d` spec, `b41757f5` plan, `ee236873` implementation, `7eaa2d24` report/index, `1a1a0dce` completion report.

## Finding

The read-only emulator backup `/Users/loitran/podcast_bak_emulator/Reader` contains an NPR article titled “Hit a midlife wall? This test identifies your strengths and what matters to you”: `rawDescription` is empty; `fullContent` is 12,324,490 characters and 15,890,764 UTF-8 bytes. Only lengths/markup-shape counts were inspected; body text was not printed or logged.

Current `RssHelper.parseFullContent()` already bounded network HTML at 1 MiB. `RssHelper.article()` stores Rome `SyndEntry.contents` after sanitizing and this path had no prior byte cap; it was a plausible bypass, but the exact historical article ingestion path was not reproduced. Other writers were independently checked and capped. No historical body was deleted or rewritten.

## Implementation

- Added `FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES = 1_048_576`, with UTF-8 byte sizing, null/empty distinction, and safe oversize metadata logging.
- `RssHelper.article()` sanitizes then rejects over-cap RSS content. `parseFullContent()` shares the same cap for declared length, bounded chunked read, and cleaned result. Known-length rejection logs byte count; chunked rejection logs `>cap`; neither logs body text.
- Fever and Google Reader preserve the article and its metadata while setting over-cap `fullContent` to null.
- ReadLaterWorker now uses metadata/body APIs, reuses an existing nonblank body, never updates a body-less Article entity, and writes only through `updateFullContent` after policy validation.
- OfflinePrecache reuses stored fullContent; accepted new content is written only to that column. Oversize content is rejected without retry or body logging.
- No Room schema changes, destructive history cleanup, content truncation or new dependency.

## Verification

- Focused writer matrix: 45 tests pass across UTF-8 policy, Rome RSS parsing, network Readability, sanitizer, Fever, Google Reader, ReadLater and OfflinePrecache.
- Native RSS boundary: content `<=1 MiB` retained, `cap+1` rejected; title/raw/summary/link remain available.
- `parseFullContent` exact/near limit and known-length/chunked rejection tests pass. Logs contain source/byte count and no body sentinel.
- Final `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin --rerun-tasks`: **BUILD SUCCESSFUL**, 1245 tests, 0 failures/errors/skipped. The configured `testDevDebugUnitTest` JVM preloads ByteBuddy per `app/build.gradle` to avoid MockK self-attach failure.
- Pixel 7 Pro (`2B051FDH3006MU`), per owner’s session override: isolated `ArticleListCursorWindowInstrumentationTest#rssOversizedContent_isRejectedAndMetadataSurvivesInRoom` **1/1 PASS**. It parses a synthetic RSS `content:encoded` > cap, confirms body rejection and preserved metadata, inserts into real in-memory Room and reads the list/body API. This verifies controlled RSS mapping, not the source of the historical payload.
- Pixel DB was backed up before the test and restored afterward. Restored DB: v18, integrity `ok`, 207 BBC articles, BBC feed only. Backup: `/Users/loitran/fix18_bak_pixel_preinst_20261022`; post-smoke pre-restore copy: `/Users/loitran/fix18_bak_pixel_postsmoke_20261022`.
- Instrumentation build compiled successfully. Full instrumentation suite was not rerun on Pixel in this task.
- `lintDevDebug` was not run; no lint pass is claimed.

## Limits and follow-up

- The exact source/parser/writer for the preserved 15.9 MB article remains **unknown**. `RssHelper.article()` was the plausible bypass, now capped, but no claim is made that it created that historical row.
- The historical emulator DB `/Users/loitran/podcast_bak_emulator/Reader` remains read-only and untouched. The Pixel DB used for verification was restored from backup.
- Historical oversized bodies are not automatically deleted. Other unrelated `SELECT *` queries are outside this task.
- No device audio/Bluetooth work was needed for this storage-boundary task.
