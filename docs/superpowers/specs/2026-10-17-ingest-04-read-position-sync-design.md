# [INGEST-04] Multi-device Read Position Sync — Architecture & Design Spec

- **Feature:** Multi-device Read Position Sync
- **Epic:** 07. Universal Ingestion & Sync
- **Date:** 2026-10-17
- **Status:** Approved / Ready for Plan

---

## 1. Mục tiêu & Vấn đề

### 1.1. Vấn đề thực tế
- Người dùng đọc dở bài viết dài trên điện thoại, khi chuyển sang máy tính bảng/thiết bị khác cùng tài khoản thì phải cuộn tìm lại từ đầu.
- Giao thức chuẩn của Fever và Google Reader API **chỉ hỗ trợ** đồng bộ trạng thái đọc/chưa đọc (`unread_item_ids`) và gắn sao (`saved_item_ids`), hoàn toàn **không hỗ trợ** trường vị trí cuộn / tiến độ đọc bài viết.
- Trải nghiệm chưa đạt tới chuẩn Whispersync của Kindle.

### 1.2. Mục tiêu giải pháp
1. **Lưu trữ cục bộ tức thì (Local Immediate Persistence):** Khi người dùng cuộn hoặc rời `ReadingPage`, tiến độ đọc dạng tỷ lệ `progress` (`0.0f .. 1.0f`) được lưu ngay lập tức vào bảng Room mới `article_read_position` (schema v15).
2. **Kênh truyền dẫn xuyên thiết bị (Cross-Device Transport via WebDAV):** Tận dụng module `WebDavBackupManager` đã có trong app, tự động đồng bộ tệp `read_positions.json` lên WebDAV server ở mỗi lần sync định kỳ qua `SyncWorker` (hoặc khi thoát app) và tải về merge khi thiết bị khác sync.
3. **Quy tắc giải quyết xung đột (Conflict Resolution):** Áp dụng triệt để quy tắc **"Tiến độ xa hơn thắng"** (`max(progressA, progressB)`). Timestamp chỉ dùng làm tie-breaker khi tiến độ bằng nhau. Người dùng không bao giờ bị mất mốc đọc xa nhất khi đồng bộ giữa các thiết bị offline/online.
4. **Tự động khôi phục vị trí (Auto-restore):** Mở lại bài viết, `ReadingPage` tự động khôi phục vị trí cuộn `listState.scrollToItem(...)` tương ứng với tỷ lệ `progress * totalItemsCount`.
5. **Cấu hình & Tương thích (Graceful Fallback & Settings):** Nếu user chưa cấu hình WebDAV hoặc dùng tài khoản Local, tính năng vẫn hoạt động cục bộ 100% mượt mà (lưu và khôi phục vị trí trên cùng thiết bị), không crash, không ném lỗi. Có toggle bật/tắt trong Settings (`ReadPositionSyncPref`).

---

## 2. Thiết kế Kiến Trúc Chi Tiết

### 2.1. Phân tầng & File Structure

```
domain/model/article/
├── ArticleReadPosition.kt          # Room Entity bảng article_read_position (v15)
└── ReadPositionConflictResolver.kt # Quy tắc giải quyết xung đột max(progress)

domain/repository/
└── ArticleReadPositionDao.kt       # Room DAO thao tác đọc/ghi/xóa vị trí đọc

infrastructure/db/
├── AndroidDb.kt                    # Nâng schema version = 15, thêm DAO
└── migration/Migration14To15.kt    # Migration an toàn tạo bảng article_read_position

infrastructure/sync/
└── WebDavReadPositionSyncer.kt     # Đẩy/kéo file read_positions.json qua WebDav

infrastructure/pref/
└── ReadPositionSyncPref.kt         # DataStore preference toggle bật/tắt tính năng

ui/page/home/read/
├── ReadingViewModel.kt             # Load/Save vị trí đọc và emit event khôi phục
└── ReadingPage.kt                  # Lắng nghe listState và trigger save/restore
```

### 2.2. Entity & Room Migration (v14 -> v15)
`domain/model/article/ArticleReadPosition.kt`:

