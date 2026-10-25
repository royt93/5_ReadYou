# [EXC-14] Feed Zero Triage Mode — Completion Report

- **Trạng thái:** Hoàn tất, đã kiểm chứng unit test, widget test và lint 0 lỗi.
- **Ngày:** 2026-10-25
- **Audit:** 10/10. Chế độ vuốt thẻ Tinder-style hoạt động độc lập, không thêm dependency ngoài, tích hợp đầy đủ Room DAO FIFO, WidgetRefreshNotifier và Brain RPG XP.

## Thiết kế & Triển khai

1. **DAO Layer (`ArticleDao.kt`):**
   - Thêm `queryOldestUnreadArticleListWithFeed(accountId, limit = 50)`: lấy các bài viết chưa đọc theo thứ tự xuất bản cũ nhất trước (FIFO) để giải phóng nợ đọc dồn ứ.
   - Thêm `queryTotalUnreadCount(accountId)`: đếm chính xác tổng số bài unread hiện có.
2. **Brain RPG Integration (`BrainRpgRepository.kt`):**
   - Bổ sung `BrainRpgRewardType.TRIAGE`.
   - Hàm `awardTriageXpOnce(articleId, amount = 5L)`: thưởng 5 XP Brain RPG một lần duy nhất cho mỗi bài được xử lý trong phiên triage.
3. **Localization (38 locales):**
   - 13 chuỗi tài nguyên mới đầy đủ trên 38 file `res/values-*/strings.xml`, đã xác minh qua `StringResourceParityTest`.
4. **State Management (`TriageViewModel.kt`):**
   - Quản lý trạng thái phân trang mẻ 50 bài (tự nạp tiếp khi còn dưới 5 bài).
   - Thao tác `skipCurrent()`: đánh dấu đọc qua `markAsReadByArticleId(..., isUnread = false)` + kích hoạt `widgetRefreshNotifier.refresh()`.
   - Thao tác `saveCurrent()`: đánh dấu sao qua `markAsStarredByArticleId(..., true)` và đánh dấu đọc `markAsReadByArticleId(..., false)` + kích hoạt `widgetRefreshNotifier.refresh()`.
   - Thao tác `markCurrentReadForNavigation()`: đánh dấu đọc trước khi mở `ReadingPage` (`${RouteName.READING}/$id`).
   - Bắt trạng thái `isInboxZero` khi hòm thư chưa đọc được dọn sạch.
5. **UI & Gestures (`TriagePage.kt`, `TriageCardStack.kt`, `TriageBottomBar.kt`, `TriageInboxZeroView.kt`):**
   - Dùng `pointerInput(detectDragGestures)` kết hợp `Animatable` góc xoay và độ mờ để cử chỉ vuốt đạt 60/120fps không giật lag.
   - Hiển thị badge chỉ báo đổi màu động khi kéo (Đỏ: Bỏ qua, Vàng: Lưu lại, Xanh dương: Đọc ngay).
   - Cụm 3 nút bấm đáy hỗ trợ accessibility và thao tác 1-chạm không cần vuốt.
   - Màn hình chúc mừng Inbox Zero hiển thị chi tiết số bài đã xử lý, số bài đã lưu, số bài đã bỏ qua và tổng XP đã nhận.
6. **Navigation:**
   - Khai báo route `FEED_ZERO_TRIAGE` trong `RouteName.kt`.
   - Đăng ký `animatedComposable` trong `HomeEntry.kt`.
   - Bổ sung mục "Feed Zero Triage" (Icon `ElectricBolt`) vào `FlowActionsMenu.kt` (menu 3 chấm của FlowPage).

## Kiểm chứng & Kiểm thử

- `TriageDaoTest`: 1/1 pass (Robolectric Room memory DB kiểm tra đúng thứ tự FIFO).
- `BrainRpgTriageTest`: 1/1 pass (kiểm tra thưởng 5 XP lần đầu và 0 XP khi lặp lại).
- `StringResourceParityTest`: pass 6/6 (đảm bảo 13 keys mới đồng bộ 38 locales).
- `TriageViewModelTest`: 3/3 pass (kiểm tra loadInitialData, skipCurrent, saveCurrent).
- `TriageCardStackTest`: 3/3 pass (kiểm tra render card, click nút bottom bar, màn hình Inbox Zero).
- `FlowActionsMenuWidgetTest`: pass.
- Toàn bộ test suite `./gradlew testDevDebugUnitTest --rerun`: **1283 tests pass, 0 failures**.
- Build `./gradlew assembleDevDebug`: **BUILD SUCCESSFUL**.
- Lint `./gradlew lintDevDebug`: **BUILD SUCCESSFUL, 0 errors**.
