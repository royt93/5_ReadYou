# Thiết Kế: [EXC-14] Feed Zero Triage Mode — Chế Độ Vuốt Kiểu Tinder Xử Lý Bài Chưa Đọc

- **Mã tính năng:** EXC-14
- **Loại:** Exclusive Killer Feature / UX Productivity
- **Độ ưu tiên:** P2 (Medium)
- **Ngày lập:** 2026-10-25
- **Tác giả:** Claude Code & roy
- **Nhánh:** `dev`

---

## 1. Mục tiêu & Bối cảnh

### 1.1 Vấn đề thực tế
Người dùng theo dõi nhiều feed thường tích lũy hàng trăm bài chưa đọc, sinh ra "cảm giác nợ đọc" (inbox fatigue) và ngại mở app. Cơ chế cuộn danh sách dọc truyền thống trên `FlowPage` không hỗ trợ xử lý hàng loạt nhanh chóng.

### 1.2 Giải pháp
Cung cấp chế độ **Feed Zero Triage** với trải nghiệm vuốt thẻ bài 1 chạm kiểu Tinder:
- **Vuốt trái (hoặc nút Bỏ qua):** Đánh dấu bài là đã đọc (`isUnread = 0`), đẩy bài ra khỏi unread queue.
- **Vuốt phải (hoặc nút Lưu lại):** Đánh dấu sao (`isStarred = 1`) và đánh dấu đã đọc (`isUnread = 0`) để chuyển bài vào mục Starred đọc sau.
- **Vuốt lên (hoặc nút Đọc ngay):** Mở màn hình đọc bài `ReadingPage` cho bài viết hiện tại.
- **Nút điều khiển đáy:** 3 nút tròn lớn (Bỏ qua, Đọc ngay, Lưu lại) để người dùng có thể bấm trực tiếp thay vì vuốt.
- **Tiến độ & Chúc mừng:** Hiển thị bộ đếm số bài đã xử lý trên tổng số bài; khi xử lý hết hiển thị màn hình chúc mừng "Inbox Zero" kèm thống kê phiên và cộng XP Brain RPG.

---

## 2. Kiến trúc & Luồng dữ liệu

```
[User Interaction: Swipe / Button Click]
                 │
                 ▼
          [TriagePage (UI)]
                 │
                 ▼
         [TriageViewModel]
         ├── Nạp bài unread batch 50 FIFO từ ArticleDao
         ├── Quản lý card stack state (current, next)
         ├── Ghi Room DB (markAsRead / markAsStarred)
         ├── Kích hoạt WidgetRefreshNotifier.refresh()
         └── Thưởng 5 XP qua BrainRpgRepository (nếu khả dụng)
                 │
                 ▼
        [Room Database v18]
```

### 2.1 Truy vấn dữ liệu (ArticleDao)
Thêm truy vấn lấy danh sách bài unread theo thứ tự cũ nhất trước (FIFO) để giải phóng nợ đọc dồn ứ:
```kotlin
@Transaction
@Query("SELECT " + ARTICLE_LIST_COLUMNS + " FROM article WHERE accountId = :accountId AND isUnread = 1 ORDER BY date ASC LIMIT :limit")
suspend fun queryOldestUnreadArticleListWithFeed(accountId: Int, limit: Int = 50): List<ArticleListItemWithFeed>
```

### 2.2 Luồng xử lý hành vi
1. **Bỏ qua (Skip):**
   - Gọi `articleDao.markAsReadByArticleId(accountId, articleId, false)` trên `Dispatchers.IO`.
   - Gọi `widgetRefreshNotifier.refresh()`.
   - Ghi nhận 1 bài skipped, cộng 5 XP vào Brain RPG.
2. **Lưu lại (Save/Star):**
   - Gọi `articleDao.markAsStarredByArticleId(accountId, articleId, true)` trên `Dispatchers.IO`.
   - Gọi `articleDao.markAsReadByArticleId(accountId, articleId, false)` trên `Dispatchers.IO`.
   - Gọi `widgetRefreshNotifier.refresh()`.
   - Ghi nhận 1 bài saved, cộng 5 XP vào Brain RPG.
3. **Đọc ngay (Read now):**
   - Điều hướng `navController.navigate("${RouteName.READING}/$articleId")`.
   - Khi quay lại (Back), bài kế tiếp sẵn sàng.

---

## 3. Thiết kế Giao diện (Jetpack Compose)

### 3.1 Cấu trúc màn hình `TriagePage`
- **TopAppBar:**
  - Nút đóng (X) quay lại `FlowPage`.
  - Tiêu đề "Feed Zero Triage".
  - Thanh tiến độ `LinearProgressIndicator` kèm text tỉ lệ `processedCount / totalCount`.
