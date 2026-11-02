# FRONT-03 — Ngân Hà Tri Thức Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm chế độ xem Ngân Hà cho các bài đã đọc bên trong Second Brain, có đường nối theo khái niệm, phối cảnh xoay/thu phóng và mở lại bài.

**Architecture:** Giữ đồ thị khái niệm hiện tại nguyên vẹn; bổ sung đường dữ liệu riêng từ metadata bài đã đọc qua `GalaxySv` và `GalaxyViewModel`. Các thuật toán đồ thị, bố cục và camera là Kotlin thuần; `GalaxyCanvas` chiếu/vẽ bằng Compose Canvas, còn `SecondBrainPage` giữ toggle và dùng chung khoảng thời gian.

**Tech Stack:** Kotlin, Room (schema v20 không đổi), Hilt, coroutines/StateFlow, Jetpack Compose Canvas/Material3 1.0.1, JUnit4, MockK, Robolectric, AndroidX Compose UI test.

## Global Constraints

- Chỉ nhánh `dev`; không worktree; không commit `.idea/*`; không push.
- Không thay đổi Room schema v20, không OpenGL, không AI/API key, quảng cáo, IAP hay tiền.
- Galaxy chỉ chứa bài `isUnread = 0`; truy vấn chỉ lấy `ARTICLE_LIST_COLUMNS`, không lấy body.
- Giới hạn: `MAX_STARS = 600`, `MAX_LINES = 400`, và `MAX_NEIGHBORS_PER_STAR` là hằng có tên.
- Toàn bộ chuỗi mới có mặt trong 38 locale; EN và VI viết tay, các locale còn lại dùng tiếng Anh; `StringResourceParityTest` phải đạt 6/6.
- Mọi `@HiltViewModel` chỉ có một constructor `@Inject`; không truyền function-typed constructor parameter; dispatcher test phải tiêm cả `defaultDispatcher` và `ioDispatcher`.
- Compose Material3 1.0.1; không dùng `ModalBottomSheet`.
- Kiểm thử Compose/Robolectric: `@Config(sdk = [34], application = Application::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createAndroidComposeRule<ComponentActivity>()`, một `setContent` mỗi test; dùng `onAllNodesWithTag(...).assertCountEquals(0)` để xác nhận phần tử vắng.
- Không tuyên bố hiệu năng tốt nếu chưa đo trên Tecno KJ7 `115333744A005844`; lệnh thiết bị luôn `adb -s 115333744A005844`, tuyệt đối không dùng Pixel hoặc `connectedDevDebugAndroidTest`.
- Smoke: nếu `dumpsys` báo Dozing/keyguard, dừng thao tác UI và nhờ owner mở khóa. Sao lưu Room (`databases/Reader`, `Reader-wal`, `Reader-shm`) cùng `files/datastore/settings.preferences_pb`; datastore phải không rỗng; chạy `PRAGMA integrity_check` trên bản sao DB. DB gốc hiện khoảng 152 bài/1 feed, không phải 1.689 bài; chỉ bài đã đọc thành sao. Ưu tiên mở vài bài qua UI; nếu cần thêm dữ liệu, chỉ sửa `isUnread=0` trên bản sao DB, kiểm integrity, rồi mới nạp bản sao lên thiết bị. Không bao giờ xóa DataStore. Khôi phục cả bốn file bằng `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local`, đọc Room với đủ ba file và xác nhận `cmp` cho mọi file.
- Đóng full-screen `AdActivity` chủ động, chỉ bấm nhãn `Tiếp tục đến ứng dụng` từ dump `uiautomator` mới (xóa `/sdcard/ui.xml` trước dump); kiểm tra `topResumedActivity` trước từng tap; không bấm creative/toạ độ cũ. Nếu nhãn không xuất hiện, đợi/thử lại khoảng một phút để quảng cáo tự đóng; báo cáo quảng cáo đã xuất hiện.
- LazyColumn không luôn đưa item mới nạp vào viewport: toggle/sheet trong danh sách phải có key; nếu thay đổi nội dung đầu danh sách sau khi nạp, cuộn về đầu và xác minh trên thiết bị. Mutation-check test hồi quy: tắt fix phải khiến test đỏ. Sau sửa hành vi đã thấy sai trên thiết bị, chạy lại đúng smoke trên thiết bị.
- Đọc lỗi test thực từ `app/build/test-results/testDevDebugUnitTest/*.xml`. Helper script lưu thành file rồi đọc output bằng Read; tránh Python heredoc dài trong zsh; dùng `printf`, không `echo =====`.

