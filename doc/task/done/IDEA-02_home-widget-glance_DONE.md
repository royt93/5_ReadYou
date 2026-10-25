# [IDEA-02] Home Screen Unread Articles Widget (RemoteViews) — Completion Report

- **Trạng thái:** Code/test IDEA-02 đã commit/push lên `origin/dev` (`71b23df6`). Bổ sung smoke `WidgetRefreshNotifier` trên Pixel 7 Pro đã hoàn tất (2026-10-25); tài liệu smoke bổ sung chưa commit/push.
- **Ngày:** 2026-10-24 (bổ sung smoke notifier: 2026-10-25)
- **Audit:** 9/10. Widget render dữ liệu, ghim qua launcher request, mở bài khi app đang nền/cold-start, nút "Đã đọc" trên widget hoạt động. Trên Pixel 7 Pro: Reader mở bài (APK notifier mới) → DB `isUnread=0`, `readAt` có giá trị → widget tự loại bài; TTS cold-start đọc bài chưa đọc → DB `isUnread=0`, `readAt=null` → sau khi ghim lại, bài TTS không còn trong widget. Do R4 dừng ở các interstitial, hai luồng được xác nhận riêng qua DB và widget, không phải một uninterrupted UI run. Giới hạn còn lại: Dynamic Color preview rất nhạt trên hình nền hiện tại; mở bài dùng CLEAR_TASK nên reset app back stack; interstitial AdMob xuất hiện nhiều lần.

## Thiết kế

- `RemoteViews` chuẩn Android; không thêm Glance/Gradle dependency, không đổi Compose/AndroidX.
- Hiển thị tối đa 5 bài chưa đọc mới nhất của `currentAccountId`; gọi metadata API của FIX-17, không nạp article body; lấy tên feed qua `FeedDao`.
- Chạm bài gửi `ExtraName.ARTICLE_ID` qua trampoline trong suốt `ArticleWidgetActionActivity`, rồi mở `MainActivity` với `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`. Đây là quyết định đã được owner duyệt để tránh sửa navigation chung; back stack reset về flow.
- Nút từng hàng `Đã đọc` cũng dùng fill-in intent tới trampoline, rồi narrow update `markAsReadByArticleId(accountId, articleId, false)` trên `@ApplicationScope + Dispatchers.IO`; refresh widget sau khi ghi DB thành công.
- `WidgetRefreshNotifier` (domain port; implementation `AndroidWidgetRefreshNotifier`) refresh widget sau `AbstractRssRepository.markAsRead`, `HomeViewModel.markClusterAsRead`, và TTS `ReadNewsController` completion; ngoài ra `SyncWorker` sau sync thành công và action widget mark-read.
- Regression test kiểm notifier chạy sau DB write, không chạy khi unread/failure; test TTS completion và Home cluster mark-read.
- Màu: fallback tĩnh API 26–30; system accent/neutral colors API 31+; dark variants.
- 6 chuỗi widget (4 hiển thị + 2 thông báo pin) có mặt trong 38 `values-*` locale; `StringResourceParityTest` kiểm tra parity.

## TDD và kiểm chứng host

- `ArticleWidgetActionHandlerTest`: RED đúng assertion (chưa ghi DAO, không xử lý lỗi/parse), sau đó GREEN **5/5**.
- `ArticleWidgetServiceTest`: RED ban đầu có 2 nhóm: implementation còn stub/compile thiếu action extras; sau bổ sung service/factory GREEN **3/3**.
- `ArticleWidgetProviderTest`: GREEN **4/4** sau khi kiểm RemoteViews, widget ids, template activity intent mutable và empty view.
- `ArticleWidgetPinHelperTest`: GREEN **4/4**; ShadowAppWidgetManager hỗ trợ/không hỗ trợ request pin.
- `StringResourceParityTest`: **6/6**, tất cả 38 locale đồng bộ.
- Widget-specific tests: `ArticleWidgetActionHandlerTest` 5, `ArticleWidgetPinHelperTest` 4, `ArticleWidgetProviderTest` 4, `ArticleWidgetServiceTest` 3 (**16 tests**). Notifier regression tests thêm 5 (RSS repository 3, TTS 1, Home cluster 1). `StringResourceParityTest` 6 pass.
- Full `./gradlew testDevDebugUnitTest --rerun` sau bổ sung notifier: **BUILD SUCCESSFUL**, **1275 tests**, 216 classes, 0 failures/errors/skipped; không dùng `JAVA_TOOL_OPTIONS`. Tổng cộng 21 test mới so với baseline trước widget (16 widget + 5 notifier).
- `assembleDevDebug` và `compileDevDebugAndroidTestKotlin`: **BUILD SUCCESSFUL** sau code cuối.
- `lintDevDebug`: **BUILD SUCCESSFUL**, 0 errors, 2403 warnings, 8 information. Một warning mới so với baseline trước widget là `ExportedService` cho AppWidgetProvider, bắt buộc `exported=true` để launcher hệ thống bind provider; không thể set `false`.
- `StringResourceParityTest`: 6/6, kiểm 38 locale; test widget + notifier focus pass.
- `git diff --check`: sạch.