- **Khu vực trung tâm (Card Stack):**
  - Sử dụng `Box` xếp chồng 2 thẻ:
    - Thẻ nền (`nextCard`): Thu nhỏ nhẹ `scale = 0.95f`, `alpha = 0.8f`.
    - Thẻ mặt (`currentCard`): Xử lý cử chỉ vuốt qua `Modifier.pointerInput(detectDragGestures)` kết hợp `Animatable` (offsetX, offsetY, rotationZ).
    - Góc xoay `rotationZ = (offsetX / screenWidth) * 20f` tạo cảm giác lướt tự nhiên.
    - Badges chỉ báo nổi lên khi kéo:
      - Kéo sang trái: Badge đỏ/cam "Bỏ qua".
      - Kéo sang phải: Badge xanh/vàng "Lưu lại".
      - Kéo lên trên: Badge xanh dương "Đọc ngay".
- **BottomBar (3 nút tròn):**
  - Trái: Nút đỏ/xám tròn (Icon `Close`) -> Kích hoạt animation vuốt trái -> Bỏ qua.
  - Giữa: Nút xanh dương tròn (Icon `ArrowUpward` / `MenuBook`) -> Vuốt lên -> Đọc ngay.
  - Phải: Nút vàng/primary tròn (Icon `Star`) -> Vuốt phải -> Lưu lại.
- **Màn hình chúc mừng Inbox Zero:**
  - Hiển thị khi `unreadCount == 0` hoặc đã xử lý sạch hàng đợi.
  - Icon cúp / pháo hoa ăn mừng, tiêu đề chúc mừng Inbox Zero.
  - Bảng tổng kết phiên: Tổng số bài đã xử lý, số bài lưu, số bài bỏ qua, tổng XP đã tích lũy.
  - Nút "Hoàn tất" quay về FlowPage.

---

## 4. Điều hướng & Entry Point

- Khai báo route mới trong `RouteName.kt`:
  ```kotlin
  const val FEED_ZERO_TRIAGE = "feed_zero_triage"
  ```
- Thêm màn hình vào `HomeEntry.kt` bên trong `NavHost`.
- Bổ sung mục "Feed Zero Triage" (Icon `ElectricBolt` / `FlashOn`) vào `FlowActionsMenu.kt` (menu 3 chấm của FlowPage).

---

## 5. Đa ngôn ngữ (Localization)

Thêm các chuỗi sau vào `strings.xml` và duy trì đầy đủ trên toàn bộ 38 locale:
1. `feed_zero_triage_title`: Tiêu đề trang ("Feed Zero Triage")
2. `triage_swipe_left_skip`: Chỉ báo vuốt trái ("Bỏ qua")
3. `triage_swipe_right_save`: Chỉ báo vuốt phải ("Lưu lại")
4. `triage_swipe_up_read`: Chỉ báo vuốt lên ("Đọc ngay")
5. `triage_progress_format`: Định dạng tiến độ ("%1$d / %2$d")
6. `triage_inbox_zero_title`: Tiêu đề chúc mừng ("🎉 Đạt Inbox Zero!")
7. `triage_inbox_zero_subtitle`: Phụ đề chúc mừng ("Bạn đã xử lý sạch hòm thư chưa đọc")
8. `triage_summary_processed`: Tổng xử lý ("Đã xử lý: %1$d bài")
9. `triage_summary_saved`: Đã lưu ("Đã lưu lại: %1$d bài")
10. `triage_summary_skipped`: Đã bỏ qua ("Đã bỏ qua: %1$d bài")
11. `triage_summary_xp`: XP tích lũy ("+%1$d XP Brain RPG")
12. `triage_finish_button`: Nút hoàn tất ("Hoàn tất")
13. `triage_empty_state`: Trạng thái rỗng ban đầu ("Không có bài viết chưa đọc nào cần xử lý")

---

## 6. Kế hoạch Kiểm thử & TDD

1. **Unit Tests (`TriageViewModelTest`):**
   - Nạp đúng 50 bài unread cũ nhất từ DAO khi khởi tạo.
   - Thao tác Skip gọi đúng `markAsReadByArticleId(..., isUnread = false)` và cập nhật bộ đếm.
   - Thao tác Save gọi đúng `markAsStarredByArticleId(..., true)` + `markAsReadByArticleId(..., false)`.
   - Xử lý hết danh sách -> `isInboxZero` chuyển sang `true`.
   - DAO rỗng ban đầu -> `isInboxZero` hoặc `isEmpty` chuyển sang `true` ngay lập tức.
   - Xử lý lỗi DAO an toàn không gây crash app.
2. **Compose UI Tests (`TriagePageTest`):**
   - Kiểm tra hiển thị TopBar, Card Stack và 3 nút hành động.
   - Mô phỏng click các nút Bỏ qua / Lưu lại / Đọc ngay.
   - Mô phỏng gesture swipe trái / phải / lên trên thẻ bài.
3. **Parity Tests (`StringResourceParityTest`):**
   - Đảm bảo 13 chuỗi mới có mặt đồng bộ ở 38 locale.
4. **Smoke Test trên thiết bị thật:**
   - Thực thi trên Pixel 7 Pro (serial `2B051FDH3006MU`).
   - Mở Feed Zero Triage từ menu FlowPage, vuốt thử bài, bấm thử nút, xác nhận cập nhật DB Room và widget.