---

## File map

- `app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyGraph.kt`: `GalaxyStar`, `GalaxyLine`, `GalaxyGraph`.
- `.../GalaxyGraphBuilder.kt`: lọc ngày/giới hạn sao, dựng cạnh theo concept, giới hạn bậc và số cạnh.
- `.../GalaxyLayout.kt`: bố trí spiral deterministic theo id, gom concept gần nhau, kẹp biên.
- `.../GalaxyCamera.kt`: yaw/pitch/zoom, phép chiếu phối cảnh, thứ tự depth và hit-test thuần.
- `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`: chỉ thêm query metadata bài đọc.
- `app/src/main/java/com/mckimquyen/reader/domain/sv/GalaxySv.kt`: lấy bài đã đọc, phát hiện concept offline, dựng `GalaxyGraph` trên Default.
- `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/galaxy/GalaxyViewModel.kt`: trạng thái loading/empty/error/ready, tải/hủy, chọn sao và khoảng ngày.
- `.../GalaxyCanvas.kt`, `.../GalaxyStarSheet.kt`: hiển thị và thao tác; không giữ `Context`/`Activity`.
- `.../SecondBrainPage.kt`: thêm tham số mới cuối danh sách với default, toggle và nội dung Galaxy; giữ nguyên API cũ cho test/caller.
- `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`: kết nối state/callback và điều hướng `${RouteName.READING}/$id`.
- `app/src/main/res/values*/strings.xml`: chuỗi Galaxy đủ 38 locale.
- Unit tests đặt cạnh package tương ứng trong `app/src/test/java/com/mckimquyen/reader/`; Room in-memory test trong `domain/galaxy/`; UI Robolectric test trong `ui/page/secondbrain/`; device test dưới `app/src/androidTest/java/com/mckimquyen/reader/ui/page/secondbrain/`.
- Hoàn tất: `doc/task/done/FRONT-03_3d-knowledge-galaxy_DONE.md`, `doc/feature.md`, `doc/task/todo/10_FRONTIER_AND_ECOSYSTEM.md`; xóa todo riêng bằng `git rm doc/task/todo/FRONT-03_3d-knowledge-galaxy.md`.

## Interfaces

- `GalaxyStar(id: String, title: String, feedName: String, date: Long, concepts: Set<String>)`.
- `GalaxyLine(a: String, b: String, sharedConcepts: Int)`; `GalaxyGraph(stars: List<GalaxyStar>, lines: List<GalaxyLine>)`.
- `GalaxyGraphBuilder.build(articles: List<GalaxyStar>, from: Long = Long.MIN_VALUE, to: Long = Long.MAX_VALUE): GalaxyGraph`.
- `GalaxyLayout.compute(stars: List<GalaxyStar>): Map<String, GalaxyPoint>`; `GalaxyPoint(x: Float, y: Float, z: Float)`.
- `GalaxyCamera(yaw: Float = 0f, pitch: Float = 0f, zoom: Float = 1f)`: `rotateBy(dx: Float, dy: Float)`, `zoomBy(factor: Float)`, `project(point: GalaxyPoint, width: Float, height: Float): GalaxyProjection?`, `depthOrder(points: Map<String, GalaxyPoint>): List<String>`, `hitTest(stars: List<GalaxyStar>, positions: Map<String, GalaxyPoint>, width: Float, height: Float, x: Float, y: Float, radiusPx: Float): String?`.
- `ArticleDao.queryReadArticleListWithFeed(accountId: Int, limit: Int): List<ArticleListItemWithFeed>`.
- `GalaxySv.load(accountId: Int, from: Long, to: Long): GalaxyGraph`.
- `GalaxyUiState`: `Loading`, `Empty`, `Error`, `Ready(graph, selectedStarId, from, to)`; `GalaxyViewModel` exposes `StateFlow<GalaxyUiState>`, `refresh()`, `setRange(from,to)`, `selectStar(id?)`.

