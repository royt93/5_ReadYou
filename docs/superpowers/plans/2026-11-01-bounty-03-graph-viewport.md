# BOUNTY-03 Second Brain Graph Viewport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the existing Second Brain screen with pinch-zoom, drag-pan, double-tap reset, neighbour highlight on selection, zoom-aware non-overlapping node labels, and a vertical graph/slider/list layout.

**Architecture:** Three pure-Kotlin units (`GraphViewport`, `GraphHighlight`, `GraphLabels`) hold all new logic and are JVM-tested. `SecondBrainPage` composes them: the canvas draws through the viewport, hit-testing converts screen taps back to unit space, and the viewport lives in Compose state (not the ViewModel). Data (`BrainGraphBuilder`, `SecondBrainSv`), the sunflower layout and Room are untouched.

**Tech Stack:** Kotlin, Jetpack Compose (ui 1.7.8 resolved, so `rememberTextMeasurer` and `DrawScope.drawText` exist), Material 3 1.0.1, JUnit4, Robolectric Compose tests, Android Compose instrumentation. No new dependency.

## Global Constraints

- Branch `dev` only; no worktrees. Never mention, suggest or remind about pull requests.
- Pixel 7 Pro serial `2B051FDH3006MU` is the only device (`adb -s 2B051FDH3006MU ...`). Never use `connectedDevDebugAndroidTest`.
- Smoke MUST back up BOTH `databases/Reader*` AND `files/datastore/settings.preferences_pb` (non-empty, `PRAGMA integrity_check` ok) and restore BOTH, verifying with `cmp`. Never delete the datastore. Write files back with `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local` (the inner single quotes matter).
- Standing owner rule: if a full-screen `com.google.android.gms.ads.AdActivity` appears, close it via its labelled control ("Tiếp tục đến ứng dụng"), re-check `topResumedActivity`, never tap ad creative, and report that ads appeared.
- Constants (exact): `MIN_SCALE = 1f`, `MAX_SCALE = 6f`, `GRAPH_WEIGHT = 0.55f`, `LIST_WEIGHT = 0.45f`, `MAX_BASE_LABELS = 8`, `MAX_ZOOMED_LABELS = 24`, `HIT_RADIUS_DP = 24`.
- Do not change `BrainGraphBuilder`, `SecondBrainSv`, `BrainGraphLayout.compute`, `SecondBrainStateReducer`, the route, Room (DB stays v20) or add dependencies.
- Existing `SecondBrainPageWidgetTest` content descriptions must keep working: the graph keeps `second_brain_graph_desc` as its `contentDescription`.
- Exactly 4 new strings in all 38 `values*/strings.xml`: `second_brain_select_hint`, `second_brain_reset_view`, `second_brain_zoom_hint`, `second_brain_zoom_level` (`Zoom %1$d%%`). English and Vietnamese hand-written, the rest English fallback. Added in the task that first uses them.
- Compose pitfalls: `by` needs `androidx.compose.runtime.getValue` (and `setValue` for `var`); `stringResource` is `@Composable`, hoist it out of lambdas; one `setContent` per Compose test; use `useUnmergedTree = true` for children of merged nodes; new composable parameters go last with defaults; read the real failing assertion from `app/build/test-results/testDevDebugUnitTest/*.xml` before guessing.
- Separate `pointerInput` blocks for transform and tap so one does not cancel the other.
- Honest limits: concept nodes from the existing detector and saved tags, not real entities; layout is the fixed sunflower spiral so closeness is not relatedness.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/GraphViewport.kt`: immutable zoom/pan value and coordinate transforms.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/GraphHighlight.kt`: neighbour set and active-edge test.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/GraphLabels.kt`: label selection with budget, clamping and overlap rejection.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainPage.kt`: vertical layout, gestures, highlight and label drawing, reset control.
- Modify the 38 `app/src/main/res/values*/strings.xml`.
- Tests: `GraphViewportTest`, `GraphHighlightTest`, `GraphLabelsTest` (JVM), `SecondBrainViewportUiTest` (Robolectric Compose), extend `SecondBrainPageWidgetTest` (instrumentation).
- Docs: `doc/task/done/BOUNTY-03_...DONE.md`, `doc/feature.md`, `doc/task/todo/15_BOUNTY_HUNTER_AGENT_AND_GRAPH.md`, delete `doc/task/todo/BOUNTY-03_interactive-knowledge-graph.md`.

## Interfaces

Task 1 produces (package `com.mckimquyen.reader.ui.page.secondbrain`):

```kotlin
data class GraphViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isIdentity: Boolean
    fun withSize(width: Float, height: Float): GraphViewport
    fun zoomBy(factor: Float, focusX: Float, focusY: Float): GraphViewport
    fun panBy(dx: Float, dy: Float): GraphViewport
    fun toScreen(p: NodePos): Offset
    fun toUnit(x: Float, y: Float): NodePos
    fun reset(): GraphViewport
    companion object { const val MIN_SCALE = 1f; const val MAX_SCALE = 6f }
}
```

Task 2 produces:

```kotlin
object GraphHighlight {
    fun neighbours(selectedId: String?, edges: List<BrainEdge>): Set<String>
    fun isActiveEdge(selectedId: String?, edge: BrainEdge): Boolean
}
```

Task 3 produces:

