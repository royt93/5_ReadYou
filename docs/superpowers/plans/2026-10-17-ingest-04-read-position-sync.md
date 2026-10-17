# [INGEST-04] Multi-device Read Position Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đồng bộ và khôi phục vị trí đọc dở của bài viết (tiến độ 0.0 .. 1.0) tức thì vào Room DB (v15), tự động cuộn đến đoạn đã đọc khi mở lại bài viết, giải quyết xung đột theo nguyên tắc "tiến độ xa hơn thắng", và hỗ trợ đồng bộ xuyên thiết bị qua WebDAV server.

**Architecture:** Bảng Room mới `article_read_position` tách biệt với bảng `article`, Room migration an toàn `MIGRATION_14_15`. Quy tắc `ReadPositionConflictResolver` đảm bảo `max(progress)` thắng. Tích hợp `ReadingViewModel` và `ReadingPage` để bắt sự kiện cuộn và khôi phục vị trí mượt mà (60/120 FPS). Tích hợp module `WebDavReadPositionSyncer` qua `SyncWorker` khi người dùng đã cấu hình WebDAV.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose, Room Database 2.6.1 (schema v15), KAPT, DataStore, OkHttp, JUnit4, MockK, Robolectric.

## Global Constraints

- Device Lock: Chỉ dùng `Pixel_10_Pro` (`emulator-5554`). Tuyệt đối không gửi lệnh tới thiết bị vật lý.
- Dừng ngay khi phát hiện quảng cáo toàn màn hình (R4) trong quá trình test màn hình UI.
- Tuân thủ TDD nghiêm ngặt: Viết test trước (RED) -> Implement tối thiểu (GREEN) -> Refactor & Commit.
- Không magic numbers, đầy đủ hằng số mô tả (`const val`), null-safe, coroutines trên `Dispatchers.IO`.
- Bảo toàn 100% các unit test hiện có (830 tests).

---

### Task 1: Domain Models & Conflict Resolver (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleReadPosition.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/ReadPositionConflictResolver.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/article/ReadPositionConflictResolverTest.kt`

**Interfaces:**
- Produces: `ArticleReadPosition(articleId: String, accountId: Int, progress: Float, lastReadTime: Long, isSynced: Boolean)`
- Produces: `ReadPositionConflictResolver.resolve(local: ArticleReadPosition?, remote: ArticleReadPosition?): ArticleReadPosition?`

- [ ] **Step 1: Write failing unit test for ReadPositionConflictResolver**
  Tạo `ReadPositionConflictResolverTest.kt` phủ các trường hợp:
  - Local null, remote non-null -> trả về remote.
  - Remote null, local non-null -> trả về local.
  - Cả hai null -> trả về null.
  - Remote progress > Local progress (0.7f vs 0.4f) -> Remote thắng.
  - Local progress > Remote progress (0.8f vs 0.2f) -> Local thắng.
  - Tiến độ bằng nhau (0.5f vs 0.5f): bản ghi có `lastReadTime` lớn hơn thắng.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.article.ReadPositionConflictResolverTest"`
  Expected: FAIL (lớp chưa tồn tại).

- [ ] **Step 3: Implement ArticleReadPosition & ReadPositionConflictResolver**
  Viết `ArticleReadPosition.kt` và `ReadPositionConflictResolver.kt` với so sánh float và timestamp chính xác.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.article.ReadPositionConflictResolverTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(sync): add ArticleReadPosition model and ReadPositionConflictResolver`.

---

### Task 2: Room Entity, DAO & Migration 14 -> 15 (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleReadPositionDao.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration14To15Test.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleReadPositionDaoTest.kt`

**Interfaces:**
- Produces: `ArticleReadPositionDao` với các hàm `getPosition(articleId): ArticleReadPosition?`, `upsertPosition(position: ArticleReadPosition)`, `deletePosition(articleId: String)`.
- Produces: `MIGRATION_14_15` trong `AndroidDb.kt`, nâng database version lên 15.

