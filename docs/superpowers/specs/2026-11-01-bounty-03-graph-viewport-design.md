# BOUNTY-03 — Second Brain Graph: Zoom, Pan and Neighbour Highlight Design

- **Date:** 2026-11-01
- **Branch:** `dev`
- **Status:** Owner-approved design; implementation not started.
- **Device lock:** Google Pixel 7 Pro, serial `2B051FDH3006MU` only.
- **Builds on:** EXC-08 Second Brain (`ui/page/secondbrain/`, `domain/model/secondbrain/`, `domain/sv/SecondBrainSv.kt`).

## 1. Goal and Honest Scope

The original BOUNTY-03 idea was a new Obsidian-style graph of articles and entities. EXC-08 already ships a concept graph (nodes = detected concepts and saved AI tags, edges = co-occurrence in an article, time-range slider, tap-to-select, related-articles list). The owner chose to **upgrade that screen** instead of building a second graph.

What the upgrade adds, and nothing else:

1. Pinch-zoom, drag-pan and double-tap-reset on the graph canvas.
2. When a node is selected, its direct neighbours and connecting edges stay bright; everything else dims.
3. Node labels drawn on the canvas, chosen by zoom level, selection and weight, without overlapping.
4. A vertical layout: graph on top, time slider in the middle, scrollable related-articles list below, so a tap shows its result without scrolling the page.
5. A reset-view button (accessibility and discoverability).

Out of scope: force-directed layout, article nodes, named entities, any data-source or Room change (DB stays v20), network, AI, ads, new dependencies.

Honest limits: nodes are concepts from the existing detector plus tags the user already generated, not real-world entities. Layout stays the deterministic sunflower spiral, so spatial closeness does **not** mean relatedness; only the drawn edges and the highlight do. With few saved articles the graph is sparse.

## 2. Owner Decisions (AskUserQuestion, 2026-11-01)

1. Scope: upgrade Second Brain with zoom/pan and neighbour highlight (not a new graph, not highlight-only).
2. Labels: by zoom level, always for the selected node and its neighbours.
3. Layout: vertical split, graph above, list below, slider between.
4. Approach: a pure-Kotlin viewport (not `graphicsLayer` scaling).

## 3. Components

All new logic is pure Kotlin, JVM-testable, in `ui/page/secondbrain/`.

### 3.1 `GraphViewport`
Immutable value: `scale: Float`, `offsetX: Float`, `offsetY: Float` in canvas pixels, plus the canvas size.
- Constants: `MIN_SCALE = 1f`, `MAX_SCALE = 6f`, `DOUBLE_TAP_RESET`.
- `zoomBy(factor, focusX, focusY)`: scale clamped to the range, content under the focus point stays under it.
- `panBy(dx, dy)`: offset clamped so the unit square cannot leave the canvas entirely (at scale 1 no pan is possible).
- `toScreen(NodePos)` and `toUnit(x, y)` are exact inverses (round-trip tested). Both drawing and hit-testing use them.
- `reset()` returns scale 1 and zero offset.
- Stroke widths and node radii are **not** multiplied by scale; node radius grows by `sqrt(scale)` capped, so zoom reveals spacing without blobs.

### 3.2 `GraphHighlight`
`GraphHighlight.neighbours(selectedId, edges): Set<String>` returns the direct neighbours (excluding the node itself). Empty selection yields an empty set and means "nothing dimmed". Edge is active when it touches the selected node.

### 3.3 `GraphLabels`
`GraphLabels.select(nodes, positionsOnScreen, selectedId, neighbourIds, scale, canvasSize, measure)` returns the labels to draw:
- Priority, not exemption: the selected node is placed first, then its neighbours (weight desc, id), then the rest. Priority labels are not counted against the budget.
- Others: up to `MAX_BASE_LABELS` (8) at scale 1, growing linearly to `MAX_ZOOMED_LABELS` (24) at `MAX_SCALE`, ordered by weight then id.
- A node whose centre is outside the canvas gets no label. Otherwise the label box is clamped inside the canvas (and flips below the node when there is no room above). A label is dropped when its box overlaps one already placed, so even a neighbour can be dropped, but the selected node (placed first) never is while visible. `measure` is injected so the function stays pure and testable.

### 3.4 `SecondBrainPage` changes
- Column: graph box `weight(GRAPH_WEIGHT)` (about 55%), slider, list box `weight(LIST_WEIGHT)`.
- Gestures: `detectTransformGestures` for pinch/pan and `detectTapGestures` for tap and double-tap, in separate `pointerInput` blocks keyed by the graph; list scrolling stays in its own region, so no gesture conflict.
- Tap uses `viewport.toUnit` then the existing `BrainGraphLayout.hitTest`. The hit radius is defined in screen pixels (converted), so it stays finger-sized at any zoom.
- Viewport lives in Compose state (`rememberSaveable`-style saver for scale/offset), not in `SecondBrainViewModel`. Selection stays in the ViewModel as today.
- Changing the time window or graph resets the viewport.
- Reset-view button (disabled while the viewport is the identity) beside a one-line visible caption describing the gestures. The graph keeps its existing content description unchanged (existing tests rely on it) and exposes the zoom level through `stateDescription`.
- Empty-selection hint above the list; list shows related articles newest first as today.

### 3.5 Unchanged
`BrainGraphBuilder`, `SecondBrainSv`, `BrainGraphLayout.compute`, `SecondBrainStateReducer`, route and Settings entry.

## 4. Localization
Exactly 4 new `second_brain_*` keys: `second_brain_select_hint`, `second_brain_reset_view`, `second_brain_zoom_hint` (visible gesture caption), `second_brain_zoom_level` (`Zoom %1$d%%`, used as `stateDescription`) in all 38 `values*/strings.xml`: English and Vietnamese hand-written, others English fallback. Added in the task that first uses them; `StringResourceParityTest` must stay 6/6.

## 5. Tests

- **JVM:** `GraphViewportTest` (scale clamp, focus-stable zoom, pan clamp, no pan at scale 1, round-trip `toScreen`/`toUnit`, reset), `GraphHighlightTest` (neighbours, empty selection, isolated node, self-loop ignored), `GraphLabelsTest` (selected and neighbours always kept, budget grows with scale, overlap dropped, out-of-canvas dropped, deterministic order).
- **Existing tests stay green:** `BrainGraphLayoutTest`, `SecondBrainViewModelTest`, `SecondBrainStateReducerTest`, `BrainGraphBuilderTest`.
- **Robolectric Compose:** selecting a node shows its related articles in the lower region, neighbours highlighted state exposed through semantics, reset button restores the viewport, empty-selection hint.
- **Instrumentation (Pixel only, `am instrument`):** extend the Second Brain widget test with a real pinch/double-tap; the existing performance test must still pass with 200 nodes (< 1 s).
- **Device smoke (Pixel `2B051FDH3006MU`):** back up Room AND DataStore (non-empty, `integrity_check` ok), pinch, pan, tap a node, confirm neighbours stay bright and the list updates, reset, restore both backups and verify with `cmp`. Ads are closed automatically per the owner's standing instruction and reported.

## 6. Risks

- Gesture conflicts between pan and tap: mitigated by separate pointer inputs and by testing on the device.
- Label overlap cost: O(labels^2) on at most the budget (a few dozen), negligible.
- Zoomed 200-node drawing must stay within the performance test bound.
- The two-lambda trailing-lambda pitfall: new composable parameters are added last with defaults.

## 7. Non-goals

No force-directed simulation, no article nodes, no entity extraction, no persistence of the viewport, no new data, no AI.
