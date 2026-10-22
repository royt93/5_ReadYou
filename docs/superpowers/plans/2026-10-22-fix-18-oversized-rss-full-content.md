# [FIX-18] Investigate Oversized RSS `fullContent` Persistence — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Find and close every production write path that can persist `Article.fullContent` above 1 MiB UTF-8 while preserving ordinary content byte-for-byte and never deleting historical rows automatically.

**Architecture:** Add a pure byte-count policy (`FullContentPolicy`) as the one acceptance boundary and apply it to RSS `SyndEntry.contents`, network Readability enrichment, Fever, Google Reader, ReadLater and OfflinePrecache writes. Log only article ID, source type/provider, byte count and reject action. First add a regression fixture at the parser boundary; trace the preserved 15.9 MB row by metadata without printing body text. Do not change Room schema or clean historical content.

**Tech Stack:** Kotlin, Rome RSS parser, `NewsletterSanitizer`, Room/KAPT, OkHttp MockWebServer, Robolectric 4.14.1, JUnit4, MockK, `./gradlew`.

## Global Constraints

- `MAX_STORED_FULL_CONTENT_BYTES = 1_048_576` bytes measured after UTF-8 encoding.
- Content of at most 1 MiB is preserved byte-for-byte; over-limit content is rejected as fullContent (null/not updated), never truncated.
- Keep title, rawDescription, shortDescription, metadata, unread/star flags and article/feed rows intact when fullContent is rejected.
- Apply policy to every production writer: `RssHelper.article`, `parseFullContent`, `FeverRssSv`, `GoogleReaderRssSv`, `ReadLaterWorker`, `OfflinePrecacheService.updateFullContent`.
- Log metadata only (article ID, source/provider, byte count, accepted/rejected); never log article body, credentials or the preserved 15.9 MB body.
- Do not delete/rewrite historical oversized DB rows automatically; no migration or SQL cleanup.
- Keep existing null-vs-empty semantics: null stays null; an empty string is accepted as an empty body; oversize nonempty string becomes null/not written.
- No Room schema change, new dependency, provider/security relaxation, or unrelated refactor.
- Run tests on host only until device is explicitly requested/available; any later device work must obey session device lock and backup rules.

---

