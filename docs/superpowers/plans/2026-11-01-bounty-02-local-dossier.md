# BOUNTY-02 Local Intelligence Dossier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From a BOUNTY-01 investigation result, build a citation-backed four-section dossier of what the user's own saved archive says about an entity, show it in four Material 3 tabs, and export it as Markdown through the Android share sheet.

**Architecture:** A pure-Kotlin `DossierBuilder` turns `BountyInvestigationResult` (>= 2 articles) into a `Dossier` (verbatim summary quotes, per-day timeline, recurring phrases, per-source framings). `DossierMarkdownBuilder` renders it; `DossierSharer` writes it to `cacheDir/dossiers` and builds a `FileProvider` share intent (same pattern as `QuoteCardSharer`). `ReadingViewModel` holds the dossier in memory only; `DossierSheetContent` renders four tabs in the existing `BottomDrawer`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 1.0.1 (`TabRow`/`Tab`), existing `BottomDrawer`, `FileProvider`, JUnit4, Robolectric, Android Compose instrumentation. No Room change (DB stays v20), no network, no AI, no new dependency.

## Global Constraints

- Branch `dev` only; no worktrees.
- Pixel 7 Pro serial `2B051FDH3006MU` is the only device (`adb -s 2B051FDH3006MU ...`). Never use `connectedDevDebugAndroidTest`.
- Owner standing instruction: close a full-screen `AdActivity` yourself (tap its labelled close control, re-check `topResumedActivity`) and continue; report that ads appeared.
- Smoke MUST back up BOTH `databases/Reader*` AND `files/datastore/settings.preferences_pb` (check both files are non-empty) and restore BOTH. Never `rm` the datastore.
- Offline mechanical digest of the saved archive: no Gemini, no API key, no network, no Reddit/Hacker News.
- Inputs are list metadata only (`title`, `shortDescription`, feed, date, id); never read article bodies (FIX-17).
- Constants (exact): `SUMMARY_DEDUPE_JACCARD_THRESHOLD = 0.8`; max 3 summary sentences; 240 chars per sentence (surrogate-safe); max 8 recurring phrases, each in >= 2 different articles; max 3 headlines per source; 24h cache retention; builder returns `null` below 2 articles.
- "Recurring phrases" counts capitalized phrases; it is not named-entity recognition and the UI must say so. The dossier is not a fact-check and shows the standing limitation line.
- Do not modify `SnapshotNormalizer` or any Room entity/DAO.
- Material3 is 1.0.1: use `TabRow`/`Tab`; there is no `ModalBottomSheet`.
- Match whole words/phrases, never substrings (substring matching produced false positives on real BBC data in BOUNTY-01/ECHO-01).
- All `dossier_*` strings are added to all 38 `values*/strings.xml` in the task that first uses them (EN and VI hand-written, others English fallback).

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/model/bounty/Dossier.kt`: data models.
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/bounty/DossierBuilder.kt`: pure builder.
- Create `app/src/main/java/com/mckimquyen/reader/domain/model/bounty/DossierMarkdownBuilder.kt`: pure Markdown renderer.
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/bounty/DossierSharer.kt`: file write, retention, share intent.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`: dossier state, build, share, dismiss.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/bounty/DossierSheetContent.kt`: four-tab UI.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/bounty/BountyInvestigationSheetContent.kt`: add the `Create dossier` button.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`: sheet branch, BackHandler, share launch.
- Tests under `app/src/test` and `app/src/androidTest` (listed per task).

## Interfaces

Task 1 produces:

```kotlin
data class QuotedSentence(val text: String, val feedName: String, val date: Date, val articleId: String)
data class TimelineEntry(val day: LocalDate, val articles: List<DossierArticleRef>)
data class DossierArticleRef(val articleId: String, val title: String, val feedName: String, val date: Date)
data class RecurringPhrase(val phrase: String, val articleCount: Int)
data class SourceFraming(val feedName: String, val headlines: List<DossierArticleRef>)

data class Dossier(
    val query: String,
    val summary: List<QuotedSentence>,
    val timeline: List<TimelineEntry>,
    val phrases: List<RecurringPhrase>,
    val framings: List<SourceFraming>,   // empty when fewer than 2 sources
    val sourceCount: Int,
    val generatedAtMillis: Long,
)

