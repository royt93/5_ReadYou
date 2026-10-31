# RPG-01 Skill Tree Constellation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace flat six-row category XP bars on Brain RPG page with a fixed, asymmetric constellation graph and a detail sheet, without changing game rules.

**Architecture:** Add a pure Kotlin layout model for six stable node positions and fixed decorative edges. A Compose `SkillTreeConstellationCard` draws those nodes/edges using Canvas and Material components; tapping a node opens a detail dialog with existing category XP/decay/recovery state. `BrainRpgPage` remains owner of current recovery progress and callback.

**Tech Stack:** Kotlin/JVM, Jetpack Compose Canvas + Material 3 bottom sheet, JUnit4, Android Compose instrumentation tests. No new dependencies or Room schema.

## Global Constraints

- Branch `dev` only; no worktrees.
- Do not alter XP, decay, streak, quiz, rewarded-ad, or recovery business logic.
- Preserve six existing `QuizGeneratorService.CATEGORY_*` identifiers and localization IDs.
- Node positions remain fixed regardless of XP. XP only affects clamped size/brightness.
- Decay: dim node, show warning marker, show retained percentage; detail sheet uses existing recover callback only if `baseXp > effectiveXp`.
- Existing recovery one-ad-at-a-time behavior and `recoveringCategoryId` must remain intact.
- No schema/database/network/API/dependency changes.
- Verify devices before device operations; only use owner-authorized current target `R5CX613VZBR` (Samsung Galaxy S24 Ultra) with `adb -s`; check foreground before every UI action; obey R4 and restore Room after any fixture use.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/SkillTreeLayout.kt`: pure data model, six stable IDs, fixed normalized positions, decorative fixed edge pairs, XP-derived bounded node diameter and retained-percent helper.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt`: replace `KnowledgeSkillTreeCard` rendering with `SkillTreeConstellationCard`; keep current `recoveringCategoryId` state and `viewModel.recoverCategory(activity, categoryId)` callback.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/SkillTreeConstellationCard.kt`: draw connector lines and render nodes in `BoxWithConstraints`; open a localized `AlertDialog` for the selected category (Material3 dependency is version 1.0.1 and does not expose Material3 `ModalBottomSheet`); display existing recovery action.
- Modify `app/src/androidTest/java/com/mckimquyen/reader/ui/page/rpg/KnowledgeSkillTreeCardWidgetTest.kt`: preserve decay XP/recovery/pending tests against the new constellation card, add fixed node/graph/sheet assertions.
- Create `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/SkillTreeLayoutTest.kt`: stable topology, monotonic size, minimum size, zero-max XP and retained-percent edge cases.
- Add a small set of RPG tree detail/accessibility strings to all 38 maintained `strings.xml` files; update `StringResourceParityTest` is not needed if parity is automatic.

## Interfaces

Task 1 produces:

```kotlin
data class SkillTreeNodeSpec(val categoryId: String, val x: Float, val y: Float)
data class SkillTreeEdge(val fromCategoryId: String, val toCategoryId: String)
data class SkillTreeNodeVisual(val diameterDp: Float, val emphasis: Float, val retainedPercent: Int, val isDecayed: Boolean)
object SkillTreeLayout {
    val nodes: List<SkillTreeNodeSpec>
    val edges: List<SkillTreeEdge>
    fun visual(effectiveXp: Long, baseXp: Long, maxEffectiveXp: Long): SkillTreeNodeVisual
}
```

Task 2 produces:

```kotlin
@Composable
internal fun SkillTreeConstellationCard(
    progress: UserProgress,
    recoveringCategoryId: String? = null,
    onRecover: ((String) -> Unit)? = null,
)
```

The existing `BrainRpgPage` supplies these values; no ViewModel/repository API changes.

---

### Task 1: Pure fixed constellation layout and XP visual mapping

**Files:** Create `SkillTreeLayout.kt`, `SkillTreeLayoutTest.kt`.

- [ ] **Step 1: Write failing layout tests**

```kotlin
@Test fun nodesContainExactlyTheSixExistingCategoriesInFixedPositions() {
    assertEquals(
        setOf(TECH, BUSINESS, SCIENCE, HEALTH, PHILOSOPHY, GENERAL),
        SkillTreeLayout.nodes.map { it.categoryId }.toSet(),
    )
    val expected = SkillTreeLayout.nodes.associate { it.categoryId to (it.x to it.y) }
    assertEquals(expected, SkillTreeLayout.nodes.associate { it.categoryId to (it.x to it.y) })
    assertTrue(SkillTreeLayout.nodes.all { it.x in 0f..1f && it.y in 0f..1f })
    assertEquals(6, SkillTreeLayout.edges.size)
    assertTrue(SkillTreeLayout.edges.all { e -> expected.containsKey(e.fromCategoryId) && expected.containsKey(e.toCategoryId) })
}