### Task 1: Pure UTF-8 content policy and Local RSS parser boundary

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/FullContentPolicy.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperFullContentTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperSanitizerTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/article/FullContentPolicyTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperDescriptionTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object FullContentPolicy {
      const val MAX_STORED_FULL_CONTENT_BYTES = 1_048_576
      fun byteSize(content: String): Int
      fun accept(content: String?): String? // null -> null; <= limit -> same string; > limit -> null
  }
  ```
  Keep the cap a named constant, not a magic value. `RssHelper.article()` applies this policy after `NewsletterSanitizer.cleanArticleHtml`. `parseFullContent()` reuses the same cap; remove its duplicate `MAX_FULL_CONTENT_BYTES` only after tests prove request and cleaned-output limits are identical.

- [x] **Step 1: Write failing policy tests (RED)**
  - Test `null -> null`, `"" -> ""`, ordinary HTML is identical, ASCII exactly-at-cap is accepted, cap+1 ASCII is rejected, and byte size equals `toByteArray(UTF_8).size`.
  - Test multibyte UTF-8: a 3-byte character whose encoded content lands exactly at the cap is accepted; one byte over is rejected; emoji/surrogate pair and combining sequence are measured as UTF-8 bytes, never Kotlin UTF-16 `length`.
  - Keep fixture generation bounded: use `"x".repeat(MAX - suffixBytes)` plus a short multibyte suffix; do not allocate multiple 15 MB strings per test.
  - Expected RED: `FullContentPolicy` does not exist.

- [x] **Step 2: Run policy RED**
  - Run: `./gradlew testDevDebugUnitTest --tests "*FullContentPolicyTest"`
  - Expected: compilation fails because the policy is absent.

- [x] **Step 3: Implement policy and test it**
  - Implement only the interface above; `accept` returns original `String` when UTF-8 byte size is within cap.
  - Run `./gradlew testDevDebugUnitTest --tests "*FullContentPolicyTest"`; expected all tests pass.

- [x] **Step 4: Add failing `RssHelper.parseFullContent()` response/output cap and safe-log tests (RED)**
  - MockWebServer known-length body `cap + 1`: expect empty result and a metadata-only `Log.w` with exact byte count.
  - MockWebServer chunked body >cap: expect empty result, `>cap` log marker and no sentinel body text in log.
  - HTML with large removable junk and small retained readable output: verify the cap applies after Readability + sanitizer to persisted output; if Readability does not retain/strip a controlled selector fixture, test the post-sanitizer policy directly instead of keeping an invalid parser fixture.

- [x] **Step 5: Add failing `RssHelper.article()` regressions (RED)**
  - Reuse the real `SyndFeedInput` pattern in `RssHelperDescriptionTest` or the entry helper in `RssHelperAudioTest`.
  - For an RSS `<content:encoded>` of exactly the cap: expect `article.fullContent` byte-identical and title/raw/shortDescription intact.
  - For cap+1, including an HTML body whose cleaned output remains over cap: expect `fullContent == null`, while raw/short description/title/link remain populated and no entry sibling is dropped.
  - Add a synthetic oversized provider-content fixture with a short title/summary and large `content:encoded`; assert the parser mapping and rejection. This is a path-level reproduction, not a claim that it proves the historical NPR source.
  - Add a sanitizer case where removed junk makes input over limit but cleaned output at/below the cap; policy evaluates persisted cleaned UTF-8 bytes.

- [x] **Step 6: Run parser/network RED**
  - Run: `./gradlew testDevDebugUnitTest --tests "*RssHelperDescriptionTest" --tests "*RssHelperSanitizerTest" --tests "*FullContentPolicyTest"`
  - Expected: only the new oversized/exact-boundary assertions fail; existing tests remain green.

- [x] **Step 7: Implement RSS enforcement and pass tests**
  - In `RssHelper.article()`, sanitize first, then set `fullContent = FullContentPolicy.accept(cleanFull)`.
  - On rejection log only safe metadata via a short `Log.w`: article link/ID only if already known and non-sensitive public link, source type, encoded byte count, action. Do not log title if title can contain private data; do not log any body excerpt.
  - In `parseFullContent`, use the shared named cap for content-length, bounded source read (`cap + 1`), and cleaned UTF-8 output. Keep current behavior returning `""` on oversize network enrichment (not retry).
  - Run targeted tests above; expected all green.

- [x] **Step 8: Commit** — `fix(rss): bound stored fullContent by UTF-8 bytes`.

---

### Task 2: Fever and Google Reader provider writes

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/FeverRssSv.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/GoogleReaderRssSv.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/FeverRssSvTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/GoogleReaderRssSvTest.kt`

**Interfaces:**
- Consumes: `FullContentPolicy` from Task 1.
- Produces: provider article mapping stores fullContent only when the original provider string is within 1 MiB UTF-8; raw and short descriptions continue to follow current mapping.

- [x] **Step 1: Add provider regression tests (RED)**
  - In Fever sync fixture, return one normal item and one item whose `html` is over cap; capture the `Article` passed to `articleDao.insert`. Assert normal body unchanged, oversize fullContent null, and title/raw/short/unread/star/link still mapped.
  - In Google Reader sync fixture, use `summary.content` at cap and cap+1; capture inserted articles and assert the same boundary behavior.
  - Assert logs/test output contain byte count/action only, never payload text (use a unique sentinel body and assert captured logs omit sentinel).
  - Expected RED: provider mappers retain oversized strings.

- [x] **Step 2: Run provider RED**
  - Run: `./gradlew testDevDebugUnitTest --tests "*FeverRssSvTest" --tests "*GoogleReaderRssSvTest"`
  - Expected: newly added oversize assertions fail; existing provider behavior tests remain green.