object DossierBuilder {
    const val MIN_ARTICLES = 2
    const val MAX_SUMMARY_SENTENCES = 3
    const val MAX_SENTENCE_CHARS = 240
    const val MAX_PHRASES = 8
    const val MIN_ARTICLES_PER_PHRASE = 2
    const val MAX_HEADLINES_PER_SOURCE = 3
    const val SUMMARY_DEDUPE_JACCARD_THRESHOLD = 0.8
    fun build(result: BountyInvestigationResult, zone: ZoneId = ZoneId.systemDefault(), nowMillis: Long = System.currentTimeMillis()): Dossier?
}
```

Task 2 produces: `object DossierMarkdownBuilder { fun build(dossier: Dossier, zone: ZoneId = ZoneId.systemDefault()): String }`.

Task 3 produces: `class DossierSharer(context, ioDispatcher) { suspend fun write(markdown: String, query: String, nowMillis: Long): File; fun buildShareIntent(file: File, chooserTitle: String): Intent; companion { DIR_NAME = "dossiers"; RETENTION_MS; MIME_MARKDOWN = "text/markdown" } }`.

Task 4 produces: `ReadingViewModel.dossier: StateFlow<Dossier?>`, `openDossier()`, `dismissDossier()`, `suspend prepareDossierShare(chooserTitle: String): Intent?`; `ReadingUiState.showDossierSheet`.

Task 5 produces: `@Composable fun DossierSheetContent(dossier: Dossier, onOpenArticle: (String) -> Unit, onShare: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)`.

---

### Task 1: Pure `DossierBuilder`

**Files:** Create `Dossier.kt`, `DossierBuilder.kt`, `DossierBuilderTest.kt`.

- [ ] **Step 1: Write failing tests** in `app/src/test/java/com/mckimquyen/reader/domain/model/bounty/DossierBuilderTest.kt` (plain JUnit, no Android types). Use a helper that builds `ArticleListItemWithFeed` (as in `BountyInvestigationSheetContentTest.item`) and `BountyInvestigationResult`. Cover each assertion below with its own `@Test`:
  - `null` for 0 and 1 article; non-null for 2.
  - Summary: only sentences containing the query as a whole phrase (`disruption` does not match `disrupt`); at most 3; each <= 240 chars and surrogate-safe; verbatim (the exact substring exists in the source title/description); newest article first; near-duplicate sentence (Jaccard >= 0.8) dropped while a clearly different one is kept; each quote carries its own feed name, date and article id; no qualifying sentence gives an empty list.
  - Timeline: two articles on the same day in one entry; newest day first; the same instant lands on different days for different zones (`UTC` vs `Pacific/Auckland`); total articles across entries equals the input size.
  - Phrases: a capitalized phrase in 2 different articles is listed with count 2; a phrase in only 1 article is not; the query itself is excluded; days/months and a sentence-initial single common word ("The") are excluded; sorted by count desc then alphabetically; capped at 8; a title with no capitals (e.g. Japanese) yields an empty list.
  - Framings: 2 feeds give 2 `SourceFraming`, each with at most 3 headlines, newest first; 1 feed gives an empty `framings` and `sourceCount == 1`.
  - `generatedAtMillis` equals the injected `nowMillis`.

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*DossierBuilderTest"`. Expected: unresolved `DossierBuilder`/`Dossier`.

- [ ] **Step 3: Implement.** Sentence split on `(?<=[.!?])\s+` plus line breaks; whole-phrase match helper (a phrase matches only when both neighbours are non-letter/digit, as in `ArticleSignalAnalyzer.containsPhrase`); Jaccard over lowercase word sets using `SUMMARY_DEDUPE_JACCARD_THRESHOLD`; `safePrefix` that never ends on a high surrogate; timeline via `Instant.ofEpochMilli(date.time).atZone(zone).toLocalDate()`; phrase candidates as runs of 1-4 capitalized tokens (`Character.isUpperCase` of the first letter) with an explicit stop set (weekday and month names in English and Vietnamese, "The", "A", "An", "In", "On", "At"); count distinct article ids per phrase.

- [ ] **Step 4: Run green.** Same command with `--rerun`; expected all pass.

