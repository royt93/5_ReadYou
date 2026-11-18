# FRONT-02s — Chữ đã gõ trong ô Thêm nguồn khi Activity tạo lại, kiểm trên Activity test (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-18 · **Nhánh:** `dev` · **Nền:** FRONT-02d (`SubscribeDialogRecreateDeviceTest` trên `MainActivity`), FRONT-02e (`SubscribeNostrPreviewRecreateDeviceTest` trên Activity test), FRONT-02m (lỗi gõ chữ chập chờn)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-18): (1) chuyển 02d sang **Activity test** (như 02e), bỏ nút `+` và `MainActivity`; (2) **bỏ test xoay thật**, chỉ giữ recreate; (3) **gõ qua giao diện thật**, để lỗi gõ hiện rõ, **không gõ lại**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Giữ điều FRONT-02d chứng minh: **chữ người dùng đã gõ vào ô Thêm nguồn** và hộp thoại còn nguyên sau `recreate()` (một lần và hai lần liền). Bỏ rủi ro quảng cáo toàn màn hình của `MainActivity` (đo 2026-11-18 trên Tecno, 40 lượt: **10 trong 11 lượt đỏ có `AdActivity` ở trên cùng, 0 trong 29 lượt xanh**). Chỉ đổi test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; không mạng thật.

## Sự thật đã đọc (2026-11-18)
- 02d dùng `createAndroidComposeRule<MainActivity>()`, tìm nút `+` bằng `hasContentDescription(R.string.subscribe) and hasClickAction()` (đúng 1 node), bấm, gõ chuỗi thử `abc-rotation-probe` vào ô, rồi `recreate()` hoặc xoay thật; kiểm ô còn chữ qua `hasSetTextAction() and hasText(PROBE_TEXT)`. Có `mainClock.autoAdvance = false` vì `FeedsPage.kt:127` có animation vô hạn (Compose không bao giờ rảnh).
- Nút `+` chỉ gọi `subscribeViewModel.showDrawer()` (`FeedsPage.kt:215`); hộp thoại là `SubscribeDialog(subscribeViewModel)` đặt ở `FeedsPage.kt:380`; `SubscribeViewModel` lấy bằng `hiltViewModel()` ở `FeedsPage.kt:91`.
- 02e đã dựng đúng cách này trên Activity test: `ViewModelProvider(activity, subscribeViewModelFactory(...))`, `activity.setContent { SubscribeDialog(viewModel) }`, `viewModel.showDrawer()`, `recreate()`, rồi **dựng lại hộp thoại** bằng `setContent` trên Activity mới (Activity test không tự dựng lại nội dung, khác `MainActivity`). 02e gõ một `npub` và kiểm **bản xem trước**, **không** kiểm chữ còn trong ô sau `recreate()`. Phần "chữ trong ô" vì vậy chỉ 02d chứng minh.
- FRONT-02m: gõ chữ qua giao diện thất bại ~1% (4/400, rồi 3/199) ngoài `MainActivity`, gốc chưa rõ; có `TypingFlakeClassifier` (thuần) phân biệt "chữ chưa vào" với "chữ vào rồi mất". 02d đã thử gõ lại tối đa 3 lần khi chữ chưa từng vào ô; owner chọn **không gõ lại** ở 02s.
- FRONT-02e: `UiAutomation.setRotation(UNFREEZE)` đôi khi để `user_rotation=1` trên Activity test (không có `configChanges`, nên xoay tạo lại Activity); gốc chưa rõ. 02d trên `MainActivity` 0/80 lượt lệch xoay (Tecno, 2026-11-18), và nguyên nhân khả dĩ là `MainActivity` giữ `configChanges` (giả thuyết chưa kiểm, báo cáo 02d). Vì vậy 02s **không dùng `setRotation`**.
- `SubscribeViewModel.init()` chỉ gán `groups = pullGroups()` (Flow lười, không chạm DB nếu không thu); `SubscribeDialog` thu `groups` nhưng test không bấm Subscribe nên không ghi Room.