### Task 1: Pure Galaxy graph builder

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyGraph.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyGraphBuilder.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyGraphBuilderTest.kt`

**Interfaces:**
- Consumes: no app/Android dependencies.
- Produces: graph models and `GalaxyGraphBuilder.build(...)` from Interfaces.

- [ ] **Step 1: Write focused failing tests.** Cover common-concept connection and shared count; date window inclusive; newest-first cap 600 with deterministic tie by id; total line cap 400; popular-concept neighbor cap; empty/singleton; no shared concepts produce no line. Use deterministic fixture helper:

```kotlin
private fun star(id: String, date: Long, vararg concepts: String) =
    GalaxyStar(id, "Title $id", "Feed", date, concepts.toSet())

@Test fun build_connectsStarsBySharedConceptAndSortsStrongestFirst() {
    val graph = GalaxyGraphBuilder.build(listOf(
        star("a", 1, "ai", "chips"), star("b", 2, "ai", "chips"), star("c", 3, "ai"),
    ))
    assertEquals(GalaxyLine("a", "b", 2), graph.lines.first())
}

@Test fun build_filtersInclusiveWindowAndCapsNewestStars() {
    val graph = GalaxyGraphBuilder.build(listOf(star("old", 1, "x"), star("in", 2, "x"), star("new", 3, "x")), 2, 3)
    assertEquals(listOf("in", "new"), graph.stars.map { it.id }.sorted())
}

@Test fun build_capsStarsLinesAndNeighbors() {
    val stars = (0..GalaxyGraphBuilder.MAX_STARS).map { star("%04d".format(it), it.toLong(), "common") }
    val graph = GalaxyGraphBuilder.build(stars)
    assertEquals(GalaxyGraphBuilder.MAX_STARS, graph.stars.size)
    assertTrue(graph.lines.size <= GalaxyGraphBuilder.MAX_LINES)
    assertTrue(graph.lines.flatMap { listOf(it.a, it.b) }.groupingBy { it }.eachCount().values
        .all { it <= GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR * 2 })
}
```

Expected: compile/test FAIL because Galaxy API does not exist.
- [ ] **Step 2: Run tests red.** `./gradlew testDevDebugUnitTest --tests "*GalaxyGraphBuilderTest"`; expect unresolved Galaxy symbols.
- [ ] **Step 3: Implement minimum pure models/builder.** Filter input to `date in from..to`; choose newest `MAX_STARS` using date descending then id, then stable output order by date/id. Normalize concepts with trim/lowercase `Locale.ROOT`. For each concept sort members by date/id and create candidate pairs only to the next `MAX_NEIGHBORS_PER_STAR` members; aggregate shared concept counts, sort by count descending then `(a,b)`, take 400, then emit stable `(a,b)` order. Define all three limits as named constants in builder.
- [ ] **Step 4: Verify green.** Run focused Gradle command above; inspect XML on failure. Add assertions that endpoints exist and no self-edge; expected PASS.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/model/galaxy app/src/test/java/com/mckimquyen/reader/domain/model/galaxy && git commit -m "feat(front-03): build bounded article galaxy graph"` with `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

### Task 2: Deterministic 3D layout and camera

**Files:**
- Create: `.../domain/model/galaxy/GalaxyLayout.kt`
- Create: `.../domain/model/galaxy/GalaxyCamera.kt`
- Test: `.../app/src/test/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyLayoutTest.kt`
- Test: `.../app/src/test/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyCameraTest.kt`

**Interfaces:**
- Consumes: `GalaxyStar` from Task 1.
- Produces: `GalaxyPoint`, `GalaxyLayout.compute`, `GalaxyCamera`, and `GalaxyProjection`.

- [ ] **Step 1: Add failing layout tests.** Assert identical map for reversed input with same IDs; each x/y/z finite and within named layout bounds; empty returns empty; stars sharing a concept have lower mean 3D distance than unrelated fixture groups.

```kotlin
@Test fun compute_isStableByIdAndFiniteWithinBounds() {
    val input = listOf(star("a", 1, "ai"), star("b", 2, "ai"), star("c", 3, "news"))
    val forward = GalaxyLayout.compute(input)
    assertEquals(forward, GalaxyLayout.compute(input.reversed()))
    forward.values.forEach { p ->
        assertTrue(p.x.isFinite() && p.y.isFinite() && p.z.isFinite())
        assertTrue(p.x in -GalaxyLayout.RADIUS..GalaxyLayout.RADIUS)
        assertTrue(p.y in -GalaxyLayout.RADIUS..GalaxyLayout.RADIUS)
        assertTrue(p.z in -GalaxyLayout.GALAXY_THICKNESS..GalaxyLayout.GALAXY_THICKNESS)
    }
}
```

- [ ] **Step 2: Add failing camera tests.** Verify rotation changes projected coordinates, pitch/zoom clamp to constants, invalid/nonpositive dimensions return null projection, near depth projects larger than far depth, depth ordering far-to-near, and hit test returns closest projected star or null.
- [ ] **Step 3: Run red.** `./gradlew testDevDebugUnitTest --tests "*GalaxyLayoutTest" --tests "*GalaxyCameraTest"`; expect unresolved API.
- [ ] **Step 4: Implement.** Sort by id before deterministic spiral placement; place concept cohorts near deterministic sector anchors; clamp x/y/z to explicit bounds, z within `±GALAXY_THICKNESS`. Camera rotation uses bounded yaw and pitch; zoom uses explicit positive min/max. `project` uses a guarded perspective denominator and returns screen center, depth and radius scale; reject non-finite inputs/results. Depth order sorts farthest first then id. Hit test chooses nearest screen-space squared distance inside radius; ties break by id.
- [ ] **Step 5: Verify green.** Run Task 2 focused Gradle command and inspect XML. Add edge-case test for zero-size and extreme factor if absent; expect PASS.
- [ ] **Step 6: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/model/galaxy app/src/test/java/com/mckimquyen/reader/domain/model/galaxy && git commit -m "feat(front-03): add deterministic galaxy camera and layout"` (+ trailer).

