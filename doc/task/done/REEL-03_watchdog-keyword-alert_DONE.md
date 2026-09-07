# [REEL-03] Chó Săn Cảnh Báo Từ Khóa Khẩn Cấp (Keyword Alert Watchdog) ✅ DONE

- **Type:** Automation / High-Priority Alert
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [09. REEL — Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)](../todo/09_VISUAL_REELS_AND_MEDIA.md)
- **Location:**
  - [`app/src/main/java/com/mckimquyen/reader/domain/watchdog/WatchdogEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/watchdog/WatchdogEngine.kt)
  - [`app/src/main/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManager.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManager.kt)
  - [`app/src/main/java/com/mckimquyen/reader/domain/model/watchdog/WatchdogKeyword.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/watchdog/WatchdogKeyword.kt)
  - [`app/src/main/java/com/mckimquyen/reader/ui/component/watchdog/WatchdogSheet.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/component/watchdog/WatchdogSheet.kt)
  - [`app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt)
  - [`app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt#L92)

## Vấn đề thực tế
Nhà đầu tư, chuyên gia hoặc người dùng quan tâm đặc biệt đến các sự kiện nhạy cảm (ví dụ: mã cổ phiếu `$VIC`, `$FPT`, `Bitcoin`, tên công ty đối thủ, bão lũ, biến động giá vàng). Họ không thể chờ đến lúc rảnh mới mở app mà cần được báo ngay khi có tin liên quan.

## User Story
> Là nhà đầu tư chứng khoán hoặc chuyên gia theo dõi thị trường,
> Tôi muốn đặt từ khóa cảnh báo ưu tiên cao (ví dụ "Lãi suất", "Nghị định 10"),
> Để ngay khi bất kỳ báo nào đăng bài chứa từ khóa này, điện thoại sẽ phát chuông báo động khẩn cấp.

## Acceptance Criteria (Gherkin)
- **Given** mục "Cảnh Báo Từ Khóa" (Watchdog)
- **When** người dùng nhập từ khóa: ví dụ `VN-Index`, `Bitcoin`, `Giá vàng`
- **Then** trong chu kỳ sync, nếu phát hiện bài viết mới chứa từ khóa này
- **And** hệ thống phát thông báo mức độ ưu tiên cao (`NotificationManager.IMPORTANCE_HIGH`) kèm âm thanh cảnh báo riêng biệt
- **And** gắn cờ đỏ nổi bật trên thẻ bài viết trong app.

---

## ✅ Completion Report

**Trạng thái:** Đã audit code thực tế (đọc toàn bộ file liên quan, không suy đoán) — xác nhận task đã **triển khai đầy đủ và đã commit vào `dev`**.

**Commit liên quan:** `f366bb9` — `feat(watchdog): implement Keyword Alert Watchdog with background sync alerts, M3 sheet, emergency badges and full test suite (REEL-03)`. Xác nhận qua `git log --oneline` và `git status` (working tree hiện sạch cho toàn bộ các file watchdog, không còn thay đổi chưa commit).

### Đã làm gì (đối chiếu Acceptance Criteria)

1. **Matching engine** — `domain/watchdog/WatchdogEngine.kt`: hỗ trợ khớp mã ticker dạng `$VIC`/`$FPT` (regex biên từ + có/không dấu `$`), khớp cụm từ tiếng Việt có dấu (case-insensitive), khớp biên từ cho từ ngắn (≤4 ký tự) để tránh false-positive (vd. "vàng" không khớp nhầm trong "hoangvàng"), quét cả `title`, `shortDescription` và `fullContent`.
2. **Persistence & state** — `infrastructure/watchdog/WatchdogManager.kt`: CRUD từ khóa (add/remove/toggle/incrementMatchCount) lưu qua `SharedPreferences` dạng JSON, expose `StateFlow<List<WatchdogKeyword>>` cho UI quan sát reactive.
3. **Notification ưu tiên cao** — `infrastructure/android/NotificationHelper.kt`: có channel riêng `WATCHDOG_CHANNEL_ID` cấu hình `IMPORTANCE_HIGH` + rung (`shouldVibrate()` — verify bởi `WatchdogIntegrationTest.watchdog_notificationChannelCreated_withHighImportance`), hàm `notifyWatchdogAlert(article, keyword, feedName)`.
4. **Wiring vào chu kỳ sync** — `domain/sv/AbstractRssRepository.kt:92` gọi `watchdogManager.checkAndNotify(insertedArticles, it.feed)` ngay sau khi insert bài viết mới từ sync; cả `LocalRssSv` và `FeverRssSv` đều được Hilt inject `WatchdogManager` qua constructor — đúng nguyên tắc "implement against `AbstractRssRepository`, không gọi thẳng provider" trong CLAUDE.md.
5. **UI — cờ đỏ trên thẻ bài viết**: `ui/page/home/flow/ArticleItem.kt` import `WatchdogBadge` + `LocalWatchdogKeywords`, gọi `WatchdogEngine.matchArticle(...)` để tô badge cảnh báo trực tiếp trên từng article card trong danh sách (đúng yêu cầu "gắn cờ đỏ nổi bật trên thẻ bài viết").
6. **UI — quản lý từ khóa**: `ui/component/watchdog/WatchdogSheet.kt` (bottom sheet M3) + `HomeViewModel` (`openWatchdogSheet`/`closeWatchdogSheet`/`addWatchdogKeyword`/`removeWatchdogKeyword`/`toggleWatchdogKeyword`) + `FlowPage.kt` (entry point mở sheet, `CompositionLocalProvider(LocalWatchdogKeywords ...)` để truyền danh sách xuống toàn bộ list bài viết).

### Test đã có

- **Unit test:**
  - `app/src/test/java/com/mckimquyen/reader/domain/watchdog/WatchdogEngineTest.kt` — 9 test case: khớp ticker `$`, khớp biên từ ngắn, khớp cụm dài, khớp title/description/content, bỏ qua keyword disabled, không khớp, danh sách rỗng.
  - `app/src/test/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManagerTest.kt` — 8 test case (Robolectric): add/remove/toggle/increment, trùng lặp case-insensitive, blank, **persistence qua nhiều instance** (`persistence_reloadsCorrectlyAcrossInstances`), `checkAndNotify` chỉ bắn đúng bài khớp và bỏ qua keyword tắt.
- **Instrumented/Compose UI test:**
  - `app/src/androidTest/java/com/mckimquyen/reader/ui/component/watchdog/WatchdogSheetWidgetTest.kt` — 5 test: render badge, render sheet có/không keyword, callback toggle/remove, render dialog đầy đủ.
- **Integration test:**
  - `app/src/androidTest/java/com/mckimquyen/reader/integration/WatchdogIntegrationTest.kt` — verify notification channel `IMPORTANCE_HIGH` thật (không mock), và luồng end-to-end: thêm keyword → nhận batch bài viết từ "sync" → `checkAndNotify` → verify match-count tăng đúng → render UI (`WatchdogBadge` + `WatchdogSheet`) live bằng `ComposeView`.

### Chấm điểm khách quan: **8.5 / 10**

Lý do không cho điểm tuyệt đối (xem chi tiết gap ở dưới):
- Toàn bộ Acceptance Criteria gốc đã thỏa mãn (keyword UI, sync detection, `IMPORTANCE_HIGH` notification, badge đỏ trên article card), test coverage tốt ở cả 3 tầng (unit/widget/integration).
- Điểm bị trừ do lớp persistence (`WatchdogManager` lưu JSON qua `SharedPreferences.edit().apply()`) **chưa an toàn với truy cập đồng thời** và **nuốt lỗi parse JSON bằng cách xóa sạch dữ liệu** — xem task `[REEL-04]` mới tạo bên dưới. Đây là lỗi tiềm ẩn mất dữ liệu người dùng thực sự (không phải nitpick), nên không thể chấm >9.
- Entry point thực tế nằm ở `FlowPage` (nút mở sheet) thay vì trong trang Settings như mô tả gốc "mục Cảnh Báo Từ Khóa trong Settings" — đây là lệch nhỏ so với AC gốc về vị trí UI, không ảnh hưởng chức năng, không tính là blocker nhưng ghi nhận lại để tránh nhầm lẫn khi audit sau này.

### Gap còn lại (đã tách thành task riêng trong `doc/task/todo/`)

1. **`[REEL-04]` P1 — Watchdog persistence chưa atomic**: race condition đọc-sửa-ghi giữa UI thread và sync worker; lỗi parse JSON hiện tại xóa sạch toàn bộ state RAM thay vì giữ nguyên/khôi phục.
2. **`[REEL-05]` P2 — Watchdog matcher theo batch, tối ưu hiệu năng**: chưa pre-compile/normalize keyword, chưa giới hạn phần nội dung quét, mỗi `incrementMatchCount` là một lần ghi `SharedPreferences` riêng lẻ (I/O lặp lại không cần thiết khi có nhiều match trong 1 batch).
3. **`[REEL-06]` P2 — Watchdog Alert Inbox**: chưa có màn hình lịch sử cảnh báo (matched excerpt, đã đọc/chưa đọc, snooze, quiet-hours riêng theo từ khóa).
