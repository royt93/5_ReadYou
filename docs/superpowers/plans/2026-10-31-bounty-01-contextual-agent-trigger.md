# BOUNTY-01 Contextual Agent Trigger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let readers select text, investigate it against same-account Room article metadata, inspect related articles in a radar sheet, and optionally request a Gemini context summary using only a user-supplied BYOK key.

**Architecture:** Add a pure query sanitizer and local investigation service backed by a Room DAO projection using `ARTICLE_LIST_COLUMNS` and `ArticleListItemWithFeed`. Extend `QuoteTextToolbar` with a second selection action; `ReadingViewModel` owns scan/results/summary state and existing `ReadingPage` `BottomDrawer` presents it. Optional AI uses a dedicated BYOK-only gateway call that never resolves default keys.

**Tech Stack:** Kotlin, Room, Coroutines/Flow, Jetpack Compose Canvas, existing Material `BottomDrawer` (`ModalBottomSheetLayout`), OkHttp/AiRequestGateway, JUnit4, Robolectric, Android Compose instrumentation tests.

## Global Constraints

- Branch `dev` only; no worktrees.
- TECNO BG6 serial `118743744X002560` is only authorized hardware target; it was not connected at planning time. Before device operations run `adb devices`, verify exact model/serial, and do not target other devices.
- Observe R4: if full-screen `AdActivity` covers UI, stop immediately, tell owner, wait for `done`.
- Query only same-account `title` and `shortDescription`; never materialize `rawDescription` or `fullContent` (FIX-17).
- Use existing `ARTICLE_LIST_COLUMNS` projection and `ArticleListItemWithFeed` DTO, not `SELECT *` or `Article` entity.
- Escape user `%`, `_`, and `\` before SQL `LIKE`; use SQL `ESCAPE '\'` so selected text is literal.
- Optional AI action appears only when `context.customGeminiApiKey.isNotBlank()`; request passes only that custom key. Never call `GeminiSummaryService.resolveApiKeys()` for BOUNTY requests because it appends default system keys.
- Preserve quote sharing behavior, selection toolbar platform actions, and existing quote tests.
- Use existing `BottomDrawer`; project Material3 is 1.0.1 and does not expose Material3 `ModalBottomSheet`.
- Add resource keys to all 38 maintained `values*/strings.xml` files. No new dependency or schema migration.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/model/bounty/BountyQuerySanitizer.kt`: normalize selected text and escape SQL LIKE metacharacters.
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/bounty/BountyInvestigationResult.kt`: result metadata (query, articles, unique feed count, optional min/max dates).
- Modify `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`: one bounded same-account query using `ARTICLE_LIST_COLUMNS`, `ArticleListItemWithFeed`, escaped literal LIKE.
- Create `app/src/main/java/com/mckimquyen/reader/domain/repository/BountyInvestigationRepository.kt`: call DAO and calculate result stats (no network/body access).
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/bounty/BountyInvestigationState.kt`: Idle/Scanning/Results/AI summary/Error UI state.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`: open/dismiss investigation, asynchronous Room query, explicitly BYOK-only AI summary; keep cancellation and loading/error states.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/component/quote/QuoteTextToolbar.kt`: second action while preserving existing `triggerShare()` contract.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/component/quote/QuoteShareChip.kt` or create `SelectionActionChips.kt`: render 2 popup AssistChips and position them within window bounds.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt`: capture selected text once, restore previous clipboard immediately, call investigation callback; preserve share callback.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`: pass callbacks, add sheet state/content branch and row navigation via `${RouteName.READING}/$articleId`.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/bounty/BountyInvestigationSheetContent.kt`: scanning radar Canvas, summary stats, related article rows/empty state, BYOK-only action.
- Add 10 BOUNTY resource keys to all 38 `strings.xml` files.
- Create unit/DAO/service tests and Compose widget tests under `app/src/test` and `app/src/androidTest` following repository patterns.

## Interfaces

Task 1 produces:

```kotlin
object BountyQuerySanitizer {
    const val MAX_QUERY_LENGTH: Int = 100
    fun sanitize(raw: String?): String?
    fun escapeLikeLiteral(query: String): String
}