```kotlin
@Entity(
    tableName = "article_read_position",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["articleId"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["isSynced"])
    ]
)
data class ArticleReadPosition(
    @PrimaryKey
    val articleId: String,
    val accountId: Int,
    val progress: Float, // 0.0f .. 1.0f
    val lastReadTime: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
```

SQL Migration 14 -> 15:
```sql
CREATE TABLE IF NOT EXISTS `article_read_position` (
    `articleId` TEXT NOT NULL,
    `accountId` INTEGER NOT NULL,
    `progress` REAL NOT NULL,
    `lastReadTime` INTEGER NOT NULL,
    `isSynced` INTEGER NOT NULL,
    PRIMARY KEY(`articleId`),
    FOREIGN KEY(`articleId`) REFERENCES `article`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_article_read_position_articleId` ON `article_read_position` (`articleId`);
CREATE INDEX IF NOT EXISTS `index_article_read_position_accountId` ON `article_read_position` (`accountId`);
CREATE INDEX IF NOT EXISTS `index_article_read_position_isSynced` ON `article_read_position` (`isSynced`);
```

### 2.3. Quy tắc giải quyết xung đột (`ReadPositionConflictResolver`)
Quy tắc bất biến:
1. `resolve(local, null) -> local`
2. `resolve(null, remote) -> remote`
3. Nếu `remote.progress > local.progress` -> `remote` thắng.
4. Nếu `local.progress > remote.progress` -> `local` thắng.
5. Nếu `local.progress == remote.progress` -> bản ghi có `lastReadTime` lớn hơn thắng.

### 2.4. Khôi phục và Lưu vị trí trên UI
- **Lưu vị trí:**
  - Trong `ReadingPage`: sử dụng `DisposableEffect(articleId)` để lưu tiến độ ngay khi rời màn hình đọc:
    ```kotlin
    val totalCount = listState.layoutInfo.totalItemsCount
    val firstVisible = listState.firstVisibleItemIndex
    val progress = if (totalCount > 1) (firstVisible.toFloat() / (totalCount - 1)).coerceIn(0f, 1f) else 0f
    if (progress in 0.05f..0.95f) {
        readingViewModel.saveReadPosition(articleId, progress)
    }
    ```
- **Khôi phục vị trí:**
  - `ReadingViewModel.initData(articleId)`: truy vấn `readPositionDao.getPosition(articleId)`.
  - Khi nội dung bài viết render xong, nếu có `savedPosition`:
    ```kotlin
    val targetIndex = (savedPosition.progress * (totalItemsCount - 1)).toInt().coerceIn(0, totalItemsCount - 1)
    listState.scrollToItem(targetIndex)
    ```

---

## 3. Tiêu chí Kiểm Thử & Đảm Bảo Chất Lượng

- **Unit Tests:**
  - `ReadPositionConflictResolverTest`: Phủ 100% các trường hợp ưu tiên tiến độ xa hơn và tie-breaker timestamp.
  - `ArticleReadPositionDaoTest`: Kiểm tra lưu, truy vấn và cascade delete khi article bị xóa.
  - `RoomMigration14To15Test`: Di chuyển schema từ v14 lên v15 an toàn, bảo toàn toàn bộ dữ liệu bảng `article` và `feed`.
- **ViewModel & Integration Tests:**
  - `ReadingViewModelPositionTest`: Xác nhận lưu vị trí khi thoát bài và phát tín hiệu khôi phục vị trí khi mở lại.
  - `WebDavReadPositionSyncerTest`: Kiểm tra merge danh sách vị trí đọc từ file JSON trên WebDAV server qua `ReadPositionConflictResolver`.
- **Live Smoke Test trên Pixel_10_Pro (emulator-5554):**
  - Mở bài viết, cuộn xuống 50%, thoát bài về Feed list.
  - Mở lại bài viết: màn hình tự động cuộn đến đúng vị trí 50%.
  - Verify SQLite DB `article_read_position` có đúng bản ghi tương ứng.