```kotlin
data class LabelBox(val nodeId: String, val text: String, val left: Float, val top: Float, val width: Float, val height: Float)

object GraphLabels {
    const val MAX_BASE_LABELS = 8
    const val MAX_ZOOMED_LABELS = 24
    fun budget(scale: Float): Int
    fun select(
        nodes: List<BrainNode>,
        screenPositions: Map<String, Offset>,
        selectedId: String?,
        neighbourIds: Set<String>,
        scale: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        measure: (String) -> Pair<Float, Float>,
        gapFor: (String) -> Float = { DEFAULT_GAP },
    ): List<LabelBox>
}
```

`gapFor(nodeId)` is the clearance from the node centre to its label (the canvas passes the drawn node radius plus padding), added after the first device smoke showed labels drawn on top of node circles.

Task 5 produces tags `SECOND_BRAIN_GRAPH_TAG = "second_brain_graph"`, `SECOND_BRAIN_RESET_TAG = "second_brain_reset"`, `SECOND_BRAIN_LIST_TAG = "second_brain_list"`, `SECOND_BRAIN_HINT_TAG = "second_brain_hint"`.

---

### Task 1: `GraphViewport` (pure)

**Files:** Create `GraphViewport.kt`, `GraphViewportTest.kt` (`app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/`).

- [ ] **Step 1: Write failing tests**

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphViewportTest {
    private val base = GraphViewport().withSize(1000f, 800f)

    @Test
    fun identity_atStart_andResetReturnsIt() {
        assertTrue(base.isIdentity)
        assertTrue(base.zoomBy(3f, 500f, 400f).reset().isIdentity)
    }

    @Test
    fun zoomBy_clampsScaleToRange() {
        assertEquals(GraphViewport.MAX_SCALE, base.zoomBy(100f, 500f, 400f).scale, 0.0001f)
        assertEquals(GraphViewport.MIN_SCALE, base.zoomBy(0.01f, 500f, 400f).scale, 0.0001f)
    }

    @Test
    fun zoomBy_keepsContentUnderTheFocusPointFixed() {
        val focusX = 300f
        val focusY = 200f
        val before = base.toUnit(focusX, focusY)
        val zoomed = base.zoomBy(2.5f, focusX, focusY)
        val after = zoomed.toUnit(focusX, focusY)
        assertEquals(before.x, after.x, 0.0001f)
        assertEquals(before.y, after.y, 0.0001f)
    }

    @Test
    fun panBy_isIgnoredAtScaleOne() {
        val panned = base.panBy(120f, -80f)
        assertEquals(0f, panned.offsetX, 0.0001f)
        assertEquals(0f, panned.offsetY, 0.0001f)
    }

    @Test
    fun panBy_isClampedSoTheUnitSquareCannotLeaveTheCanvas() {
        val zoomed = base.zoomBy(2f, 500f, 400f)
        val farRight = zoomed.panBy(100_000f, 100_000f)
        val farLeft = zoomed.panBy(-100_000f, -100_000f)
        // At scale 2 the content is 2000x1600: offset may range from -(content - canvas) to 0.
        assertEquals(0f, farRight.offsetX, 0.0001f)
        assertEquals(0f, farRight.offsetY, 0.0001f)
        assertEquals(-1000f, farLeft.offsetX, 0.0001f)
        assertEquals(-800f, farLeft.offsetY, 0.0001f)
    }

    @Test
    fun toScreen_andToUnit_areInverses() {
        val v = base.zoomBy(3f, 700f, 100f).panBy(-150f, 40f)
        val p = NodePos(0.31f, 0.77f)
        val screen = v.toScreen(p)
        val back = v.toUnit(screen.x, screen.y)
        assertEquals(p.x, back.x, 0.0001f)
        assertEquals(p.y, back.y, 0.0001f)
    }

    @Test
    fun zoomOut_toMinScale_recentresOffsetToZero() {
        val zoomed = base.zoomBy(4f, 100f, 100f).panBy(-500f, -500f)
        val out = zoomed.zoomBy(0.001f, 500f, 400f)
        assertEquals(1f, out.scale, 0.0001f)
        assertEquals(0f, out.offsetX, 0.0001f)
        assertEquals(0f, out.offsetY, 0.0001f)
    }

    @Test
    fun withSize_reclampsExistingOffset() {
        val zoomed = base.zoomBy(2f, 500f, 400f).panBy(-1000f, -800f)
        val smaller = zoomed.withSize(500f, 400f)
        assertTrue(smaller.offsetX >= -500f)
        assertTrue(smaller.offsetY >= -400f)
    }

    @Test
    fun zeroSizedViewport_neverProducesNaN() {
        val v = GraphViewport().zoomBy(2f, 0f, 0f).panBy(10f, 10f)
        assertFalse(v.offsetX.isNaN() || v.offsetY.isNaN() || v.scale.isNaN())
        val p = v.toUnit(5f, 5f)
        assertFalse(p.x.isNaN() || p.y.isNaN())
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*GraphViewportTest"`. Expected: unresolved `GraphViewport`.

- [ ] **Step 3: Implement**

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.ui.geometry.Offset

/**
 * Zoom/pan state for the Second Brain canvas. Pure value type so coordinate maths is unit-testable.
 * Screen = unit * (size * scale) + offset; offset is clamped so the unit square always covers the canvas.
 */
data class GraphViewport(
    val scale: Float = MIN_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isIdentity: Boolean get() = scale == MIN_SCALE && offsetX == 0f && offsetY == 0f

    fun withSize(width: Float, height: Float): GraphViewport =
        copy(width = width, height = height).clamped()

    /** Zooms by [factor] keeping the content under ([focusX], [focusY]) where it is on screen. */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float): GraphViewport {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == scale) return this
        val ratio = newScale / scale
        return copy(
            scale = newScale,
            offsetX = focusX - (focusX - offsetX) * ratio,
            offsetY = focusY - (focusY - offsetY) * ratio,
        ).clamped()
    }

    fun panBy(dx: Float, dy: Float): GraphViewport =
        copy(offsetX = offsetX + dx, offsetY = offsetY + dy).clamped()

    fun toScreen(p: NodePos): Offset = Offset(p.x * width * scale + offsetX, p.y * height * scale + offsetY)

    fun toUnit(x: Float, y: Float): NodePos {
        val w = width * scale
        val h = height * scale
        return NodePos(
            x = if (w == 0f) 0f else (x - offsetX) / w,
            y = if (h == 0f) 0f else (y - offsetY) / h,
        )
    }

    fun reset(): GraphViewport = copy(scale = MIN_SCALE, offsetX = 0f, offsetY = 0f)

    private fun clamped(): GraphViewport {
        val minX = -(width * scale - width).coerceAtLeast(0f)
        val minY = -(height * scale - height).coerceAtLeast(0f)
        return copy(offsetX = offsetX.coerceIn(minX, 0f), offsetY = offsetY.coerceIn(minY, 0f))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
    }
}
```

- [ ] **Step 4: Run green.** Same command with `--rerun`; expected 9/9.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/GraphViewport.kt app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/GraphViewportTest.kt
git commit -m "feat(bounty-03): add pure zoom and pan viewport for the graph"
```

