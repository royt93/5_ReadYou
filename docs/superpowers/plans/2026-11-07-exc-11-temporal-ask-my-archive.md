# EXC-11 Temporal Ask My Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build fully on-device archive search with explicit date range, month timeline, verbatim article-body evidence, and cautious opposing-word signals.

**Architecture:** `ArchiveQueryRepository` scans account/date-scoped metadata pages using FIX-17 columns, reuses `SemanticSearchEngine.rank`, keeps only top three body candidates per month, then reads bounded bodies sequentially. Pure Kotlin domain components parse supported date language, group results, extract verbatim evidence, and detect explicit opposing cue pairs. A Hilt ViewModel owns cancellation/state; Compose page provides query/range controls and timeline.

**Tech Stack:** Kotlin, Room v20, Coroutines, Hilt, Jetpack Compose/Material You, existing `SemanticSearchEngine`, existing chunked `ArticleDao.queryArticleBody`, existing Jsoup dependency, JUnit4/MockK/Robolectric and Compose UI test dependencies already in project.

## Global Constraints

- Keep Room at v20; add no entity, migration, dependency, cloud, Gemini, API key, ad or IAP.
- Search title + shortDescription metadata only; metadata query uses `ARTICLE_LIST_COLUMNS`; body reads only after ranking and only selected top candidates.
- Apply account predicate to every metadata/body lookup; query text must never be interpolated into SQL.
- Default range is current calendar month plus prior 11 months; maximum range is 12 months; group on `Article.date`, never `readAt`.
- Max query text: 500 chars; page size: 200; max body candidates: 3/month; max body reads: 36/query; max article body accepted: 1,048,576 bytes; text scan cap: 40,000 chars; visible quote cap: 300 chars.
- Month shift requires consecutive calendar months, at least 2 distinct ranked article IDs each, opposing whole-word/phrase cues in selected body evidence, and no cue in earlier selected evidence. Label heuristic as “possible”, never as proof of opinion change.
- All new strings in all 38 maintained locale files; EN/VI authored; other locales English fallback; every key used and parity test passes.
- UI uses Material You, `BaseScaffold`, Back, edge-to-edge/IME insets, TalkBack labels and accessible contrast not relying on color alone.
- All database work off Main on injected `@IODispatcher`; CPU-bound ranking/text analysis off Main. Cancellation must propagate; stale query/account results must not overwrite current state.
- Device target is Tecno BG6 `118743744X002560` only. Never address KJ7, A50s or Pixel; never run `connectedDevDebugAndroidTest`. Before device smoke make fresh Room + DataStore backup; restore and `cmp` every saved file; never delete DataStore. During BG6 runs set `stay_on_while_plugged_in=2`, restore previous value (currently `0`). Close full-screen ads only after fresh UI dump and foreground verification, using labelled control; banner may be skipped.
- Branch `dev`; no worktree, no push, no PR suggestion, no `.idea` commits. Each implementation task uses test-red → minimal code → test-green → focused commit.

---

## File Map

### New domain files (`app/src/main/java/com/mckimquyen/reader/domain/archivequery/`)

- `ArchiveQueryModels.kt` — date range, parsed query, month bucket/evidence/cue pair, result status models; no Android or infrastructure types.
- `ArchiveQueryInputParser.kt` — length/blank checks and supported EN/VI relative-month syntax; no general NLP.
- `ArchiveQueryDateRange.kt` — default 12-calendar-month range, validation, month stepping/adjacency using `java.time` and injected `Clock`/`ZoneId` values.
- `EvidenceExtractor.kt` — HTML-to-text boundary via existing Jsoup dependency, bounded sentence scan, verbatim evidence extraction.
- `PerspectiveShiftDetector.kt` — named EN/VI cue-pair table and conservative evidence-only shift logic.

### New infrastructure and UI files

