# FRONT-02d — Test thiết bị: ô Thêm nguồn khi Activity tạo lại hoặc xoay (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-15 · **Nhánh:** `dev` · **Nền:** `SubscribeViewModelInterruptionTest` (JVM, ghi rõ phần chưa kiểm), `SubscribeDialog`, `SubscribeViewModel`, `FeedsPage`

> Trạng thái: owner đã duyệt thiết kế trong brainstorm (2026-11-15) và chọn: tìm nút "+" bằng Compose semantics; chỉ kiểm chữ đã gõ + hộp thoại còn mở, không cần mạng. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Điền khoảng trống lớn nhất còn nợ của FRONT-02/02b/02c: chứng minh **trên máy thật** rằng khi Activity bị tạo lại hoặc màn hình xoay, hộp thoại **Thêm nguồn** vẫn mở và ô nhập giữ nguyên chữ người dùng đã gõ. Chỉ thêm test, không đổi mã production.

## Sự thật đã đọc trong code (2026-11-15)
- `MainActivity` khai báo `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"` (`AndroidManifest.xml:83`). **Xoay màn hình không tạo lại Activity**; giao diện chỉ bố trí lại. Activity chỉ bị tạo lại thật khi đổi ngôn ngữ, chế độ tối hay cỡ chữ hệ thống (không có trong `configChanges`).
- `SubscribeViewModel` là `@HiltViewModel`, trạng thái nằm trong `MutableStateFlow<SubscribeUiState>` (`visible`, `linkContent`, `lockLinkInput`…). Nó được lấy bằng `hiltViewModel()` trong `FeedsPage` (`FeedsPage.kt:91`) nên **theo lý thuyết** sống qua `recreate()` (ViewModel store của Activity được giữ khi tạo lại do đổi cấu hình), và **không** sống qua việc process bị giết. Phần "sống qua `recreate()`" là giả thuyết chưa kiểm trên máy; test (a) chính là phép thử của nó.
- Hộp thoại mở bằng `SubscribeViewModel.showDrawer()`; nơi duy nhất gọi là nút `+` trong `FeedsPage.kt:215`, `contentDescription = stringResource(R.string.subscribe)`, chỉ hiện khi `rssService.get().subscribe` là true (tài khoản Local) và bộ lọc hiện tại không phải tab AddSources.
- Ô nhập là `ClipboardTextField` với `placeholder = stringResource(R.string.feed_or_site_url)`; test JVM hiện có tìm nó bằng `onNodeWithText("Feed or Site URL")` (bản tiếng Anh).
- `MainActivity` là `@AndroidEntryPoint`, bắt đầu ở `STARTUP` nếu `isFirstLaunch` là true, ngược lại `FEEDS` (`HomeEntry.kt:296`). `EdgeToEdgeInsetsTest` ghi nhận `ActivityScenario` có thể báo "No compose hierarchies found" với Activity do scenario sở hữu.
- Chưa có thư mục `app/src/debug`; `androidTest` đã dùng `createAndroidComposeRule`, `ActivityScenario`, `ui-test-manifest`. `EInkVolumeDispatchInstrumentationTest` đã mở `MainActivity` thật bằng `ActivityScenario.launch`.

## Phạm vi trung thực
- Chỉ kiểm: hộp thoại còn mở và ô nhập còn nguyên chữ sau `recreate()`, sau xoay thật, và sau `recreate()` hai lần liên tiếp.
- **Không kiểm:** xem trước Nostr sau xoay (cần mạng/relay), app xuống nền rồi quay lại, process bị giết (ViewModel không lưu qua lần đó, chữ sẽ mất, hành vi đã biết), đổi ngôn ngữ hay chế độ tối thật, hai điện thoại.
- Không dependency mới, không đổi manifest/chuỗi/Room/DataStore, không đổi mã production, không Gemini/ad/IAP/tiền.
- Chạy chỉ trên **Pixel 7 Pro `2B051FDH3006MU`**. Không bấm hay vuốt theo tọa độ; việc "bấm" nút `+` diễn ra bên trong tiến trình test bằng Compose semantics theo tên nút.

## Thiết kế
Một tệp test mới `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt`, dùng `createAndroidComposeRule<MainActivity>()` (nếu gặp "No compose hierarchies found" thì dùng cách riêng của `EdgeToEdgeInsetsTest`, không thêm `sleep`).

Hàm dùng chung:
1. Chờ có nút theo `contentDescription` lấy từ `R.string.subscribe` bằng `waitUntil` có điều kiện. Nếu nút không xuất hiện (tab AddSources, tài khoản không phải Local, hay đang ở màn STARTUP) thì test **báo lỗi rõ lý do** chứ không đoán đường đi và không bấm gì khác.
2. Bấm nút `+` bằng `performClick()`, chờ ô nhập (`R.string.feed_or_site_url`) hiện.
3. Gõ một chuỗi thử cố định không phải khóa hay địa chỉ thật (hằng số có tên, ví dụ `ROTATION_PROBE_TEXT`), **không bấm Tìm** và không gửi gì ra mạng.

