# FRONT-02d (Test máy thật: ô Thêm nguồn khi Activity tạo lại hoặc xoay màn hình)

**Date:** 2026-11-15  
**Branch:** `dev` (không push)  
**Scope:** chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, dependency. Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-15-front-02d-add-source-recreate-device-test-design.md`, `docs/superpowers/plans/2026-11-15-front-02d-add-source-recreate-device-test.md`.

## Điều test chứng minh

Trên **Pixel 7 Pro (`2B051FDH3006MU`, Android 17)**, với `MainActivity` thật, tìm nút `+` bằng Compose semantics (không tọa độ), mở hộp thoại **Thêm nguồn**, gõ chuỗi thử `abc-rotation-probe` (không phải khóa hay địa chỉ thật, **không bấm Tìm, không gọi mạng**) rồi gây gián đoạn:

1. `recreate_keepsTheDialogOpenAndTheTypedText`: sau `recreate()` Activity khác instance, hộp thoại còn mở, ô còn đúng chữ và vẫn sửa được (không bị khóa).
2. `realRotation_...withoutRecreatingTheActivity`: xoay thật bằng `UiAutomation.setRotation` (90° rồi 0°): hộp thoại và chữ còn nguyên, và Activity **cùng instance** (khóa sự thật rằng `configChanges` làm xoay không tạo lại Activity).
3. `recreateTwice_...`: `recreate()` hai lần liên tiếp, chữ còn.

Kết luận thực nghiệm: **giả thuyết "ViewModel giữ trạng thái qua `recreate()`" đúng** trên máy thật (khi test chạy qua được bước gõ).

## Nói thẳng về phạm vi

- **Không kiểm:** bản xem trước Nostr sau xoay (cần relay), app xuống nền rồi quay lại, process bị giết (ViewModel không lưu qua lần đó, chữ sẽ mất, đã biết), đổi ngôn ngữ hay chế độ tối thật, hai điện thoại, máy khác Pixel.
- `autoAdvance = false` bắt buộc: `FeedsPage.kt:127` tạo `rememberInfiniteTransition()` vô điều kiện (animation xoay nút Refresh), nên Compose không bao giờ rảnh. Lượt chạy đầu với đồng hồ mặc định **đỏ 3/3** vì `ComposeNotIdleException`. Đây là chẩn đoán từ stack và mã; **chưa chứng minh** bằng cách tắt animation (không đổi mã production theo spec).
- Máy không tắt tự xoay của owner: test đọc `ACCELEROMETER_ROTATION` trước, `@After` trả đúng trạng thái; kiểm sau mỗi vòng: `accelerometer_rotation=1`, `user_rotation=0`.

## Chập chờn đã gặp, và cách xử lý (trung thực)

Sau `autoAdvance = false`, bước **gõ chữ** (trước khi gây gián đoạn) thỉnh thoảng không có hiệu lực: ô còn đó, đã focus, nhưng chữ rỗng (`editableTextLengths=[0]`, `peakEditableTextLength=0`).

| Phiên bản test | Số lượt | Lượt đỏ |
|---|---|---|
| không đọc lại sau khi gõ | 11 | 3 |
| thêm dòng đọc lại ngay sau khi gõ | 36 | 0 |
| bỏ dòng đó, thêm chẩn đoán lúc đỏ | 24 + 40 | 0 + 5 (run13, 24, 33, 36, 39) |
| thêm chẩn đoán độ dài chữ lớn nhất | 40 | 2 (run7, run32) |
| **gõ lại khi chữ chưa từng vào ô** (bản cuối) | **40** | **0** |

- Mọi lượt đỏ nằm ở bước gõ; chưa lần nào đỏ ở bước kiểm sau `recreate()` hay xoay khi không có đột biến.
- **Nguyên nhân gốc chưa biết.** Bản cuối thử gõ lại tối đa 3 lần **chỉ khi** chữ chưa từng xuất hiện trong ô (peak = 0); nếu chữ đã vào rồi biến mất thì đỏ ngay, nên vẫn bắt được lỗi app mất chữ. Bản này che sự chập chờn của bước gõ (hạ tầng), không giải thích nó. 40/40 xanh không chứng minh hết chập chờn (tỉ lệ đỏ trước đó 2 đến 9%).
- Kiểm tra trước khi gõ lại: ô phải trống, nếu không thì dừng (không nối chữ).
- **Điều tra sau khi commit (2026-11-15), giả thuyết sai:** `BaseTextField.kt:55-58` tự `requestFocus()` sau `delay(100)` trên đồng hồ ảo, nên nghi test gõ trước khi focus đến. Phép thử một biến: chờ ô `isFocused()` rồi mới gõ, **bỏ** cơ chế gõ lại, 80 lượt trên Pixel: **79 xanh, 1 đỏ** với đúng triệu chứng cũ (`focusedEditable=1`, `editableTextLengths=[0]`, `peakEditableTextLength=0`). Lượt đỏ đã focus xong trước khi gõ mà chữ vẫn không vào, nên **focus muộn không phải nguyên nhân (hoặc không phải nguyên nhân duy nhất)**. Tỉ lệ 1/80 không phân biệt được với 2 đến 9% trước đó bằng mẫu này, nên **không kết luận** chờ focus có giúp hay không. Bản thử đã hoàn lại, test commit vẫn là bản gõ lại có điều kiện. **Chưa kiểm:** (c) `AnimatedContent` dựng lại ô nhập, (d) `LaunchedEffect(visible)` chạy `init()`/`reset()` muộn (xem `SubscribeDialog.kt:62`), và việc `performTextInput` đến trước khi ô nhận input connection.

## Kiểm chứng

- **Mutation chạy trên máy** (đổi tạm mã, build, cài, chạy, khôi phục; `cmp` giống hệt; `git diff app/src/main` = 0):

| Đột biến | Kết quả |
|---|---|
| `m01` `init()` xóa `linkContent` | `recreate` và `recreateTwice` đỏ ở `assertDialogStillOpenWithText` (2/3); xoay vẫn xanh vì xoay không tạo lại Activity |
| `m02` `inputLink` bỏ chữ | 3/3 đỏ ở bước gõ |
| `m03` `showDrawer` không mở | 3/3 đỏ ở bước mở |
| `m04` bỏ `configChanges` khỏi manifest | `realRotation` đỏ ở `assertSame` (1/3) |

  Hai lượt `base` (đầu và cuối batch) đều xanh 3/3.
- JVM full suite `--rerun-tasks`: **3.627 test, 0 lỗi, 0 bỏ qua** (475 lớp), không đổi so với trước. Lint: 0 lỗi, **2.465 cảnh báo** (bằng cũ), 10 thông tin, tệp test mới nằm trong `androidTest`, mà `lintDevDebug` mặc định **không quét** thư mục đó (0 mục nhắc tới `androidTest` trong báo cáo), nên lint **không kiểm** tệp này; chỉ biên dịch được kiểm. `assembleDevDebug assembleDevDebugAndroidTest` thành công.
- Tổng số lượt chạy test thiết bị: khoảng 190 lượt ba test (một lượt đầu thiếu `--user 0`, `am` chỉ in trợ giúp, chạy lại đúng).
- An toàn máy: cổng chỉ đọc trước mỗi lượt (launcher ở trên cùng, đã mở khóa, không instrumentation; có một lần cổng dừng đúng vì app khác `lenslauncher` đang ở trên cùng, owner thoát về màn hình chính). Sao lưu và khôi phục byte-exact **6 tệp** mỗi vòng, băm DataStore `ed9f6887…` luôn trùng, đã gỡ `com.mckimquyen.reader.test`. Một phiên khác dùng cùng Pixel đã được báo dừng và báo trả máy.
- Cổng đã sửa hai lần của tôi: `grep` sai tên launcher (Pixel dùng `com.google.android.apps.nexuslauncher`), và dòng `pidof` báo nhầm vì tiến trình test còn đóng băng sau mỗi lượt (đã bỏ, vẫn giữ kiểm `dumpsys activity processes`).

## Giới hạn

- Một máy (Pixel 7 Pro, Android 17); không so với Tecno KJ7 hay A50s.
- Test phụ thuộc: tài khoản Local, không ở màn khởi động lần đầu, không có quảng cáo che nút `+`; nếu không thì đỏ có lý do rõ.
- Chập chờn bước gõ chưa hiểu nguyên nhân (xem trên).
- `describeScreen()` chỉ ghi số lượng và cờ, không ghi chữ trên màn hình.

## Commits trên `dev`

`5996f211` spec · `473bcd8b` plan · (commit test + tài liệu: xem `git log`)