- `infrastructure/archivequery/ArchiveQueryRepository.kt` — paginated DAO + semantic engine + bounded body reads orchestration; one query execution returns domain models.
- `ui/page/archivequery/ArchiveQueryUiState.kt` — sealed UI state, display models if not already placed in domain.
- `ui/page/archivequery/ArchiveQueryViewModel.kt` — injected context, repository, `@IODispatcher`, one cancellable job and latest-request/account guard.
- `ui/page/archivequery/ArchiveQueryPage.kt` — route wrapper and stateless Compose content for query/date controls, timeline, evidence and markers.

### Modified production files

- `domain/repository/ArticleDao.kt` — `queryArticleMetadataPage(accountId, sinceMillis, untilMillis, beforeDate, beforeId, limit)` returning `ArticleListItemWithFeed`, using `ARTICLE_LIST_COLUMNS` and account/date/keyset predicates; no body columns.
- `ui/page/common/RouteName.kt` — `ARCHIVE_QUERY` route.
- `ui/page/common/HomeEntry.kt` — route and scoped ViewModel; Back/disposal cancellation.
- `ui/page/setting/SettingsPage.kt` — entry row to archive query.
- `app/src/main/res/values/strings.xml` and maintained 37 locale `values-*/strings.xml` files — only strings used by page and accessibility labels.

### Tests

- JVM tests under `app/src/test/java/com/mckimquyen/reader/domain/archivequery/` and infrastructure tests under `.../infrastructure/archivequery/`.
- Room integration test alongside existing ArticleDao Room tests, using native SQLite for large-body boundaries.
- Compose tests under existing Android test setup/pattern; verify exact source article navigation with seeded Room fixture where framework permits.
- No schema snapshot changes expected. Verify `git diff -- app/schemas` stays empty.

---

## Task 1: Date Range and Query Input Domain

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/domain/archivequery/ArchiveQueryModels.kt`
- Create `app/src/main/java/com/mckimquyen/reader/domain/archivequery/ArchiveQueryDateRange.kt`
- Create `app/src/main/java/com/mckimquyen/reader/domain/archivequery/ArchiveQueryInputParser.kt`
- Test `app/src/test/java/com/mckimquyen/reader/domain/archivequery/ArchiveQueryInputParserTest.kt`
- Test `app/src/test/java/com/mckimquyen/reader/domain/archivequery/ArchiveQueryDateRangeTest.kt`

**Interfaces:**
- `data class ArchiveQueryRange(val startInclusive: Instant, val endExclusive: Instant)`
- `data class ParsedArchiveQuery(val topic: String, val explicitRange: ArchiveQueryRange?, val needsDateClarification: Boolean)`
- `sealed interface ArchiveQueryInputResult { data class Valid(val value: ParsedArchiveQuery); data class Invalid(val reason: InputError) }`
- `ArchiveQueryDateRange.default(now: Instant, zone: ZoneId): ArchiveQueryRange`
- `ArchiveQueryDateRange.validate(start: LocalDate, endInclusive: LocalDate, now: Instant, zone: ZoneId): RangeValidation`
- `ArchiveQueryInputParser.parse(text: String, now: Instant, zone: ZoneId): ArchiveQueryInputResult`

- [ ] **Step 1: Write tests for date boundaries.** Cover December→January, leap year, local midnight/zone, current month inclusive, future date, reversed dates, 12-month max and blank/oversized topic.
- [ ] **Step 2: Run focused tests and verify red.** Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.archivequery.ArchiveQueryDateRangeTest' --tests 'com.mckimquyen.reader.domain.archivequery.ArchiveQueryInputParserTest'`; expect compile/test failure because types do not exist.
- [ ] **Step 3: Add minimum domain types and implementation.** Use `YearMonth` and `Clock` inputs; no `Calendar` mutable state. Default start = first day of current month minus 11 months at local start-of-day; endExclusive = captured `now`; month range validation based on inclusive `YearMonth` count.
- [ ] **Step 4: Implement exact grammar only.** Support VI `từ tháng M [năm YYYY] đến giờ|đến nay`; EN `since <month> [YYYY]` and `from <month> [YYYY] to now`; missing year resolves to latest occurrence not in future. Remove only a matched date clause from topic. Recognized but malformed/out-of-range temporal phrase returns `needsDateClarification`, never silently defaults. Trim/cap input at 500; reject empty remaining topic.
- [ ] **Step 5: Run both focused tests.** Expect all pass; add parameterized boundary examples if any behavior differs by timezone.
- [ ] **Step 6: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/archivequery app/src/test/java/com/mckimquyen/reader/domain/archivequery && git commit -m 'feat(exc-11): parse archive query dates'`.

## Task 2: Month Buckets and Adjacent-Period Model

**Files:**
- Modify `domain/archivequery/ArchiveQueryModels.kt`
- Create `domain/archivequery/TemporalGrouper.kt`
- Test `app/src/test/java/com/mckimquyen/reader/domain/archivequery/TemporalGrouperTest.kt`

**Interfaces:**
- `data class RankedArchiveArticle(val articleId: String, val date: Instant, val title: String, val feedName: String, val score: Float, val shortDescription: String)`
- `data class ArchiveMonth(val year: Int, val month: Int, val articleCount: Int, val topCandidates: List<RankedArchiveArticle>)`
- `TemporalGrouper.add(month: YearMonth, article: RankedArchiveArticle, maxCandidates: Int = 3): Map<YearMonth, ArchiveMonth>` or an equivalent accumulator preserving count and top-3.

- [ ] **Step 1: Write tests** for empty input, several months, date boundaries, stable score/date/id tie-break, duplicate IDs, count across >3 articles, and adjacent month arithmetic across year change.
- [ ] **Step 2: Run focused test** with `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.archivequery.TemporalGrouperTest'`; expect red.
- [ ] **Step 3: Implement immutable month aggregation.** Deduplicate article IDs; count all unique relevant articles; retain only top three candidates. Month labels remain UI concern.
- [ ] **Step 4: Verify tests green.** Add case proving aggregation state retains count while candidate list remains at most 3.
- [ ] **Step 5: Commit** `feat(exc-11): group ranked archive articles by month`.

