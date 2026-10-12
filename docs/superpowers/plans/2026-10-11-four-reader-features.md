# RSS Cat Hub Four Reader Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver EXC-01, EXC-08, EXC-03, and EXC-07 as four sequential, testable vertical slices.

**Architecture:** Reuse current Gemini, clustering, semantic-search, mind-map, Room, DataStore, and Compose architecture. Add persistence only for digest history; keep graph composition and bionic formatting as pure/testable logic; isolate ML Kit behind a small translation adapter. Complete and verify each feature before starting next.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Room, Hilt, DataStore, existing OkHttp/AiRequestGateway/GeminiSummaryService, Google ML Kit Translation (verify API/dependency before implementation), JUnit4, MockK, Robolectric, AndroidX instrumentation/Compose UI tests.

## Global Constraints

- Keep module `:app`, namespace `com.mckimquyen.reader`, Java 17, minSdk 26.
- Keep Room/Hilt processing on KAPT and update `app/schemas` for Room schema changes.
- Any new user-facing string must have all maintained/shipped locale values and resource configuration consistent with repository policy.
- Every feature requires meaningful unit, Compose widget/UI, and integration tests; do not substitute a no-op test for a boundary that does not exist.
- Before any build/test/install on Android, run `adb devices`; if multiple devices are listed, ask user to select and lock one device for the rest of session. Use only `adb -s <serial>` commands thereafter.
- Run JVM tests with `./gradlew testDevDebugUnitTest --tests "<fully.qualified.TestClass>"`; run instrumentation with `./gradlew connectedDevDebugAndroidTest --tests "<fully.qualified.TestClass>"` where supported, otherwise use the full connected task.
- Run `./gradlew assembleDevDebug` after each vertical slice. Do not commit or push unless user explicitly asks.
- Feature order: EXC-01 → EXC-08 → EXC-03 → EXC-07. Each next feature starts only after the current feature's unit, widget, integration, build, and authorized device-smoke gates pass.

---

## Task 1: EXC-01 AI Daily Smart Digest

### File map

- Create `app/src/main/java/com/mckimquyen/reader/domain/model/digest/DailyDigest.kt`: Room entity for generated digest metadata/body/source article IDs.
- Create `app/src/main/java/com/mckimquyen/reader/domain/repository/DailyDigestDao.kt`: insert/load/list queries.
- Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt`: register entity and DAO; add explicit Room migration and schema snapshot.
- Create `app/src/main/java/com/mckimquyen/reader/domain/repository/DigestArticleSource.kt` only if existing DAO queries cannot provide the bounded eligible article set; prefer extending `ArticleDao` when its current structure fits.
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/digest/DigestArticleSelector.kt`: deterministic candidate ordering, empty/limit handling, source mapping.
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/DailyDigestService.kt`: call `AiRequestGateway`, construct strict prompt, parse/validate structured response, keep source references limited to provided article IDs.
- Create `app/src/main/java/com/mckimquyen/reader/domain/sv/DailyDigestSv.kt`: orchestrate selection → generation → Room persistence; inject DAO/service.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/digest/DigestViewModel.kt`, `DigestPage.kt`, and `DigestState.kt`: loading/empty/error/success/history states.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt`, `HomeEntry.kt`, `FlowPage.kt`: add navigation and entry point.
- Create `app/src/test/java/com/mckimquyen/reader/domain/model/digest/DigestArticleSelectorTest.kt` and `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/DailyDigestParserTest.kt`.
- Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/digest/DigestPageWidgetTest.kt` and `app/src/androidTest/java/com/mckimquyen/reader/integration/DailyDigestPersistenceIntegrationTest.kt`.

### Tasks

1. Inspect `ArticleDao`, `Article`, `GroupDao`, `FeedDao`, `AndroidDatabase` migration history, and current Flow navigation. Confirm eligibility query, article fields, existing DB version, and safe migration pattern before coding.
2. Add failing selector tests for no unread items, stable newest-first ordering, maximum candidate count, blank source content, and duplicate article IDs; run targeted JVM test and confirm failure.
3. Implement the pure selector using existing article model fields. Keep candidate cap as a named constant and never include untrusted content as generated source URLs.
4. Add failing parser tests for valid response, malformed JSON, empty digest sections, unknown source IDs, and duplicate references. Then implement strict JSON parsing/validation; reject or sanitize unknown IDs before persistence/rendering.
5. Add digest Room entity/DAO and migration. Write instrumented migration/persistence test that inserts related article/feed/group rows, persists one digest with source IDs, reloads it, and verifies source linkage and ordering. Keep generated text and source IDs in one transactional record or transaction.
6. Add orchestration service tests with fake generator and fake DAO verifying success save, empty input short-circuit, and generator error not persisted. Implement minimum service.
7. Add Compose widget tests for loading, empty, error/retry, successful digest, and source-chip callback. Implement state-driven page.
8. Add FlowPage entry and navigation; integration test navigation or page route using established Compose/navigation test patterns. Preserve existing behavior for other toolbar actions.
9. Run targeted tests, then full `./gradlew testDevDebugUnitTest`, relevant connected UI/integration test classes, and `./gradlew assembleDevDebug`.
10. Run device smoke test only after checking/locking Android target per global constraint. Verify generate → saved history → reopen → source article navigation. If no device is available, report smoke test blocked; do not claim verified.