### Task 3: Metadata-only DAO and `GalaxySv`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/GalaxySv.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/galaxy/GalaxyArticleDaoIntegrationTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/GalaxySvTest.kt`

**Interfaces:**
- Consumes: Task 1 graph types, existing `ArticleListItemWithFeed`, `SemanticSearchEngine.detectConceptsStrict`.
- Produces: `queryReadArticleListWithFeed(accountId, limit)` and `GalaxySv.load(accountId, from, to)`.

- [ ] **Step 1: Write Room integration test.** Follow `ContrastingCoverageRoomIntegrationTest`: in-memory `AndroidDatabase`, insert Local account/group/feed; insert articles with dates and mixed unread flags; query limit 2. Assert returned ids are read-only rows ordered by `date DESC, id DESC`, feed relation populated, size respects limit. Confirm test does not inspect/body-load `fullContent`.
- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*GalaxyArticleDaoIntegrationTest"`; expect missing DAO method.
- [ ] **Step 3: Add exact SQL query.** In `ArticleDao.kt`:

```kotlin
@Transaction
@Query("SELECT " + ARTICLE_LIST_COLUMNS + " FROM article WHERE accountId = :accountId AND isUnread = 0 ORDER BY date DESC, id DESC LIMIT :limit")
suspend fun queryReadArticleListWithFeed(accountId: Int, limit: Int): List<ArticleListItemWithFeed>
```

No entity/schema change.
- [ ] **Step 4: Write `GalaxySvTest` then run red.** Mock DAO rows and verify `load` passes account/limit, maps title/feed/date/id, combines strict detected concepts with no external AI call, applies requested date window, and does not expose article body in `GalaxyStar`.
- [ ] **Step 5: Implement `GalaxySv`.** One `@Inject` constructor with `ArticleDao`, `SemanticSearchEngine`, `@DefaultDispatcher CoroutineDispatcher`; query on IO by DAO's suspend execution, map metadata and concepts inside `withContext(defaultDispatcher)`, then call `GalaxyGraphBuilder.build(stars, from, to)`. Use named `MAX_READ_ARTICLES = 600`.
- [ ] **Step 6: Verify.** `./gradlew testDevDebugUnitTest --tests "*GalaxyArticleDaoIntegrationTest" --tests "*GalaxySvTest"`; expect PASS. Check Room version stays 20 and no schema files changed.
- [ ] **Step 7: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt app/src/main/java/com/mckimquyen/reader/domain/sv/GalaxySv.kt app/src/test/java/com/mckimquyen/reader/domain/galaxy app/src/test/java/com/mckimquyen/reader/domain/sv/GalaxySvTest.kt && git commit -m "feat(front-03): load read articles for galaxy"` (+ trailer).

### Task 4: Galaxy view state and ViewModel

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/galaxy/GalaxyUiState.kt`
- Create: `.../GalaxyViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/galaxy/GalaxyViewModelTest.kt`