## Task 3: Bounded Verbatim Evidence Extraction

**Files:**
- Create `domain/archivequery/EvidenceExtractor.kt`
- Test `app/src/test/java/com/mckimquyen/reader/domain/archivequery/EvidenceExtractorTest.kt`

**Interfaces:**
- `data class ArchiveEvidence(val articleId: String, val title: String, val feedName: String, val publishedAt: Instant, val quote: String, val quoteTruncated: Boolean, val topicMatched: Boolean)`
- `EvidenceExtractor.toPlainText(html: String, maxChars: Int = 40_000): String`
- `EvidenceExtractor.extract(article: RankedArchiveArticle, bodyHtml: String, topicTokens: Set<String>): ArchiveEvidence?`

- [ ] **Step 1: Write tests** for blank body, JS/script/style suppression, entities, tags, raw-description fallback input, Unicode surrogate boundary, scan cap, phrase boundaries, sentence order, punctuation, quote cap and truncation metadata. Assert quote equals substring of normalized body; never assert paraphrased text.
- [ ] **Step 2: Run focused test and verify red.**
- [ ] **Step 3: Implement HTML conversion with existing Jsoup.** Parse body fragment with no network/resource fetching; remove `script`, `style`, `noscript`, and non-visible metadata nodes; normalize whitespace; cap while preserving surrogate pairs.
- [ ] **Step 4: Implement sentence extraction.** Use `BreakIterator` with topic language locale (VI/EN fallback), stop after 40,000 chars, select first sentence with whole-token topic match, else first sentence ≥20 meaningful chars. Keep exactly one substring per article; truncate at word/Unicode boundary to 300 chars and put ellipsis outside quote payload.
- [ ] **Step 5: Run focused test.** Expect all pass; verify script text/attributes never enter quote.
- [ ] **Step 6: Commit** `feat(exc-11): extract bounded archive evidence`.

## Task 4: Conservative Opposing Cue Pairs

**Files:**
- Create `domain/archivequery/PerspectiveShiftDetector.kt`
- Test `app/src/test/java/com/mckimquyen/reader/domain/archivequery/PerspectiveShiftDetectorTest.kt`

