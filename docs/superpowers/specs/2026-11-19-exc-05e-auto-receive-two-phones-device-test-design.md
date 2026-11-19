# EXC-05e — Kiểm EXC-05d trên hai máy thật (Tecno KJ7 + Pixel 7 Pro) bằng instrumentation (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** EXC-05d (`AutoReceivePolicy`, `LocalSyncViewModel.maybeAutoReceive`), 05b (NSD + Keystore), 05c (hai máy thật)

> Trạng thái: owner cho phép dùng Pixel + Tecno **chỉ cho việc này** (2026-11-19) và chọn "điều khiển bằng intent/instrument, không tọa độ". Cả hai máy đã qua cổng chỉ đọc và đã **sao lưu byte-exact** (Tecno: Room v20, 212 bài; Pixel: Room v22, 1.785 bài). Chưa cài gì. Spec chờ owner duyệt trước khi lập plan. Chưa có mã.

## Mục tiêu
Chứng minh trên hai máy thật, với NSD thật, Keystore thật, Room thật và mạng Wi-Fi thật: máy nhận mở màn hình Đồng bộ (ViewModel thật) khi máy kia đang phát thì **tự thấy hộp xem trước** không cần bấm nhận, **không tự ghi vào Room**, và đo thời gian từ lúc bắt đầu tìm đến lúc có xem trước (tiêu chí 3 giây của EXC-05 gốc). Chỉ thêm test `androidTest`; **không đổi mã production**, giao thức, Room, DataStore, manifest, `app/build.gradle`; không dependency.

## Sự thật đã đọc (2026-11-19)
- Cả hai máy đang cài app `2026.09.06`. Pixel là Android 17 (cần cấp tạm `ACCESS_LOCAL_NETWORK`, như 05b: runner cấp và thu hồi). APK dev debug mới (`22d9ce4e`) và APK test có thể build bằng `assembleDevDebug assembleDevDebugAndroidTest`; cài **đè** app giữ dữ liệu (`install -r`).
- **Pixel có Room v22, Tecno v20** (app cũ trên Tecno). Cài APK mới lên Tecno sẽ **nâng Room lên v22** ngay khi mở app: đó là một thay đổi dữ liệu thật không hoàn tác bằng khôi phục byte-exact của tệp v20 + app mới. Xem Rủi ro.
- Mục Cài đặt "Local Sync" dẫn tới route `LOCAL_SYNC`; `LocalSyncPage` có thẻ test (`SYNC_PEER_RECEIVE_TAG`, `SYNC_CONFIRM_APPLY_TAG`...). Không có deep link tới route này, và điều khiển qua `MainActivity` bằng bấm dễ dính quảng cáo toàn màn hình (đã đo ở 02f).
- Các test thiết bị sẵn có (`PairingNsdDeviceTest`, `LocalSyncDeviceTest`) dùng **NSD thật** và **engine thật**; `KeystoreSecretWrapperDeviceTest` dùng Keystore thật.

## Thiết kế
Một lớp test mới `app/src/androidTest/.../infrastructure/p2psync/AutoReceiveTwoPhonesDeviceTest.kt` chạy **hai vai** trên hai máy cùng lúc, điều phối từ máy tính bằng một script (`/tmp`, không commit):
- **Vai phát (máy S):** dựng `LocalSyncViewModel` thật (engine, LAN, Keystore, NSD thật; Room thật chỉ **đọc** để lấy snapshot), ghép sẵn một máy bằng `PairingStore.add(label, secret)` với **secret chung do script sinh và truyền qua đối số instrument** (không gõ mã ghép trong giao diện: việc ghép bằng giao diện đã kiểm ở 05b/05c), gọi `servePaired(...)`, in `READY`, giữ phát tới khi nhận tín hiệu kết thúc (đọc cờ trong thư mục cache của test).
- **Vai nhận (máy R):** ghép sẵn **cùng secret** bằng `PairingStore.add`, dựng `LocalSyncViewModel` thật, bắt đầu `startFindingPeers()` và gọi `maybeAutoReceive(...)` mỗi khi `discovered`/`pairedPeers` đổi (**bản sao 5 dòng của `LaunchedEffect` ở `HomeEntry`**), chờ `state` thành `Preview`, ghi thời gian, **không Apply**, rồi `cancelPreview()`. Kiểm: số bài trong xem trước khớp snapshot của máy S; Room của máy R **không đổi** (đếm bài/nguồn trước và sau).
- Hai ca: (1) **đúng một máy ghép + đúng một máy phát** → tự thấy xem trước; (2) **hai máy phát** (S phát hai dịch vụ giả? **bỏ**: không dựng được hai máy phát với hai máy thật) → thay bằng ca **máy R có hai máy ghép** (thêm một secret thứ hai) → **không** tự nhận (state vẫn `Idle`, 0 yêu cầu mạng).
- Đổi vai (Tecno phát, Pixel nhận; rồi Pixel phát, Tecno nhận) để kiểm cả hai chiều.