data class BountyInvestigationResult(
    val query: String,
    val articles: List<ArticleListItemWithFeed>,
    val sourceCount: Int,
    val earliestDate: Date?,
    val latestDate: Date?,
)
```

Task 2 produces:

```kotlin
suspend fun ArticleDao.queryRelatedArticleListWithFeed(
    accountId: Int,
    currentArticleId: String,
    escapedKeyword: String,
): List<ArticleListItemWithFeed>

class BountyInvestigationRepository(private val articleDao: ArticleDao) {
    suspend fun investigate(accountId: Int, currentArticleId: String, query: String): BountyInvestigationResult
}
```

Task 3 produces:

```kotlin
enum class BountyInvestigationPhase { SCANNING, RESULTS, AI_SUMMARY, ERROR }
data class BountyInvestigationState(
    val phase: BountyInvestigationPhase = BountyInvestigationPhase.SCANNING,
    val query: String = "",
    val result: BountyInvestigationResult? = null,
    val aiSummary: String? = null,
)
```

`ReadingViewModel.openBountyInvestigation(query: String)` uses current `articleWithFeed` from `ReadingUiState` to obtain `accountId` and current `articleId`; no UI caller passes untrusted account scope.

---

### Task 1: Sanitize selected query safely

**Files:** Create `BountyQuerySanitizer.kt` and `BountyQuerySanitizerTest.kt`.

- [ ] **Step 1: Write failing unit tests**

```kotlin
@Test fun sanitizeCollapsesWhitespaceAndTrimsEdgePunctuation() {
    assertEquals("DeepSeek-R1", BountyQuerySanitizer.sanitize("  \"DeepSeek-R1!\"  "))
}
@Test fun sanitizeUsesFirstSentenceAndCapsAt100Characters() {
    assertEquals("Company Alpha", BountyQuerySanitizer.sanitize("Company Alpha. Second sentence"))
    assertEquals(100, requireNotNull(BountyQuerySanitizer.sanitize("A".repeat(130))).length)
}
@Test fun sanitizeRejectsBlankOrOneCharacterSelection() {
    assertNull(BountyQuerySanitizer.sanitize("  \n "))
    assertNull(BountyQuerySanitizer.sanitize("x"))
}
@Test fun escapeLikeLiteralTreatsPercentUnderscoreAndBackslashAsText() {
    assertEquals("A\\%B\\_C\\\\D", BountyQuerySanitizer.escapeLikeLiteral("A%B_C\\D"))
}
```

- [ ] **Step 2: Run targeted test and verify red**

Run: `./gradlew testDevDebugUnitTest --tests "*BountyQuerySanitizerTest"`
Expected: compilation fails because `BountyQuerySanitizer` is absent.

- [ ] **Step 3: Implement sanitizer**

Use `QuoteSelection.sanitize(raw)` to collapse Unicode whitespace. Select first non-empty sentence split at `.`, `;`, or line break; trim surrounding quote/bracket/punctuation characters; cap to 100 Unicode code points without breaking surrogate pairs. Return null for final length under 2. `escapeLikeLiteral()` escapes `\` first, then `%` and `_` with `\` prefix.

- [ ] **Step 4: Run unit tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*BountyQuerySanitizerTest" --rerun`
Expected: all sanitizer tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/bounty/BountyQuerySanitizer.kt app/src/test/java/com/mckimquyen/reader/domain/model/bounty/BountyQuerySanitizerTest.kt
git commit -m "feat(bounty-01): sanitize selected investigation queries"
```

### Task 2: Same-account Room search and investigation stats

**Files:** Modify `ArticleDao.kt`; create `BountyInvestigationResult.kt`, `BountyInvestigationRepository.kt`, and Robolectric tests.

- [ ] **Step 1: Add failing in-memory Room tests**

In `app/src/test/java/com/mckimquyen/reader/domain/repository/BountyInvestigationRepositoryTest.kt`, create an in-memory `AndroidDatabase`, insert feeds/articles from two accounts, and assert: matches in title and shortDescription; excludes current article and other accounts; `%`, `_`, and `\` are literal; result limit is 20 newest; source count deduplicates feed IDs; earliest/latest dates correct; empty result has null dates and zero sources.

- [ ] **Step 2: Run targeted test red**

Run: `./gradlew testDevDebugUnitTest --tests "*BountyInvestigationRepositoryTest"`
Expected: unresolved repository/query or missing result behavior.

- [ ] **Step 3: Add bounded metadata DAO query**

Add `@Transaction @Query` selecting exactly `ARTICLE_LIST_COLUMNS` from `article`, joined through Room `@Relation` to `Feed`, with predicates `accountId = :accountId AND id != :currentArticleId AND (title LIKE '%' || :escapedKeyword || '%' ESCAPE '\\' OR shortDescription LIKE '%' || :escapedKeyword || '%' ESCAPE '\\')`, order by date descending and limit 20. Return `List<ArticleListItemWithFeed>`. No entity/body projection.

- [ ] **Step 4: Implement repository and stats**

Sanitize/escape is handled before repository call. Repository calls the DAO, calculates distinct `feedId` count, min/max `article.date`, and returns `BountyInvestigationResult`. For empty lists, both dates null.

- [ ] **Step 5: Run targeted Room tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*BountyInvestigationRepositoryTest" --rerun`
Expected: all Room search and aggregation cases pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt app/src/main/java/com/mckimquyen/reader/domain/model/bounty app/src/main/java/com/mckimquyen/reader/domain/repository/BountyInvestigationRepository.kt app/src/test/java/com/mckimquyen/reader/domain/repository/BountyInvestigationRepositoryTest.kt
git commit -m "feat(bounty-01): search related article metadata locally"
```

### Task 3: Preserve quote chip and add investigation selection action

**Files:** Modify `QuoteTextToolbar.kt`, `QuoteShareChip.kt` (or create `SelectionActionChips.kt`), `Content.kt`; extend toolbar/popup tests and Android UI tests.

- [ ] **Step 1: Write failing toolbar tests**

Extend `QuoteTextToolbarTest`: standard platform callbacks still forward; `triggerShare()` still invokes copy then share only; `triggerInvestigate()` invokes copy then investigate only; both are no-op without a current selection; `hide()` clears both actions and anchor.

- [ ] **Step 2: Run toolbar test red**

Run: `./gradlew testDevDebugUnitTest --tests "*QuoteTextToolbarTest"`
Expected: missing investigation action API/test failures.

- [ ] **Step 3: Generalize toolbar and chip composition**

Add `onInvestigate: () -> Unit` callback to `QuoteTextToolbar` while retaining the existing constructor behavior with a default no-op for source compatibility. `triggerInvestigate()` calls the saved platform `onCopyRequested`, then the investigation callback. Replace the single `QuoteShareChip` with `SelectionActionChips` that shows the existing quote AssistChip plus a BOUNTY AssistChip side-by-side in a bounded `Popup`; reuse `QuoteChipPlacement` and clamp total row width to window width. Do not modify Android's native selection menu.

- [ ] **Step 4: Read and restore clipboard on investigation action**

In `Content`, preserve the prior clipboard clip, invoke the saved selection copy, read selected text, restore the prior clip immediately, sanitize using `BountyQuerySanitizer`, then invoke `onInvestigate(cleanQuery)`. Keep `QuoteShareChip` share flow unchanged and never persist the selected text elsewhere.

- [ ] **Step 5: Run unit/popup tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*QuoteTextToolbarTest" --tests "*QuoteChipPlacementTest" --rerun`
Expected: all old and new quote behavior passes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/component/quote app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt app/src/test/java/com/mckimquyen/reader/ui/component/quote
git commit -m "feat(bounty-01): add investigation action beside quote chip"
```

### Task 4: Investigation state, local lookup and BYOK-only Gemini action

**Files:** Create BOUNTY state type; modify `ReadingViewModel.kt`; modify `GeminiSummaryService.kt`; add ViewModel/service tests.

- [ ] **Step 1: Write failing ViewModel tests**

Extend existing `ReadingViewModel` unit tests or add `ReadingViewModelBountyTest`: opened query enters SCANNING then RESULTS after DAO completes; results stay scoped to loaded article's `accountId` and exclude current article; dismiss cancels/reset state; invalid query does not issue DAO call; lookup exceptions become ERROR state and do not crash reader; no key means `canUseAiContext=false` and no gateway call.

- [ ] **Step 2: Add failing explicit-BYOK request tests**

Add `GeminiSummaryServiceBountyTest` with a fake `AiRequestGateway`: blank custom key returns `MissingApiKey` without gateway call; nonblank custom key calls gateway with exactly `listOf(customKey)`; failure does not retry/fallback to `GeminiConfig.API_KEYS`.

- [ ] **Step 3: Implement BOUNTY state and local investigation**

Add `bountyInvestigationState: StateFlow<BountyInvestigationState>` and `openBountyInvestigation(query)` / `dismissBountyInvestigation()` on `ReadingViewModel`. Use `articleWithFeed.article.accountId` and `.id`; use `viewModelScope` with existing injected IO dispatcher for Room work. Store/propagate `CancellationException`; convert other exceptions to error state. Compute results immediately, retain `SCANNING` at least 700 ms with coroutine delay, then publish RESULTS. No blocking main-thread work.

- [ ] **Step 4: Implement BYOK-only AI API**

Add a dedicated `GeminiSummaryService.summarizeBountyContext(query, relatedArticles)` path. It reads `context.customGeminiApiKey.trim()` and throws `SummaryException.MissingApiKey` if blank. Build prompt only from bounded related article titles and short descriptions returned by the same-account query; call `AiRequestGateway.execute(useCase = "bounty_context", requestBody = ..., keys = listOf(customKey))`. Do not call `resolveApiKeys()` and do not retry with default keys. Add response-empty/error classification using existing `SummaryException` types.

- [ ] **Step 5: Run ViewModel/service tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelBountyTest" --tests "*GeminiSummaryServiceBountyTest" --rerun`
Expected: all state, scoping, cancellation, and BYOK-only tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/read/bounty app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt app/src/test
git commit -m "feat(bounty-01): add local investigation state and BYOK context"
```

### Task 5: Radar/result sheet and ReadingPage navigation

**Files:** Create `BountyInvestigationSheetContent.kt`; modify `ReadingPage.kt`; add Compose tests.

- [ ] **Step 1: Write failing Compose tests**

Test scanning text and radar Canvas tag; results header/query/stats/date range; empty state; related article title/feed/date row; BYOK button hidden when unavailable and shown when available; AI summary/error display; close callback; selecting related row calls article ID callback once.

- [ ] **Step 2: Run targeted UI tests red**

Run: `./gradlew testDevDebugUnitTest --tests "*BountyInvestigationSheetContentTest"`
Expected: unresolved composable/tag/state.

- [ ] **Step 3: Implement radar and result content**

Create `BountyInvestigationSheetContent(state, canUseAiContext, onRequestAiContext, onOpenArticle, onClose)`. For SCANNING, draw three faded concentric rings and a rotating sweep with `Canvas` + `rememberInfiniteTransition`; expose localized content description and stable test tag. For RESULTS, show entity chip, result counts/unique sources/date range, `LazyColumn` rows from `ArticleListItemWithFeed`, highlight query in title with `AnnotatedString`/`SpanStyle`, and localized empty state. Show AI action only when `canUseAiContext`. Use named constants for scan delay and max results, not magic business numbers.

- [ ] **Step 4: Wire into `ReadingPage` existing drawer**

Extend `isAnySheetOpen` with bounty visibility; add BOUNTY branch in `BottomDrawer.sheetContent` before quote/summary branches; add `openBountyInvestigation` and `dismissBountyInvestigation` handlers; pass related article callback that dismisses sheet and runs `navController.navigate("${RouteName.READING}/$articleId")`. Preserve current quote, deep-read, mind-map and summary drawer branches and BackHandler precedence.

- [ ] **Step 5: Run Compose tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*BountyInvestigationSheetContentTest" --rerun`
Expected: all sheet state/render/navigation callback tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read app/src/test/java/com/mckimquyen/reader/ui/page/home/read
 git commit -m "feat(bounty-01): show radar results in reader drawer"