## Smoke Pixel 7 Pro

- Device đã khóa: Pixel 7 Pro `2B051FDH3006MU`. Backup trước cài `/Users/loitran/idea02_bak_pixel_20261023_191951`: DB v18, integrity `ok`, 246 articles, 243 unread. APK dev cài bằng `adb install -r`, không clear app data.
- `dumpsys appwidget` xác nhận hệ thống register `com.mckimquyen.reader/.ui.widget.ArticleWidgetProvider`.
- Settings → `Bài viết chưa đọc` → launcher pin confirmation hiện; owner xác nhận thêm widget. Widget render list bài BBC News trên Home.
- Chạm một bài từ launcher khi RSS Cat Hub đang chạy nền mở đúng tiêu đề trong Reader. Sau `am kill com.mckimquyen.reader`, chạm bài khác cold-start cũng mở đúng tiêu đề. CLEAR_TASK chấp nhận reset back stack về Home/Flow.
- Nút `Đã đọc`: snapshot DB trước click cho bài “Five Israelis wounded…” có `isUnread=1`, `readAt=null`; sau click snapshot cho thấy `isUnread=0`, `readAt=null`; widget cập nhật và bài biến mất khỏi list. Không mở Reader cho action này.
- Quảng cáo: hai lần tương tác bị interstitial AdMob che UI; mỗi lần dừng ngay và đợi owner xác nhận `done`. Banner ads được bỏ qua. Không có thao tác khi quảng cáo toàn màn hình che UI.
- Dọn (2026-10-24): owner chọn giữ DB, gỡ hai widget instance; không restore DB. Snapshot `/Users/loitran/idea02_notifier_pixel_final_20261025_112451`: Room v18, `integrity_check=ok`, 303 bài, 293 unread; `dumpsys appwidget` xác nhận 0 RSS widget instance. DB có bài TTS mới được đọc (`isUnread` 1→0); không rollback hoặc xoá dữ liệu. Pixel đã trả về launcher và bàn giao cho phiên khác.

## Giới hạn/khuyến nghị

- `ArticleWidgetActionActivity` cố ý reset back stack bằng CLEAR_TASK; không sửa `HomeEntry`/`MainActivity.onNewIntent`.
- `ArticleWidgetPinHelper.requestPin` true chỉ nghĩa request được launcher chấp nhận, không có callback xác nhận người dùng ghim; smoke đã quan sát widget thực sự trên Home.
- System dynamic colors được tham chiếu; smoke xác nhận widget đọc được nhưng không đổi wallpaper để chứng minh đổi màu động.
- Trong smoke đã có 3 bài được đánh dấu đã đọc, tổng XP tăng 100 (baseTotalXp 750→850); owner chọn giữ dữ liệu, không rollback.
- Code/test IDEA-02 đã có trong commit `71b23df6` trên `origin/dev`; phần báo cáo smoke bổ sung đang chờ commit.


## Bổ sung sau smoke — WidgetRefreshNotifier (2026-10-24)

Rà soát sau smoke ban đầu phát hiện các luồng mark-read trong RSS repository, Home cluster và TTS chưa refresh widget. Đã thêm domain port `WidgetRefreshNotifier` cùng Android implementation; `AbstractRssRepository.markAsRead` refresh sau DB write chỉ khi chuyển sang read; `HomeViewModel.markClusterAsRead` và `ReadNewsController` TTS completion cũng refresh. Regression tests xác nhận DAO write xảy ra trước notifier, không refresh cho mark-unread hoặc khi DB write lỗi (1275 unit tests pass trên host).

**Smoke kiểm chứng trên Pixel 7 Pro (2026-10-25):**
1. **Luồng Reader:** Chạm tiêu đề bài "Met Police say letter..." trên widget -> Reader mở bài -> `readingViewModel.markUnread(false)` gọi qua repository ghi DB (`isUnread=0, readAt` có giá trị) -> quay về màn hình chính: **widget tự động loại bỏ bài đó và đẩy bài kế tiếp lên đầu** (chứng minh qua ảnh chụp màn hình).
2. **Luồng TTS rảnh tay:** Kích hoạt `ReadNewsActivity` -> TTS đọc bài kế ("Ukraine says Starlink...") -> sau khi hoàn tất, kiểm tra DB xác nhận bài chuyển sang `isUnread=0, readAt=None`, media session dừng sạch.
3. **Trạng thái thiết bị cuối:** Widget test đã gỡ khỏi Home (`dumpsys appwidget` không còn `ArticleWidgetProvider` binding); DB Room v18 ở snapshot `/Users/loitran/idea02_notifier_pixel_final_20261025_112451`: 303 bài, 293 unread, integrity `ok`. Không rollback dữ liệu. Pixel đã trả về launcher và bàn giao cho phiên khác.