- [x] **Step 3: Implement**
  - Wrap each provider body assignment in `FullContentPolicy.accept(providerContent)` after preserving its existing rawDescription/shortDescription calculation.
  - Log rejection with provider enum/name and byte size only; never log the raw provider HTML.
  - Do not drop the article or alter sync cursor because only optional fullContent is rejected.

- [x] **Step 4: Run provider tests GREEN** with the same command.
- [x] **Step 5: Commit** — `fix(rss): cap Fever and Google Reader full content`.

---

### Task 3: ReadLaterWorker and OfflinePrecache writers

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorker.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheService.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt` (re-use the existing narrow `updateFullContent`; no entity-wide update)
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorkerTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheServiceTest.kt`

**Interfaces:**
- Consumes: `FullContentPolicy`, `ArticleDao.updateFullContent(accountId, articleId, content)` from FIX-17.
- Produces: network/cache enrichment enforces the same byte cap and never writes an oversized body or a partially projected Article entity.

- [x] **Step 1: Add failing worker/cache tests (RED)**
  - ReadLater: no existing cached body + under-cap parsed output -> only `updateFullContent(accountId,id,content)` runs; cap+1 output -> no write and no body/entity mutation; an already nonblank cached body -> worker skips network parse/write and succeeds. Keep missing article/URL failure tests.
  - OfflinePrecache: stored under-cap content is reused without network parse; missing content with under-cap network result writes only fullContent; over-cap mock network result is rejected and `updateFullContent` is not called; image/cache behavior for other articles continues.
  - Assert both enrichment writers never call generic `articleDao.update(article)`, never overwrite rawDescription/read state/flags, and never pass another account ID to `updateFullContent`.
  - Expected RED: workers persist mock oversize content.

- [x] **Step 2: Run RED**
  - Run: `./gradlew testDevDebugUnitTest --tests "*ReadLaterWorkerTest" --tests "*OfflinePrecacheServiceTest"`

- [x] **Step 3: Implement**
  - `ReadLaterWorker`: fetch `ArticleListItemWithFeed` by ID and `ArticleBody` by account/ID. If cached fullContent is nonblank, return success without parsing. Otherwise parse; gate immediately before narrow `updateFullContent`. Never materialize or update a body-less `Article` entity.
  - `OfflinePrecacheService`: gate content after parsing; retain existing nonblank cached body; write accepted body through `updateFullContent` only. Oversize is a metadata-only rejection, not a retry.

- [x] **Step 4: Run tests GREEN** with the same command; verify no body sent to logger.
- [x] **Step 5: Commit** — `fix(rss): cap ReadLater and offline cached content`.

---

