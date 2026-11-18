# FRONT-02e (Test máy thật: bản xem trước Nostr khi Activity tạo lại hoặc xoay màn hình)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Scope:** chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore. Chỉ thêm **một** dòng Gradle: `androidTestImplementation "com.squareup.okhttp3:mockwebserver3-junit4:5.0.0-alpha.11"` (owner cho phép; cùng phiên bản đã dùng cho unit test). Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-15-front-02e-nostr-preview-recreate-device-test-design.md`, `docs/superpowers/plans/2026-11-15-front-02e-nostr-preview-recreate-device-test.md`.

## Điều test chứng minh

Test dùng **một `ComponentActivity` của test (không phải `MainActivity`)**. `SubscribeViewModel` thật được lấy qua `ViewModelProvider(activity, factory)` (ViewModelStore thật), dựng từ các singleton thật của app (đọc từ field `@Inject` công khai của `RApp`), nhưng `NostrRelayClient` trỏ vào một **relay giả** (`MockWebServer` WebSocket trên `127.0.0.1`, không mạng thật). Ba test (từ 2026-11-18; trước đó là bốn, xem mục "Test xoay thật đã gỡ"):

1. `fixtures_areSignedAndNameTheProfile`: ghi chú và hồ sơ sao chép từ fixture JVM vẫn có chữ ký hợp lệ và hồ sơ cho tên `Alice Liddell`.
2. `previewSurvivesRecreate`: tìm một `npub`, có bản xem trước (tên `Nostr · Alice Liddell`, 1 ghi chú, ô nhập bị khóa); `recreate()`: ViewModelStore trả đúng **cùng instance**, hộp thoại dựng lại bằng `setContent`, tiêu đề hiện trên màn hình, state còn nguyên, relay chỉ nhận 1 yêu cầu.
3. `recreateWhileTheRelayIsStillAnswering_stillShowsThePreview`: giữ phản hồi của relay bằng cổng, `recreate()` lúc relay còn im lặng, rồi mở cổng: bản xem trước vẫn đến đúng, không lỗi, **không** có lần tìm thứ hai.

## Nói thẳng về phạm vi

- **Không** chứng minh `hiltViewModel()` trong `FeedsPage` giữ xem trước Nostr trên `MainActivity` thật (FRONT-02d chỉ chứng minh chữ đã gõ ở đó). Test này chứng minh: (i) ViewModelStore của Android giữ `SubscribeViewModel` qua `recreate()`, (ii) `SubscribeDialog` dựng lại đúng từ state còn nguyên.
- Một relay giả, **không** hai relay, **không** relay thật, không mạng thật.
- **Không kiểm:** xoay màn hình thật trên Activity test (đã gỡ, xem dưới; FRONT-02d vẫn xoay thật trên `MainActivity`), xuống nền, process bị giết, đổi ngôn ngữ/chế độ tối thật, đồng bộ nền, mở `njump.me`, tên hồ sơ thật.
- Lint **không quét** `androidTest`, nên lint không kiểm các tệp test mới; chỉ biên dịch được kiểm.

## Kiểm chứng

**Hai máy, nói rõ để không nhầm.** Phần lớn bằng chứng đến từ **Pixel 7 Pro (Android 17)**; owner sau đó ra lệnh "chỉ dùng tecno, cấm đụng pixel" (Pixel đang chạy app của owner), nên phần cuối chạy trên **Tecno KJ7 (Android 14, 1080x2436)**. Số liệu hai máy không so sánh được.