**Interfaces:**
- `data class OpposingCuePair(val first: Set<String>, val second: Set<String>)`
- `data class ArchivePerspectiveShift(val earlier: YearMonth, val later: YearMonth, val firstCue: String, val secondCue: String, val earlierEvidence: ArchiveEvidence, val laterEvidence: ArchiveEvidence)`
- `PerspectiveShiftDetector.detect(earlier: ArchiveMonthWithEvidence, later: ArchiveMonthWithEvidence): ArchivePerspectiveShift?`

- [ ] **Step 1: Write tests** for both pair directions, same cue both sides, cue absent earlier, fewer than two unique articles on either side, gap month, same sentence with both cues, direct negation in EN/VI, longer-word false match, missing evidence, and duplicate article IDs.
- [ ] **Step 2: Run focused test and verify red.**
- [ ] **Step 3: Implement named static cue pairs** from spec (`increase/rising` vs `decrease/falling`, `support(s)` vs `oppose(s)`, `approve(s)` vs `reject(s)`, `tăng/tăng lên` vs `giảm/giảm xuống`, `ủng hộ` vs `phản đối`, `chấp thuận` vs `bác bỏ`). Use `WholePhraseMatcher` from `domain/steelman`; do not use `ContrarianCueLexicon` as shift evidence.
- [ ] **Step 4: Require adjacent months, two unique ranked IDs each, selected evidence on both sides, new opposing cue in later and absent from earlier selected evidence.** Ignore sentences containing both directions or immediate negation (`not`, `never`, `no`, `không`, `chưa`, `chẳng`). Emit the exact two evidence rows for UI.
- [ ] **Step 5: Run focused tests.** Expected all pass.
- [ ] **Step 6: Commit** `feat(exc-11): detect cautious opposing archive signals`.

## Task 5: Account-Scoped FIX-17 DAO Paging

**Files:**
- Modify `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Test existing ArticleDao Room integration test class in `app/src/test/java/com/mckimquyen/reader/domain/repository/` (create `ArticleDaoArchiveQueryTest.kt` if no focused class exists).

**Interface:**

```kotlin
@Transaction
@Query("""
    SELECT $ARTICLE_LIST_COLUMNS FROM article
    WHERE accountId = :accountId
      AND date >= :sinceMillis
      AND date < :untilMillis
      AND (
        :beforeDate IS NULL
        OR date < :beforeDate
        OR (date = :beforeDate AND id < :beforeId)
      )
    ORDER BY date DESC, id DESC
    LIMIT :limit
""")
suspend fun queryArticleMetadataPage(
    accountId: Int,
    sinceMillis: Long,
    untilMillis: Long,
    beforeDate: Long?,
    beforeId: String?,
    limit: Int,
): List<ArticleListItemWithFeed>
```

- [ ] **Step 1: Add integration tests before query.** Seed two accounts, equal-timestamp articles with ordered IDs, start-inclusive/end-exclusive dates, body fields containing large sentinels. Assert account isolation, exact stable page sequence, no body columns surfaced.
- [ ] **Step 2: Run test and verify compile/query failure.**
- [ ] **Step 3: Add DAO method** using only `ARTICLE_LIST_COLUMNS`, account and date predicates, keyset `(date,id)` ordering. Validate `limit > 0` at repository boundary; do not concatenate user data into SQL.
- [ ] **Step 4: Run DAO test.** Expected pass across repeated pages; ensure repeated timestamp boundary yields no duplicate/missing rows.
- [ ] **Step 5: Commit** `feat(exc-11): page archive metadata by account and date`.

## Task 6: Archive Repository and Streaming Top-K

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/archivequery/ArchiveQueryRepository.kt`
- Test `app/src/test/java/com/mckimquyen/reader/infrastructure/archivequery/ArchiveQueryRepositoryTest.kt`
- Extend/add Room integration test for full repository flow.

**Interface:**
- `suspend fun search(accountId: Int, parsed: ParsedArchiveQuery, range: ArchiveQueryRange, now: Instant, zone: ZoneId): ArchiveQueryResult`
- Result carries range, per-month all-relevant count, top evidence candidates, evidence failures, and shifts; no body strings retained after extraction.

