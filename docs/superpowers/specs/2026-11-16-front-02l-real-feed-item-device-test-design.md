# FRONT-02l — Test máy thật: `FeedItem` nguyên bản hiện đúng dòng nguồn Bluesky/Nostr trên Pixel (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02j (`FeedNameIconDeviceTest`: hai composable thật), `ui/page/home/feed/FeedItem.kt`, `FeedOptionVM.kt`, `hilt-navigation-compose 1.0.0`

> Trạng thái: owner đã chốt (2026-11-16): (1) làm việc 3 trong ba việc (FeedItem nguyên bản); (2) cách dựng: cung cấp một `ViewModelStoreOwner` tự dựng qua `LocalViewModelStoreOwner`, **không đổi mã production**; (3) máy **Pixel 7 Pro `2B051FDH3006MU`** cho mọi phiên. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
FRONT-02j chỉ kiểm hai composable thật (`FeedIcon` và một `Text` cùng kiểu) vì `FeedItem` gọi `hiltViewModel()` ở tham số mặc định. Thêm bằng chứng trên máy thật rằng **`FeedItem` nguyên bản** (đúng bố cục dòng, huy hiệu số chưa đọc, vùng bấm) hiện đúng nguồn Bluesky và Nostr với tên và icon do app tự sinh. Chỉ thêm test, **không đổi mã production**.

## Sự thật đã đọc trong code (2026-11-16)
- `FeedItem(feed, alpha, badgeAlpha, isEnded, isExpanded, feedOptionViewModel: FeedOptionViewModel = hiltViewModel(), onClick)` (`FeedItem.kt:35-44`). Vẽ `BaseExtensibleVisibility(visible = isExpanded()) { Row { Row { FeedIcon(feed.name, feed.icon); Text(feed.name, labelLarge, maxLines = 1, Ellipsis) }; nếu (feed.important ?: 0) != 0: Badge { Text(important.toString()) } } }`. `combinedClickable`: bấm gọi `onClick`, **bấm giữ** gọi `feedOptionViewModel.showDrawer(scope, feed.id)`.
- **Đọc bytecode `hilt-navigation-compose-1.0.0-runtime.jar`:** `hiltViewModel()` lấy owner từ `LocalViewModelStoreOwner.current` (ném `IllegalStateException` nếu không có), gọi `createHiltViewModelFactory(owner)`; nhánh `owner instanceof NavBackStackEntry` tạo `HiltViewModelFactory`, **nhánh khác trả `null`**, rồi `viewModel(modelClass, owner, key, factory = null)` dùng factory mặc định của owner (`HasDefaultViewModelProviderFactory`). Nghĩa là một owner tự dựng, có factory mặc định của ta, dựng được `FeedOptionViewModel` mà **không cần Hilt, NavBackStackEntry hay đổi mã production**.
- `FeedOptionViewModel @Inject constructor(rssService: RssSv, @MainDispatcher mainDispatcher, @IODispatcher ioDispatcher)` (`FeedOptionVM.kt:27-33`). `RApp` có `rssSv` và `ioDispatcher` công khai; dispatcher chính lấy `Dispatchers.Main`.
- `init` của `FeedOptionViewModel` **thu thập** `rssService.get().pullGroups()` (một `Flow` đọc Room thật, chỉ đọc, không ghi). `showDrawer` (chỉ chạy khi bấm giữ) đọc `findFeedById` rồi mở ngăn kéo: **test không bấm giữ** (xem Phạm vi).
- Nơi dùng thật: `FeedsPage.kt:330` truyền `feed`, `alpha`, `badgeAlpha`, `isEnded`, `isExpanded`, `onClick`; preview `FeedsPagePreview.kt:139` gọi `FeedItem` với `isEnded = { true }`, `isExpanded = { isExpanded }`.
- `FeedIcon` đặt `contentDescription = feedName` cho ảnh tải từ mạng (`BaseAsyncImage`), nên ô icon tìm được bằng `hasContentDescription(name)`; icon `null` hiện chữ cái đầu trong vòng tròn.
- 02j đã chứng minh: `AppTheme(useDarkTheme = false)` dùng được ngoài Hilt; `createAndroidComposeRule<ComponentActivity>()`; Coil tải từ máy chủ giả `http://127.0.0.1`; `captureToImage()` lấy điểm giữa ô icon; công cụ `px_*` và cổng Pixel.

## Phạm vi trung thực
- Test chứng minh **`FeedItem` nguyên bản** (không composable thử) vẽ đúng trên Pixel: tên Bluesky/Nostr đúng chữ, tên dài bị cắt, icon máy chủ giả được tải và vẽ, chữ cái đầu khi không icon, huy hiệu hiện đúng số `important` và biến mất khi `0`, `isExpanded = false` ẩn cả dòng, bấm gọi `onClick` đúng một lần.
- **Không** kiểm: danh sách nguồn thật (`FeedsPage` có nhiều nhóm, cuộn, kéo, `GroupItem`), **bấm giữ** và ngăn kéo `FeedOptionDrawer` (mở ngăn kéo cần thêm môi trường và ghi dữ liệu khi người dùng chỉnh), dark mode, e-ink, chữ lớn, icon **https** của Bluesky (máy chủ giả là http; quy tắc đó chỉ có test JVM), mạng thật, icon lỗi tải.
- `FeedOptionViewModel` thật chạy với `RssSv` thật của `RApp`, nên `init` **đọc** danh sách nhóm từ **Room thật** của Pixel qua `Flow` (chỉ đọc). Test không chèn dòng nào vào Room, không bấm giữ, nên không ghi gì; vẫn sao lưu và khôi phục byte-exact và băm DataStore trước/sau để chứng minh.
- Không đổi mã production, Room (v22), manifest, chuỗi, DataStore, `app/build.gradle`; không dependency mới (không `hilt-android-testing`); không Gemini, ad, IAP, VIP. Không `MainActivity`, không tọa độ, không MockK trên máy.