## Phạm vi trung thực
- **Chứng minh:** trên Tecno, `ViewModelStore` của Android giữ `SubscribeViewModel` qua `recreate()` (một và hai lần) và `SubscribeDialog` dựng lại **đúng chữ người dùng đã gõ** từ state còn nguyên; ô vẫn sửa được (không bị khóa).
- **Mất so với 02d cũ (nói thẳng):** (a) nút `+` thật của `FeedsPage` không còn được bấm (`showDrawer()` là việc duy nhất nút đó làm); (b) `hiltViewModel()` trong `FeedsPage` và `HomeEntry` không còn được kiểm (chỉ `ViewModelProvider`, cùng `ViewModelStore`); (c) **xoay thật** không còn ở 02d (và 02e đã bỏ từ 2026-11-18); bằng chứng xoay thật chỉ còn là số đo cũ của test `MainActivity` bị thay: Pixel khoảng 190 lượt chạy ba test (báo cáo 02d, 2026-11-15) và Tecno 80 lượt ngày 2026-11-18 (0 lệch xoay, hai vòng 40 lượt; 29 và 30 lượt xanh, các lượt đỏ do quảng cáo); các số đó **không được làm mới** ở 02s; (d) kiểm "xoay không tạo lại Activity vì `configChanges`" (`assertSame`) bị bỏ.
- **Không kiểm:** xuống nền, process bị giết, đổi ngôn ngữ/chế độ tối thật, bản xem trước Nostr hay Bluesky (02e, 02r), `MainActivity`/`HomeEntry`.
- Chỉ **Tecno KJ7 `115333744A005844`** (Android 14, 1080x2436). Không tọa độ, không MockK, không `connected*`, không `setRotation`, không mạng, **không ghi Room** (test không bấm Tìm hay Đăng ký).

## Thiết kế
Thay nội dung `SubscribeDialogRecreateDeviceTest.kt` (giữ tên lớp và đường dẫn để lịch sử git và tài liệu còn khớp); **không** thêm tệp mới.

1. `createAndroidComposeRule<ComponentActivity>()`; `SubscribeViewModel` qua `ViewModelProvider(activity, subscribeViewModelFactory(UNUSED_RELAY))` (hàm 02e có sẵn; `subscribeViewModelFactoryWith(blueskyApi = null)` tương đương và dựng `BlueskyApiClient` thật nhưng **không bao giờ được gọi**, vì test không bấm Tìm). `check(currentAccountType == Local)` ở `@Before` như 02e/02r.
2. `openDialogAndType()`: `viewModel.showDrawer()` rồi `setContent { SubscribeDialog(viewModel) }`; chờ ô nhập; gõ `PROBE_TEXT` **một lần** bằng `performTextInput`; chờ chữ xuất hiện trong ô. **Không gõ lại.** Nếu hết hạn chờ: ném lỗi có chẩn đoán dạng số (chiều dài chữ lớn nhất từng thấy trong ô, `peakEditableTextLength`, số ô, ô có focus không, ViewModel đang giữ bao nhiêu ký tự), **không** ghi nội dung.
3. `recreateAndShowAgain()`: `recreate()`, `assertNotSame(activityTrước, activitySau)`, `assertSame(viewModelTrước, viewModelSau)` (từ `ViewModelStore` mới), dựng lại hộp thoại bằng `setContent`, chờ chữ trong ô, ô còn sửa được.
4. Hai test: `recreate_keepsTheDialogOpenAndTheTypedText`, `recreateTwice_keepsTheDialogOpenAndTheTypedText`.
5. Không `mainClock.autoAdvance = false` (không có `FeedsPage`), nên dùng `compose.waitUntil` mặc định như 02e. Nếu lượt thử đầu cho thấy Compose "bận", đó là **bằng chứng mới cần điều tra**, không tự động quay lại đồng hồ thủ công.
6. Bộ phân loại `TypingFlakeClassifier` (có sẵn, thuần) **không** nối vào test để tránh phình to; chẩn đoán số trong thông báo lỗi đủ để phân biệt "chưa vào" (`peak=0`) với "vào rồi mất" (`peak>0`).

## Quy tắc an toàn thiết bị
Như FRONT-02e/02r trên Tecno: cổng chỉ đọc `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; sao lưu và khôi phục **byte-exact 6 tệp** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng sau mỗi vòng; xoay `1 0` trước và sau mỗi lượt (test không chạm xoay nên lệch xoay = tín hiệu bất thường); `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; không `connected*`; không xóa DataStore; sau mỗi vòng gỡ `com.mckimquyen.reader.test`.