- **Pixel, trước khi phiên khác chen vào:** một lượt thử 4/4; **40/40 xanh** (`OK (4 tests)`).
- **Pixel, mutation (lô hợp lệ, phiên khác im lặng):** `base` xanh; `m01` (`init()` xóa feed/chữ) đỏ `previewSurvivesRecreate` (1/4); `m02` (bỏ gán `feed`) đỏ 3/4; `m03` (bỏ khóa ô) đỏ 3/4; `m04` (tên bỏ qua hồ sơ) đỏ 3/4. **Một lô trước đó bị loại** vì phiên Claude khác (lens-launcher) chạm Pixel đúng lúc lượt `m01` kết thúc (đã báo và đối chiếu giờ log).
- **Tecno KJ7, 2026-11-18, `m01`..`m04` (lô riêng, cổng đo xoay `1 0`, sao lưu và khôi phục byte-exact, băm DataStore `9ca1c202…` trùng):** `base` xanh; `m01` (`init()` xóa feed/chữ) đỏ `previewSurvivesRecreate` (1/4); `m02` (bỏ gán `feed`) đỏ 3/4; `m03` (bỏ khóa ô) đỏ 3/4; `m04` (tên bỏ qua hồ sơ) đỏ 3/4 (`expected:<Nostr · Alice Liddell> but was:<Nostr · npub1mlc…7a0a>`), `base` cuối xanh. **Khớp từng test đỏ với Pixel** cho `m01`..`m04`. Lô này bị cắt giữa chừng bởi `user_rotation=1` (xem dưới) nên `m04` và `base` cuối chạy ở lô thứ hai sau khi owner cho phép đặt lại về 0.
- **Tecno KJ7, sau lệnh của owner (2026-11-16):** `base` xanh; `m05` (bỏ ghi chú) đỏ 3/4; `m06` (đột biến **trong test**: dựng ViewModel ngoài `ViewModelProvider`) đỏ 3/4, cả ba ở `assertSame`: chứng minh khẳng định cùng-instance là phần làm test có nghĩa, không đúng một cách tầm thường; `base` cuối xanh. **40/40 xanh** trên Tecno. `m01`..`m04` **không** chạy lại trên Tecno.
- **Lỗi của chính test đã sửa:** lần chạy `m06` đầu trên Tecno có thêm một lỗi `IOException: Gave up waiting for queue to shut down`: `NostrFakeRelay.close()` tắt server trong khi một luồng còn bị cổng giữ. Sửa: mở cổng trước khi tắt. Chạy lại `m05`, `m06`, `base` sau sửa: sạch.
- JVM full suite `--rerun-tasks`: **3.627 test, 0 lỗi, 0 bỏ qua** (475 lớp), không đổi. `assembleDevDebug assembleDevDebugAndroidTest` thành công. `git diff app/src/main` và `app/schemas` rỗng; `app/build.gradle` chỉ thêm đúng 2 dòng (chú thích + dependency).
- **Máy Tecno sau mỗi vòng:** cổng chỉ đọc qua trước mỗi lượt (launcher `com.transsion.hilauncher` trên cùng, mở khóa, không instrumentation, xoay `1 0`, 1080x2436); sao lưu và khôi phục byte-exact **6 tệp**; băm DataStore `9ca1c202…` trùng; đã gỡ `com.mckimquyen.reader.test`; xoay vẫn `1 0`.

## Điều chưa giải thích và việc còn dang dở (nói thẳng)

- **Pixel đang bị lệch `user_rotation=1`** (ban đầu 0, `accelerometer_rotation=1`). Không có bằng chứng trực tiếp là do ai: nó xuất hiện sau một lượt `m03` có test xoay đỏ ở dòng 180 (lần hai) và sau `m03`/`m04`; phiên khác đã nhắn im lặng, nhưng không thể loại trừ hoàn toàn. Tôi đã thử sửa `@After` (`FREEZE_0` rồi `UNFREEZE`) và **giả thuyết đó bị bác**: lệch vẫn xảy ra. Tôi đã hoàn lại thay đổi đó ở test FRONT-02d (chưa kiểm lại, ngoài phạm vi); test FRONT-02e giữ `@After` đã sửa vì nó đã được kiểm 40/40 trên Tecno, nơi xoay luôn trả về `1 0`. **Tôi KHÔNG chạm Pixel nữa theo lệnh của owner**: owner cần tự đặt lại `user_rotation=0` (hoặc cho phép).
- **Pixel còn dữ liệu `com.mckimquyen.reader` (bản dev tôi cài)** đã khôi phục byte-exact ở vòng cuối trên Pixel; sau đó không đụng.
- **Cập nhật 2026-11-18 (Tecno KJ7, Android 14): câu "Tecno không gặp lệch xoay" ở bản trước là SAI**, tôi đã sửa. Chạy `m01`..`m03` trên Tecno: `user_rotation` thành **1** sau `m03` (lượt có `previewSurvivesRealRotation` đỏ ở dòng 180), cổng chặn `m04`. Vậy lệch xảy ra trên **cả hai máy** (Pixel Android 17, Tecno Android 14), chỉ có test của tôi chạm Tecno, nên **không phải do phiên khác**.
- **Cơ chế `UiAutomation.setRotation` (đo bằng probe riêng, không dính test, trên Tecno):** `FREEZE_90` đặt `user_rotation=1` và `accelerometer_rotation=0`; `UNFREEZE` chỉ trả `accelerometer_rotation` về 1 và **không** trả `user_rotation`; `FREEZE_0` mới đưa `user_rotation` về 0. Chuỗi `FREEZE_90`, `FREEZE_0`, `UNFREEZE` (kể cả gọi liền nhau không chờ, 3/3 lần) kết thúc sạch (`user_rotation=0`, `accelerometer_rotation=1`); chuỗi `FREEZE_90`, `UNFREEZE` hoặc dừng sau `FREEZE_90` để lại `user_rotation=1`.
- **Điều chưa giải thích:** `@After` hiện tại gọi đúng chuỗi sạch (`FREEZE_0` rồi `UNFREEZE`), nhưng lượt `m03` trên Tecno vẫn để lại `1`. Lượt `m04` (cũng đỏ ở dòng 180) ở lần chạy sau **không** để lại lệch (đo trực tiếp sau lượt: `user_rotation=0`, `accelerometer_rotation=1`). Chưa biết vì sao `@After` không phát huy ở `m03`. Hai khả năng, **chưa phân biệt**: lệnh `setRotation` trong `@After` không có hiệu lực khi Activity đang bị tạo lại, hoặc tiến trình test bị cắt giữa `@After`. Đã có số đo dòng thời gian (lệch xuất hiện ngay sau lượt) nhưng không có bản ghi `@After` có chạy hay không (không thêm nhật ký vào test đã commit).
- **Cách tôi xử lý lệch khi chạy:** script đột biến hiện đo `user_rotation` **trước** khi đặt lại về 0 (owner cho phép đặt lại), nên lệch vẫn được ghi nhận chứ không bị che.
- `/tmp` bị dọn giữa chừng (mất script, bản sao lưu cũ); công cụ được dựng lại ngoài `/tmp` (không nằm trong repo).
- Chập chờn bước gõ chữ của FRONT-02d (gốc chưa rõ) **không tái xuất** ở đây (test này gõ `npub`; 40/40 Pixel và 40/40 Tecno), nhưng không chứng minh nó biến mất.

