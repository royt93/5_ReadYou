# [KNOW-07] Semantic Smart Collections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng tính năng Semantic Smart Collections (KNOW-07) cho phép lưu các câu truy vấn tự nhiên thành các bộ sưu tập thông minh, tự động đối chiếu các bài báo mới sau mỗi chu kỳ đồng bộ RSS bằng SemanticSearchEngine và embedding index persistent, hiển thị và quản lý trong màn hình điều hướng riêng.

**Architecture:** Sử dụng Room Database v16 với 2 bảng `smart_collection` và `smart_collection_article` (liên kết nhiều-nhiều, foreign key ON DELETE CASCADE). Một service chuyên trách `SmartCollectionMatcher` chạy ngay sau khi repository chèn bài mới để ghép chính xác candidates với collections, dùng threshold `0.22f`. UI xây dựng bằng Jetpack Compose gồm `SmartCollectionsPage`, `SmartCollectionDetailPage`, dialog tạo nhanh từ `FlowPage` khi tìm kiếm ngữ nghĩa, và menu điều hướng trong `FlowActionsMenu`.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose, Material 3, Room Database 2.6.1 (KAPT, schema v16), Navigation Compose, Coroutines/Flow, JUnit4, Robolectric, MockK.

## Global Constraints

- Chỉ chạy test và tương tác trên emulator Pixel 10 Pro (`emulator-5554`) khi có live test.
- Nếu gặp quảng cáo toàn màn hình che UI, dừng ngay và chờ người dùng xác nhận.
- Tuyệt đối không magic numbers: dùng `const val` định danh rõ ràng.
- Toàn bộ query Room và vector computation chạy trên `Dispatchers.IO` hoặc `Dispatchers.Default`.
- Localize đầy đủ 6 ngôn ngữ: `en`, `vi`, `zh-rCN`, `ja`, `fr`, `de`.
- Bảo toàn 100% các unit test hiện có (858 tests).

---

### Task 1: Domain Entities & SmartCollectionDao (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/collection/SmartCollection.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/collection/SmartCollectionArticle.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/collection/SmartCollectionWithCount.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/repository/SmartCollectionDao.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/repository/SmartCollectionDaoTest.kt`

**Interfaces:**
- Produces: `SmartCollection(id, accountId, name, query, minScoreThreshold, createdAt)`
- Produces: `SmartCollectionArticle(collectionId, articleId, score, matchedAt)`
- Produces: `SmartCollectionDao` với các phương thức CRUD, đếm bài, và query danh sách bài dạng `ArticleWithFeed`

- [ ] **Step 1: Write failing tests for SmartCollectionDao**
  Viết `SmartCollectionDaoTest.kt` (Robolectric in-memory SQLite):
  - Test thêm và đọc collection theo `accountId`.
  - Test đếm số lượng bài match trong `SmartCollectionWithCount`.
  - Test cascade delete: xóa collection thì junction row bị xóa nhưng `Article` gốc vẫn còn nguyên.
  - Test cascade delete: xóa bài viết gốc thì junction row tương ứng tự động biến mất.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.SmartCollectionDaoTest"`
  Expected: FAIL (lớp chưa tồn tại).

- [ ] **Step 3: Implement Models & SmartCollectionDao**
  Tạo các entity Room với `@Entity`, `@ForeignKey(onDelete = CASCADE)`, `@Index`, và DAO interface.
  Lưu ý query lấy `ArticleWithFeed` phải dùng `@Transaction` và `@Query("SELECT a.* FROM article a INNER JOIN smart_collection_article ca ON a.id = ca.articleId WHERE ca.collectionId = :collectionId ORDER BY ca.matchedAt DESC")`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.SmartCollectionDaoTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với message `feat(db): add SmartCollection entities and SmartCollectionDao with tests`.

---

### Task 2: Room Migration 15 -> 16 & AndroidDatabase Integration

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration15To16Test.kt`

**Interfaces:**
- Produces: `MIGRATION_15_16: Migration`
- Modifies: `AndroidDatabase.version = 16`, thêm `smartCollectionDao()`

- [ ] **Step 1: Write failing test for RoomMigration15To16**
  Tạo `RoomMigration15To16Test.kt` kiểm tra nâng cấp DB từ v15 lên v16:
  - Tạo bảng `smart_collection` và `smart_collection_article`.
  - Tạo các index tương ứng và kiểm tra `sqlite_master`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.db.RoomMigration15To16Test"`
  Expected: FAIL (chưa có migration).

- [ ] **Step 3: Implement MIGRATION_15_16 & update AndroidDatabase**
  Khai báo `MIGRATION_15_16` với câu lệnh `CREATE TABLE` và `CREATE INDEX` chính xác.
  Thêm `SmartCollection::class`, `SmartCollectionArticle::class` vào `entities` trong `AndroidDatabase`.
  Nâng `version = 16` và thêm abstract fun `smartCollectionDao(): SmartCollectionDao`.

- [ ] **Step 4: Run test to verify it passes & schema exported**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.db.RoomMigration15To16Test"`
  Expected: PASS, schema snapshot `app/schemas/.../16.json` được tạo.

- [ ] **Step 5: Commit**
  Run: `git commit` với message `feat(db): add Room MIGRATION_15_16 for smart collections`.

---

