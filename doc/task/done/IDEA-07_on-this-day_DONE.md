# [IDEA-07] On This Day — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed.
- **Completed:** 2026-10-18
- **Commits:**
  - `8c13afed` — docs: add IDEA-07 On This Day implementation plan
  - `6d0328b9` — feat(domain): add OnThisDay calendar helper and Room query
  - `d6293355` — feat(ui): add OnThisDayViewModel and state machine
  - `45d838ee` — feat(i18n): localize On This Day across all 38 maintained locales
  - `76746374` — feat(ui): add On This Day card to FeedsPage and entry to FlowPage menu
  - `4efd28dd` — feat(ui): add dedicated OnThisDayPage and navigation route
  - `54a3b568` — test(onthisday): add edge-to-edge instrumentation test

## Implementation

- `OnThisDayCalendar`: domain helper chuyển đổi `Clock` và `ZoneId` thành `monthDay` dạng `MM-dd` (zero-padded) và `currentYear` (4 chữ số). Có test leap day 29/02 và biên nửa đêm giữa các múi giờ.
- `ArticleDao.queryOnThisDay`: truy vấn Room read-only, khớp `accountId`, `isUnread = 0`, SQLite `strftime('%m-%d', date / 1000, 'unixepoch', 'localtime') = :monthDay`, `strftime('%Y', ...) < :currentYear`, sắp xếp `date DESC, id DESC`, giới hạn `limit = 5`. Không migration, không schema change.
- `OnThisDayViewModel`: Hilt ViewModel chạy truy vấn trên `@IODispatcher`, lấy account từ `Context.currentAccountId` (default 1, không ép kiểu null-unsafe), phát ra `OnThisDayUiState` (`Loading`, `Empty`, `Content(articles, monthDay, currentYear)`, `Error(messageRes)`).
- Đa ngôn ngữ: bổ sung 4 string keys và 1 plurals (`on_this_day_years_ago` với quantity `one` và `other`, có các nhánh `zero/two/few/many` cho tiếng Ả Rập/Nga/Ba Lan/v.v.) trên toàn bộ 38 thư mục `values*/strings.xml`. `StringResourceParityTest` 6/6 PASS, `processDevDebugResources` PASS.
- `OnThisDayCard`: card compact hiển thị tiêu đề, mô tả và tối đa 5 hàng bài viết với badge năm (dùng plurals). Gắn vào `FeedsPage` sau banner, trước subtitle "Feeds"; tự ẩn khi `Loading`, `Empty`, `Error` hoặc khi ở tab AddSources.
- `OnThisDayPage` + route `RouteName.ON_THIS_DAY`: màn riêng đăng ký trong `HomeEntry` qua `animatedComposable(eInk = eInk)`. Hỗ trợ empty state, error state kèm nút retry (thực thi `viewModel.refresh()`). Quản lý status-bar và navigation-bar insets tường minh. Truy cập từ menu 3 chấm overflow của `FlowPage`.

## Verification

- `./gradlew testDevDebugUnitTest`: **958 tests, 0 failures, 0 errors, 0 skipped** (21 tests mới cho calendar, DAO, ViewModel, plurals, card widget và page).
- `./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin`: **PASS**.
- `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`: **6/6 PASS**.
- `emulator-5554`, runner cô lập `EInkUiTestRunner`:
  - `OnThisDayEdgeToEdgeTest`: **1/1 PASS** (tiêu đề nằm dưới status bar inset `titleTop >= statusInsetTop`, empty text nằm trên navigation bar `emptyBottom <= decorHeight - navInsetBottom`).
- Live smoke test trên `emulator-5554` (kiểm chứng bằng screenshot adb thực tế, dừng khi gặp ad theo rule R4):
  - Khởi động app: FeedsPage không có bài năm trước → card ẩn hoàn toàn (đúng spec).
  - Mở FlowPage All → mở menu 3 chấm overflow → mục "On This Day" hiển thị kèm icon History.
  - Chạm "On This Day" → mở `OnThisDayPage`, hiển thị text empty state đúng chuỗi tài nguyên, tiêu đề nằm dưới status bar.
  - Chèn 2 bài mẫu ngày 18/10 các năm 2025 và 2023 vào DB thiết bị → mở lại app → FeedsPage hiển thị card với 2 hàng: "1 year ago" và "3 years ago".
  - Chạm header card → mở màn đầy đủ danh sách 2 bài.
  - Chạm bài đầu → điều hướng mở đúng bài trong Reader.
  - Reader hiển thị test ad banner ở đáy (không che UI), người dùng xác nhận "done, ad banner thì cứ skip thôi".
  - Khôi phục DB emulator về trạng thái sạch (xoá bài mẫu), xác nhận card tự ẩn lại và All còn 21 bài gốc.
- Tuân thủ quy tắc thiết bị: toàn bộ thao tác cài đặt, instrumentation và smoke test chỉ gửi tới `emulator-5554`. Không chạy lệnh `connectedDevDebugAndroidTest` của Gradle để tránh rò sang thiết bị vật lý.

## Limits and known issues

- Không bổ sung timestamp `readAt` hay migration Room; định nghĩa bài cũ dựa trên `date` xuất bản và cờ `isUnread = 0`.
- Ngày 29/02 chỉ khớp vào các năm nhuận; không tự động chiếu sang 28/02 hay 01/03 ở năm thường (theo đúng spec đã duyệt).
- Không có cấu hình tắt card trong Settings (YAGNI, P3).
- Lint chưa chạy vì lỗi metadata SDK 37 có sẵn của môi trường máy chủ.
