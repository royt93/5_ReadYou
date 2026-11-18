# FRONT-02d (Test máy thật: ô Thêm nguồn khi Activity tạo lại hoặc xoay màn hình)

> **Đã được thay bằng FRONT-02s (2026-11-18).** Test `SubscribeDialogRecreateDeviceTest` nay chạy trên Activity test (không còn `MainActivity`, nút `+`, `hiltViewModel()` và xoay thật), vì `MainActivity` bị quảng cáo toàn màn hình làm đỏ khoảng 25% lượt trên Tecno. Nội dung dưới đây là kết quả của bản cũ (Pixel, `MainActivity`); lịch sử git giữ mã cũ. Xem `FRONT-02s_typed-text-recreate-on-test-activity_DONE.md`.

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

## Chạy lại trên Tecno KJ7 (2026-11-16): quảng cáo toàn màn hình làm test đỏ, không phải lỗi app

Sau lệnh của owner "chỉ dùng tecno, cấm đụng pixel", tôi chạy lại test này trên **Tecno KJ7 (Android 14)** để có bằng chứng đúng máy. Kết quả **không ổn định** và nguyên nhân **không phải ViewModel mất chữ**:

| Bản test | Lượt | Lượt đỏ |
|---|---|---|
| bản đã commit (`a52dbdee`) | 6 | 2 |
| thêm chẩn đoán | 24 | 5 |
| chẩn đoán trên luồng chính | 24 | 11 |
| thêm đóng quảng cáo bằng Back | 24 | 9 |

