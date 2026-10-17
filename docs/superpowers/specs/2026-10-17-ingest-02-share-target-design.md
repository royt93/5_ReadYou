# INGEST-02: Android Share Target — Đọc Sau (Read-Later) Design Spec

- **Task:** `[INGEST-02] Android Share Target — Đọc Sau (Read-Later) Từ Chrome & Mạng Xã Hội`
- **Epic:** `07. Universal Ingestion & Sync`
- **Date:** 2026-10-17
- **Status:** Draft / Proposed for Review

---

## 1. Mục tiêu & Bối cảnh

### 1.1 Vấn đề
Người dùng đọc bài trên trình duyệt ngoài (Chrome, Firefox) hoặc mạng xã hội (Twitter/X, Facebook, Threads) và muốn lưu lại để đọc sau trong RSS Cat Hub với giao diện đọc sạch (Readability), không quảng cáo, hỗ trợ đọc offline và tích hợp tính năng AI tóm tắt / Brain RPG sẵn có.

### 1.2 Quyết định cốt lõi (User Choices)
1. **Nơi hiển thị:** Lưu vào **System Feed "Đọc sau" (Read Later)** trong tab Feeds. Gán cờ `isReadLater = true` cho bài viết. Tận dụng 100% UI Feed hiện có mà không làm phức tạp FilterBar.
2. **Trải nghiệm tiếp nhận:** Mở **ModalBottomSheet xác nhận** trên nền trong suốt (`ShareTargetActivity`). Người dùng xem trước URL + Tiêu đề, có nút "Lưu vào Đọc sau" và "Hủy".
3. **Kiến trúc xử lý:** Phản hồi tức thì < 500ms bằng cách tạo record tạm trong Room và dispatch tác vụ tải toàn văn ngầm qua **`WorkManager` (`ReadLaterWorker`)**, tránh rủi ro process death khi Activity đóng ngay sau đó.

---

## 2. Luồng dữ liệu & Kiến trúc (Architecture & Data Flow)

```
[Chrome / App ngoài]
       │
       ▼ (ACTION_SEND, text/plain)
[ShareTargetActivity (Translucent)]
       │
       ▼ Trích xuất URL + Check SSRF
[ShareTargetBottomSheet]
   ├── [Hủy] ──► finish()
   └── [Lưu vào Đọc sau]
             │
             ├── 1. Đảm bảo System Feed 'Đọc sau' tồn tại (FeedDao)
             ├── 2. Tạo placeholder Article (isReadLater=true, isUnread=true)
             ├── 3. Enqueue OneTimeWorkRequest (ReadLaterWorker)
             ├── 4. Toast "Đã lưu vào Đọc sau"
             └── 5. finish()
                         │
                         ▼ (Background execution)
             [ReadLaterWorker]
                   ├── Fetch HTML qua OkHttpClient an toàn
                   ├── Trích xuất toàn văn (Readability4JExtended)
                   └── Cập nhật Article(fullContent, title, img, shortDescription)
```

### 2.1 Các Components chính

1. **`ShareTargetActivity`**:
   - Khai báo intent-filter `android.intent.action.SEND` với mimeType `text/plain`.
   - Theme `Theme.Reader.Transparent` (`windowIsTranslucent = true`, không thanh tiêu đề, nền mờ/trong suốt).
   - Thiết lập `excludeFromRecents = true`, `noHistory = true`.
   - Lấy `Intent.EXTRA_TEXT`, trích xuất link HTTP(S) đầu tiên.

2. **`UrlExtractor` & Security Validator**:
   - Regex tìm URL: `https?://[^\s]+`.
   - Validate URL: Chặn schema nguy hiểm (`file://`, `content://`, `javascript://`), chỉ nhận HTTP/HTTPS.
   - Chặn IP nội bộ / private IP nếu cần để phòng chống SSRF.

3. **`SystemReadLaterFeedManager`**:
   - Hằng số định danh: `Feed.READ_LATER_FEED_ID = "system_feed_read_later"`.
   - Kiểm tra `feedDao.queryById(READ_LATER_FEED_ID)`. Nếu chưa có:
     - Tạo `Feed` với `id = "system_feed_read_later"`, `name = context.getString(R.string.read_later_feed_title)`, `sourceType = "READ_LATER"`, `url = "https://local.rsscathub/read-later"`, `groupId = defaultGroupId`.
     - `feedDao.insert(feed)`.