- [ ] **Step 5: Real-data probe (temporary, never committed).** Export titles/descriptions from the Pixel Room backup to `/tmp/dossier-real/articles.json`, run a temporary Robolectric test that builds dossiers for several real queries ("Trump", "Storm", "Budget"), print section sizes and the phrase list, and inspect for junk (stop words, all-caps noise, one-letter phrases). Fix the builder (not the test) if junk appears, add a regression test for each fix, then delete the probe and `/tmp/dossier-real`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/bounty app/src/test/java/com/mckimquyen/reader/domain/model/bounty
git commit -m "feat(bounty-02): build local dossier from investigation results"
```

### Task 2: `DossierMarkdownBuilder`

**Files:** Create `DossierMarkdownBuilder.kt`, `DossierMarkdownBuilderTest.kt`.

- [ ] **Step 1: Write failing tests** (plain JUnit): title contains the query and generation date; the standing limitation line is present (English text, not localized, because the file leaves the app); sections appear in order Summary, Timeline, Recurring phrases, Sources; summary quotes are rendered with `>` and carry feed and date; Markdown control characters in article text (`*`, `_`, `[`, `]`, `` ` ``, `#`, leading `>` or `-`) are escaped and a title containing a newline cannot start a new Markdown block; empty sections render an explicit "none" line instead of a blank heading; one-source dossiers state that only one source exists; output is deterministic for equal input and the same zone.

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*DossierMarkdownBuilderTest"`.

- [ ] **Step 3: Implement** with a private `escape()` that backslash-escapes `\ ` * _ [ ] ( ) # > + - ! |` only where they could change meaning, replaces CR/LF with a space, and trims; no raw HTML is ever emitted.

- [ ] **Step 4: Run green** (`--rerun`).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/bounty/DossierMarkdownBuilder.kt app/src/test/java/com/mckimquyen/reader/domain/model/bounty/DossierMarkdownBuilderTest.kt
git commit -m "feat(bounty-02): render dossier as escaped Markdown"
```

### Task 3: `DossierSharer`

**Files:** Create `infrastructure/bounty/DossierSharer.kt`, `DossierSharerTest.kt` (Robolectric, same annotations as `QuoteCardSharerTest`).

- [ ] **Step 1: Write failing tests**: file written under `cacheDir/dossiers` with the Markdown content round-tripped as UTF-8 (including Vietnamese diacritics and an emoji); name is `dossier_<slug>_<millis>.md` where the slug is lowercase ASCII, at most 40 chars, and never contains path separators or `..` even for a hostile query such as `../../etc/passwd`; files older than 24h are deleted while newer files are kept; `buildShareIntent` is an `ACTION_CHOOSER` wrapping an `ACTION_SEND` with type `text/markdown`, `FLAG_GRANT_READ_URI_PERMISSION`, an `EXTRA_STREAM` URI under the app's FileProvider authority, and `FLAG_ACTIVITY_NEW_TASK`; an empty query still yields a valid slug.

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*DossierSharerTest"`.

- [ ] **Step 3: Implement** mirroring `QuoteCardSharer` (`@ApplicationContext`, `@IODispatcher`, `withContext`, `Context.getFileProvider()`). `file_paths.xml` already exposes `cacheDir` through `cache-path "."`, so no resource change is needed; assert this in a test that resolves the URI.

- [ ] **Step 4: Run green** (`--rerun`).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/bounty app/src/test/java/com/mckimquyen/reader/infrastructure/bounty
git commit -m "feat(bounty-02): share dossier Markdown through FileProvider"
```

### Task 4: Strings and ViewModel state

**Files:** Modify all 38 `app/src/main/res/values*/strings.xml`; modify `ReadingViewModel.kt`; create `ReadingViewModelDossierTest.kt`.

- [ ] **Step 1: Add 16 `dossier_*` keys** (EN and VI hand-written, all other locales English): `dossier_create_action`, `dossier_sheet_title`, `dossier_tab_summary`, `dossier_tab_timeline`, `dossier_tab_names`, `dossier_tab_sources`, `dossier_share_action`, `dossier_chooser_title`, `dossier_limitation`, `dossier_summary_empty`, `dossier_names_note`, `dossier_names_empty`, `dossier_sources_single`, `dossier_sources_note`, `dossier_article_count` (`%1$d articles`), `dossier_share_error`. Run `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun processDevDebugResources` (expect 6/6).

- [ ] **Step 2: Write failing ViewModel tests** (copy the setup of `ReadingViewModelBountyTest`): `openDossier()` builds a dossier only when the bounty state has `RESULTS` with >= 2 articles and sets `showDossierSheet`; with 0/1 articles or a non-RESULTS phase it does nothing; `dismissDossier()` clears the dossier and the flag; `dismissBountyInvestigation()` also clears the dossier; switching article clears both; build runs on `defaultDispatcher` (use a `StandardTestDispatcher` and assert nothing is built before it advances); `prepareDossierShare` returns an intent through a fake `DossierSharer` and returns null with no dossier; a sharer exception returns null without crashing.

- [ ] **Step 3: Run red**, then **implement**: `_dossier`/`dossier`, `openDossier`, `dismissDossier`, `prepareDossierShare`; add `showDossierSheet` to `ReadingUiState`; give `ReadingViewModel` an optional `DossierSharer? = null` constructor parameter at the end (existing tests stay source-compatible) and keep Hilt injection working. Build via `withContext(defaultDispatcher)`.

- [ ] **Step 4: Run green** including every `ReadingViewModel*` test: `./gradlew testDevDebugUnitTest --tests "*ReadingViewModel*" --rerun`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/res app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read
git commit -m "feat(bounty-02): manage dossier state and strings"
```

