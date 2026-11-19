# EXC-05e (partial; EXC-05 remains in progress): kiểm EXC-05d trên MỘT máy thật

**Date:** 2026-11-19
**Branch:** `dev`
**Device:** chỉ **Pixel 7 Pro `2B051FDH3006MU`** (Android 17, Room v22). Tecno KJ7 rơi khỏi adb nên **không có máy thứ hai**: đây **không** phải kiểm hai máy.
**Scope:** chỉ thêm **một tệp test** `app/src/androidTest/.../infrastructure/p2psync/AutoReceiveTwoPhonesDeviceTest.kt` (2 test; tên lớp giữ từ bản hai máy ban đầu). Không đổi mã production, giao thức, Room, DataStore, manifest, `app/build.gradle`; không dependency mới.
**Spec:** `docs/superpowers/specs/2026-11-19-exc-05e-auto-receive-two-phones-device-test-design.md` (viết cho hai máy; **không còn đúng cho phần "hai máy"**, xem Điều chỉnh).

## Việc đã làm
Hai `LocalSyncViewModel` thật chạy **cạnh nhau trên cùng một máy**: một bên **phát** (`servePaired`, như máy kia sẽ làm), một bên là phía **nhận** của màn hình Đồng bộ, gọi `maybeAutoReceive` đúng như `LaunchedEffect` ở `HomeEntry` (bản sao vòng gọi). Dùng **NSD thật, Keystore thật, engine thật, Room thật (chỉ đọc), Wi-Fi thật**. Mỗi bên có **tệp pairing riêng** (đổi tên tệp qua `ContextWrapper`), nên `pairing_store` thật của app **không bị ghi**. Hai test: (1) một máy ghép + một máy phát: **tự hiện hộp xem trước, không Apply, Room không đổi**; (2) hai máy ghép + một máy phát (đã thấy lượt phát qua NSD): **không tự nhận** (trạng thái vẫn `Idle`, Room không đổi).

## Kiểm chứng (đo thật, Pixel)
- **15 lượt: 15/15 xanh (2 test mỗi lượt)**: lượt thử đầu, rồi 10 lượt có thời gian (4.452 đến 4.708 ms), rồi 5 lượt có mốc chi tiết. Mỗi lượt có cổng chỉ đọc `GATE OK`; sao lưu byte-exact trước mỗi đợt; khôi phục `RESTORE OK` (mọi tệp **BYTE IDENTICAL**); băm DataStore trước và sau **trùng** (`ed9f68879fc775bb5fb1b5f24206a6c1a924bcee`); xoay `1 0`; quyền `ACCESS_LOCAL_NETWORK` cấp tạm rồi **thu hồi** (xác nhận `granted=true` đếm 0); APK test đã gỡ.
- **Thời gian từ lúc bắt đầu đến xem trước: 4.452 đến 4.833 ms** (15 lượt, trung vị khoảng 4,6 s). **Vượt tiêu chí 3 giây của EXC-05 gốc.** Chia nhỏ (5 lượt có mốc): bên phát **sẵn sàng sau 1.819 đến 2.018 ms** (chụp snapshot Room, mã hóa PBKDF2 310.000 vòng, mở máy chủ, đăng ký NSD); từ lúc bắt đầu tìm đến xem trước **2.744 đến 2.863 ms** (NSD tìm thấy, tải, giải mã PBKDF2, dựng xem trước). Hai phần cộng lại khoảng 4,6 s. **Phía nhận một mình (tìm đến xem trước) ~2,8 s, gần sát 3 s**; tiêu chí 3 giây của bản gốc ("hai máy tự nhận diện... trong vòng 3 giây") chưa đạt nếu tính cả thời gian phát chuẩn bị, và **chưa đo giữa hai máy** (còn độ trễ radio).
- **Đột biến trên máy 2/2 khớp:** d1 (`maybeAutoReceive` không gọi `receiveFromPaired`) đỏ đúng test "một máy ghép, một máy phát"; d2 (bỏ điều kiện "đúng một máy ghép/phát") đỏ đúng test "hai máy ghép". Batch đầu và cuối xanh (`base`). Mã khôi phục giống hệt (`cmp`), `git diff` production rỗng.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.908 test, 0 lỗi** (không đổi, vì chỉ thêm test máy).

## Điều chỉnh khi thực thi
- Spec nêu hai máy (Tecno + Pixel). **Tecno rơi khỏi adb** trước khi cài gì; Pixel là máy duy nhất. Owner chọn biến thể một máy (2026-11-19). Tecno **không** bị nâng Room v20 lên v22 (rủi ro đã được chấp nhận trong spec không xảy ra vì không cài).
- Pixel bị các app khác của bạn tự bật lên làm cổng dừng ba lần (SkyJoy hai lần, Facebook một lần); theo lời bạn tôi chỉ `force-stop` đúng từng gói đó rồi về launcher, ghi từng lần, không đụng dữ liệu của chúng.
- Test đầu tiên in thời gian bằng `println`, không hiện trong chạy instrument thô; đổi sang `sendStatus` (số đo chỉ có từ lượt thứ hai).
- Lỗi tên trong script thống kê (`exc05e_fi d_to_preview_ms`) chỉ là lỗi hiển thị của `tr`, không phải của số đo.

## Giới hạn (nói thẳng)
- **Không phải hai máy:** không có độ trễ radio, không có hai bộ Keystore khác nhau, không có hai tiến trình app. Hai ViewModel dùng chung một Room, một Keystore.
- **Chưa chứng minh `HomeEntry`/`LaunchedEffect` thật, `LocalSyncPage` thật, hộp thoại quyền Android 17** (cấp bằng `pm grant`), ghép bằng giao diện, tự phát nền, ACK.
- **Tiêu chí 3 giây chưa đạt và chưa đo đúng:** phía nhận ~2,8 s, tổng ~4,6 s; cần hai máy thật để biết con số thật. Phần lớn thời gian là PBKDF2 310.000 vòng ở cả hai phía (đã nêu ở 05a), không phải NSD.
- Một máy, một mạng Wi-Fi; Android 17 duy nhất.
- Task gốc EXC-05 còn: kiểm hai máy thật, tự phát nền, ACK, đo 3 giây giữa hai máy.
