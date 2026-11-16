# FRONT-02e — Test thiết bị: bản xem trước Nostr khi Activity tạo lại hoặc xoay (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-15 · **Nhánh:** `dev` · **Nền:** `SubscribeDialogRecreateDeviceTest` (FRONT-02d), `NostrRelayProfileTest` (JVM, MockWebServer), `SubscribeViewModel`, `SubscribeDialog`

> Trạng thái: owner đã chốt trong brainstorm (2026-11-15): lấy `SubscribeViewModel` qua `ViewModelProvider` của một Activity test (ViewModelStore thật, không Hilt); cho phép thêm `mockwebserver3` vào scope androidTest (đúng phiên bản đã dùng ở unit test). Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
FRONT-02d chỉ chứng minh **chữ đã gõ** sống sót. Phần còn nợ: sau khi tìm một `npub` và hiện bản xem trước Nostr (tên nguồn, danh sách ghi chú, ô nhập bị khóa), việc Activity bị tạo lại hoặc màn hình xoay có làm mất bản xem trước không. Chỉ thêm test, **không đổi mã production**, không dependency mới ngoài một khai báo scope test cho thư viện đã có.

## Sự thật đã đọc trong code (2026-11-15)
- `NostrRelayClient` có constructor `internal` nhận `(OkHttpClient, CoroutineDispatcher, relays: List<String>, requireSecure: Boolean, timeoutMs: Long)`; constructor `@Inject` cố định `RELAYS = [wss://nos.lol, wss://relay.primal.net]` và `requireSecure = true`. `androidTest` cùng module nên gọi được constructor `internal`.
- `SubscribeViewModel` (`@HiltViewModel`) cần 8 phụ thuộc: `Context`, `OpmlSv`, `RssSv`, `RssHelper`, `AndroidStringsHelper`, `WebFeedScraper`, `NewsletterUrlResolver`, `NostrRelayClient`. Tất cả trừ `NostrRelayClient` là lớp `@Inject` thật. Đường Nostr của `search()` chỉ dùng `context`, `rssService` (`isFeedExist`) và `nostrRelayClient`.
- `RApp` là `@HiltAndroidApp`; test thiết bị chạy cùng tiến trình với `RApp`, nên một `@EntryPoint(SingletonComponent)` đọc được các phụ thuộc thật mà không cần `hilt-android-testing`.
- Manifest có `usesCleartextTraffic="true"`, nên relay giả `ws://127.0.0.1` kết nối được với `requireSecure = false`.
- `mockwebserver3-junit4:5.0.0-alpha.11` và `okhttp-tls` hiện chỉ ở `testImplementation` (`app/build.gradle:262-263`). `NostrTestEvents` (sự kiện đã ký) nằm ở `app/src/test/...` nên **androidTest không thấy được**.
- Hộp thoại hiện qua `SubscribeDialog(subscribeViewModel)`; tên nguồn hiển thị ở tiêu đề bằng `subscribeUiState.feed?.name`.
- FRONT-02d đã chứng minh: Compose test trên `FeedsPage` thật cần `mainClock.autoAdvance = false`; **Activity test đơn giản không có animation vô hạn của `FeedsPage`**, nên có thể không cần.

## Vì sao không dựng ViewModel ngoài Activity (đã cân nhắc, bị loại)
Nếu test tự `new SubscribeViewModel(...)` rồi đưa vào `SubscribeDialog`, ViewModel nằm ngoài `ViewModelStore` của Activity, nên nó **sống qua `recreate()` một cách tầm thường** và test xanh mà không chứng minh gì về `ViewModelStore`. Vì vậy ViewModel phải được lấy qua `ViewModelProvider(activity, factory)`.