### Task 4: Historical-row safety and complete writer audit

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoLightweightReadTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperFullContentTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvWebFeedSyncTest.kt`

**Interfaces:**
- Consumes: all six production writer boundaries from Tasks 1–3.
- Produces: a documented writer inventory and proof that the historical 15.9 MB row is not auto-deleted/rewritten by opening DB or reading a lightweight list.

- [x] **Step 1: Add regression tests (RED)**
  - Room test inserts a historical oversized `fullContent` row directly; reopen Room DB and query the FIX-17 lightweight list. Assert row/body size in the DB remains byte-identical and metadata list succeeds.
  - RSS web-feed sync integration test with synthetic large provider content confirms row remains saved with bounded fullContent policy and normal sibling entries/cursor behavior are preserved.
  - Build a test-only writer inventory assertion or source-level audit note listing: RssHelper RSS mapping, parseFullContent enrichment, Fever, Google Reader, ReadLaterWorker, OfflinePrecacheService. Verify `rg` search has no unaccounted production assignment to `fullContent` or full-content DAO update.
  - Expected RED: historical-body/policy assertions fail until Tasks 1–3 are complete, or the audit finds an unhandled writer; fix only the writer in scope with a test.

- [x] **Step 2: Run focused tests**
  - Run: `./gradlew testDevDebugUnitTest --tests "*ArticleDaoLightweightReadTest" --tests "*RssHelperFullContentTest" --tests "*LocalRssSvWebFeedSyncTest"`

- [x] **Step 3: Implement only a missing in-scope writer if found**; do not add migration, pruning, compression or general sanitizer behavior.

- [x] **Step 4: Run focused tests GREEN** and repeat writer grep audit.
- [x] **Step 5: Commit** — `test(rss): verify oversized content rejection and historical rows`.

---

### Task 5: Full validation, device smoke and close task

**Files:**
- Create: `doc/task/done/FIX-18_oversized-rss-full-content_DONE.md`
- Modify: `doc/task/todo/01_FIX_BUGS_AND_STABILITY.md`
- Modify: `doc/feature.md`
- Delete: `doc/task/todo/FIX-18_oversized-rss-full-content.md`

- [x] **Step 1: Run writer-focused regression tests** from Tasks 1–4.
- [x] **Step 2: Full validation** — `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`; use the MockK agent config now committed in `app/build.gradle`. Report fresh XML sums and exact build output. Do not claim lint passed unless run successfully.
- [ ] **Step 3: Device smoke** — check `adb devices -l` and obey the current locked device; do not assume the earlier Tecno is connected. Backup DB first. Use a synthetic public feed with an oversize `content:encoded` body from the app Subscribe UI only if the feed fixture is controlled and does not expose real private content. Confirm normal item retained, oversized body logged as metadata-only rejection, and article can still be opened using description. Check logcat has no body payload. Restore the backup after the run. If full-screen ad appears, stop per global rules.
- [ ] **Step 4: Report** — state whether the historical source was reproduced or remains unknown; record cap, all writer paths/tests, unit/instrumentation/smoke results and explicit no-cleanup decision. Update FIX epic row to ✅ done and `doc/feature.md`; delete task todo.
- [ ] **Step 5: Commit** — `test(rss): reject oversized persisted full content`, then `docs: finalize FIX-18 oversized content report`. Do not push unless requested.

### Progress log (2026-10-22)

- Task 1 policy/parser tests: 24 tests pass across `FullContentPolicyTest`, RSS audio/description/sanitizer/full-content suites. Rome fixture uses bound `content` namespace and confirms `SyndEntry.contents`; oversized content is rejected while raw metadata stays. Network known-length log has exact bytes; chunked logs `>cap`; sentinel absent.
- Task 2 provider tests: `FeverRssSvTest` 7 and `GoogleReaderRssSvTest` 5 pass; normal provider content preserved, oversize fullContent null, item metadata remains.
- Task 3 workers: `ReadLaterWorkerTest` 4, `OfflinePrecacheServiceTest` 5 pass; under-cap writes use `updateFullContent`, oversize rejected, cache reused, no entity-wide update.
- Focused writer matrix: 45 tests pass, zero failures. Full suite/device verification not yet run for FIX-18.


## Progress log (2026-10-22)

- Tasks 1–4 implemented and host-verified. Full suite: 1245 tests, 0 failures/errors/skipped; `assembleDevDebug` and `compileDevDebugAndroidTestKotlin` pass (MockK agent preloaded by committed test configuration).
- Targeted writer tests: `FullContentPolicyTest` 5, `RssHelperAudioTest` 5, `RssHelperFullContentTest` 10, `RssHelperDescriptionTest` 3, `RssHelperSanitizerTest` 2, `FeverRssSvTest` 7, `GoogleReaderRssSvTest` 5, `ReadLaterWorkerTest` 4, `OfflinePrecacheServiceTest` 5 — all pass.
- Parser reproduction establishes that Rome exposes the synthetic `content:encoded`; over-cap body is rejected while RSS metadata remains. It does not establish that this is the source of the historical emulator row.
- Task 5 instrumentation/device smoke is BLOCKED: device list has Pixel 7 Pro and `R5CX613VZBR`, but the current session target remains Tecno `115333744A005844`, which is disconnected. No device target switch was requested; do not use Pixel/Samsung instead.
- Do not move task to DONE or claim the 15.9 MB historical ingestion source is proven. Recheck adb; when Tecno reconnects, backup first, perform a controlled UI test, verify only metadata logs, restore DB and then complete report/index/todo/commit.