@Test fun nodeSizeIsMonotonicBoundedAndZeroXpRemainsVisible() {
    val low = SkillTreeLayout.visual(0L, 0L, 100L)
    val mid = SkillTreeLayout.visual(50L, 50L, 100L)
    val high = SkillTreeLayout.visual(100L, 100L, 100L)
    assertTrue(low.diameterDp >= SkillTreeLayout.MIN_NODE_DIAMETER_DP)
    assertTrue(low.diameterDp <= mid.diameterDp && mid.diameterDp <= high.diameterDp)
    assertTrue(high.diameterDp <= SkillTreeLayout.MAX_NODE_DIAMETER_DP)
}

@Test fun decayVisualUsesEffectiveXpAndClampedRetainedPercent() { ... }
@Test fun maxZeroXpDoesNotDivideByZeroAndUsesMinimumVisual() { ... }
@Test fun baseZeroOrNegativeNeverProducesInvalidPercent() { ... }
```

- [x] **Step 2: Run targeted test and confirm red**

Evidence: missing `SkillTreeLayout` unresolved references before implementation.

Run: `./gradlew testDevDebugUnitTest --tests "*SkillTreeLayoutTest"`
Expected: compile failure for missing `SkillTreeLayout` types.

- [x] **Step 3: Implement minimum layout model**

Use six explicit normalized positions forming a mildly asymmetric 3-column/two-row constellation. Store six fixed nearest-neighbor edges as category ID pairs. `visual()` clamps normalized XP to `[0,1]`, linearly maps to named min/max diameter constants, uses normalized XP for emphasis, sets `isDecayed = baseXp > effectiveXp`, and computes retained percent with integer floor only when `baseXp > 0`, otherwise 100. Do not let XP change node positions.

- [x] **Step 4: Run target tests green**

Evidence: `SkillTreeLayoutTest` 6/6 passed.

- [x] **Step 5: Commit**

Commit: `75b3c642`.


```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/rpg/SkillTreeLayout.kt app/src/test/java/com/mckimquyen/reader/domain/model/rpg/SkillTreeLayoutTest.kt
git commit -m "feat(rpg-01): model fixed constellation skill layout"
```

### Task 2: Constellation card and detail bottom sheet

**Files:** Create `SkillTreeConstellationCard.kt`; modify `KnowledgeSkillTreeCardWidgetTest.kt` and relevant string XML files.

- [ ] **Step 1: Add failing Compose widget tests**

Keep existing test coverage but update host to render `SkillTreeConstellationCard`. Assert all six localized node names and XP labels are present; the graph Canvas has a stable test tag; decay category has localized decay marker/retained percentage; normal category has no decay marker. Click a category node and assert detail sheet title, XP/base/retained information. Assert recovery action only appears for decayed selected node and callback receives exactly that category ID. Assert pending recovery disables action and shows pending copy.

- [x] **Step 2: Run targeted test and confirm the new assertions fail**

Evidence: initial A50 instrumentation run found 4 assertion failures: two stale flat-row recovery expectations, one XP-text count that assumed six rows (actual eight visible XP texts due to total XP and decay details), and detail-sheet semantics hosted in another Compose window.

Run: `./gradlew assembleDevDebugAndroidTest` (compile) then run only `KnowledgeSkillTreeCardWidgetTest` on the locked S24 Ultra:

```bash
adb -s R5CX613VZBR shell am instrument -w -e class com.mckimquyen.reader.ui.page.rpg.KnowledgeSkillTreeCardWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: compilation or new graph/sheet assertions fail before UI implementation. Do not run connected test task.

- [x] **Step 3: Implement fixed constellation drawing and interactive nodes**

Evidence: `SkillTreeConstellationCard.kt` draws 6 fixed nodes, 6 canvas edges, and opens `CategoryDetailDialog` displaying effective/base XP, retention %, and recovery action when decayed.

- [x] **Step 4: Run widget test green**

Evidence: `KnowledgeSkillTreeCardWidgetTest` 6/6 and `SkillTreeConstellationCardTest` 4/4 passed on Samsung A50 `R58MA6WYRPE` (10/10 total).

