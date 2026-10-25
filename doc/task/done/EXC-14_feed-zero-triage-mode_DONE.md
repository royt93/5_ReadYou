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

## Smoke Test Thực Tế Trên Pixel 7 Pro (2026-10-25)

- **Thiết bị:** Pixel 7 Pro (serial `2B051FDH3006MU`).
- **Backup snapshot trước smoke:** `/Users/loitran/exc14_bak_pixel_20261025_130439` (Room v18, integrity `ok`, 303 bài, 293 chưa đọc).
- **Quy trình kiểm chứng:**
  1. Mở `FlowPage` -> mở menu 3 chấm -> hiển thị đúng mục "Feed Zero Triage" kèm icon `ElectricBolt`.
  2. Màn hình `Feed Zero Triage` khởi động mượt mà, render thẻ bài đầu tiên ("BBC News app"), thẻ kế tiếp nằm ẩn bên dưới với tỉ lệ thu nhỏ nhẹ, thanh tiến độ hiển thị "0 / 293".
  3. Bấm nút tròn đỏ "Bỏ qua" ở chân màn hình -> thẻ trượt đi, tiến độ cập nhật thành "1 / 293", bài kế tiếp ("Coming soon: Sir David...") xuất hiện.
  4. Bấm nút tròn vàng "Lưu lại" -> tiến độ cập nhật thành "2 / 293", bài kế tiếp ("Sign up now") xuất hiện.
  5. Vuốt cử chỉ sang trái trên thẻ bài -> thẻ trượt bay sang trái, tiến độ cập nhật thành "3 / 293".
  6. Vuốt cử chỉ sang phải trên thẻ bài -> thẻ trượt bay sang phải, tiến độ cập nhật thành "4 / 293".
  7. Bấm nút tròn xanh "Đọc ngay" ở giữa -> điều hướng mượt mà sang `ReadingPage` của đúng bài viết ("Brad Pitt stars as a tank commander..."); bấm Back quay lại thì tiến độ nhảy lên "5 / 293" và bài kế tiếp sẵn sàng.
  8. Thoát về màn hình chính, kiểm tra tab "Yêu thích" (Starred): hiển thị chính xác 2 bài vừa lưu ("Unravel the secrets..." và "Coming soon: Sir David Attenborough...").
  9. Kiểm tra cơ sở dữ liệu Room thật:
     - Số bài chưa đọc giảm từ 293 xuống đúng 288 bài.
     - Số bài đã gắn sao tăng từ 0 lên 2 bài.
     - Bảng `brain_rpg_reward` ghi nhận đúng 5 lượt triage reward (`rewardType = 'TRIAGE'`).
     - Tổng XP Brain RPG tăng thêm 175 XP (từ 850 lên 1025 XP).

