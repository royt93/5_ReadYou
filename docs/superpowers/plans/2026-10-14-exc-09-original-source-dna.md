# EXC-09 Original Source DNA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add on-demand, local-only Source DNA graph to eligible story clusters, showing feed-reported chronology and high lexical similarity without asserting verified provenance or copying.

**Architecture:** Add pure analyzer under `domain/model/cluster`, consuming `ArticleWithFeed` and an injected similarity function; it returns immutable nodes and uncertainty/truncation state. Invoke only when user taps Source DNA in `StoryClusterSheet`, reusing `StoryClusteringEngine` similarity and rendering a vertically scrollable graph. No DB, network, AI, or clustering membership change.

**Tech Stack:** Kotlin, JUnit4, Jetpack Compose Material 3, ActivityScenario/AndroidJUnitRunner, existing `StoryClusteringEngine`, Android string resources.

## Global Constraints

- Device target: Tecno serial `115333744A005844`; never use Pixel `2B051FDH3006MU`.
- Never run Gradle `connected*`; invoke instrumented classes using `adb -s 115333744A005844 shell am instrument ...`.
- Stop immediately if any full-screen ad appears; wait for owner’s `done` before further device actions.
- Preserve `.idea/workspace.xml`; never stage or revert it.
- No force unwraps/`late` without documented invariant; no magic business constants; dispose listeners/scopes.
- Add strings to every maintained locale; translate en/vi/zh-rCN/ja/fr/de; English fallback elsewhere; preserve `resourceConfigurations` parity.
- TDD: every behavior change must have a test observed RED before implementation and GREEN after.
- Work inline on `dev`; no worktree and no push.

---

## File Map

- Create `app/src/main/java/com/mckimquyen/reader/domain/model/cluster/SourceDnaAnalyzer.kt`: pure analysis types, named constants, graph builder.
- Create `app/src/test/java/com/mckimquyen/reader/domain/model/cluster/SourceDnaAnalyzerTest.kt`: algorithm edge cases and evaluation-count limit.
- Reuse public `StoryClusteringEngine.calculateSimilarity(a1, a2)`; do not modify engine unless compilation/API inspection proves it inaccessible (it is currently public).
- Modify `app/src/main/java/com/mckimquyen/reader/ui/component/cluster/StoryClusterSheet.kt`: hidden/shown state, click-triggered analysis, source graph UI, disclaimer and uncertainty/error states.
- Modify all maintained `app/src/main/res/values*/strings.xml`: Source DNA copy.
- Extend `app/src/androidTest/java/com/mckimquyen/reader/ui/component/cluster/StoryClusterSheetWidgetTest.kt`: action eligibility, graph, disclaimer, uncertainty, node click, long text.
- Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/flow/SourceDnaFlowIntegrationTest.kt`: Flow cluster → Source DNA → article route; existing lead path unchanged.
- Update `doc/task/todo/EXC-09_original-source-dna.md` and epic index only after verification passes.

## Shared API Contract

```kotlin
enum class SourceDnaRelation { EARLIEST_IN_CLUSTER, HIGH_SIMILARITY, NO_CLEAR_RELATIONSHIP }

data class SourceDnaNode(
    val article: ArticleWithFeed,
    val parentArticleId: String?,
    val relation: SourceDnaRelation,
    val similarityToParent: Float?,
    val order: Int,
)

data class SourceDnaResult(
    val nodes: List<SourceDnaNode>,
    val isEarliestAmbiguous: Boolean,
    val analyzedArticleCount: Int,
    val wasTruncated: Boolean,
    val hasAnySimilarityEdge: Boolean,
)