- Mọi lượt đỏ dừng ở `Never saw: the + button` với `No compose hierarchies found`.
- Chẩn đoán từ lifecycle monitor, đọc lúc đỏ: `AdActivity` ở trạng thái **RESUMED**, `MainActivity` **PAUSED**, khoảng **18 giây** sau khi test bắt đầu. Đó là quảng cáo toàn màn hình (interstitial) che `MainActivity`, nên Compose không còn gì để truy vấn.
- Tôi thử gửi phím Back khi `AdActivity` ở trên cùng (đúng quy tắc owner: chỉ Back). Bước này chạy 7 lần trong mỗi lượt đỏ mà quảng cáo **vẫn còn**. **Chưa biết vì sao**: có thể quảng cáo chặn Back, hoặc phím không tới đúng cửa sổ; chưa kiểm. Số lượt đỏ không giảm (9/24), mẫu quá nhỏ để nói Back làm tệ hơn hay không.
- Tôi **không** thử bấm nút đóng quảng cáo (cần tọa độ, bị cấm).
- Thay đổi test (chẩn đoán + bước Back) **đã hoàn lại, không commit**. Test đã commit giữ nguyên.
- **Đo lại ngày 2026-11-18 trên Tecno (bản đã commit, không đổi gì, 40 lượt, đo `user_rotation` ngay sau mỗi lượt):** **30 xanh, 10 đỏ (25%)**, cả 10 lượt đỏ cùng một lỗi `Never saw: the + button` với `No compose hierarchies found` (khớp với mô tả ở trên; hôm nay **không** đo lại `AdActivity` vì test không ghi nhật ký vòng đời, nên "do quảng cáo" ở đây là kết luận của ngày 11-16, chưa kiểm lại). Sao lưu và khôi phục byte-exact, băm DataStore `9ca1c202…` trùng.
- **Rủi ro lệch xoay của test này (câu hỏi mở từ FRONT-02e): 0 trên 40 lượt.** Sau mỗi lượt, kể cả lượt đỏ, `user_rotation=0` và `accelerometer_rotation=1`, không lần nào phải đặt lại. Test này xoay thật trên `MainActivity` (có `configChanges`) với cùng `@After` (`UNFREEZE`, hoặc `FREEZE_0` nếu tự xoay ban đầu tắt). **Chưa chứng minh an toàn, nhưng khác 02e rõ rệt:** với 0 lệch trong 40 lượt, cận trên 95% của tỉ lệ lệch là khoảng **7,2%** (tính: 1 trừ 0,05 mũ 1/40); nếu 02d lệch bằng tỉ lệ 02e (khoảng 18 đến 20%) thì xác suất thấy 0/40 chỉ cỡ **0,01 đến 0,04%**, nên rất khó là cùng tỉ lệ. Chỉ đo trên Tecno; 02e lệch 1/5 và 2/11 trên cùng máy nhưng ở **Activity test không có `configChanges`**, nơi xoay thật sự tạo lại Activity. Giả thuyết (chưa kiểm) là lệch gắn với việc Activity bị tạo lại giữa lúc xoay.
- **Đo gốc đỏ trên Tecno, 2026-11-18, 40 lượt nữa (bản đã commit, không đổi test; một bộ theo dõi chỉ-đọc ghi activity ở trên cùng mỗi ~0,4 giây bằng `dumpsys`, nằm ngoài test; `user_rotation` đo sau mỗi lượt):** **29 xanh, 11 đỏ.** Trong **10 trong 11 lượt đỏ** `com.google.android.gms.ads.AdActivity` có mặt ở trên cùng; trong **0 trong 29 lượt xanh** nó xuất hiện. Đây là bằng chứng trực tiếp, trên máy này, rằng **lượt đỏ gần như luôn trùng với quảng cáo toàn màn hình** (kết luận ngày 11-16 giờ có số đo ủng hộ). Lượt đỏ thứ 11 (run37) **không có quảng cáo**: lỗi khác, `Never saw: the typed text in an editable field` ở test xoay (`editableTextLengths=[0]`), chính là lỗi gõ chữ chập chờn đã mô tả ở FRONT-02m (1 trong 40 ở đây). **Vẫn 0 lệch xoay** (`user_rotation=0`, `accelerometer_rotation=1` sau cả 40 lượt; cộng với 40 lượt trước, **0/80**). Sao lưu/khôi phục byte-exact, băm DataStore `9ca1c202…` trùng.
- **Chưa biết:** vì sao quảng cáo chèn vào chỉ ở một số lượt (khoảng 25%), ai quyết định (SDK quảng cáo, thời điểm tải), và vì sao phím Back không đóng nó (ngày 11-16). Tôi không thử bấm đóng quảng cáo (cấm tọa độ) và không đổi cấu hình quảng cáo (cấm theo ghi nhớ của owner: không dùng cờ ad/VIP để né test). Do đó **bản commit giữ nguyên**: test đỏ ~25% trên máy có quảng cáo xen kẽ là hành vi đã biết, không phải lỗi ViewModel.
- **Sửa một câu sai của tôi trong lúc làm việc 2026-11-18:** tôi từng nói "02d xanh 40/40 trên Tecno". Sai; con số 40/40 trên Tecno là của FRONT-02e.
- **Hệ quả cho kết luận FRONT-02d:** bằng chứng "chữ và hộp thoại sống sót qua `recreate()`/xoay trên `MainActivity` thật" đến từ **Pixel** (190 lượt, mutation 4/4); trên Tecno test chỉ ổn định khi quảng cáo chưa chèn vào. Test này có thể đỏ ngẫu nhiên trên máy có quảng cáo xen kẽ. **FRONT-02e không bị ảnh hưởng** vì nó dùng Activity test, không khởi động `MainActivity` hay quảng cáo (40/40 xanh trên Tecno).
- Pixel đang lệch `user_rotation=1` do test (nguyên nhân chưa rõ, giả thuyết sửa `@After` đã bị bác); không chạm Pixel nữa theo lệnh owner.

## Giới hạn

- Một máy (Pixel 7 Pro, Android 17); không so với Tecno KJ7 hay A50s.
- Test phụ thuộc: tài khoản Local, không ở màn khởi động lần đầu, không có quảng cáo che nút `+`; nếu không thì đỏ có lý do rõ.
- Chập chờn bước gõ chưa hiểu nguyên nhân (xem trên). **Cập nhật FRONT-02m (2026-11-16):** đã điều tra một phần, lỗi tái hiện ~1% (4/400) ngoài `MainActivity`; còn nghi cuộc đua gõ và focus hoặc `reset()` muộn; **chưa kết luận gốc**, xem `FRONT-02m_typing-flake-investigation_DONE.md`. **Cập nhật 2026-11-17:** vòng đo thứ hai (199 lượt) cho thêm: một lượt kiểu chữ vào ViewModel rồi bị đặt về rỗng lặp lại giống hệt ba lần; vẫn chưa biết ai ghi.
- `describeScreen()` chỉ ghi số lượng và cờ, không ghi chữ trên màn hình.

## Commits trên `dev`

`5996f211` spec · `473bcd8b` plan · (commit test + tài liệu: xem `git log`)