- [ ] **Step 1: Write repository tests** with fake DAO/engine for page boundary, rank score threshold, top-three merge across pages, cancellation between pages, equal-score deterministic tie-break, no candidates, account switch, and read-body cap.
- [ ] **Step 2: Run focused tests and verify red.**
- [ ] **Step 3: Implement metadata scan.** Loop DAO pages of 200 using last row `(date,id)`; adapt each metadata item through `toArticleWithFeed()`, call actual `rank(..., limit = page.size)`, merge score results into month counters/top-three, discard page/list before next iteration. Check coroutine cancellation between pages.
- [ ] **Step 4: Ensure full-window recall.** Test 1,001+ metadata rows, relevant article on oldest page; no default rank top-50 truncation before aggregation. Cache remains engine-owned.
- [ ] **Step 5: Add bounded body reads.** For each month top-three sequentially: call `queryArticleBodyLengths` first; if `max(rawBytes, fullBytes ?: 0) > 1_048_576`, emit oversize status and skip body query. Else `queryArticleBody`; choose nonblank fullContent else rawDescription; immediately normalize/extract and release body. Verify at most 36 queries and cancellation between each article.
- [ ] **Step 6: Detect shifts** only after all per-month evidence extraction and preserve empty month buckets across requested range so a gap prevents comparison.
- [ ] **Step 7: Run repository + Room integration.** Fixture at least two consecutive months with two unique relevant articles each, one older page candidate, a body-based quote per selected article, and one explicit opposing pair; assert exact IDs, counts, quote substrings, cue pair and account isolation.
- [ ] **Step 8: Commit** `feat(exc-11): search archive metadata and bounded evidence`.

## Task 7: ViewModel State, Cancellation, Account and Clock

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/archivequery/ArchiveQueryUiState.kt`
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/archivequery/ArchiveQueryViewModel.kt`
- Test `app/src/test/java/com/mckimquyen/reader/ui/page/archivequery/ArchiveQueryViewModelTest.kt`

**Interface:**
- `ArchiveQueryViewModel.submit(text: String, startDate: LocalDate?, endDateInclusive: LocalDate?)`
- `ArchiveQueryViewModel.retry()`
- `StateFlow<ArchiveQueryUiState>` states: `Idle`, `InvalidInput(reason)`, `Searching(parsedTopic, range)`, `NoResults(range, months)`, `Ready(result)`, `Error`.

- [ ] **Step 1: Write tests** for state transitions, parser result, no-results, repository error, retry, latest submit wins, blank cancels and clears, account switch discards old result, cancellation rethrows/does not become Error, injected clock makes dates deterministic.
- [ ] **Step 2: Run focused test and verify red.**
- [ ] **Step 3: Implement ViewModel** with `@ApplicationContext Context`, repository, `@IODispatcher CoroutineDispatcher`, injected `Clock`/zone provider. Use one `Job`, cancel before submit, increment request generation, snapshot `currentAccountId` once on IO; check current account/generation before publishing results.
- [ ] **Step 4: Handle failures safely.** Catch `CancellationException` and rethrow; translate input errors to localized resource IDs; avoid logging query/body/URLs/exception payload; retry last valid immutable request.
- [ ] **Step 5: Run tests.** Expect pass; verify cancelled older job cannot publish Ready/Error.
- [ ] **Step 6: Commit** `feat(exc-11): add cancellable archive query state`.

## Task 8: UI Strings and Resource Parity

**Files:**
- Modify `app/src/main/res/values/strings.xml`
- Modify all maintained `app/src/main/res/values-*/strings.xml` (38 total locales)
- Test existing `StringResourceParityTest` (do not add duplicate parity infrastructure).

- [ ] **Step 1: Add EN/VI strings** for title, field labels/hint, search, start/end dates, understood range/topic, date clarification, invalid ranges, searching, no results, empty month, insufficient comparison, no/large body, quote truncation accessibility, possible shift, cue signal, disclaimer, retry/error, article count, month period and TalkBack labels. Add only strings used in UI.
- [ ] **Step 2: Add English fallback values to other 36 maintained locales** preserving XML escaping and placeholders exactly; do not edit unmaintained locales.
- [ ] **Step 3: Run `./gradlew testDevDebugUnitTest --tests '*StringResourceParityTest'`;** expect pass with exact 38-locale key parity.
- [ ] **Step 4: Run `./gradlew lintDevDebug`;** ensure no `UnusedResources`, `PluralsCandidate`, or malformed formatting. Record pre-existing warnings honestly.
- [ ] **Step 5: Commit** `feat(exc-11): add archive query localized strings`.

