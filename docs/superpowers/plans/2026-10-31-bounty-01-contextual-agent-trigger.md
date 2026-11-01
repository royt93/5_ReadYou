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

- [x] **Step 1: Write failing unit tests**
- [x] **Step 2: Run targeted test and verify red** (missing `BountyQuerySanitizer` unresolved references)
- [x] **Step 3: Implement sanitizer** (split on sentence, trim boundary punctuation, safe 100-char cap respecting surrogates, escape `\`, `%`, `_`)
- [x] **Step 4: Run unit tests green** (7/7 tests passed)
- [x] **Step 5: Commit** (`4b04b9cf`)

### Task 2: Same-account Room search and investigation stats

**Files:** Modify `ArticleDao.kt`; create `BountyInvestigationResult.kt`, `BountyInvestigationRepository.kt`, and Robolectric tests.

- [x] **Step 1: Add failing in-memory Room tests** (8 cases in `BountyInvestigationRepositoryTest`)
- [x] **Step 2: Run targeted test red** (unresolved `BountyInvestigationRepository`)
- [x] **Step 3: Add bounded metadata DAO query** (`queryRelatedArticleListWithFeed`, `ARTICLE_LIST_COLUMNS`, `ESCAPE '\'`)
- [x] **Step 4: Implement repository and stats** (repository escapes LIKE metacharacters itself; callers pass the sanitized raw query)
- [x] **Step 5: Run targeted Room tests green** (8/8; DAO/ArticleHistory/Bounty regression 52/52)
- [x] **Step 6: Commit**

Deviation recorded: the plan said callers pre-escape; the repository now owns escaping so no caller can forget it. First run failed with `ESCAPE expression must be a single character` because the Room query lives in a Kotlin raw string, where `'\\'` is two characters; a single `'\'` is correct.

### Task 3: Preserve quote chip and add investigation selection action

**Files:** Modify `QuoteTextToolbar.kt`, `QuoteShareChip.kt`, `Content.kt`; extend toolbar tests; add `SelectionActionChipsTest`; add the 13 `bounty_*` strings to all 38 locales (moved here because the chip needs `bounty_action_investigate`).

- [x] **Step 1: Write failing toolbar tests** (5 new cases in `QuoteTextToolbarTest`)
- [x] **Step 2: Run toolbar test red** (`onInvestigate` / `triggerInvestigate` unresolved)
- [x] **Step 3: Generalize toolbar and chip composition** (primary ctor `(delegate, onInvestigate, onShareQuote)` + secondary `(delegate, onShareQuote)` for source compatibility; chip row with `QUOTE_INVESTIGATE_CHIP_TAG`)
- [x] **Step 4: Read and restore clipboard on investigation action** (`Content.kt`: shared `readSelectionAndRestoreClipboard()`, `BountyQuerySanitizer` applied only to the investigate path, `onInvestigate: (String) -> Unit = {}` parameter)
- [x] **Step 5: Run unit/popup tests green** (`QuoteTextToolbarTest` 11/11, `SelectionActionChipsTest` 4/4, `QuoteChipPlacementTest` 4/4, `StringResourceParityTest` 6/6; `compileDevDebugKotlin` passes)
- [x] **Step 6: Commit**

Findings: (1) a single constructor with a defaulted `onInvestigate` before `onShareQuote` makes a trailing lambda bind to the wrong parameter, hence the secondary constructor. (2) Investigation still invokes the platform copy even with no `onInvestigate` callback, matching share's contract. (3) `ReadingPage` does not pass `onInvestigate` yet, so the chip is inert in the real UI until Task 4/5 wire the ViewModel and sheet.

### Task 4: Investigation state, local lookup and BYOK-only Gemini action

**Files:** Create `BountyInvestigationState.kt`; modify `ReadingViewModel.kt`; modify `GeminiSummaryService.kt`; add `GeminiSummaryServiceBountyTest` and `ReadingViewModelBountyTest`.

- [x] **Step 1: Write failing ViewModel tests** (6 cases in `ReadingViewModelBountyTest`)
- [x] **Step 2: Add failing explicit-BYOK request tests** (5 cases in `GeminiSummaryServiceBountyTest`)
- [x] **Step 3: Implement BOUNTY state and local investigation** (`ReadingViewModel`: `openBountyInvestigation`, `dismissBountyInvestigation`, `bountyInvestigationState`, 700ms minimum scan delay, scoped to loaded article's account ID and excluding the current article)
- [x] **Step 4: Implement BYOK-only AI API** (`GeminiSummaryService.summarizeBountyContext`, `hasCustomApiKey()`; reads custom key directly via `customKeyReader`; never calls `resolveApiKeys()` and never falls back to `GeminiConfig.API_KEYS`)
- [x] **Step 5: Run ViewModel/service tests green** (51/51 across `ReadingViewModel*` and `GeminiSummaryService*`)
- [x] **Step 6: Commit**

### Task 5: Radar/result sheet and ReadingPage navigation

**Files:** Create `BountyInvestigationSheetContent.kt`; modify `ReadingPage.kt`; add Compose tests.

- [x] **Step 1: Write failing Compose tests** (10 cases in `BountyInvestigationSheetContentTest`)
- [x] **Step 2: Run targeted UI tests red** (unresolved `BountyInvestigationSheetContent`)
- [x] **Step 3: Implement radar and result content** (Canvas rings + `rememberInfiniteTransition` sweep, stats, date range, highlighted related rows, empty/error states, BYOK-gated AI action)
- [x] **Step 4: Wire into `ReadingPage` existing drawer** (`isAnySheetOpen`, `BackHandler` precedence, outside-dismiss, `BottomDrawer` branch, row navigation via `${RouteName.READING}/$id`, `Content(onInvestigate = ...)`)
- [x] **Step 5: Run Compose tests green** (10/10; combined Bounty/Quote/ReadingViewModel/Gemini/Parity suite 144/144)
- [x] **Step 6: Commit**

Note: the plan's radar action used `OutlinedButton`; the Failed state offers `Button` retry. Detail-sheet host remains the app's `BottomDrawer` (Material `ModalBottomSheetLayout`).

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