object SourceDnaAnalyzer {
    const val MAX_ARTICLES = 20
    const val HIGH_SIMILARITY_THRESHOLD = 0.75f
    fun analyze(
        articles: List<ArticleWithFeed>,
        similarity: (ArticleWithFeed, ArticleWithFeed) -> Float,
    ): SourceDnaResult
}
```

Algorithm contract:
- Dedupe nonblank article IDs; deterministic winner is earliest date, then first input occurrence. Sort by `(dateMillis, articleId)` and take 20 oldest; set `wasTruncated` when more than 20 distinct, nonblank IDs existed.
- Earliest label only when the minimum timestamp is unique. Timestamp ties never establish temporal ordering.
- For a candidate, only compare parents whose timestamp is **strictly earlier**. Choose highest valid similarity; ties choose earlier timestamp, then article ID. Add parent edge only at `>= 0.75f`.
- Similarity must be finite and within `[0f, 1f]`; non-finite/out-of-range scores are invalid and ignored (do not clamp invalid values into evidence).
- If every timestamp is equal, no edges are created. If no eligible edge exists, expose `hasAnySimilarityEdge=false` and render explicit uncertainty.
- Blank feed name falls back to URL host if parseable, otherwise localized unknown-source label. Blank IDs are excluded.

---

### Task 1: Pure Analyzer (TDD)

**Files:** create analyzer and unit test above. Existing fixture: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngineTest.kt` has private article/feed builder; create a small local fixture in the new test, using `Article` and `Feed` constructors shown there.

- [ ] **Step 1: Write minimal RED tests** for unique earliest and date/id deterministic ordering; tied earliest; only strict-earlier parent candidates; highest similarity and deterministic tie-break; exact `0.75f` accepted and `0.749f` rejected; NaN/infinity/negative/>1 invalid; duplicate IDs keep earliest; blank IDs excluded; empty/single input; all-equal timestamps return no edges; 20/21 cap and truncation; blank feed name; similarity calls never exceed 190 for 20 unique articles.
- [ ] **Step 2: Run `./gradlew testDevDebugUnitTest --tests "*SourceDnaAnalyzerTest"`; expect compile failure because analyzer is absent.**
- [ ] **Step 3: Implement only types and algorithm above.** No Android/Compose/Context import; deterministic collection ordering; call similarity exactly once per valid strict-earlier pair.
- [ ] **Step 4: Run focused test plus clustering regression:** `./gradlew testDevDebugUnitTest --tests "*SourceDnaAnalyzerTest" --tests "*StoryClusteringEngineTest"`; expect GREEN and ≤190 calls.
- [ ] **Step 5: Add a small elapsed-time measurement test utility or test log** using 1 warmup + 10 runs on 20 ArticleWithFeed instances; report median, but do not assert wall-clock `<200 ms` in JVM test (flaky). Assert ≤190 comparisons. Actual Tecno warm/cold timing gets recorded in Task 5.

### Task 2: Reuse Similarity API (only if needed)

**Files:** currently none expected; `StoryClusteringEngine.calculateSimilarity(ArticleWithFeed, ArticleWithFeed)` is public.

- [ ] **Step 1: Compile analyzer integration using existing public function:** pass `{ a, b -> clusteringEngine.calculateSimilarity(a, b) }`.
- [ ] **Step 2: Run focused analyzer and engine tests.** Do not edit `StoryClusteringEngine` unless visibility/API truly prevents this call; any such change needs its own RED test first.
- [ ] **Step 3: Verify `cluster()` thresholds, membership, and lead selection are unchanged with existing engine test suite.**

### Task 3: Source DNA Cluster Sheet (TDD)

**Files:** `StoryClusterSheet.kt`, all maintained resource `strings.xml`, `StoryClusterSheetWidgetTest.kt`.

**Strings:** `source_dna_action`, `source_dna_title`, `source_dna_disclaimer`, `source_dna_earliest_posted`, `source_dna_high_similarity_to`, `source_dna_no_clear_relationship`, `source_dna_uncertain`, `source_dna_showing_earliest_limit`, `source_dna_unknown_source`, `source_dna_error`, `source_dna_back`.