- [ ] **Step 1: Write failing test for ArticleReadPositionDao and RoomMigration14To15**
  Viết `ArticleReadPositionDaoTest.kt` (Robolectric in-memory DB) và `RoomMigration14To15Test.kt` kiểm tra bảng mới, foreign key cascade delete khi bài viết bị xoá.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.ArticleReadPositionDaoTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement Dao, Entity annotations, MIGRATION_14_15 and update AndroidDatabase**
  Thêm `ArticleReadPosition::class` vào danh sách entities trong `AndroidDatabase`, đăng ký `articleReadPositionDao()`, định nghĩa `MIGRATION_14_15`.

- [ ] **Step 4: Run test to verify it passes and update Room schema**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.ArticleReadPositionDaoTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(db): add ArticleReadPositionDao and Room MIGRATION_14_15`.

---

### Task 3: ReadPositionSyncPref & Settings Toggle

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/ReadPositionSyncPref.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt`
- Modify: `app/src/main/res/values/strings.xml` và các file ngôn ngữ liên quan (`values-vi`, `values-zh-rCN`, `values-ja`, `values-fr`, `values-de`).

- [ ] **Step 1: Write unit test verifying ReadPositionSyncPref default value is true**
  Tạo test đọc/ghi qua DataStore.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.ReadPositionSyncPrefTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement ReadPositionSyncPref & Settings integration**
  Thêm key vào DataStoreKeys, cài đặt `ReadPositionSyncPref(default = true)`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.ReadPositionSyncPrefTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(pref): add ReadPositionSyncPref setting`.

---

### Task 4: ReadingViewModel & ReadingPage Auto-Save & Auto-Restore (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelPositionTest.kt`

- [ ] **Step 1: Write failing test for ReadingViewModel position management**
  Tạo `ReadingViewModelPositionTest.kt` kiểm tra:
  - Khi mở bài viết: query `readPositionDao.getPosition(articleId)`.
  - Khi `saveReadPosition(articleId, progress)`: lưu vào DB và giải quyết xung đột với `ReadPositionConflictResolver`.
  - Không lưu tiến độ nếu < 0.05f hoặc > 0.95f (tránh ép cuộn ở đầu/cuối bài).

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.ReadingViewModelPositionTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement ReadingViewModel position save/restore & ReadingPage scroll restoration**
  Thêm `saveReadPosition` vào `ReadingViewModel`, phát event `restoreScrollPosition` để `ReadingPage` gọi `listState.scrollToItem(targetIndex)`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.ReadingViewModelPositionTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(read): auto-save and restore read progress in ReadingPage`.

---

### Task 5: WebDAV Cross-Device Read Position Syncer (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/sync/WebDavReadPositionSyncer.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/sync/WebDavReadPositionSyncerTest.kt`

- [ ] **Step 1: Write failing test for WebDavReadPositionSyncer**
  Kiểm tra serializer/deserializer JSON danh sách `ArticleReadPosition`, merge remote positions với local qua `ReadPositionConflictResolver`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.sync.WebDavReadPositionSyncerTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement WebDavReadPositionSyncer and integrate into SyncWorker**
  Thực hiện đọc/ghi `read_positions.json` trên WebDAV server nếu `webDavBackupManager.getConfig().isConfigured == true`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.sync.WebDavReadPositionSyncerTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(sync): add WebDavReadPositionSyncer for cross-device position sync`.

---

### Task 6: Toàn Bộ Verification Suite & Live Smoke Test trên Pixel 10 Pro

- [ ] **Step 1: Chạy toàn bộ Unit Tests & Lint**
  Run: `./gradlew testDevDebugUnitTest lintDevDebug`
  Expected: Toàn bộ tests pass (>= 830 tests), 0 lint error.

- [ ] **Step 2: Build & Cài đặt lên Pixel 10 Pro (emulator-5554)**
  Run: `./gradlew assembleDevDebug && adb -s emulator-5554 install -r app/build/outputs/apk/dev/debug/*.apk`

- [ ] **Step 3: Thực hiện Smoke Test trên thiết bị**
  Mở 1 bài viết dài, cuộn 50%, thoát về danh sách feed, mở lại: xác nhận màn hình tự động cuộn đến 50%.
  Verify bản ghi trong Room DB table `article_read_position`.

- [ ] **Step 4: Tạo completion report & Push**
  Tạo `doc/task/done/INGEST-04_multi-device-read-position-sync_DONE.md`, cập nhật `doc/feature.md`, commit và push lên `origin/dev`.