## Phạm vi trung thực
- Test dùng **một Activity đơn giản của test** (`ComponentActivity`), **không phải `MainActivity`**, không qua `HomeEntry`, không qua `hiltViewModel()`. Nó chứng minh: (1) `ViewModelStore` của Android giữ `SubscribeViewModel` qua `recreate()` và state xem trước còn nguyên; (2) `SubscribeDialog` dựng lại đúng từ state đó. **Không** chứng minh scope của `hiltViewModel()` trong `FeedsPage` (đó là điều FRONT-02d đã kiểm chữ gõ trên `MainActivity` thật, chưa kiểm xem trước).
- Relay giả chạy trong tiến trình test (`MockWebServer`, WebSocket), **không mạng thật**, không chạm `nos.lol` hay `relay.primal.net`.
- **Không kiểm:** `MainActivity`/`HomeEntry` thật với xem trước Nostr, app xuống nền, process bị giết (ViewModel không lưu qua lần đó), đổi ngôn ngữ/chế độ tối thật, hai relay (chỉ một relay giả), đồng bộ nền, mở `njump.me`, tên hồ sơ thật.
- Không dependency mới: chỉ thêm `androidTestImplementation "com.squareup.okhttp3:mockwebserver3-junit4:5.0.0-alpha.11"` (cùng phiên bản đã có). Không đổi manifest/chuỗi/Room/DataStore; không đổi mã production.
- Chạy chỉ trên **Pixel 7 Pro `2B051FDH3006MU`**; không tọa độ; **không dùng MockK** trên máy; không gọi mạng thật.

## Thiết kế
Một tệp test mới `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeNostrPreviewRecreateDeviceTest.kt` và một tệp phụ `NostrDeviceFixtures.kt` cùng thư mục. Tệp phụ chứa **một** ghi chú và **một** hồ sơ đã ký của cùng một tác giả (hằng số `AUTHOR_HEX`), sao chép nguyên văn từ `NostrTestEvents.PLAIN`, `PROFILE` và `AUTHOR`; không dùng chung nguồn với `src/test` để không đổi cấu trúc Gradle.

Các thành phần:
1. **`@EntryPoint` + factory:** `NostrTestDeps` (interface `@EntryPoint @InstallIn(SingletonComponent::class)`) lộ `OpmlSv`, `RssSv`, `RssHelper`, `AndroidStringsHelper`, `WebFeedScraper`, `NewsletterUrlResolver`, `OkHttpClient`; lấy bằng `EntryPointAccessors.fromApplication`. Một `ViewModelProvider.Factory` dựng `SubscribeViewModel(context, opml, rss, helper, strings, scraper, resolver, nostrRelayClient = NostrRelayClient(okHttp, Dispatchers.IO, listOf(fakeRelayUrl), requireSecure = false, timeoutMs = 3000L))`.
2. **Activity test** (`ComponentActivity` qua `createAndroidComposeRule`): `viewModel = ViewModelProvider(activity, factory)[SubscribeViewModel::class.java]`; `setContent { SettingsProvider { SubscribeDialog(viewModel) } }`. Nếu `SettingsProvider`/`AccountSettingsProvider` không cần thiết cho dialog thì bỏ.
3. **Relay giả:** `MockWebServer` + `withWebSocketUpgrade`: nhận `REQ`, trả `EVENT` ghi chú, `EVENT` hồ sơ, `EOSE`; `onClosing` đóng lại (như `NostrRelayProfileTest`). Một biến thể có cổng `CompletableDeferred` giữ phản hồi.
4. **Điều kiện đầu vào:** tài khoản hiện hành là Local (kiểm `currentAccountType`, nếu không thì test báo lỗi rõ, không đoán); `isFeedExist("nostr:<npub>")` phải false; test dùng `AUTHOR` tĩnh nên không có nguồn nào bị lưu (không bấm Subscribe).

Ba test:
- **(a) `previewSurvivesRecreate`:** gõ `npub` (từ `NostrKeys.hexToNpub(AUTHOR)`), bấm Tìm bằng semantics, chờ xem trước; `recreate()`; khẳng định cùng instance ViewModel (`assertSame`), `state.feed?.name` bắt đầu bằng `Nostr · `, `state.articles.size == 1`, `lockLinkInput == true`, và tiêu đề hiện trong dialog mới.
- **(b) `previewSurvivesRealRotation`:** như (a) nhưng xoay thật bằng `UiAutomation.setRotation` (giữ cài đặt tự xoay của owner như FRONT-02d); khẳng định xem trước còn nguyên.
- **(c) `recreateWhileRelayIsStillAnswering`:** giữ phản hồi bằng cổng, gọi `recreate()`, rồi mở cổng; khẳng định xem trước vẫn đến và đúng, không có `errorMessage`, `lockLinkInput` đúng.