- [ ] **Step 1: Add widget RED cases** for hidden action when fewer than 3 distinct feed IDs; visible at ≥3; open/return; chronological graph nodes and edge similarity; persistent heuristic disclaimer; tied/all-equal/no-edge uncertainty; invalid/blank source fallback; article node tap delegates correct `ArticleWithFeed`; long title/source wraps without horizontal scroll; analysis exception shows localized non-blocking error and existing perspective list remains available.
- [ ] **Step 2: Run existing ActivityScenario widget test class on Tecno before implementation; expect RED for missing controls/text.** Before every UI action, assert `topResumedActivity` belongs to `com.mckimquyen.reader`; if full-screen ad appears, stop and await `done`.
- [ ] **Step 3: Add localized strings** in all maintained resource directories. Translate six primary locales, English fallback for others; run `StringResourceParityTest` before UI implementation.
- [ ] **Step 4: Add state to `StoryClusterSheet`.** Initial mode remains existing perspective list. Only show action when `cluster.articles.map { it.feed.id }.distinct().size >= 3`. On click, compute once through analyzer and pass immutable result to a dedicated composable. Catch exception at click boundary, keep sheet open/perspectives usable, show localized error. Back action returns to perspectives. Tapping graph node calls `onArticleClick(article)` and closes sheet.
- [ ] **Step 5: Render graph** as vertically scrolling Compose rows with parent connector/indentation, category-neutral theme colors, feed/time/title/similarity labels and persistent disclaimer. Keep width bounded by sheet; no horizontal page scroll. All uncertainty and truncation states visible and accessible.
- [ ] **Step 6: Run cluster widget tests GREEN** using `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.ui.component.cluster.StoryClusterSheetWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner < /dev/null`; never use `connected*`.
- [ ] **Step 7: Run unit/resource parity and `git diff --check`.**

### Task 4: Flow Integration

**Files:** create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/flow/SourceDnaFlowIntegrationTest.kt`; reuse existing Flow/cluster integration fixtures if any.

- [ ] **Step 1: Write RED instrumented integration test:** render/open an actual multi-source cluster through Flow’s existing `StoryClusterSheet`; tap Source DNA; select a non-lead article; assert existing navigation callback routes to that article ID. Assert “read main” still routes to `cluster.leadArticle` and analyzer does not mutate `StoryCluster` or `StoryClusterResult`.
- [ ] **Step 2: Run only this class via explicit Tecno `am instrument`; expected fail because Source DNA UI is absent.**
- [ ] **Step 3: Wire only existing `onArticleClick`/`onDismissRequest`; no `HomeViewModel` or clustering membership changes.**
- [ ] **Step 4: Run integration GREEN, widget tests, and all `StoryClusteringEngineTest` cases.**

### Task 5: Acceptance, Performance, Docs, and Commit

**Files:** update EXC-09 task doc and `05_EXCLUSIVE_KILLER_FEATURES.md` only after all verification.

- [ ] **Step 1: Run `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest lintDevDebug`.** Record output truthfully; do not hide pre-existing lint failures.
- [ ] **Step 2: Run widget and integration classes individually** with `adb -s 115333744A005844 shell am instrument -w -e class <Class> com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner < /dev/null`; never use `connected*`.
- [ ] **Step 3: Measure 20-node analysis on Tecno:** 1 cold call + 1 warmup + 10 warm calls; report median/worst and similarity comparison count. Require ≤190 comparisons and warm median <200 ms. If target fails, stop and revise algorithm before shipping.
- [ ] **Step 4: Smoke on Tecno:** open eligible Flow cluster; verify graph/disclaimer, ambiguity state, node opens correct article; confirm no network call. Stop on any full-screen ad and wait for `done`.
- [ ] **Step 5: Audit code vs approved spec, update task status and completion report/epic index.** Explicitly state UI labels are heuristic and do not establish copying.
- [ ] **Step 6: `git diff --check`, inspect full diff, stage only EXC-09 files (never `.idea/workspace.xml`), commit a focused `feat(exc-09): ...` message with `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Do not push.**