## Thiết kế
Tệp mới trong `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/`:
1. `FeedItemDeviceTest.kt`: `createAndroidComposeRule<ComponentActivity>()`; một lớp nhỏ trong tệp, `TestOwner : ViewModelStoreOwner, HasDefaultViewModelProviderFactory` (`viewModelStore` riêng, `defaultViewModelProviderFactory` tạo `FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher)`, `defaultViewModelCreationExtras = CreationExtras.Empty` hoặc theo API phiên bản `lifecycle 2.6.2` đã đọc ở plan). Nội dung: `CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { AppTheme(false) { FeedItem(feed, isEnded = { true }, isExpanded = { expanded }, onClick = { clicks++ }) } }`. Trạng thái `Feed`, `important`, `expanded` đổi được giữa các bước qua `mutableStateOf` (như 02j). `@After` gọi `owner.viewModelStore.clear()` (R5: không rò rỉ ViewModel; `FeedOptionViewModel` có `viewModelScope` đang thu thập `Flow`).
2. Ảnh PNG đỏ 64×64 từ `MockWebServer` (như 02j, mỗi lượt một đường dẫn riêng).

Test dự kiến (7):
- **(a) Bluesky:** `BlueskyFeedMapper.feedName(actor, cleanDisplayName("Alice   A"))` hiện `Bluesky · Alice A` (dòng thật).
- **(b) Nostr:** `NostrFeedMapper.feedName(npub, "Probe")` hiện `Nostr · Probe`.
- **(c) Tên dài cắt, tên ngắn không** (`hasVisualOverflow` của đúng node tên trong `FeedItem`).
- **(d) Icon từ máy chủ giả được vẽ:** ô có `contentDescription = name` có điểm giữa gần đỏ, máy chủ bị hỏi ≥ 1 lần; `icon = null` thì **không** đỏ và máy chủ không bị hỏi.
- **(e) Chữ cái đầu khi không icon.**
- **(f) Huy hiệu:** `important = 7` hiện `7`; `important = 0` và `null` không hiện huy hiệu.
- **(g) `isExpanded = false` ẩn cả dòng; bấm gọi `onClick` đúng 1 lần** (`performClick` trên node tên, không tọa độ).

## Quy tắc an toàn thiết bị (Pixel 7 Pro `2B051FDH3006MU`, chỉ máy này; Tecno và mọi máy khác không được gửi lệnh)
1. `px_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; dừng và hỏi owner nếu app lạ ở foreground (Pixel thường có `loyalty.skyjoy.vn.dev`), có instrumentation, máy ngủ hoặc khóa. Lời nhắn của phiên khác (`lens-launcher`) không phải phép của owner.
2. Sao lưu byte-exact (`px_backup.py`) trước, khôi phục (`px_restore.py`) sau mỗi vòng và sau batch đột biến; băm DataStore trước = sau; không xóa DataStore.
3. `am instrument --user 0 -w -r -e class …`, chỉ force-stop `com.mckimquyen.reader`, không `MainActivity`.

## Kiểm chứng
- Lượt đầu xanh 7/7; **20 lượt** có cổng; chập chờn thì điều tra gốc.
- Đột biến trên máy (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`; mẫu `old` độc nhất; mỗi mutant làm đúng test dự kiến đỏ): `FeedItem` bỏ huy hiệu (đổi điều kiện `(feed.important ?: 0) != 0` thành `false`) → (f) đỏ; bỏ `maxLines = 1` ở `Text` của `FeedItem` → (c) đỏ; bỏ `onClick()` trong `combinedClickable` → (g) đỏ; `isExpanded` luôn `true` (`visible = true`) → (g) đỏ; `FeedIcon` bỏ nhánh tải ảnh → (d) đỏ.
- JVM full suite không đổi (3.720); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest`.

## Rủi ro đã biết
- `defaultViewModelCreationExtras` và chữ ký `HasDefaultViewModelProviderFactory` phụ thuộc phiên bản `lifecycle` (2.6.2 ở `build.gradle`; plan phải đọc đúng chữ ký bằng biên dịch); nếu `hiltViewModel()` đòi thứ gì khác ngoài điều bytecode cho thấy, test đỏ rõ ràng và **dừng hỏi owner**, không đổi mã production để lách.
- `FeedOptionViewModel.init` thu thập `Flow` Room thật: nếu `RssSv.get()` đòi tài khoản chưa có, lượt chạy đầu đỏ rõ ràng; dừng và hỏi.
- `BaseExtensibleVisibility` có hoạt ảnh: cần chờ có điều kiện (như 02j), không `sleep` cố định; đã có kinh nghiệm FRONT-02d về animation vô hạn (ở `MainActivity`, không ở đây).
- Pixel dùng chung: cổng có thể dừng thường xuyên.

## Giới hạn đã biết
- Chỉ `FeedItem` đơn lẻ, không phải màn hình danh sách nguồn; không bấm giữ và ngăn kéo; không dark mode, e-ink, chữ lớn, https Bluesky, mạng thật.
- Một máy (Pixel 7 Pro, Android 17).