## Task 2: EXC-08 Second Brain Graph

### File map

- Create `app/src/main/java/com/mckimquyen/reader/domain/model/secondbrain/BrainGraph.kt`: immutable nodes/edges/time bounds/source references.
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/secondbrain/BrainGraphBuilder.kt`: pure merge/dedupe/filter/cap logic over existing cluster, semantic concept, mind-map models.
- Create `app/src/main/java/com/mckimquyen/reader/domain/sv/SecondBrainSv.kt`: gather persisted data through existing DAOs/services and build bounded graph off main thread.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainViewModel.kt`, `SecondBrainPage.kt`, `BrainGraphCanvas.kt`, and `BrainNodeDetails.kt`.
- Modify `RouteName.kt`, `HomeEntry.kt`, and `SettingsPage.kt` or `FlowPage.kt` to add one discoverable entry.
- Create unit tests `app/src/test/java/com/mckimquyen/reader/domain/model/secondbrain/BrainGraphBuilderTest.kt`.
- Create widget tests `app/src/androidTest/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainPageWidgetTest.kt`.
- Create integration test `app/src/androidTest/java/com/mckimquyen/reader/integration/SecondBrainIntegrationTest.kt`.

### Tasks

1. Inspect exact output APIs/models of `StoryClusteringEngine`, `SemanticSearchEngine`, `ArticleMindMapExtractor`, related DAOs, existing clustering integration tests, and Compose Canvas patterns. Confirm how mind maps and dates are persisted; do not assume they can be joined if DB has no persistence.
2. Define stable graph node identity and edge rules from existing persisted data. Keep graph cap at 200 via a named constant; order truncation deterministically by recency and relevance. Specify behavior for missing mind maps and unmatched concepts in tests first.
3. Write failing `BrainGraphBuilderTest` cases for empty sources, duplicated concepts, isolated nodes, related edges, date-window filtering, future dates, and >200 node cap; run test to verify failure.
4. Implement pure graph builder. Do not add a graph library; keep graph model independent of Compose.
5. Add ViewModel/service state tests for empty/loading/error/success and date-window changes; perform DB/query/graph computation on injected IO/Default dispatchers, never Main.
6. Build Canvas renderer with deterministic initial layout for same graph, bounded node/edge drawing, accessible selectable semantics, and tap selection. Add detail panel ordered by timestamp.
7. Add a time slider that filters the current graph window. Keep selected node stable only while it remains in filtered graph; test selection removal when window changes.
8. Widget-test empty graph, populated graph, selecting a node, timeline filtering, content descriptions, and large graph does not crash. Add a graph-build/render benchmark or instrumentation timing assertion suitable for stable CI; avoid a flaky strict wall-clock UI assertion.
9. Integration-test real Room/DAO inputs plus existing clustering/mind-map results flowing into graph and selected node details. Use in-memory Room and deterministic engines/fixtures; no AI network calls.
10. Run targeted + full JVM and relevant connected tests, `./gradlew assembleDevDebug`, then device smoke test after target lock. Verify graph with empty data and populated data; report actual 200-node timing rather than assert unverified target.

## Task 3: EXC-03 Bionic Reading

### File map

- Create `app/src/main/java/com/mckimquyen/reader/ui/component/reader/BionicTextFormatter.kt`: pure plain-text-to-`AnnotatedString` formatter with fixed 40% leading emphasis rule and safe Unicode boundaries.
- Create a preference implementation using existing patterns in `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/` and register it in `Pref.kt`, `Settings.kt`, and `SettingsProvider` only if reading UI accesses the preference through those layers; otherwise use the established reading-style preference pattern already used by `ReadingStylePage`.
- Modify `ReadingStylePage.kt` to enable the current placeholder and persist the setting.
- Modify `HtmlToComposable.kt`/reader text renderer only at the smallest safe point where visible text can be formatted without changing HTML links, images, selection, or copied content; keep original source text for semantics/copy.
- Create `app/src/test/java/com/mckimquyen/reader/ui/component/reader/BionicTextFormatterTest.kt`.
- Create `app/src/androidTest/java/com/mckimquyen/reader/ui/component/reader/BionicReadingWidgetTest.kt`.
- Create `app/src/androidTest/java/com/mckimquyen/reader/integration/BionicReadingPreferenceIntegrationTest.kt`.

### Tasks