## Test xoay thật đã gỡ khỏi 02e (2026-11-18, owner chọn) và vì sao

`previewSurvivesRealRotation` (xoay 90° rồi 0° bằng `UiAutomation.setRotation`) **đã bị gỡ**, cùng `@Before`/`@After` xoay. Lý do, theo thứ tự bằng chứng đo trên **Tecno KJ7 (Android 14)**:

1. **Cơ chế (probe riêng, không dính test):** `FREEZE_90` đặt `user_rotation=1` và `accelerometer_rotation=0`; `UNFREEZE` chỉ trả `accelerometer_rotation` về 1; `FREEZE_0` mới đưa `user_rotation` về 0.
2. **`@After` đúng chuỗi, chạy trọn, không ném lỗi** (nhật ký chỉ-số, 5 lượt ép test xoay đỏ bằng `m03`): `ENTER` luôn đủ cặp `EXIT`, `setRotation` luôn `returned=true`. Giả thuyết "`@After` không chạy hoặc bị cắt" **bị bác**.
3. **Chỗ lệch thật:** trong lượt lệch (run1), `UNFREEZE` **tự đặt lại `user_rotation=1`** dù ngay trước đó `FREEZE_0` vừa đặt 0 (`before: user=0` rồi `after: user=1`); trong lượt sạch (run2), cùng chuỗi, cùng trạng thái đầu vào, nó giữ 0. Hành vi này của hệ điều hành **không ổn định**; tôi chưa biết vì sao.
4. **Thử sửa bằng đọc lại và thử lại tối đa 3 lần** (`RotationRestore`, 6 test logic xanh, mutation 9/9 bắt): trên Tecno, 20 lượt ép test xoay đỏ vẫn có **2/11 lượt lệch** (lượt 12 trở đi bị cổng chặn), lệch bị báo thành lỗi thứ tư thay vì im lặng. **Thử lại không chữa được.** Nhật ký từng bước ở đợt sau (11 lượt, 0 lệch) không bắt được lượt lệch nào, nên **gốc cuối cùng vẫn chưa biết**. Mã `RotationRestore` **không** được giữ.
5. **Tần suất đo được, mẫu nhỏ:** 1/5, 2/11 và 0/11 lượt lệch ở ba đợt khác nhau trên Tecno; Pixel (Android 17) cũng lệch hai lần. Không đủ để ước tính tỉ lệ.

Sau khi gỡ: **12/12 lượt `base` xanh (`OK (3 tests)`) và 12/12 lượt `m03` đỏ, cả 24 lượt `user_rotation=0`, `accelerometer_rotation=1` ngay sau mỗi lượt** (Tecno, 2026-11-18). Điều đó cho thấy lệch chỉ đến từ test xoay, nhưng 24 lượt không chứng minh lệch biến mất.

**Hệ quả:** FRONT-02e **không còn kiểm xoay thật**. `m02`, `m03`, `m04` trước kia cũng bị bắt bởi test xoay; sau khi gỡ, ba mutation đó vẫn bị bắt bởi hai test recreate còn lại (đã đo ở lô 12 lượt `m03`: 12/12 đỏ). FRONT-02d giữ test xoay thật trên `MainActivity` (cùng cơ chế `@After`; **chưa kiểm** xem nó có cùng rủi ro lệch không, 190 lượt trước đó không để lại lệch).

## Giới hạn

- Hai máy khác nhau, số đo thời gian không so được; **cả sáu đột biến `m01`..`m06` nay đã chạy trên Tecno** (`m01`..`m04` ngày 2026-11-18, `m05`, `m06` ngày 2026-11-16), và `m01`..`m04` còn chạy trên Pixel; `m05`, `m06` chỉ trên Tecno.
- Activity test không phải `MainActivity`; không kiểm `HomeEntry`/`hiltViewModel()`.
- Test dùng `NostrDeviceFixtures` sao chép từ `NostrTestEvents`; nếu fixture JVM đổi, test thiết bị không tự đổi theo (test 1 bắt chữ ký sai, không bắt bản sao lỗi thời).

## Commits trên `dev`

`28880c82` spec · `19bc158d` plan · (commit test + tài liệu: xem `git log`)