## Kiểm chứng
- **40 lượt trên Tecno**, mỗi vòng có cổng, sao lưu, khôi phục. **Không** gõ lại, **không** tăng timeout, **không** `sleep`. Mọi lượt đỏ phải đọc nguyên nhân (chưa vào hay vào rồi mất) trước khi dùng. Tỉ lệ lỗi gõ ~1% dự kiến (02m): **1 trong 40 lượt đỏ vì lỗi gõ là kết quả chấp nhận được và phải được ghi**, không phải lý do để thêm gõ lại. Từ 2 lượt đỏ trở lên trong 40: dừng, điều tra theo systematic-debugging, hỏi owner.
- **Đột biến chạy trên máy** (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`): (a) `SubscribeViewModel.init()` xóa `linkContent` → cả hai test đỏ ở bước kiểm sau recreate; (b) `inputLink` bỏ chữ (`linkContent = ""`) → đỏ ở bước gõ; (c) `showDrawer` đặt `visible = false` → đỏ ở bước chờ ô; (d) **đột biến trong test**: dựng ViewModel ngoài `ViewModelProvider` → `assertSame` đỏ (chứng minh khẳng định cùng-instance không thừa). Mỗi đột biến phải làm **đúng** test dự định đỏ.
- JVM full suite không đổi (đo lại lúc thực thi); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng trong báo cáo).
- Báo cáo: số lượt, lượt đỏ và nguyên nhân từng lượt, bảng đột biến, băm DataStore trước và sau, xoay sau mỗi lượt, những gì không kiểm.

## Rủi ro đã biết
- **Lỗi gõ chữ ~1% (02m, gốc chưa rõ)** tái xuất nếu test gõ qua giao diện; đây chính là phần 02d vốn chứng minh. Chấp nhận có chủ ý (owner chọn không gõ lại).
- **Mất bằng chứng chỉ `MainActivity` mới có** (mục "Mất so với 02d cũ").
- **Thay một test đã commit:** `SubscribeDialogRecreateDeviceTest.kt` đổi hẳn nội dung; lịch sử git giữ bản cũ. Báo cáo 02d và `feature.md` phải ghi 02d cũ đã được thay bằng 02s.
- **`ComponentActivity` không tự dựng lại nội dung sau `recreate()`**: test phải `setContent` lại (như 02e); quên bước đó sẽ cho một lỗi giả "không thấy ô".
- Hộp thoại dùng `AlertDialog` (cửa sổ riêng): `compose.onAllNodes` thấy được trong 02e, nên không dự kiến vấn đề, nhưng chưa kiểm cho ô nhập (02e kiểm trên preview).

## Giới hạn đã biết
- Một máy (Tecno KJ7, Android 14); không so với Pixel.
- Không chứng minh `hiltViewModel()` hay nút `+` thật; không xoay thật.
- Không chứng minh quảng cáo hết gây đỏ cho các test khác dùng `MainActivity` (đã grep: `EInkVolumeDispatchInstrumentationTest` là test duy nhất còn khởi động `MainActivity` bằng `ActivityScenario`); chỉ 02d được chuyển, và test kia **chưa đo** xem có chịu quảng cáo hay không.

## Điều chỉnh khi thực thi
- **Hộp thoại dựa vào `LaunchedEffect(visible)`:** `init()` chạy khi `visible`, `reset()` xóa mọi state khi không `visible`; test gọi `showDrawer()` rồi dựng `SubscribeDialog`. Danh sách nhóm đọc từ Room (chỉ đọc, không ghi).
- **Không đổi factory, không `autoAdvance`:** `waitUntil` mặc định đủ trên Activity test.
- **Thử `waitFor` bỏ qua lỗi tạm rồi gỡ:** không chữa được (ComposeTimeoutException sau 15 s); đã gỡ, giữ `waitUntil` thường.
- **Gốc lượt đỏ:** `AdActivity` của SDK quảng cáo đè lên Activity test (`lifecycle=STARTED`, `windowFocus=false`, Compose báo "No compose hierarchies found"). Đo bản cuối: 3/40 đỏ, cả 3 có `AdActivity`, 0/37 xanh có. Owner chấp nhận ~7% đỏ do quảng cáo; quy tắc "từ 2 đỏ trong 40 thì dừng" đã kích hoạt, đã điều tra và hỏi owner.
- **Nhật ký chẩn đoán tạm** (trạng thái Activity, số `ComposeView`, watcher ngoài) đã gỡ khỏi mã; chỉ còn `describe()` dạng số.
- **Mutation:** 4 đột biến đều làm đúng hai test đỏ (xem báo cáo).