**Interfaces:**
- Consumes: Task 3 `GalaxySv.load`, account lookup pattern from `SecondBrainViewModel`.
- Produces: state flow with `Loading | Empty | Error | Ready`; `refresh`, `setRange`, `selectStar`.

- [ ] **Step 1: Write Robolectric ViewModel tests.** Use `@RunWith(RobolectricTestRunner::class)`, `@Config(manifest = Config.NONE)`, `StandardTestDispatcher`; construct `GalaxyViewModel(context, galaxySv, ioDispatcher)` directly. Verify initial Loading then Ready; empty graph becomes Empty; selecting unknown id clears selection; range invokes `load(accountId, from, to)`; exception becomes Error; cancellation propagates; a newer load cancels prior job.
- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*GalaxyViewModelTest"`; expect missing state/viewmodel.
- [ ] **Step 3: Implement.** Exactly one `@Inject` constructor, inject `@IODispatcher` (GalaxySv already owns the default dispatcher), use `context.currentAccountId` on IO, cancel previous `Job`, preserve current Ready while reloading, rethrow `CancellationException`, log and publish Error otherwise. Do not add function-typed constructor arguments; set dispatchers in tests.
- [ ] **Step 4: Verify green.** `./gradlew testDevDebugUnitTest --tests "*GalaxyViewModelTest"`; read XML. Add/dispose no resources beyond ViewModel coroutine scope.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/galaxy app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/galaxy && git commit -m "feat(front-03): add galaxy view state"` (+ trailer).

### Task 5: Canvas, star selection sheet, and Second Brain mode toggle

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/galaxy/GalaxyCanvas.kt`
- Create: `.../GalaxyStarSheet.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/GalaxyPageTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/SecondBrainPageGalaxyCompatibilityTest.kt`

**Interfaces:**
- Consumes: Task 2 camera/layout, Task 4 UI state/ViewModel callbacks, existing SecondBrain graph callbacks.
- Produces: galaxy mode inside existing route; tapping star selects it, sheet opens its article via existing callback route.

- [ ] **Step 1: Add failing Compose tests.** With one `setContent` in each test, assert default mode retains existing graph tags/title and old callers compile without new args; selecting Galaxy reveals Galaxy canvas tag; Empty renders empty prompt and no canvas; tapping star shows title/feed/date and `Mở bài`; clicking button calls `onOpenArticle(id)`; reset button returns default camera. Use Robolectric imports/config exactly as Global Constraints. For absence use `onAllNodesWithTag(GALAXY_CANVAS_TAG).assertCountEquals(0)`.
- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*GalaxyPageTest" --tests "*SecondBrainPageGalaxyCompatibilityTest"`; expect missing tags/components/parameters.
- [ ] **Step 3: Implement Galaxy Canvas and sheet.** Canvas computes positions once per graph, draws capped lines/stars far-to-near, and brightness/thickness saturate with star count. `detectTransformGestures` calls camera rotate/zoom; tap uses `GalaxyCamera.hitTest`; reset button has stable test tag. Sheet contains only title/source/date and `Mở bài`, uses `TextButton`/`Button`, dismiss callback; no Material3 bottom sheet API.
- [ ] **Step 4: Add backward-compatible mode APIs to `SecondBrainPage`.** Append all new parameters after existing `modifier`, each with a default; preserve existing `ReadyContent` graph path. Add mode toggle `Khái niệm | Ngân Hà`; share `from/to` and call the galaxy range callback. Keep `LazyColumn` article items keyed; if galaxy toggle/sheet is hosted inside any lazy list, give item stable key and scroll list to top when loaded content changes. Avoid hiding existing graph/list/reset tags in concept mode.
- [ ] **Step 5: Wire route.** `HomeEntry` obtains `GalaxyViewModel` via `hiltViewModel`, collects state, passes `refresh`, `setRange`, `selectStar`, `onOpenArticle = { id -> navController.navigate("${RouteName.READING}/$id") { launchSingleTop = true } }`. Do not add separate tab/route.
- [ ] **Step 6: Verify existing and new UI tests.** `./gradlew testDevDebugUnitTest --tests "*SecondBrainPageWidgetTest" --tests "*SecondBrainViewportUiTest" --tests "*GalaxyPageTest" --tests "*SecondBrainPageGalaxyCompatibilityTest"`; expect all PASS. Mutation-check any fix for late-loaded toggle/sheet by disabling fix and confirming its regression test fails, then restore and rerun.
- [ ] **Step 7: Commit.** `git add app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain && git commit -m "feat(front-03): add galaxy mode to second brain"` (+ trailer).