## Task 9: Compose Timeline Page and Accessibility

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/archivequery/ArchiveQueryPage.kt`
- Create Compose UI test at the project’s established Compose test source set after checking existing `LocalSyncPageTest` / page tests.

- [ ] **Step 1: Write Compose tests** for Idle, input/date field labels, InvalidInput, Searching semantics, NoResults, all month buckets including empty month, exact evidence quote and source, oversize/missing body messages, possible shift with both linked source rows, retry and font-scale content visibility.
- [ ] **Step 2: Run focused test and verify red.**
- [ ] **Step 3: Build stateless `ArchiveQueryContent(state, onSubmit, onRetry, onOpenArticle)`** using Material3 and project typography. Keep text/date selection in state owner or `rememberSaveable`; never place repository logic in composable.
- [ ] **Step 4: Implement controls.** Query max length 500; date controls permit valid supported range up to 12 months; parser-unsupported date prompts explicit correction; show interpreted topic/range before results. IME Search submits; visible button has content description.
- [ ] **Step 5: Implement month timeline.** Render chronological month buckets; empty month gets localized insufficient-data row. For each evidence show quote as exact text with truncation indicator outside quote semantics, title/feed/date, and accessible open action. Month article count is not quote count.
- [ ] **Step 6: Implement shift marker** between months only, with text “possible”, matched pair, two source quotes/links, and persistent disclaimer; no color-only cue.
- [ ] **Step 7: Run Compose tests** with system font scale ≥1.3 and verify no clipped controls, horizontal overflow, or inaccessible click target.
- [ ] **Step 8: Commit** `feat(exc-11): render temporal archive evidence timeline`.

## Task 10: Navigation and Settings Entry

**Files:**
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt`
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt`
- Extend Compose navigation/settings tests at established paths.

- [ ] **Step 1: Add failing navigation tests** verifying Settings row opens route, Back returns Settings, route re-entry starts without stale result, leaving route cancels active job.
- [ ] **Step 2: Run focused tests and verify red.**
- [ ] **Step 3: Add `RouteName.ARCHIVE_QUERY = "archive_query"`; register using existing `animatedComposable` route pattern and route-scoped `hiltViewModel()`; collect state lifecycle-aware following repository’s Compose version/pattern.
- [ ] **Step 4: Wrap page with `BaseScaffold` and explicit Back callback; attach `DisposableEffect` only if required to cancel the route-owned search job, and always remove any observer on dispose. Prefer `ViewModel.onCleared` cancellation where sufficient.
- [ ] **Step 5: Add one Settings row** with localized title and navigation callback; no new settings subsection, toggle, preference or persistence.
- [ ] **Step 6: Run navigation/settings tests and parity test.** Expect pass.
- [ ] **Step 7: Commit** `feat(exc-11): add archive query navigation entry`.

## Task 11: End-to-End Tests and Full Verification

**Files:**
- Add/extend integration tests under `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/archivequery/` only if existing runner supports Hilt/Room fixture; otherwise use established Room + Compose integration setup.
- No production file changes unless a verified test exposes defect; any defect fix gets its own failing test and task commit.

- [ ] **Step 1: Add integration fixture** with two accounts, 2 adjacent months plus a gap month case, 2+ relevant articles/month, one irrelevant article, body HTML and opposing cues. Assert route-repository-Room result and open destination article/account.
- [ ] **Step 2: Run targeted JVM/Room/Compose tests.** Record exact commands and outcomes.
- [ ] **Step 3: Run full `./gradlew testDevDebugUnitTest --rerun`;** expected 0 failures/errors. If any failure occurs, stop and investigate rather than broad rerun/ignore.
- [ ] **Step 4: Run `./gradlew lintDevDebug`;** expected 0 errors; report warning count, not claim warning-free if not.
- [ ] **Step 5: Run `./gradlew assembleDevDebug` and `./gradlew assembleDevDebugAndroidTest`;** verify no Room schema output/change and inspect `git status` for unrelated `.idea` files. Do not use `connectedDevDebugAndroidTest`.
- [ ] **Step 6: Review `git diff --check`, full diff, all 38 locale parity, and source/body/account flows.** Confirm no body-bearing metadata query, no external network, no default key usage, no new dependency, no schema migration.
- [ ] **Step 7: Commit integration tests/final corrections** only with their relevant tests, message `test(exc-11): verify archive timeline end to end` (or narrow fix commit if test exposed defect).

## Task 12: BG6 Instrumentation and Smoke

**Precondition:** Tasks 1–11 pass. Before any device command run `adb devices` to enumerate; do not issue any operation to a device except serial `118743744X002560`. Never run connected test task.

- [ ] **Step 1: Fresh backup.** On BG6 only, copy Room DB + journal/WAL/SHM if present and every relevant DataStore file to `/tmp/exc11-bg6-backup`; record SHA/size and DB integrity. Do not delete DataStore. If backups cannot be verified, stop before install/smoke.
- [ ] **Step 2: Record and set awake value.** Read `stay_on_while_plugged_in`, set to `2` for test, record original; do not change other device settings.
- [ ] **Step 3: Install/run instrumentation only on BG6.** Build instrumentation APK already done; invoke runner with `adb -s 118743744X002560 shell am instrument ...` using explicit component. Do not invoke `connectedDevDebugAndroidTest`.
- [ ] **Step 4: Seed only a restorable test fixture.** Use test-only in-memory Room fixture for automated test; if UI smoke requires persisted fixture, back up first, add fixture rows with a script reviewed before execution, never change real article body/date, and retain exact restore files.
- [ ] **Step 5: Smoke UI.** Open Settings → Ask My Archive, try default query, explicit EN/VI month grammar, unsupported temporal phrase, two-month results, quotes opening right article, adjacent-month cue marker and no-data result. Confirm Back cancels active query. Check ads only when visible: fresh UI dump + foreground identity, tap labelled close/continue; skip banner if not blocking.
- [ ] **Step 6: Restore.** Force-stop app, restore backed-up Room and DataStore; do not delete DataStore. `cmp` every original backup file against device copy; verify DB integrity. Restore `stay_on_while_plugged_in` to recorded original (currently `0`).
- [ ] **Step 7: Record evidence and limitations.** State actual UI/test outcomes; if archive lacks cues or adequate article count, report shift as not observed (do not invent). Any HiOS freeze, ad, instrumentation failure or restore mismatch is reported and blocks completion.
- [ ] **Step 8: Commit smoke report and update docs.** After verified smoke, write completion report under `doc/task/done/EXC-11_temporal-ask-my-archive_DONE.md`, update `doc/feature.md`, move/update epic row only if user explicitly approves completion. Keep EXC-11 open if any AC or smoke condition remains unverified. Never push.

## Self-Review Checklist

- **Spec coverage:** date parsing/default/range, full paginated metadata scan, FIX-17, body bounds/quotes, opposing cue logic, missing-month semantics, account/privacy, UI/navigation/38 locales, unit/Room/Compose/integration tests, BG6 backup/restore all have tasks.
- **Placeholder scan:** no TBD/TODO or “write tests for above”; exact behavior, interfaces and test cases are listed.
- **Type consistency:** domain query parser yields `ParsedArchiveQuery`; DAO emits metadata rows; repository maps rank results to domain `RankedArchiveArticle`; body extraction emits `ArchiveEvidence`; shift detector consumes month/evidence model; UI state consumes repository result.
- **Scope check:** one cohesive feature through domain/data/UI; no independent services, schema, cloud, or new dependency.
- **Risk note:** spec deliberately limits full body load; implementation must apply body-length gate before `queryArticleBody`, because chunked cursor reads alone do not bound total memory.