```

### Task 6: Localization, full verification and hardware smoke

**Files:** all 38 maintained `strings.xml`; add/extend `SelectionActionChipsTest` and instrumentation coverage; update task/feature docs and Epic 15 index.

- [ ] **Step 1: Add localized resources**

Add all 10 `bounty_*` keys from the approved spec to every maintained `values*/strings.xml`. Use English in non-EN/VI locales as repository fallback convention. Keep format argument indices identical.

- [ ] **Step 2: Run parity/resource merge**

Run `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun processDevDebugResources`.
Expected: parity 6/6 and Android resource merge pass.

- [ ] **Step 3: Run unit suite, lint and builds**

Run `./gradlew testDevDebugUnitTest --rerun`, `./gradlew lintDevDebug`, `./gradlew assembleDevDebug assembleDevDebugAndroidTest`; report exact tests/warnings/errors.

- [ ] **Step 4: Gate and run device instrumentation/smoke**

First run `adb devices`; continue only if serial `118743744X002560` is listed and `getprop ro.product.model` confirms TECNO BG6. Do not use any other listed serial. Run instrumentation using `adb -s 118743744X002560 shell am instrument ...`, not connected Gradle task. For smoke, inspect foreground before every UI action. Open a local feed/article with existing selected text; verify both chips, trigger investigate, observe ~700 ms scan, results list, tap to navigate; verify Gemini action absent without BYOK and no default API request in logs. If full-screen ad appears, stop and wait for owner. Back up Room before any fixture; restore and integrity-check afterward; clear only test-created data, not existing user data.

- [ ] **Step 5: Update completion docs**

Only move `BOUNTY-01_contextual-agent-trigger.md` to done when unit/UI tests and actual TECNO smoke pass. Report limitations honestly (local archive scope; optional BYOK context grounded only on title/shortDescription; no default-key fallback). Update `doc/feature.md` and Epic 15 row. If TECNO unavailable or smoke blocked, keep task in progress and record blocker.

- [ ] **Step 6: Commit**

```bash
git add app/src doc/feature.md doc/task
 git commit -m "feat(bounty-01): add contextual local investigation agent"
```

## Final Verification Checklist

- [ ] Sanitizer covers whitespace, first sentence, Unicode-safe 100-character bound, minimum length and literal LIKE escapes.
- [ ] Room query uses only `ARTICLE_LIST_COLUMNS`, enforces account/current article bounds, literal LIKE search and 20-result limit.
- [ ] Related feed count/time range are correct; empty state and navigation callback work.
- [ ] Quote chip copy/share still passes existing tests; investigation restores prior clipboard and does not store selected text.
- [ ] Scanning and result UI tests pass; TalkBack semantics and all 38 locale keys are present.
- [ ] Gemini request is unavailable without custom key and passes exactly one BYOK key with no fallback.
- [ ] Full unit suite, lint, app build, test APK build pass.
- [ ] Instrumentation/smoke only runs on verified TECNO BG6 serial `118743744X002560`; R4 observed; test data cleaned/restored.