Ba test:
- **(a) `recreate()`:** sau bước 3 gọi `recreate()` của Activity, chờ tạo lại xong; hộp thoại vẫn mở, ô nhập vẫn là chuỗi thử, nút Tìm vẫn bật.
- **(b) xoay thật:** sau bước 3 đặt xoay bằng `UiAutomation.setRotation(ROTATION_90)` (API framework, không phải cử chỉ), rồi `ROTATION_0` trong `finally` để trả máy về thẳng. Hộp thoại và chữ còn nguyên. Test cũng ghi lại việc Activity **không** bị tạo lại (so sánh instance trước/sau) để khóa đúng sự thật về `configChanges`; nếu một ngày `configChanges` đổi, test này đổi nghĩa và phải được xem lại có chủ đích.
- **(c) `recreate()` hai lần liên tiếp:** chữ vẫn còn, `lockLinkInput` vẫn false.

Phần dọn dẹp bắt buộc: `UiAutomation.setRotation(ROTATION_UNFREEZE)` hay trả về hướng ban đầu trong `finally`; đóng hộp thoại bằng `hideDrawer()`/Back (không tạo nguồn nào).

## Quy tắc an toàn thiết bị
1. **Cổng chỉ đọc:** `adb devices`; `dumpsys activity` kiểm không có instrumentation và `topResumedActivity` là launcher; `ps`; keyguard mở. Nếu `loyalty.skyjoy.vn.dev` ở foreground, màn khóa hay có hộp sinh trắc: **dừng và hỏi owner**.
2. **Sao lưu byte-exact** (`/tmp/pixel-backup.py`, nếu /tmp bị dọn thì dựng lại): Room + `-wal` + `-shm`, DataStore, `shared_prefs`. Sau chạy: khôi phục và so `cmp` + băm DataStore. Test không lưu nguồn nào, nhưng mở `MainActivity` thật chạy khởi động app (sync, ghi Room), nên sao lưu vẫn bắt buộc.
3. Chỉ `adb -s 2B051FDH3006MU`; chỉ `am instrument -w -e class …` với runner `com.mckimquyen.reader.test`; **không** `connected*`; chỉ force-stop `com.mckimquyen.reader`.
4. Không xóa DataStore. Quảng cáo toàn màn hình chắn giao diện: đóng bằng Back rồi tiếp tục (owner đã cho phép).
5. Máy mới cài (`isFirstLaunch` true): test phát hiện màn STARTUP và dừng với thông báo rõ, không tự đi tiếp.

## Kiểm chứng
- Không có mã production mới nên "đỏ" nằm ở khả năng bắt lỗi: tạm đổi `SubscribeViewModel` (backup + `cmp`, luôn khôi phục) rồi cho test chạy lại trên máy:
  - `init()` gọi `reset()` → (a) và (c) phải đỏ vì chữ mất.
  - `hideDrawer()` được gọi trong `init()` → hộp thoại đóng, (a) (b) (c) đỏ.
  - `linkContent` không cập nhật trong `inputLink` → các test đỏ ở bước gõ.
- Nếu một test lộ lỗi thật của app: **dừng** và điều tra nguyên nhân gốc trước khi sửa, không sửa mù.
- Chạy ba lần liên tiếp trên Pixel, mỗi lần đều xanh; lint và `assembleDevDebug assembleDevDebugAndroidTest` thành công; JVM full suite không đổi (3.627).
- Báo cáo thật: số lần chạy, thời gian, đã xác minh gì và không xác minh gì.

## Rủi ro đã biết
- Nhãn nút `+` phụ thuộc `R.string.subscribe`; nếu app đổi nhãn, test đỏ rõ ràng (đúng ý).
- Pixel hay có app của owner ở foreground; cổng sẽ chặn.
- `setRotation` trên Android 17 có thể bị hạn chế; nếu không dùng được, test (b) bị bỏ và báo cáo ghi rõ, không thay bằng cách bấm.
- Quảng cáo xen kẽ có thể làm test đỏ ngẫu nhiên; phân biệt với lỗi thật bằng log và ảnh trạng thái cây semantics, không bằng tăng `timeout`.

## Giới hạn đã biết
- Không chứng minh bản xem trước Nostr hay tên hồ sơ còn nguyên sau xoay.
- Không chứng minh hành vi khi app xuống nền hoặc process bị giết.
- Chỉ một máy (Pixel 7 Pro, Android 17); không so sánh được với A50s hay KJ7.