### Task 3: SmartCollectionMatcher Service & Repository Sync Integration (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/SmartCollectionMatcher.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt` (add bounded query for recent articles after sync)
- Create: `app/src/test/java/com/mckimquyen/reader/domain/sv/SmartCollectionMatcherTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoTest.kt` (query bounds/account isolation) 

**Interfaces:**
- Produces: `SmartCollectionMatcher.matchNewArticles(accountId: Int, candidateArticles: List<ArticleWithFeed>): Int`
- Consumes: `SmartCollectionDao`, `SemanticSearchEngine`, `ArticleDao`

- [ ] **Step 1: Write failing unit test for SmartCollectionMatcher**
  Tạo `SmartCollectionMatcherTest.kt` (MockK):
  - Khi không có collection nào -> trả về 0 match, không gọi semantic rank.
  - Khi danh sách bài mới rỗng -> trả về 0 match.
  - Khi bài mới match query với score >= minScoreThreshold -> lưu vào DAO junction table.
  - Khi score < minScoreThreshold -> không lưu junction row.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.SmartCollectionMatcherTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement SmartCollectionMatcher and integrate into SyncWorker**
  - Implement `SmartCollectionMatcher` với thread-safety (`withContext(Dispatchers.Default)`).
  - Cập nhật `AbstractRssRepository` hoặc `SyncWorker` để trả về danh sách các bài viết mới được chèn (`insertedArticles`).
  - Trong `SyncWorker.doWork()`, gọi `smartCollectionMatcher.matchNewArticles(accountId, insertedArticles)` bọc trong `runCatching`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.SmartCollectionMatcherTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với message `feat(sync): implement SmartCollectionMatcher and hook into SyncWorker`.

---

### Task 4: UI ViewModels, Dialogs & Navigation Routes

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionsViewModel.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/collection/SaveSmartCollectionDialog.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionsPage.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionDetailPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowActionsMenu.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowPage.kt`
- Modify: Strings XML (`res/values/strings.xml`, `values-vi`, `values-zh-rCN`, `values-ja`, `values-fr`, `values-de`)
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionsViewModelTest.kt`

**Interfaces:**
- Produces: `SmartCollectionsViewModel` (expose `collectionsWithCount`, hàm `createCollection`, `deleteCollection`, `renameCollection`)
- Produces: Composable routes `RouteName.SMART_COLLECTIONS`, `RouteName.SMART_COLLECTION_DETAIL`

- [ ] **Step 1: Write ViewModel unit test**
  Tạo `SmartCollectionsViewModelTest.kt` kiểm tra emit danh sách, thêm, xóa, sửa collection.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.collection.SmartCollectionsViewModelTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement ViewModel, UI Pages and Navigation**
  - Thêm routes vào `RouteName.kt` và đăng ký composable trong `HomeEntry.kt`.
  - Tạo `SmartCollectionsPage.kt` với danh sách card Material 3, badge số lượng, menu xóa/sửa.
  - Tạo `SmartCollectionDetailPage.kt` hiển thị danh sách bài của collection.
  - Tạo `SaveSmartCollectionDialog.kt` và gắn nút "Lưu thành Bộ sưu tập" trên thanh search của `FlowPage.kt`.
  - Thêm mục "Bộ sưu tập thông minh" vào `FlowActionsMenu.kt`.
  - Thêm đầy đủ string resources vào 6 thứ tiếng.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.collection.SmartCollectionsViewModelTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với message `feat(ui): add SmartCollectionsPage, detail view and quick-save dialog`.

---

### Task 5: Comprehensive Verification & Multi-Perspective Audit

**Additional verification files:**
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/SmartCollectionReevaluationTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoReadCollectionsTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/collection/SaveSmartCollectionDialogTest.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionsPageTest.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionEdgeToEdgeTest.kt`

- [ ] **Step 1: Chạy toàn bộ Unit Tests**
  Run: `./gradlew testDevDebugUnitTest`
  Expected: All unit tests PASS, including account isolation, exact query re-evaluation and cancellation preserving previous memberships.

- [ ] **Step 2: Chạy Lint kiểm tra tài nguyên và code quality**
  Run: `./gradlew lintDevDebug`
  Expected: 0 error.

- [ ] **Step 3: Build APK DevDebug**
  Run: `./gradlew assembleDevDebug`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Cài đặt và thực hiện Live Smoke Test trên emulator-5554**
  - Cài đặt APK lên emulator: `adb -s emulator-5554 install -r app/build/outputs/apk/dev/debug/*.apk`
  - Mở app, vào FlowPage -> Search từ khóa "AI" -> Bấm "Lưu thành Bộ sưu tập" -> Đặt tên "Tin AI".
  - Mở menu More -> Chọn "Bộ sưu tập thông minh" -> Xác nhận collection "Tin AI" xuất hiện.
  - Đồng bộ feed -> Xác nhận bài mới phù hợp tự động gắn vào collection.

- [ ] **Step 5: Cập nhật tài liệu & Hoàn tất**
  - Cập nhật `doc/feature.md`: Chuyển KNOW-07 sang `✅ Implemented`.
  - Di chuyển `doc/task/todo/KNOW-07_semantic-smart-collections.md` sang `doc/task/done/KNOW-07_semantic-smart-collections_DONE.md`.
  - Commit và push lên `origin/dev`.