### Task 5: `DossierSheetContent` and entry button

**Files:** Create `DossierSheetContent.kt`, `DossierSheetContentTest.kt`; modify `BountyInvestigationSheetContent.kt` (button) and its test.

- [ ] **Step 1: Write failing Compose tests** (Robolectric, `createComposeRule`, one `setContent` per test, `useUnmergedTree = true` for children of merged nodes): four tabs exist with stable tags; Summary shows quotes with feed/date and the limitation line; tapping the Timeline/Names/Sources tabs shows each section (assert by tag, not by text only); empty summary and empty names show their messages; one-source dossier shows `dossier_sources_single`; Names tab shows the "not named-entity recognition" note; tapping an article row calls `onOpenArticle` once with its id; Share and Close buttons call their callbacks once; tab and row touch targets are >= 48dp and tabs expose a content description. For `BountyInvestigationSheetContent`: the `Create dossier` button is absent with 0/1 articles and present with >= 2 (extend the existing test file; keep every existing assertion).

- [ ] **Step 2: Run red**, then **implement** with `TabRow`/`Tab`, `selectedTabIndex` kept in `rememberSaveable`, a `LazyColumn` per tab, `val` hoisting for every `stringResource` (never inside a lambda), and `import androidx.compose.runtime.getValue`. Add a `Create dossier` `OutlinedButton` (tag `BOUNTY_CREATE_DOSSIER_TAG`) to `ResultsContent`, shown only when `result.articles.size >= DossierBuilder.MIN_ARTICLES`, wired through a new `onCreateDossier: () -> Unit = {}` parameter placed last so existing callers compile.

- [ ] **Step 3: Run green:** `./gradlew testDevDebugUnitTest --tests "*Dossier*" --tests "*BountyInvestigationSheetContentTest" --rerun`.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/bounty app/src/test/java/com/mckimquyen/reader/ui/page/home/read/bounty
git commit -m "feat(bounty-02): show dossier in four Material 3 tabs"
```

### Task 6: Wire into `ReadingPage`

**Files:** Modify `ReadingPage.kt`; add `ReadingPageDossierWiringTest` only if a lightweight harness exists, otherwise rely on Task 4/5 tests plus device smoke.

- [ ] **Step 1: Wire** (read the current file first; every edit must be verified with grep afterwards): `val dossier by readingViewModel.dossier.collectAsState()`; add `readingUiState.showDossierSheet` to `isAnySheetOpen`; add `dismissDossier()` to the outside-dismiss block and give `BackHandler` the precedence `echo -> dossier -> bounty -> quote ...` (dossier closes before bounty so Back returns to the results); add a `BottomDrawer.sheetContent` branch before the bounty branch rendering `DossierSheetContent` with `onOpenArticle` (dismiss both, then `navController.navigate("${RouteName.READING}/$id")`), `onShare` (launch `readingViewModel.prepareDossierShare(chooserTitle)` in a scope and `context.startActivity`), and `onClose = dismissDossier`; pass `onCreateDossier = { readingViewModel.openDossier() }` to `BountyInvestigationSheetContent`; hoist `val dossierChooserTitle = stringResource(R.string.dossier_chooser_title)`.

- [ ] **Step 2: Verify the wiring landed** with `grep -c` for each of: `showDossierSheet`, `dismissDossier`, `DossierSheetContent`, `prepareDossierShare`, `openDossier`, `dossier_chooser_title` (each must be >= 1) and run `./gradlew compileDevDebugKotlin`.

- [ ] **Step 3: Run the regression group:** `./gradlew testDevDebugUnitTest --tests "*Dossier*" --tests "*Bounty*" --tests "*ReadingViewModel*" --tests "*EchoSignal*" --tests "*Quote*" --rerun`; expected 0 failures.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt
git commit -m "feat(bounty-02): open dossier from the investigation sheet"
```