## Quy tắc an toàn thiết bị
Như FRONT-02d: cổng chỉ đọc trước mỗi lượt (`/tmp/f28_gate.sh`: launcher `com.google.android.apps.nexuslauncher` ở trên cùng, đã mở khóa, không instrumentation), phiên khác có thể dùng cùng Pixel (hỏi và đợi trả máy), sao lưu và khôi phục byte-exact 6 tệp, giữ cài đặt tự xoay, `am instrument --user 0` với runner đầy đủ, không `connected*`, không MockK, không xóa DataStore. Test không lưu nguồn nào nên Room không đổi; sao lưu vẫn bắt buộc vì `RApp` chạy khởi động.

## Kiểm chứng
- Đột biến chạy trên máy (đổi tạm mã, khôi phục `cmp`): (1) `SubscribeViewModel.reset()` bị gọi trong `init()`: (a)(b) đỏ; (2) bỏ gán `feed` khi tìm xong: cả ba đỏ; (3) bỏ `lockLinkInput = true` trong nhánh Nostr: (a)(b) đỏ ở khẳng định khóa; (4) đột biến **trong test**, không phải production: dựng `SubscribeViewModel` bằng tay ngoài `ViewModelProvider` (một biến thể tạm của tệp test). Mong đợi (a)(b) vẫn xanh về mặt nội dung nhưng `assertSame` đỏ: chứng minh khẳng định cùng-instance là phần làm test có nghĩa, không thừa.
- Chạy lặp **40 lượt** có cổng/sao lưu/khôi phục; nếu chập chờn, điều tra trước khi dùng làm bằng chứng. Không gõ lại bừa.
- JVM full suite không đổi (3.627); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng trong báo cáo).

## Rủi ro đã biết
- `SubscribeDialog` có `LaunchedEffect(visible)` gọi `init()`/`reset()`; test phải gọi `showDrawer()` trước khi tìm (hộp thoại mới hiện).
- `EntryPointAccessors.fromApplication` có thể không lấy được `OkHttpClient` nếu module Hilt không lộ; nếu vậy dùng `OkHttpClient()` thường cho relay giả (không cần cấu hình app).
- `ComponentActivity` thuần có thể thiếu `ViewModelStoreOwner` tương thích với `recreate()` của rule; nếu `createAndroidComposeRule<ComponentActivity>` không cho `recreate()` đúng thì dùng `ActivityScenario` như `EdgeToEdgeInsetsTest`.
- Chập chờn bước gõ đã gặp ở FRONT-02d (gốc chưa biết) có thể tái xuất khi gõ `npub`.
- `mockwebserver3` thêm khoảng vài trăm KB vào APK test.

## Giới hạn đã biết
- Không chứng minh `hiltViewModel()` scope trên `MainActivity` thật cho xem trước Nostr.
- Một relay giả, không chứng minh hành vi hai relay hay mạng thật.
- Chỉ một máy (Pixel 7 Pro, Android 17).

## Điều chỉnh khi thực thi
- **Không dùng `@EntryPoint`:** `RApp` đã có field `@Inject` công khai (`opmlService`, `rssSv`, `rssHelper`, `androidStringsHelper`, `okHttpClient`); `WebFeedScraper(WebFeedHttpClient(), WebFeedParser())` và `NewsletterUrlResolver()` được dựng tay vì đường Nostr không dùng chúng. Không cần `kaptAndroidTest`.
- **`SettingsProvider` không cần** cho `SubscribeDialog`; **`mainClock.autoAdvance = false` không cần** (Activity test không có `FeedsPage` và animation vô hạn).
- **Dialog dựng lại sau `recreate()`:** `ComponentActivity` thuần không tự gọi `setContent` lại, nên test gọi `setContent { SubscribeDialog(viewModel) }` trên Activity mới với **cùng ViewModel lấy từ ViewModelStore** rồi kiểm tiêu đề.
- **Đột biến test-only `m06`** thay cho "ViewModel dựng tay": đổi `viewModelOf(...)` thành factory trực tiếp; mong đợi `assertSame` đỏ.
- **`NostrFakeRelay.close()` mở cổng trước khi tắt server** để một test đỏ lúc relay đang bị giữ không để lại luồng chặn (`IOException: Gave up waiting for queue to shut down`).
- **Máy:** bắt đầu trên Pixel 7 Pro; sau lệnh của owner ("chỉ dùng tecno, cấm đụng pixel") phần còn lại chạy trên Tecno KJ7. Phiên Claude khác (lens-launcher) từng chạm Pixel đúng lúc một lượt mutation, lượt đó bị loại. `user_rotation` của Pixel bị lệch =1 chưa rõ nguyên nhân; giả thuyết `@After` bị bác.