1. Inspect ReadingStylePage's existing preference conventions, Settings registration, `HtmlToComposable` text chunk generation, links/spans, and accessibility/copy code. Select formatting boundary that does not flatten existing spans or alter href annotations.
2. Specify the word rule precisely: use Unicode code points, bold `ceil(codePointCount * 0.4)` leading code points for alphabetic/numeric tokens, minimum one for non-empty tokens; punctuation/whitespace unchanged; exclude URLs/code/preformatted content if renderer identifies them.
3. Add failing pure formatter tests for empty text, one-letter words, even/odd lengths, Vietnamese combining marks, surrogate pairs, punctuation, repeated whitespace, URLs, and plain-text preservation; run targeted test.
4. Implement formatter using Java/Kotlin Unicode-safe offsets; confirm Compose SpanStyle ranges use UTF-16 indices, so map code-point boundaries to valid UTF-16 offsets before adding spans.
5. Enable preference in ReadingStylePage and verify setting persists across recreation using existing DataStore test utilities or a Robolectric integration test.
6. Integrate formatter into reader rendering while preserving existing annotation ranges, links, and copy text. Add widget tests toggle-on visual span ranges/semantics, toggle-off unchanged output, link click unchanged, and screen reader/plain-text content unchanged.
7. Add integration test covering preference state through reader content rendering; do not create a test that only verifies DataStore separately from UI behavior.
8. Benchmark formatter on representative long article text with an assert-based test for output correctness and a separately reported timing; avoid flaky hard wall-clock thresholds unless stable benchmark harness exists. AC target remains <50ms and must be measured.
9. Run targeted + full JVM and relevant connected tests, `./gradlew assembleDevDebug`, then device smoke test after target lock. Verify setting toggle survives app/page recreation and reader layout remains usable.

## Task 4: EXC-07 Bilingual Reader

### File map

- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/translation/OnDeviceTranslator.kt`: small injectable interface for model readiness/download, paragraph translation, cancellation/close.
- Create `MlKitOnDeviceTranslator.kt` implementing the interface with Google ML Kit Translation; confirm supported dependency/API versions and minSdk before adding dependency to `app/build.gradle`.
- Create `app/src/main/java/com/mckimquyen/reader/domain/sv/TranslationSv.kt`: locale mapping, per-target cache policy, sequential full-article translation, cancellation and unsupported-language result.
- Modify `HtmlToComposable.kt`/reader content to expose paragraph identity and inline/interleaved translated state without reconstructing unsafe HTML.
- Modify reading ViewModel/state and `ReadingPage.kt`/`Reader.kt` for selected paragraph and full-article toggle; close translator/model client at lifecycle end using `ViewModel.onCleared` or owned lifecycle abstraction.
- Add all required translated strings to maintained locale resources and update `resourceConfigurations` only if a new locale qualifier is genuinely introduced.
- Create JVM tests under `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/translation/` and domain translation service tests.
- Create widget tests under `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/`.
- Create integration test `app/src/androidTest/java/com/mckimquyen/reader/integration/BilingualReaderIntegrationTest.kt`.

### Tasks

1. Verify ML Kit Translation API, current supported language set, dependency size, model download behavior, license/release requirements, and locale mapping for app-supported locales. If substantial locales are unsupported or dependency size is unacceptable, stop and ask user whether to limit language support or revisit engine; do not silently substitute Gemini.
2. Add ML Kit dependency only after compatibility check. Define translator adapter with explicit states: `Unsupported`, `ModelMissing`, `Downloading`, `Ready`, `Translating`, `Error`, `Success`; use target language from current app BCP-47 locale mapped to ML Kit language code.
3. Add failing adapter/service tests using fake translator for supported/unsupported locale, model not downloaded, download failure/retry, translation success/error, repeated paragraph cache hit, target-locale cache separation, cancellation, and full-article partial failure. Implement minimal service and deterministic cache policy.
4. Inspect HTML parsing output boundaries; add stable paragraph IDs from source order/text hash with collision handling scoped to article. Never pass arbitrary markup to translation; translate rendered text only.
5. Implement inline paragraph translation and whole-article interleaved mode. Show download state and let user retry/cancel; preserve source text if translation fails. Keep translation state scoped to current article and cancel jobs when article changes or page closes.
6. Widget-test tapping a paragraph, repeated tap/cache state, model-download progress/error/retry, whole-article toggle, target-locale label, failure fallback to original text, and accessibility semantics.
7. Integration-test paragraph extraction → fake on-device translator → rendered translated state; verify cancellation on article switch and cache isolation across locales.
8. Run targeted + full JVM and relevant connected tests, `./gradlew assembleDevDebug`, then device smoke test after target lock. Verify fresh model download state, offline translation after model readiness, and app locale change behavior. Never claim <500ms for a cold model; measure warm translation separately.

## Final campaign checks

- Review `git diff` against all four specs/AC; run `git diff --check`.
- Run full `./gradlew testDevDebugUnitTest`, relevant/full `./gradlew connectedDevDebugAndroidTest`, and `./gradlew assembleDevDebug` after final integration.
- Confirm all Room schema/migration tests and all four required test levels exist and pass.
- Report device smoke-test evidence per feature; state blockers honestly.
- Do not commit, push, or move backlog task files automatically. Ask before changing `doc/feature.md` or task status because the repo’s feature ledger is currently absent and its exact desired ownership/status convention needs confirmation.