## Phạm vi trung thực
- **Chứng minh:** trên hai máy thật, `LocalSyncViewModel` + NSD thật + Keystore thật + mạng thật: tự nhận hiện xem trước, không tự ghi Room, đo thời gian; hai máy ghép thì không tự nhận.
- **Không chứng minh:** `HomeEntry` và `LaunchedEffect` thật (test dùng **bản sao** của vòng gọi), màn hình `LocalSyncPage` thật, ghép bằng giao diện, hộp thoại quyền Android 17 (cấp bằng `pm grant`), tự phát nền, ACK, ba máy, một yêu cầu mạng từ máy lạ.
- **Nói thẳng:** đây **không** thay thế việc bấm tay trên màn hình thật; nó chứng minh phần logic và mạng ở dưới màn hình.

## Rủi ro đã biết (cần owner biết trước khi duyệt)
1. **Cài app mới lên Tecno nâng Room v20 lên v22** khi mở app (thay đổi dữ liệu có thật). Khôi phục tệp v20 sẽ chạy lại với app **mới** (v22) và migration chạy lại lúc mở; app cũ không còn trên máy. Chọn: (a) chấp nhận (migration 20 đến 22 đã có test và đã chạy ở Pixel), hoặc (b) chỉ dùng Pixel + một máy khác đã ở v22 (không có), hoặc (c) bỏ Tecno, kiểm trong Pixel với hai tiến trình (không được: NSD cần hai máy).
2. **Ghi pairing store** trên cả hai máy (khôi phục byte-exact ở cuối; Pixel có sẵn một pairing store 131 byte).
3. **Cấp tạm `ACCESS_LOCAL_NETWORK` trên Pixel**, thu hồi cuối.
4. **HiOS (Tecno) có thể đóng băng NSD** (đã gặp ở 05a: "HiOS freezer"): lượt đỏ vì NSD chưa chắc là lỗi của EXC-05d; ghi số thật.
5. **Quảng cáo**: test chạy trên `ComponentActivity`, không `MainActivity`; owner bảo bỏ qua ad.
6. Pixel đang chạy app SkyJoy của owner: tôi đã đóng app đó theo lời owner; sẽ không chạm lại.

## Kiểm chứng
- 5 lượt mỗi chiều (10 lượt) có cổng + sao lưu + khôi phục byte-exact; không `sleep` tăng timeout để cứu lượt đỏ; lượt đỏ phải đọc nguyên nhân.
- Đột biến trên máy: (a) `maybeAutoReceive` không gọi `receiveFromPaired` (đỏ ca 1); (b) `AutoReceivePolicy` bỏ điều kiện "đúng một máy ghép" (đỏ ca 2); mỗi đột biến phải làm đúng ca dự định đỏ (đổi tạm, build, cài, chạy, khôi phục, `cmp`).
- JVM full không đổi (số trước 3.908); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest`.
- Báo cáo: thời gian đo được (so với 3 giây), số lượt đỏ và nguyên nhân, bảng đột biến, hash trước/sau của mọi tệp đã sao lưu, quyền đã thu hồi, những gì không kiểm.

## Giới hạn đã biết
- Hai máy, một mạng Wi-Fi; chưa ba máy; chưa NSD các API khác ngoài Android 14 (Tecno) và 17 (Pixel).

## Điều chỉnh khi thực thi
- **Chỉ còn một máy (Pixel):** Tecno rơi khỏi adb trước khi cài gì. Owner chọn biến thể **một máy** (hai ViewModel cùng máy, mỗi bên một tệp pairing riêng qua `ContextWrapper`). Mọi chỗ spec nói "hai máy" chỉ đúng cho bản gốc chưa chạy; kết quả thật ở báo cáo `EXC-05e_auto-receive-one-phone-device-test_DONE.md`. Rủi ro 1 (nâng Room Tecno v20 lên v22) **không xảy ra** (không cài lên Tecno).
- Đột biến chạy trên máy: d1 (không gọi `receiveFromPaired`) và d2 (bỏ điều kiện "đúng một") thay cho (a) và (b) trong spec (cùng ý).
- **Kết quả:** 15/15 lượt xanh, đột biến 2/2 khớp, JVM 3.908 test (không đổi); thời gian bắt đầu đến xem trước 4,45 đến 4,83 s, chưa đạt 3 s.