### Task 2: `GraphHighlight` (pure)

**Files:** Create `GraphHighlight.kt`, `GraphHighlightTest.kt`.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphHighlightTest {
    private val edges = listOf(
        BrainEdge("ai", "chips", 2),
        BrainEdge("ai", "energy", 1),
        BrainEdge("chips", "energy", 1),
        BrainEdge("solo", "solo", 1),
    )

    @Test
    fun neighbours_returnsDirectNeighboursInEitherDirection() {
        assertEquals(setOf("chips", "energy"), GraphHighlight.neighbours("ai", edges))
        assertEquals(setOf("ai", "energy"), GraphHighlight.neighbours("chips", edges))
    }

    @Test
    fun neighbours_isEmptyWithoutSelection_orForUnknownNode() {
        assertTrue(GraphHighlight.neighbours(null, edges).isEmpty())
        assertTrue(GraphHighlight.neighbours("ghost", edges).isEmpty())
    }

    @Test
    fun neighbours_ignoresSelfLoops_andNeverIncludesTheNodeItself() {
        assertTrue(GraphHighlight.neighbours("solo", edges).isEmpty())
        assertFalse(GraphHighlight.neighbours("ai", edges).contains("ai"))
    }

    @Test
    fun isActiveEdge_isTrueOnlyForEdgesTouchingTheSelection() {
        assertTrue(GraphHighlight.isActiveEdge("ai", edges[0]))
        assertTrue(GraphHighlight.isActiveEdge("energy", edges[1]))
        assertFalse(GraphHighlight.isActiveEdge("ai", edges[2]))
        assertFalse(GraphHighlight.isActiveEdge(null, edges[0]))
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*GraphHighlightTest"`.

- [ ] **Step 3: Implement**

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge

/** Which nodes and edges stay bright when a node is selected; everything else is dimmed by the canvas. */
object GraphHighlight {
    fun neighbours(selectedId: String?, edges: List<BrainEdge>): Set<String> {
        if (selectedId == null) return emptySet()
        val result = linkedSetOf<String>()
        edges.forEach { edge ->
            when (selectedId) {
                edge.from -> if (edge.to != selectedId) result += edge.to
                edge.to -> if (edge.from != selectedId) result += edge.from
            }
        }
        return result
    }

    fun isActiveEdge(selectedId: String?, edge: BrainEdge): Boolean =
        selectedId != null && (edge.from == selectedId || edge.to == selectedId)
}
```

- [ ] **Step 4: Run green** (`--rerun`, expected 4/4).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/GraphHighlight.kt app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/GraphHighlightTest.kt
git commit -m "feat(bounty-03): add neighbour highlight logic for the graph"
```

### Task 3: `GraphLabels` (pure)

**Files:** Create `GraphLabels.kt`, `GraphLabelsTest.kt`.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.ui.geometry.Offset
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphLabelsTest {
    private fun node(id: String, weight: Int) = BrainNode(id, id.uppercase(), weight, emptyList(), 0L, 0L)
    private val measure: (String) -> Pair<Float, Float> = { text -> text.length * 10f to 12f }

    private fun select(
        nodes: List<BrainNode>,
        positions: Map<String, Offset>,
        selected: String? = null,
        neighbours: Set<String> = emptySet(),
        scale: Float = 1f,
        width: Float = 1000f,
        height: Float = 800f,
    ) = GraphLabels.select(nodes, positions, selected, neighbours, scale, width, height, measure)

    private fun spread(n: Int): Pair<List<BrainNode>, Map<String, Offset>> {
        val nodes = (1..n).map { node("n$it", 100 - it) }
        val positions = nodes.mapIndexed { i, nd -> nd.id to Offset(40f + (i % 10) * 90f, 40f + (i / 10) * 60f) }.toMap()
        return nodes to positions
    }

    @Test
    fun budget_growsLinearlyFromBaseToZoomedMax() {
        assertEquals(GraphLabels.MAX_BASE_LABELS, GraphLabels.budget(GraphViewport.MIN_SCALE))
        assertEquals(GraphLabels.MAX_ZOOMED_LABELS, GraphLabels.budget(GraphViewport.MAX_SCALE))
        assertTrue(GraphLabels.budget(3f) in GraphLabels.MAX_BASE_LABELS..GraphLabels.MAX_ZOOMED_LABELS)
    }

    @Test
    fun atScaleOne_noMoreThanTheBaseBudgetOfOrdinaryLabels() {
        val (nodes, positions) = spread(40)
        val labels = select(nodes, positions)
        assertEquals(GraphLabels.MAX_BASE_LABELS, labels.size)
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n6", "n7", "n8"), labels.map { it.nodeId })
    }

    @Test
    fun zoomingIn_showsMoreLabels() {
        val (nodes, positions) = spread(60)
        val few = select(nodes, positions, scale = 1f).size
        val many = select(nodes, positions, scale = GraphViewport.MAX_SCALE).size
        assertTrue(many > few)
        assertTrue(many <= GraphLabels.MAX_ZOOMED_LABELS)
    }

    @Test
    fun selectedAndNeighbours_areAlwaysPlacedFirst_andDoNotConsumeTheBudget() {
        val (nodes, positions) = spread(40)
        val labels = select(nodes, positions, selected = "n40", neighbours = setOf("n39", "n38"))
        val ids = labels.map { it.nodeId }
        // weights are 100 - index, so n38 (62) is heavier than n39 (61) and is placed before it
        assertEquals(listOf("n40", "n38", "n39"), ids.take(3))
        assertEquals(3 + GraphLabels.MAX_BASE_LABELS, labels.size)
    }

    @Test
    fun overlappingLabel_isDropped_butTheSelectedNodeIsKept() {
        val nodes = listOf(node("aa", 5), node("bb", 9))
        val positions = mapOf("aa" to Offset(500f, 400f), "bb" to Offset(505f, 402f))
        val labels = select(nodes, positions, selected = "aa")
        assertEquals(listOf("aa"), labels.map { it.nodeId })
    }

    @Test
    fun noTwoLabelsOverlap() {
        val (nodes, positions) = spread(60)
        val labels = select(nodes, positions, scale = GraphViewport.MAX_SCALE)
        for (i in labels.indices) for (j in i + 1 until labels.size) {
            val a = labels[i]
            val b = labels[j]
            val overlap = a.left < b.left + b.width && b.left < a.left + a.width &&
                a.top < b.top + b.height && b.top < a.top + a.height
            assertFalse("${a.nodeId} overlaps ${b.nodeId}", overlap)
        }
    }

    @Test
    fun nodeOutsideTheCanvas_getsNoLabel() {
        val nodes = listOf(node("in", 1), node("out", 9))
        val positions = mapOf("in" to Offset(100f, 100f), "out" to Offset(-50f, 100f))
        assertEquals(listOf("in"), select(nodes, positions).map { it.nodeId })
    }

    @Test
    fun label_isClampedInsideTheCanvas() {
        val nodes = listOf(node("edgenode", 1))
        val positions = mapOf("edgenode" to Offset(995f, 5f))
        val box = select(nodes, positions).single()
        assertTrue(box.left >= 0f && box.left + box.width <= 1000f)
        assertTrue(box.top >= 0f && box.top + box.height <= 800f)
    }

    @Test
    fun selection_isDeterministic_forEqualWeights() {
        val nodes = listOf(node("b", 1), node("a", 1), node("c", 1))
        val positions = mapOf("a" to Offset(100f, 100f), "b" to Offset(300f, 100f), "c" to Offset(500f, 100f))
        assertEquals(select(nodes, positions).map { it.nodeId }, select(nodes.reversed(), positions).map { it.nodeId })
        assertEquals(listOf("a", "b", "c"), select(nodes, positions).map { it.nodeId })
    }

    @Test
    fun unknownPositionOrEmptyInput_isSafe() {
        assertTrue(select(emptyList(), emptyMap()).isEmpty())
        assertTrue(select(listOf(node("x", 1)), emptyMap()).isEmpty())
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*GraphLabelsTest"`.

- [ ] **Step 3: Implement**

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.ui.geometry.Offset
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode

data class LabelBox(
    val nodeId: String,
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Chooses which node labels to draw. Selected node first, then its neighbours (neither counts against the
 * budget), then the heaviest remaining nodes up to a zoom-dependent budget. A label that would overlap an
 * already placed one is skipped, so text never piles up.
 */
object GraphLabels {
    const val MAX_BASE_LABELS = 8
    const val MAX_ZOOMED_LABELS = 24
    private const val GAP = 6f

    fun budget(scale: Float): Int {
        val span = GraphViewport.MAX_SCALE - GraphViewport.MIN_SCALE
        val t = ((scale - GraphViewport.MIN_SCALE) / span).coerceIn(0f, 1f)
        return (MAX_BASE_LABELS + (MAX_ZOOMED_LABELS - MAX_BASE_LABELS) * t).toInt()
    }

    fun select(
        nodes: List<BrainNode>,
        screenPositions: Map<String, Offset>,
        selectedId: String?,
        neighbourIds: Set<String>,
        scale: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        measure: (String) -> Pair<Float, Float>,
    ): List<LabelBox> {
        val byId = nodes.associateBy { it.id }
        val ordinary = nodes
            .filter { it.id != selectedId && it.id !in neighbourIds }
            .sortedWith(compareByDescending<BrainNode> { it.weight }.thenBy { it.id })
        val priority = buildList {
            selectedId?.let { byId[it] }?.let(::add)
            neighbourIds.mapNotNull { byId[it] }
                .sortedWith(compareByDescending<BrainNode> { it.weight }.thenBy { it.id })
                .forEach(::add)
        }

        val placed = mutableListOf<LabelBox>()
        var ordinaryPlaced = 0
        val limit = budget(scale)

        fun tryPlace(node: BrainNode): Boolean {
            val center = screenPositions[node.id] ?: return false
            if (center.x < 0f || center.y < 0f || center.x > canvasWidth || center.y > canvasHeight) return false
            val (w, h) = measure(node.label)
            val left = (center.x - w / 2f).coerceIn(0f, (canvasWidth - w).coerceAtLeast(0f))
            val above = center.y - GAP - h
            val top = (if (above >= 0f) above else center.y + GAP).coerceIn(0f, (canvasHeight - h).coerceAtLeast(0f))
            val box = LabelBox(node.id, node.label, left, top, w, h)
            if (placed.any { it.overlaps(box) }) return false
            placed += box
            return true
        }

        priority.forEach { tryPlace(it) }
        for (node in ordinary) {
            if (ordinaryPlaced >= limit) break
            if (tryPlace(node)) ordinaryPlaced++
        }
        return placed
    }

    private fun LabelBox.overlaps(o: LabelBox): Boolean =
        left < o.left + o.width && o.left < left + width && top < o.top + o.height && o.top < top + height
}
```

- [ ] **Step 4: Run green** (`--rerun`, expected 10/10). If a test fails, read the real assertion from the XML before changing code; adjust the fixture only when the test itself, not the rule, is wrong.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/GraphLabels.kt app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/GraphLabelsTest.kt
git commit -m "feat(bounty-03): choose non-overlapping node labels by zoom"
```

### Task 4: Strings in 38 locales

**Files:** Modify all `app/src/main/res/values*/strings.xml`.

- [ ] **Step 1: Add the 4 keys** with a script (escape `&` and apostrophes, insert before `</resources>`, skip files that already contain the key). English values: `second_brain_select_hint` = "Tap a topic to see the articles that mention it", `second_brain_reset_view` = "Reset view", `second_brain_zoom_hint` = "Pinch to zoom, drag to pan, double-tap to reset", `second_brain_zoom_level` = "Zoom %1$d%%". Vietnamese (`values-vi`): "Chạm vào một chủ đề để xem các bài nhắc đến nó", "Đặt lại khung nhìn", "Chụm để phóng to, kéo để di chuyển, chạm đúp để đặt lại", "Phóng to %1$d%%". All other locales get the English text.

- [ ] **Step 2: Verify.** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun`; expected 6/6, then `grep -c second_brain_zoom_level app/src/main/res/values*/strings.xml | grep -v ":1"` must print nothing.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/res
git commit -m "feat(bounty-03): add graph zoom strings across 38 locales"
```

### Task 5: `SecondBrainPage` layout, gestures, highlight and labels

**Files:** Modify `SecondBrainPage.kt`; create `app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainViewportUiTest.kt`.

**Interfaces:** consumes Tasks 1-4. Public composable signature stays `SecondBrainPage(state, onSelectNode, onRangeChange, onOpenArticle, onRetry, modifier)`; no parameter is added, so callers and existing tests compile.

- [ ] **Step 1: Write failing Robolectric Compose tests** in `SecondBrainViewportUiTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.secondbrain.BrainArticleInfo
import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecondBrainViewportUiTest {
    private companion object {
        const val DOUBLE_TAP_WAIT_MILLIS = 500L
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val graph = BrainGraph(
        nodes = listOf(
            BrainNode("ai", "Ai", 2, listOf("a1", "a2"), 1_000L, 2_000L),
            BrainNode("chips", "Chips", 1, listOf("a2"), 2_000L, 2_000L),
        ),
        edges = listOf(BrainEdge("ai", "chips", 1)),
        minTime = 1_000L,
        maxTime = 2_000L,
        articleInfo = mapOf(
            "a1" to BrainArticleInfo("a1", "First AI story", 1_000L),
            "a2" to BrainArticleInfo("a2", "Chip fab story", 2_000L),
        ),
    )

    private fun show(selected: String? = null, onSelect: (String?) -> Unit = {}) = compose.setContent {
        SecondBrainPage(
            state = SecondBrainUiState.Ready(graph, selected, 1_000L, 2_000L),
            onSelectNode = onSelect,
            onRangeChange = { _, _ -> },
            onOpenArticle = {},
            onRetry = {},
        )
    }

    private fun zoomIs(percent: Int) = SemanticsMatcher("zoom is $percent%") {
        it.config.getOrNull(SemanticsProperties.StateDescription) == str(R.string.second_brain_zoom_level, percent)
    }

    @Test
    fun withoutSelection_showsHintAndHidesArticles() {
        show()
        compose.onNodeWithTag(SECOND_BRAIN_LIST_TAG).assertExists()
        compose.onNodeWithTag(SECOND_BRAIN_HINT_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("First AI story").assertCountEquals(0)
    }

    @Test
    fun withSelection_hidesHintAndListsNewestFirst() {
        show(selected = "ai")
        compose.onAllNodesWithTag(SECOND_BRAIN_HINT_TAG).assertCountEquals(0)
        compose.onNodeWithText("Chip fab story").assertIsDisplayed()
        compose.onNodeWithText("First AI story").assertIsDisplayed()
    }

    @Test
    fun startsAtHundredPercent_withResetDisabled_andShowsTheGestureCaption() {
        show()
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).assert(zoomIs(100))
        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.second_brain_zoom_hint)).assertIsDisplayed()
    }

    @Test
    fun pinchOut_zoomsIn_andResetRestoresIdentity() {
        show()
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f), end0 = center + Offset(-200f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(200f, 0f),
            )
        }
        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsEnabled()

        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).performClick()
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).assert(zoomIs(100))
        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsNotEnabled()
    }

    @Test
    fun doubleTap_afterZoom_resetsTheViewport() {
        show()
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f), end0 = center + Offset(-200f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(200f, 0f),
            )
        }
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput { doubleClick(Offset(2f, 2f)) }
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).assert(zoomIs(100))
    }

    @Test
    fun tappingTheHeaviestNodeCentre_selectsIt() {
        var selected: String? = null
        show(onSelect = { selected = it })
        val centre = BrainGraphLayout.compute(graph).getValue("ai")
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput { click(Offset(centre.x * width, centre.y * height)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS) // onTap waits out the double-tap window
        compose.runOnIdle { assertEquals("ai", selected) }
    }

    @Test
    fun tappingAnEmptyCorner_reportsNoSelection() {
        var selected: String? = "sentinel"
        show(selected = "ai", onSelect = { selected = it })
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput { click(Offset(2f, 2f)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS) // onTap waits out the double-tap window
        compose.runOnIdle { assertNull(selected) }
    }
}
```

The graph node keeps `second_brain_graph_desc` as `contentDescription`, carries `testTag(SECOND_BRAIN_GRAPH_TAG)`, and exposes the zoom level through `stateDescription`; the semantics matcher above reads it directly, so these tests do not depend on pixel output.

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*SecondBrainViewportUiTest"`; expected failures on the missing tags.

- [ ] **Step 3: Implement.** Edit `SecondBrainPage.kt` in four places.

(a) Constants and imports. Replace the old `NODE_*`/`HIT_RADIUS` constants block with:

```kotlin
const val SECOND_BRAIN_TITLE_TAG = "second_brain_title"
const val SECOND_BRAIN_GRAPH_TAG = "second_brain_graph"
const val SECOND_BRAIN_RESET_TAG = "second_brain_reset"
const val SECOND_BRAIN_LIST_TAG = "second_brain_list"
const val SECOND_BRAIN_HINT_TAG = "second_brain_hint"
private const val NODE_MIN_RADIUS = 0.012f
private const val NODE_MAX_RADIUS = 0.032f
private const val HIT_RADIUS_DP = 24
private const val GRAPH_WEIGHT = 0.55f
private const val LIST_WEIGHT = 0.45f
private const val LABEL_TEXT_SP = 11
private const val DIMMED_ALPHA = 0.25f
private const val ZOOM_PERCENT = 100
private const val MAX_RADIUS_GROWTH = 2f
```

Add imports: `androidx.compose.runtime.setValue` (already imported), `androidx.compose.foundation.gestures.detectTransformGestures`, `androidx.compose.foundation.layout.Row`, `androidx.compose.material3.TextButton`, `androidx.compose.ui.layout.onSizeChanged`, `androidx.compose.ui.semantics.stateDescription`, `androidx.compose.ui.text.TextStyle`, `androidx.compose.ui.text.drawText`, `androidx.compose.ui.text.rememberTextMeasurer`, `androidx.compose.ui.unit.sp`, `kotlin.math.roundToInt`, `kotlin.math.sqrt`.

(b) `ReadyContent` becomes a weighted column. Keep the existing slider block and the `graph.nodes.isEmpty()` text branch unchanged; replace the rest of the body with:

```kotlin
val graph = state.graph
val description = stringResource(R.string.second_brain_graph_desc, graph.nodes.size, graph.edges.size)
val hint = stringResource(R.string.second_brain_select_hint)
val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
Column(Modifier.fillMaxSize()) {
    if (graph.nodes.isEmpty()) {
        Text(stringResource(R.string.second_brain_empty_window), modifier = Modifier.padding(vertical = 16.dp))
    } else {
        BrainGraphCanvas(
            graph = graph,
            selectedNodeId = state.selectedNodeId,
            onSelectNode = onSelectNode,
            description = description,
            modifier = Modifier.fillMaxWidth().weight(GRAPH_WEIGHT),
        )
    }
    // ... existing range slider block, unchanged ...
    Box(Modifier.fillMaxWidth().weight(LIST_WEIGHT).testTag(SECOND_BRAIN_LIST_TAG)) {
        val selected = graph.nodes.firstOrNull { it.id == state.selectedNodeId }
        if (selected == null) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(SECOND_BRAIN_HINT_TAG))
        } else {
            val articles = selected.articleIds.mapNotNull { graph.articleInfo[it] }.sortedByDescending { it.date }
            Column {
                Text(selected.label, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.second_brain_node_articles, selected.weight))
                Text(stringResource(R.string.second_brain_related), style = MaterialTheme.typography.labelLarge)
                LazyColumn {
                    items(articles, key = { it.id }) { article ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenArticle(article.id) }.padding(vertical = 6.dp)) {
                            Text(article.title, color = MaterialTheme.colorScheme.primary)
                            Text(dateFormat.format(Date(article.date)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
```

(c) Replace `BrainGraphCanvas` with the viewport-aware version. `description` is a new parameter placed before `modifier`; the old call site is the only caller.

```kotlin
@Composable
private fun BrainGraphCanvas(
    graph: BrainGraph,
    selectedNodeId: String?,
    onSelectNode: (String?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val positions = remember(graph) { BrainGraphLayout.compute(graph) }
    val maxWeight = remember(graph) { graph.nodes.maxOfOrNull { it.weight } ?: 1 }
    val neighbours = remember(graph, selectedNodeId) { GraphHighlight.neighbours(selectedNodeId, graph.edges) }
    var viewport by remember(graph) { mutableStateOf(GraphViewport()) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = LABEL_TEXT_SP.sp) }
    val zoomLabel = stringResource(R.string.second_brain_zoom_level, (viewport.scale * ZOOM_PERCENT).roundToInt())
    val resetLabel = stringResource(R.string.second_brain_reset_view)
    val zoomHint = stringResource(R.string.second_brain_zoom_hint)
    val hitRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { HIT_RADIUS_DP.dp.toPx() }
    val hasSelection = selectedNodeId != null

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { viewport = viewport.withSize(it.width.toFloat(), it.height.toFloat()) }
                .semantics { contentDescription = description; stateDescription = zoomLabel }
                .testTag(SECOND_BRAIN_GRAPH_TAG)
                .pointerInput(positions) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport = viewport.zoomBy(zoom, centroid.x, centroid.y).panBy(pan.x, pan.y)
                    }
                }
                .pointerInput(positions) {
                    detectTapGestures(
                        onDoubleTap = { viewport = viewport.reset() },
                        onTap = { tap ->
                            val unit = viewport.toUnit(tap.x, tap.y)
                            val radiusUnit = hitRadiusPx / (minOf(size.width, size.height) * viewport.scale)
                            onSelectNode(BrainGraphLayout.hitTest(positions, unit.x, unit.y, radiusUnit))
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                graph.edges.forEach { edge ->
                    val a = positions[edge.from] ?: return@forEach
                    val b = positions[edge.to] ?: return@forEach
                    val active = GraphHighlight.isActiveEdge(selectedNodeId, edge)
                    val alpha = if (!hasSelection || active) 0.6f else DIMMED_ALPHA * 0.5f
                    drawLine(
                        color = (if (active) highlight else outline).copy(alpha = alpha),
                        start = viewport.toScreen(a),
                        end = viewport.toScreen(b),
                        strokeWidth = 1.5f + edge.weight.coerceAtMost(5) + if (active) 2f else 0f,
                    )
                }
                val growth = sqrt(viewport.scale).coerceAtMost(MAX_RADIUS_GROWTH)
                graph.nodes.forEach { node ->
                    val p = positions[node.id] ?: return@forEach
                    val fraction = node.weight.toFloat() / maxWeight
                    val radius = (NODE_MIN_RADIUS + (NODE_MAX_RADIUS - NODE_MIN_RADIUS) * fraction) * size.minDimension * growth
                    val center = viewport.toScreen(p)
                    val bright = !hasSelection || node.id == selectedNodeId || node.id in neighbours
                    val color = if (node.id == selectedNodeId) highlight else primary
                    drawCircle(color = color.copy(alpha = if (bright) 1f else DIMMED_ALPHA), radius = radius, center = center)
                    if (node.id == selectedNodeId) {
                        drawCircle(color = highlight, radius = radius + 6f, center = center, style = Stroke(width = 3f))
                    }
                }
                val screen = graph.nodes.mapNotNull { n -> positions[n.id]?.let { n.id to viewport.toScreen(it) } }.toMap()
                GraphLabels.select(
                    nodes = graph.nodes,
                    screenPositions = screen,
                    selectedId = selectedNodeId,
                    neighbourIds = neighbours,
                    scale = viewport.scale,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    measure = { text -> textMeasurer.measure(text, labelStyle).size.let { it.width.toFloat() to it.height.toFloat() } },
                ).forEach { box ->
                    drawText(textMeasurer, box.text, Offset(box.left, box.top), style = labelStyle)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(zoomHint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { viewport = viewport.reset() },
                enabled = !viewport.isIdentity,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SECOND_BRAIN_RESET_TAG),
            ) { Text(resetLabel) }
        }
    }
}
```

Also add `androidx.compose.foundation.layout.heightIn` and `androidx.compose.foundation.gestures.detectTapGestures` (already imported) to the imports. `Modifier.weight` is only used on direct children of `Column`/`Row`, so no `ColumnScope` workaround is needed.

(d) `SecondBrainPage` itself is unchanged, except that `ReadyContent` is now a `Column(Modifier.fillMaxSize())`, so call it inside the existing outer `Column` with `Modifier.weight(1f)` wrapped in a `Box`: replace `is SecondBrainUiState.Ready -> ReadyContent(...)` with `is SecondBrainUiState.Ready -> Box(Modifier.weight(1f)) { ReadyContent(state, onSelectNode, onRangeChange, onOpenArticle) }`.

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*SecondBrainViewportUiTest" --tests "*SecondBrain*" --tests "*BrainGraph*" --rerun`; all pass, including `BrainGraphLayoutTest` and `SecondBrainViewModelTest`. Then `./gradlew compileDevDebugKotlin`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainViewportUiTest.kt
git commit -m "feat(bounty-03): zoom, pan and highlight neighbours on the second brain graph"
```

### Task 6: Full verification, Pixel smoke, documentation

- [ ] **Step 1: Extend the instrumentation test.** In `app/src/androidTest/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainPageWidgetTest.kt` add a test that renders a `Ready` state, performs a real pinch (`performTouchInput { pinch(...) }`) and double-tap on the graph node, and asserts the `stateDescription` goes above then back to `Zoom 100%`; keep every existing assertion. `SecondBrainPerformanceTest` must still pass unchanged.

- [ ] **Step 2: Full verification.** `./gradlew testDevDebugUnitTest --rerun`, `./gradlew lintDevDebug --rerun-tasks`, `./gradlew assembleDevDebug assembleDevDebugAndroidTest`. Record exact unit counts from `app/build/test-results/testDevDebugUnitTest/*.xml` (baseline before this plan: 1,640) and read the lint summary from `app/build/reports/lint-results-devDebug.txt` or the XML rather than assuming.

- [ ] **Step 3: Device instrumentation.** Confirm `adb devices -l` lists `2B051FDH3006MU` and `getprop ro.product.model` is `Pixel 7 Pro`. Install the debug and test APKs with `-s 2B051FDH3006MU`, then `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.secondbrain.SecondBrainPageWidgetTest,com.mckimquyen.reader.ui.page.secondbrain.SecondBrainPerformanceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; uninstall `com.mckimquyen.reader.test` afterwards.

- [ ] **Step 4: Smoke on the Pixel.** Back up `databases/Reader`, `Reader-shm`, `Reader-wal` AND `files/datastore/settings.preferences_pb` with `adb exec-out run-as ... cat`, require non-empty `Reader` and datastore, and `PRAGMA integrity_check` = `ok` on a copy. Check `topResumedActivity` before every tap and close any `AdActivity` through its labelled control. Open Settings, then Second Brain; confirm the vertical layout (graph above, slider, hint/list below). `adb shell input` cannot do two-finger pinch, so pinch is covered by the Robolectric and instrumentation tests; on the device verify a one-finger drag, a tap, a double-tap and the reset button (a drag only moves the graph after a zoom, so reach a zoomed state by trying a manual two-finger pinch on the phone if the owner is present, otherwise record that device zoom was not exercised). Tap a node and confirm the neighbours stay bright, the label appears, the list fills with related articles, and tapping an article opens it. Take screenshots and read them. Then restore BOTH backups with the quoted `run-as sh -c 'cat > ...'` form, verify with `cmp` and `integrity_check`, and confirm no test package remains.

- [ ] **Step 5: Completion docs.** Write `doc/task/done/BOUNTY-03_interactive-knowledge-graph_DONE.md` stating honest limits (upgrade of the existing EXC-08 graph rather than a new article/entity graph; concept nodes, not real entities; fixed sunflower layout so closeness is not relatedness; labels are heuristic; pinch verified by instrumentation, not by `adb`; smoke data size), update `doc/feature.md` (add a BOUNTY-03 entry after BOUNTY-02) and the BOUNTY-03 row in `doc/task/todo/15_BOUNTY_HUNTER_AGENT_AND_GRAPH.md`, delete `doc/task/todo/BOUNTY-03_interactive-knowledge-graph.md` with `git rm`, and list the real commit hashes from `git log`.

- [ ] **Step 6: Commit.**

```bash
git add app/src doc docs/superpowers/plans/2026-11-01-bounty-03-graph-viewport.md
git commit -m "feat(bounty-03): complete second brain graph zoom, pan and highlight"
```

## Final Verification Checklist

- [ ] Viewport: scale clamp 1x-6x, focus-stable zoom, pan clamp, no pan at 1x, exact round-trip, reset.
- [ ] Highlight: neighbours both directions, empty/unknown selection, self-loops ignored.
- [ ] Labels: priority order, budget grows with zoom, overlap rejected, canvas clamp, deterministic.
- [ ] Tap hit-test uses `toUnit` and a finger-sized radius in dp.
- [ ] Layout is graph / slider / list with the list always present; existing widget tests green.
- [ ] 4 strings in all 38 locales; parity 6/6.
- [ ] `BrainGraphBuilder`, `SecondBrainSv`, layout compute, Room untouched; DB still v20.
- [ ] Full unit suite, lint, builds pass; instrumentation passes on `2B051FDH3006MU` including the 200-node performance bound.
- [ ] Smoke done with Room AND datastore backed up and restored identically; ads closed automatically and reported.
