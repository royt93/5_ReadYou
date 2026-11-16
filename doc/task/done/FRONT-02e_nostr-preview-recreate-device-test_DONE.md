# FRONT-02e (Test máy thật: bản xem trước Nostr khi Activity tạo lại hoặc xoay màn hình)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Scope:** chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore. Chỉ thêm **một** dòng Gradle: `androidTestImplementation "com.squareup.okhttp3:mockwebserver3-junit4:5.0.0-alpha.11"` (owner cho phép; cùng phiên bản đã dùng cho unit test). Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-15-front-02e-nostr-preview-recreate-device-test-design.md`, `docs/superpowers/plans/2026-11-15-front-02e-nostr-preview-recreate-device-test.md`.

## Điều test chứng minh

Test dùng **một `ComponentActivity` của test (không phải `MainActivity`)**. `SubscribeViewModel` thật được lấy qua `ViewModelProvider(activity, factory)` (ViewModelStore thật), dựng từ các singleton thật của app (đọc từ field `@Inject` công khai của `RApp`), nhưng `NostrRelayClient` trỏ vào một **relay giả** (`MockWebServer` WebSocket trên `127.0.0.1`, không mạng thật). Bốn test:

1. `fixtures_areSignedAndNameTheProfile`: ghi chú và hồ sơ sao chép từ fixture JVM vẫn có chữ ký hợp lệ và hồ sơ cho tên `Alice Liddell`.
2. `previewSurvivesRecreate`: tìm một `npub`, có bản xem trước (tên `Nostr · Alice Liddell`, 1 ghi chú, ô nhập bị khóa); `recreate()`: ViewModelStore trả đúng **cùng instance**, hộp thoại dựng lại bằng `setContent`, tiêu đề hiện trên màn hình, state còn nguyên, relay chỉ nhận 1 yêu cầu.
3. `previewSurvivesRealRotation`: xoay thật bằng `UiAutomation.setRotation` (90° rồi 0°); state còn nguyên, cùng ViewModel, relay chỉ nhận 1 yêu cầu. Activity test không có `configChanges` nên có thể bị tạo lại; test ghi lại việc đó.
4. `recreateWhileTheRelayIsStillAnswering_stillShowsThePreview`: giữ phản hồi của relay bằng cổng, `recreate()` lúc relay còn im lặng, rồi mở cổng: bản xem trước vẫn đến đúng, không lỗi, **không** có lần tìm thứ hai.

## Nói thẳng về phạm vi

- **Không** chứng minh `hiltViewModel()` trong `FeedsPage` giữ xem trước Nostr trên `MainActivity` thật (FRONT-02d chỉ chứng minh chữ đã gõ ở đó). Test này chứng minh: (i) ViewModelStore của Android giữ `SubscribeViewModel` qua `recreate()`/xoay, (ii) `SubscribeDialog` dựng lại đúng từ state còn nguyên.
- Một relay giả, **không** hai relay, **không** relay thật, không mạng thật.
- **Không kiểm:** xuống nền, process bị giết, đổi ngôn ngữ/chế độ tối thật, đồng bộ nền, mở `njump.me`, tên hồ sơ thật.
- Lint **không quét** `androidTest`, nên lint không kiểm các tệp test mới; chỉ biên dịch được kiểm.

## Kiểm chứng

**Hai máy, nói rõ để không nhầm.** Phần lớn bằng chứng đến từ **Pixel 7 Pro (Android 17)**; owner sau đó ra lệnh "chỉ dùng tecno, cấm đụng pixel" (Pixel đang chạy app của owner), nên phần cuối chạy trên **Tecno KJ7 (Android 14, 1080x2436)**. Số liệu hai máy không so sánh được.