- [x] **Step 5: Commit**

Commit: `0f36446c`.


```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rpg/SkillTreeConstellationCard.kt app/src/androidTest/java/com/mckimquyen/reader/ui/page/rpg/KnowledgeSkillTreeCardWidgetTest.kt app/src/main/res
 git commit -m "feat(rpg-01): draw constellation skill tree and detail sheet"
```

### Task 3: Replace existing RPG card and verify page integration

**Files:** Modify `BrainRpgPage.kt`; add/extend page integration UI test.

- [ ] **Step 1: Add failing integration test**

Render `BrainRpgPage` or the nearest established page integration harness with 6 category values. Assert the old flat-row card is absent; constellation title and all six nodes are visible; tapping a decayed node opens detail and invokes the existing recovery callback; no XP/streak totals mutate merely from opening/tapping the graph.

- [ ] **Step 2: Run integration test red**

Run only the targeted Brain RPG UI test on `R5CX613VZBR` via `adb -s ... shell am instrument`; confirm old flat card remains / constellation tag absent before page change.

- [ ] **Step 3: Replace only the card call site**

Replace `KnowledgeSkillTreeCard(...)` in `BrainRpgPage` with `SkillTreeConstellationCard(...)`, passing the exact same `progress`, `recoveringCategoryId`, and `onRecover` lambda. Do not edit repository, DAO, worker, reward, streak or recovery logic.

- [ ] **Step 4: Run integration test green and inspect changed files**

Re-run the targeted S24 instrumentation test; verify Room-backed values display and no XP changes occur. Run `git diff --check`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt app/src/androidTest/java/com/mckimquyen/reader/ui/page/rpg
git commit -m "feat(rpg-01): replace flat category bars with constellation tree"
```

### Task 4: Localization and full verification

**Files:** All 38 maintained `strings.xml` resources, Compose tests, `doc/task/todo/RPG-01_xp-skill-tree-system.md`, `doc/feature.md`, Epic 12 index.

- [ ] **Step 1: Add strings for tree title, node accessibility, decay marker, detail title/XP/base/retained, recovery action/pending/description**

Add one identical key set to all 38 locale string files; no qualifier/resourceConfigurations changes. Keep locale XML valid and key parity exact.

- [ ] **Step 2: Run parity and resource merge**

Run: `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun processDevDebugResources`
Expected: parity 6/6 and resource merge pass.

- [ ] **Step 3: Run full unit suite, lint and build**

Run `./gradlew testDevDebugUnitTest --rerun`, `./gradlew lintDevDebug`, `./gradlew assembleDevDebug assembleDevDebugAndroidTest`; record exact counts/warnings.

- [ ] **Step 4: Device smoke on locked S24 Ultra**

First run `adb devices`; use only `R5CX613VZBR`. Back up Room (if DB exists) before testing; inspect `topResumedActivity` before each touch; stop and wait for owner if full-screen `AdActivity` appears. Open Brain RPG, verify six nodes/layout, tap an active and decayed node, verify sheet values and recovery button gating, close; verify no XP change. Restore DB backup if any test data/state changed; verify Room integrity and no test data remains. Never target A11 or other attached devices unless owner changes lock.

- [ ] **Step 5: Completion docs**

Update RPG-01 report with exact scope: visual constellation only; six existing categories; no unlock/prerequisite behavior. Record any on-device/UI smoke limitation. Move RPG-01 to done only if criteria and smoke requirements are met; otherwise leave it in todo and record the blocker accurately.

- [ ] **Step 6: Commit**

```bash
git add app/src doc/feature.md doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md doc/task/done
 git commit -m "feat(rpg-01): complete visual skill tree constellation"
```

## Final verification checklist

- [ ] Pure layout tests cover stable six-node graph, edge references, XP bounds, decay percent and zero XP.
- [ ] Compose/widget/integration tests cover graph, sheet, recovery callback and pending recovery.
- [ ] Existing RPG repository/reward/streak behavior has no code changes and tests remain green.
- [ ] All maintained locale XMLs have matching key sets.
- [ ] Full unit suite, lint, app build and androidTest compilation pass.
- [ ] Pixel/S24 smoke performed only on currently authorized serial `R5CX613VZBR`; Room restored if needed; R4 followed.
- [ ] No new dependency, Room schema, API call, ad/IAP logic, or progression rule.