### Task 6: Strings in all 38 locales and parity

**Files:**
- Modify: `app/src/main/res/values*/strings.xml`
- Test: existing `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/StringResourceParityTest.java`

**Interfaces:**
- Consumes: exact string IDs referenced in Tasks 4–5.
- Produces: translated EN/VI and English fallback text in all maintained locales.

- [ ] **Step 1: Define stable keys.** Add `galaxy_mode_concepts`, `galaxy_mode_stars`, `galaxy_empty`, `galaxy_error`, `galaxy_retry`, `galaxy_reset_view`, `galaxy_star_count`, `galaxy_star_sheet_open`, `galaxy_canvas_desc`. Keep placeholders identical; use `Stars: %1$d` (not `%1$d stars` followed by words) to avoid plural-candidate lint.
- [ ] **Step 2: Generate locale additions.** Write `/tmp/front03_strings.py` (not inline shell) using the established ECHO-03 Task 4 pattern: `glob('app/src/main/res/values*/strings.xml')`, hand-written `EN`/`VI` maps, English for remaining locales, XML-escape `&`, quotes/apostrophes, add only missing ids immediately before the single `</resources>`, and print locale file count. Expected `38 locale files`.
- [ ] **Step 3: Check coverage.** `grep -L 'name="galaxy_canvas_desc"' app/src/main/res/values*/strings.xml | wc -l` must print `0`; run `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest"`; read XML and expect 6/6.
- [ ] **Step 4: Commit.** `git add app/src/main/res/values*/strings.xml && git commit -m "feat(front-03): add galaxy strings across 38 locales"` (+ trailer).

