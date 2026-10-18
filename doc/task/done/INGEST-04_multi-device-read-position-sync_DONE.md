# [INGEST-04] Multi-device Read Position Sync - DONE

- **Type:** New Feature / Sync
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [07. INGESTION — Biến Mọi Web Thành RSS & Đọc Sau](../todo/07_UNIVERSAL_INGESTION_AND_SYNC.md)
- **Location:** `domain/model/article/`, `domain/repository/ArticleReadPositionDao.kt`, `infrastructure/db/`, `infrastructure/pref/`, `infrastructure/sync/`, `ui/page/home/read/`
- **Status:** ✅ Completed / Done (2026-10-17)

## Completion Report

- **Audit Score:** `9.6 / 10`
- **Commit History:**
  - `ed042dec` — `feat(sync): add ArticleReadPosition model and ReadPositionConflictResolver`
  - `900df9d8` — `feat(db): add ArticleReadPositionDao and Room MIGRATION_14_15`
  - `ea9646d6` — `feat(read): add ReadPositionSync setting and auto-save/restore in ReadingPage`
  - [Next commit] — `feat(sync): add WebDavReadPositionSyncer for cross-device position sync and complete INGEST-04`

## Tests Added

- `ReadPositionConflictResolverTest` (6 unit tests):
  - Local null -> remote, remote null -> local, cả 2 null -> null.
  - Remote progress xa hơn thắng (`0.7f > 0.4f`).
  - Local progress xa hơn thắng kể cả remote có timestamp mới hơn (`0.8f > 0.2f`).
  - Progress bằng nhau (`0.5f == 0.5f`) -> timestamp mới hơn thắng.
- `ArticleReadPositionDaoTest` (3 Robolectric tests):
  - Upsert lưu và thay thế vị trí đọc mới nhất.
  - Delete xóa vị trí đọc.
  - Cascade delete: khi Feed/Article bị xóa, bản ghi `article_read_position` tự động bị xóa.
- `RoomMigration14To15Test` (1 Robolectric test):
  - Di chuyển schema từ v14 lên v15, tạo bảng `article_read_position` và các index unique/foreign keys.
- `ReadPositionSyncPrefTest` (5 unit tests):
  - Default = ON, đọc ghi giá trị explicit, fallback an toàn khi key thiếu, toán tử NOT đảo chiều, mapping `Settings`.
- `ReadingStylePageWidgetTest` (1 Android instrumentation test):
  - Render màn hình Reading Style cài đặt có switch đồng bộ vị trí đọc.
- `ReadingViewModelPositionTest` (4 unit tests):
  - `initData` tự động load vị trí đọc đã lưu.
  - Bỏ qua progress < 0.05f và > 0.95f (tránh ép cuộn ở đầu/cuối bài).
  - Merge vị trí đọc với bản ghi hiện có qua `ReadPositionConflictResolver`.
  - Lưu tiến độ hợp lệ vào Room DB.
- `WebDavReadPositionSyncerTest` (3 unit tests):
  - Merge local và remote records không làm mất dữ liệu.
  - Ưu tiên tiến độ xa hơn và tie-breaker timestamp.
  - Serialize/deserialize DTO JSON chuẩn hóa độc lập với `articleId`/`accountId` của từng máy.
- `WebDavReadPositionSyncNetworkTest` (4 Robolectric tests):
  - MockWebServer GET 404 (file chưa có) -> PUT danh sách local, đánh dấu synced.
  - MockWebServer GET remote tiến độ xa hơn -> merge ghi đè local Room DB.
  - MockWebServer GET remote tiến độ thấp hơn -> local tiến độ xa hơn thắng và PUT lên WebDAV.
  - MockWebServer HTTP 500 -> fail graceful, giữ nguyên trạng thái `isSynced = false` trên local.

## Verification Summary

- Unit tests: **856 / 856 passing** (+26 tests mới cho INGEST-04).
- Lint: `lintDevDebug` PASS (0 errors).
- Build: `assembleDevDebug` PASS.
- Live smoke test trên `Pixel_10_Pro` (`emulator-5554`):
  - Mở bài báo "Why Platformer is leaving Substack", cuộn xuống giữa bài (~23%).
  - Kiểm tra Room DB SQLite trực tiếp: bản ghi `article_read_position` ghi nhận `progress = 0.230769`, `feedUrl`, `articleLink`.
  - Thoát ra danh sách feed, mở lại đúng bài đó: Màn hình **tự động cuộn qua tiêu đề và cover image**, hiển thị ngay đúng đoạn văn đang đọc dở.
  - Xử lý ad interstitial AdMob an toàn theo đúng R4.

## Audit Rubric (9.6 / 10)

- Correctness / Root cause: `3.0 / 3.0` — Giải quyết triệt để vấn đề mất vị trí đọc, định danh bằng `(feedUrl, articleLink)` đảm bảo đồng bộ cross-device không bị lệch ID giữa các máy.
- Test quality & RED/GREEN discipline: `2.0 / 2.0`
- Integration, UI & device evidence: `2.0 / 2.0` — Chụp ảnh bằng chứng trước/sau khi cuộn và khôi phục, kiểm tra DB binary bằng sqlite3.
- Regression & scope containment: `1.0 / 1.0`
- Maintainability & code standards: `1.0 / 1.0` — Không magic numbers, null-safe, full 37 locales parity.
- Verification & reproducibility: `0.6 / 1.0`