### Task 7: Full verification, Pixel smoke, documentation

- [ ] **Step 1: Write the device instrumentation test** `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/bounty/DossierDeviceTest.kt` using `createAndroidComposeRule<ComponentActivity>()`: render `DossierSheetContent` with a realistic dossier, switch all four tabs, tap an article and Share, assert callbacks; and a `DossierSharer` test writing a real file through the real FileProvider and resolving its URI.

- [ ] **Step 2: Full verification:** `./gradlew testDevDebugUnitTest --rerun`, `./gradlew lintDevDebug --rerun-tasks`, `./gradlew assembleDevDebug assembleDevDebugAndroidTest`; record exact counts (baseline before this task: 1,580 unit tests, lint 0 errors / 2,453 warnings).

- [ ] **Step 3: Device instrumentation** (verify `adb devices` lists `2B051FDH3006MU` and `getprop ro.product.model` is `Pixel 7 Pro`): install debug and test APKs with `-s 2B051FDH3006MU`, then `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.read.bounty.DossierDeviceTest,com.mckimquyen.reader.ui.page.home.read.bounty.BountyInvestigationSheetDeviceTest,com.mckimquyen.reader.ui.page.headline.EchoSignalDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; uninstall the test package afterwards.

- [ ] **Step 4: Smoke on the Pixel.** First back up `databases/Reader`, `Reader-shm`, `Reader-wal` AND `files/datastore/settings.preferences_pb` with `adb exec-out run-as ... cat`, check that each file is non-empty and that SQLite `PRAGMA integrity_check` is `ok`. Open a real article, select a word that appears in several saved headlines, tap Investigate, tap Create dossier, switch the four tabs, tap Share and confirm the chooser appears with a `.md` stream, then close it and open a related article from the Timeline tab. Check `topResumedActivity` before every tap; if an `AdActivity` appears, close it via its labelled close control and continue. Confirm the exported file in `cacheDir/dossiers` has the expected sections. Finally restore BOTH backups (Room and datastore), verify integrity and counts, and remove the cache files.

- [ ] **Step 5: Completion docs.** Write `doc/task/done/BOUNTY-02_executive-intelligence-dossier_DONE.md` stating honest limits (mechanical digest of the local archive; recurring phrases are not NER; no Reddit/Hacker News, no AI, no PDF, no persistence; the spec's "community debate" section was replaced by "different framings across sources" by owner decision; quality depends on how many related articles are saved), update `doc/feature.md` and `doc/task/todo/15_BOUNTY_HUNTER_AGENT_AND_GRAPH.md`, delete `doc/task/todo/BOUNTY-02_executive-intelligence-dossier.md`, and list the real commit hashes.

- [ ] **Step 6: Commit.**

```bash
git add app/src doc docs/superpowers/plans/2026-11-01-bounty-02-local-dossier.md
git commit -m "feat(bounty-02): complete local intelligence dossier"
```

## Final Verification Checklist

- [ ] Builder: verbatim capped quotes, whole-word matching, Jaccard dedupe at 0.8, timeline by zone, phrases need 2 articles, framings need 2 sources, `null` below 2 articles.
- [ ] Real-data probe run and cleaned up; no junk phrases on real archive samples.
- [ ] Markdown escaped and deterministic; the file name cannot escape `cacheDir/dossiers`.
- [ ] 24h retention works; no Room change; database still version 20.
- [ ] 16 `dossier_*` keys in all 38 locales; parity 6/6.
- [ ] Back order dossier -> bounty; dismissing the bounty sheet clears the dossier; article switch clears both.
- [ ] Full unit suite, lint, builds pass; instrumentation passes on `2B051FDH3006MU`.
- [ ] Smoke done with Room AND datastore backed up and restored; ads closed automatically and reported.