### Task 7: Instrumentation, full verification, Tecno smoke, and completion docs

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/secondbrain/KnowledgeGalaxyDeviceTest.kt`
- Create: `doc/task/done/FRONT-03_3d-knowledge-galaxy_DONE.md`
- Modify: `doc/feature.md`
- Modify: `doc/task/todo/10_FRONTIER_AND_ECOSYSTEM.md`
- Delete: `doc/task/todo/FRONT-03_3d-knowledge-galaxy.md`

**Interfaces:**
- Consumes: completed galaxy UI and graph APIs.
- Produces: device rendering/interaction check, truthful performance evidence, restored user data, tracked completion.

- [ ] **Step 1: Add instrumentation test.** Render a `GalaxyGraph` with two stars and a shared line in an instrumentation Compose host; assert canvas/reset tags displayed; perform reset and assert enabled state changes as expected; tap one star and assert selected sheet text. Keep test independent of app DB/network.
- [ ] **Step 2: Run full verification sequentially.** `./gradlew testDevDebugUnitTest --rerun`; `./gradlew lintDevDebug --rerun-tasks`; `./gradlew assembleDevDebug assembleDevDebugAndroidTest`. Read unit counts/failures from every `app/build/test-results/testDevDebugUnitTest/*.xml`, lint report `app/build/reports/lint-results-devDebug.xml`; record failures/warnings honestly. Verify Room schema version 20 and no migration/schema changes.
- [ ] **Step 3: Install/run instrumentation only on locked Tecno.** Confirm `adb -s 115333744A005844 get-state` returns `device`; resolve APKs with `find` (APK filename contains spaces), install app and test APK using only `adb -s 115333744A005844 install -r -d -t ...`, run `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.ui.page.secondbrain.KnowledgeGalaxyDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`. Never call `connectedDevDebugAndroidTest`.
- [ ] **Step 4: Back up and validate device data.** Check wake/keyguard first with `adb -s 115333744A005844 shell dumpsys power` and `dumpsys window`; if Dozing/locked, ask owner to unlock before taps. Force-stop app. Save Room `Reader`, `Reader-wal`, `Reader-shm` and `files/datastore/settings.preferences_pb` from `run-as` into a unique `/tmp/front03-backup`. Require all four non-empty; copy all three Room files to a separate check directory and require `sqlite3 .../Reader 'PRAGMA integrity_check;'` = `ok`. Query the backup read-only for article count, feed count, and `isUnread=0` count; expect baseline near 152 articles/1 feed, report actual values (not STRAT-02 bundle count).
- [ ] **Step 5: Ensure stars without altering original backup.** First, use app UI to mark a few existing articles read, if convenient. If too few or smoke needs a predictable shared-concept star, create a working COPY of all three Room files, checkpoint/copy consistently, update only `isUnread=0` for selected existing rows (or insert fixture only if foreign keys/data model are valid), run `integrity_check` and `foreign_key_check`, clear/checkpoint WAL correctly, then install those copied DB files; keep original backup untouched. Do not seed through direct SQL against the only/original DB. Preserve original DataStore file untouched.
- [ ] **Step 6: Smoke the app.** Launch app, navigate Second Brain → Ngân Hà using fresh UI hierarchy before every tap. Close/report any full-screen ad only via Global Constraints. Confirm nonempty stars from read articles; rotate, pinch-zoom, reset, tap star, verify title/source/date, open article and return. If database has too few eligible articles, report sparse-data limitation rather than claim a populated Galaxy. Measure frame timings before and after interaction using `adb -s 115333744A005844 shell dumpsys gfxinfo com.mckimquyen.reader framestats` and capture frame/jank counters; report observed numbers and sample caveats, never claim smoothness without measured evidence.
- [ ] **Step 7: Always restore and verify.** Force-stop. Restore all Room files and DataStore with exact `adb -s 115333744A005844 shell "run-as com.mckimquyen.reader sh -c 'cat > databases/Reader'" < /tmp/front03-backup/Reader` form (adjust path for each `-wal`, `-shm`, and `files/datastore/settings.preferences_pb`). Read back all four with `adb -s 115333744A005844 exec-out run-as ... cat PATH` into temp files and `cmp` each against backup. Re-read DB using all three Room files and verify `PRAGMA integrity_check` = `ok`. Never delete DataStore. Remove only host temp files after verification.
- [ ] **Step 8: Write truthful DONE docs and update task tracking.** DONE doc records commits, exact tests/counts/lint, instrumentation result, baseline counts, smoke path, frame measurements, ads, data restore `cmp`, sparse/untested behavior, and honest Canvas-projection limits. Add FRONT-03 under implemented in `doc/feature.md`; mark FRONT-03 done in epic 10 with link to DONE doc; `git rm doc/task/todo/FRONT-03_3d-knowledge-galaxy.md`.
- [ ] **Step 9: Commit completion docs.** `git add doc/feature.md doc/task/done/FRONT-03_3d-knowledge-galaxy_DONE.md doc/task/todo/10_FRONTIER_AND_ECOSYSTEM.md && git commit -m "docs(front-03): record knowledge galaxy completion"` (+ trailer). Confirm no `.idea/*` staged and working tree only has original unrelated `.idea` modifications.

## Self-review

- Spec coverage: mode within Second Brain, shared time slider, read-only metadata query, offline `detectConceptsStrict`, star/line caps and neighbor cap, deterministic bounded spiral, clamped camera/perspective/depth/hit test, Canvas-only projection, brightness cap, touch/reset/open article, Loading/Empty/Error/Ready, locale parity, unit/Room/ViewModel/Compose/instrumentation tests, Tecno performance smoke, data backup/restore, and scope exclusions all have tasks.
- Placeholder scan: no TBD/TODO implementation placeholders; required todo deletion is explicit. UI test assertions and executable shell commands are present at each verification gate.
- Type consistency: `GalaxyStar` date is `Long`; graph builder accepts `GalaxyStar`; `GalaxySv.load` returns `GalaxyGraph`; ViewModel uses `GalaxyUiState.Ready`; Canvas consumes graph plus camera; route uses same `onOpenArticle` reading route as Second Brain.
- Known safety decision: smoke uses originals as immutable backup and seeds only a working copy if real read data is insufficient; does not invent 1,689 article baseline.