- **Pixel, trước khi phiên khác chen vào:** một lượt thử 4/4; **40/40 xanh** (`OK (4 tests)`).
- **Pixel, mutation (lô hợp lệ, phiên khác im lặng):** `base` xanh; `m01` (`init()` xóa feed/chữ) đỏ `previewSurvivesRecreate` (1/4); `m02` (bỏ gán `feed`) đỏ 3/4; `m03` (bỏ khóa ô) đỏ 3/4; `m04` (tên bỏ qua hồ sơ) đỏ 3/4. **Một lô trước đó bị loại** vì phiên Claude khác (lens-launcher) chạm Pixel đúng lúc lượt `m01` kết thúc (đã báo và đối chiếu giờ log).
- **Tecno KJ7, sau lệnh của owner:** `base` xanh; `m05` (bỏ ghi chú) đỏ 3/4; `m06` (đột biến **trong test**: dựng ViewModel ngoài `ViewModelProvider`) đỏ 3/4, cả ba ở `assertSame`: chứng minh khẳng định cùng-instance là phần làm test có nghĩa, không đúng một cách tầm thường; `base` cuối xanh. **40/40 xanh** trên Tecno. `m01`..`m04` **không** chạy lại trên Tecno.
- **Lỗi của chính test đã sửa:** lần chạy `m06` đầu trên Tecno có thêm một lỗi `IOException: Gave up waiting for queue to shut down`: `NostrFakeRelay.close()` tắt server trong khi một luồng còn bị cổng giữ. Sửa: mở cổng trước khi tắt. Chạy lại `m05`, `m06`, `base` sau sửa: sạch.
- JVM full suite `--rerun-tasks`: **3.627 test, 0 lỗi, 0 bỏ qua** (475 lớp), không đổi. `assembleDevDebug assembleDevDebugAndroidTest` thành công. `git diff app/src/main` và `app/schemas` rỗng; `app/build.gradle` chỉ thêm đúng 2 dòng (chú thích + dependency).
- **Máy Tecno sau mỗi vòng:** cổng chỉ đọc qua trước mỗi lượt (launcher `com.transsion.hilauncher` trên cùng, mở khóa, không instrumentation, xoay `1 0`, 1080x2436); sao lưu và khôi phục byte-exact **6 tệp**; băm DataStore `9ca1c202…` trùng; đã gỡ `com.mckimquyen.reader.test`; xoay vẫn `1 0`.

## Điều chưa giải thích và việc còn dang dở (nói thẳng)

- **Pixel đang bị lệch `user_rotation=1`** (ban đầu 0, `accelerometer_rotation=1`). Không có bằng chứng trực tiếp là do ai: nó xuất hiện sau một lượt `m03` có test xoay đỏ ở dòng 180 (lần hai) và sau `m03`/`m04`; phiên khác đã nhắn im lặng, nhưng không thể loại trừ hoàn toàn. Tôi đã thử sửa `@After` (`FREEZE_0` rồi `UNFREEZE`) và **giả thuyết đó bị bác**: lệch vẫn xảy ra. Tôi đã hoàn lại thay đổi đó ở test FRONT-02d (chưa kiểm lại, ngoài phạm vi); test FRONT-02e giữ `@After` đã sửa vì nó đã được kiểm 40/40 trên Tecno, nơi xoay luôn trả về `1 0`. **Tôi KHÔNG chạm Pixel nữa theo lệnh của owner**: owner cần tự đặt lại `user_rotation=0` (hoặc cho phép).
- **Pixel còn dữ liệu `com.mckimquyen.reader` (bản dev tôi cài)** đã khôi phục byte-exact ở vòng cuối trên Pixel; sau đó không đụng.
- Tecno KJ7 không gặp lệch xoay, nhưng đó cũng là một máy khác (Android 14, không có `configChanges` khác biệt), nên không chứng minh Pixel/Android 17 sạch.
- `/tmp` bị dọn giữa chừng (mất script, bản sao lưu cũ); công cụ được dựng lại ngoài `/tmp` (không nằm trong repo).
- Chập chờn bước gõ chữ của FRONT-02d (gốc chưa rõ) **không tái xuất** ở đây (test này gõ `npub`; 40/40 Pixel và 40/40 Tecno), nhưng không chứng minh nó biến mất.

## Giới hạn

- Hai máy khác nhau, không so được; `m01`..`m04` chỉ chạy trên Pixel, `m05`, `m06` chỉ chạy trên Tecno.
- Activity test không phải `MainActivity`; không kiểm `HomeEntry`/`hiltViewModel()`.
- Test dùng `NostrDeviceFixtures` sao chép từ `NostrTestEvents`; nếu fixture JVM đổi, test thiết bị không tự đổi theo (test 1 bắt chữ ký sai, không bắt bản sao lỗi thời).

## Commits trên `dev`

`28880c82` spec · `19bc158d` plan · (commit test + tài liệu: xem `git log`)