4. **`ShareTargetBottomSheet` (Compose UI)**:
   - Hiển thị URL nguồn, favicon/icon đọc sau, trạng thái tải metadata.
   - Nút hành động:
     - "Hủy": Đóng Activity ngay lập tức.
     - "Lưu vào Đọc sau": Kích hoạt lưu, đóng sheet và báo Toast.

5. **`ReadLaterWorker` (`CoroutineWorker`)**:
   - Nhận InputData: `articleId`, `url`.
   - Thực thi trên `Dispatchers.IO`:
     - Tải và parse HTML bằng `RssHelper.parseFullContent(url, title, sourceType = Feed.WEB_SOURCE_TYPE)`.
     - Giới hạn 1 MiB, kiểm tra Content-Type HTML/XHTML, lọc NUL byte (tái sử dụng lá chắn bảo mật của INGEST-01).
     - Trích xuất tiêu đề nếu placeholder chưa có tiêu đề chuẩn.
     - Cập nhật bài viết trong Room: `articleDao.update(...)`.

---

## 3. Ràng buộc kỹ thuật & Xử lý ngoại lệ (Constraints & Edge Cases)

1. **Chống Process Death**:
   - Khi `ShareTargetActivity.finish()` được gọi, Android có thể dừng tiến trình nếu không có Activity nào khác đang foreground.
   - Sử dụng `WorkManager` với `OneTimeWorkRequestBuilder<ReadLaterWorker>()` đảm bảo tác vụ trích xuất toàn văn tiếp tục chạy bền bỉ dù app bị đóng.

2. **Trùng lặp bài viết (Deduplication)**:
   - Nếu URL đã tồn tại trong bảng `article`:
     - Cập nhật `isReadLater = true`, `isUnread = true`, dời ngày lên gần nhất.
     - Báo Toast "Đã cập nhật bài vào Đọc sau" thay vì tạo bản ghi rác.

3. **URL không hợp lệ hoặc không có kết nối mạng**:
   - Text share không chứa URL hợp lệ: Toast thông báo lỗi ("Không tìm thấy liên kết hợp lệ"), đóng Activity.
   - Ngoại tuyến (Offline): Vẫn lưu placeholder bài viết với URL và tiêu đề tạm, `ReadLaterWorker` đặt điều kiện `NetworkType.CONNECTED` để tự động tải toàn văn khi có mạng trở lại.

4. **Đa ngôn ngữ (Localization)**:
   - Hỗ trợ đầy đủ 6 ngôn ngữ: `en`, `vi`, `zh-rCN`, `ja`, `fr`, `de`.
   - String keys:
     - `read_later_feed_title`: "Đọc sau" / "Read Later" / "稍后阅读" / "後で読む" / "À lire plus tard" / "Später lesen"
     - `share_target_save_btn`: "Lưu vào Đọc sau" / "Save to Read Later"
     - `share_target_cancel_btn`: "Hủy" / "Cancel"
     - `share_target_saved_toast`: "Đã lưu vào Đọc sau" / "Saved to Read Later"
     - `share_target_invalid_url`: "Không tìm thấy liên kết hợp lệ" / "No valid URL found"

---

## 4. Kế hoạch kiểm thử (Testing Strategy)

1. **Unit Tests (`app/src/test/`)**:
   - `UrlExtractorTest`: Parse text từ Chrome, Twitter, link trần, text kèm link, link rác, link schema nguy hiểm.
   - `SystemReadLaterFeedManagerTest`: Khởi tạo feed hệ thống idempotent, không tạo trùng.
   - `ReadLaterWorkerTest`: MockWebServer kiểm tra tải toàn văn và cập nhật Room DB.

2. **Compose UI Widget Tests (`app/src/test/` hoặc `app/src/androidTest/`)**:
   - `ShareTargetBottomSheetTest`: Hiển thị đúng link, bấm Hủy gọi dismiss, bấm Lưu trigger callback.

3. **Android Instrumentation Tests (`app/src/androidTest/`)**:
   - Chạy trên `Pixel_10_Pro` (emulator-5554):
   - `ShareTargetIntegrationTest`: Bắn Intent `ACTION_SEND` vào `ShareTargetActivity`, kiểm tra Room lưu đúng `isReadLater = true` và `feedId = "system_feed_read_later"`.

4. **Live Smoke Test (Emulator-5554)**:
   - Dùng lệnh adb gửi intent share:
     `adb -s emulator-5554 shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://example.com/article"`
   - Quan sát BottomSheet hiển thị, bấm Lưu.
   - Mở app RSS Cat Hub, vào tab Feeds -> mở feed "Đọc sau", xác nhận bài viết xuất hiện đầy đủ nội dung.
