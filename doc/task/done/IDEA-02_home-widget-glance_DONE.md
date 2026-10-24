# [IDEA-02] Home Screen Unread Articles Widget (RemoteViews) — Completion Report

- **Trạng thái:** Đã thực thi Inline Execution; code/test/host verification hoàn tất. Smoke trước đó trên Pixel 7 Pro xác nhận các đường cơ bản; notifier chung được thêm sau smoke và chưa retest trên thiết bị. Chưa commit, chưa push.
- **Ngày:** 2026-10-24
- **Audit:** 8/10. Widget render dữ liệu, ghim qua launcher request, mở bài khi app đang nền/cold-start và đánh dấu đã đọc thành công. Rà soát sau smoke phát hiện widget chưa refresh khi mark-read từ các luồng app; `WidgetRefreshNotifier` đã nối vào RSS repositories, HomeViewModel và TTS completion, nhưng chỉ host-tested ở bản cuối. Giới hạn còn lại: Dynamic Color preview rất nhạt; request pin không đảm bảo launcher ghim; mở bài dùng CLEAR_TASK nên reset back stack; smoke bị interstitial làm gián đoạn 2 lần.

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
- Dọn: owner chọn giữ data, gỡ widget khỏi Home; không restore DB snapshot vì user actions đã phát sinh read state/XP. Snapshot sau dọn `/Users/loitran/idea02_pixel_postsmoke_20261023_220815`: Room v18, `integrity_check=ok`, 246 articles, 240 unread. So với backup trước test: 243 → 240 unread; `brain_rpg_profile.baseTotalXp` 750 → 850 và `General Knowledge.baseXp` 150 → 250 (3 bài mới được đọc/XP phát sinh trong smoke, gồm mở bài cold-start). Owner chọn giữ dữ liệu, không restore. Widget đã gỡ; không có RSS widget binding trong `dumpsys appwidget`. Pixel bàn giao lại cho phiên UI-032/SEARCH-012.

## Giới hạn/khuyến nghị

- `ArticleWidgetActionActivity` cố ý reset back stack bằng CLEAR_TASK; không sửa `HomeEntry`/`MainActivity.onNewIntent`.
- `ArticleWidgetPinHelper.requestPin` true chỉ nghĩa request được launcher chấp nhận, không có callback xác nhận người dùng ghim; smoke đã quan sát widget thực sự trên Home.
- System dynamic colors được tham chiếu; smoke xác nhận widget đọc được nhưng không đổi wallpaper để chứng minh đổi màu động.
- Một số live DataStore/RSS state sau smoke thay đổi (hai bài đánh dấu đã đọc, XP có thể tăng do Reader); theo yêu cầu owner giữ DB hiện trạng, không rollback.
- Chưa commit/push thay đổi IDEA-02.


## Bổ sung sau smoke — WidgetRefreshNotifier (2026-10-24)

Rà soát sau smoke phát hiện các luồng mark-read trong RSS repository, Home cluster và TTS chưa refresh widget. Thêm domain port `WidgetRefreshNotifier` cùng Android implementation; `AbstractRssRepository.markAsRead` refresh sau DB write chỉ khi chuyển sang read; `HomeViewModel.markClusterAsRead` và `ReadNewsController` TTS completion cũng refresh. Regression tests xác nhận DAO write xảy ra trước notifier, không refresh cho mark-unread hoặc khi DB write lỗi. Bản này host-tested bằng 1275 unit tests, build và lint; **chưa retest trên device** vì Pixel đã được bàn giao cho session khác.
