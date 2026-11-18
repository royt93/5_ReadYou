# FRONT-02s (Chữ đã gõ trong ô Thêm nguồn sau recreate, kiểm trên Activity test)

**Date:** 2026-11-18
**Branch:** `dev` (không push)
**Scope:** chỉ đổi test `SubscribeDialogRecreateDeviceTest.kt` (giữ tên và đường dẫn), **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`, dependency. Không mạng thật, không ghi Room, không xoay màn hình.
**Spec / plan:** `docs/superpowers/specs/2026-11-18-front-02s-typed-text-recreate-on-test-activity-design.md`, `docs/superpowers/plans/2026-11-18-front-02s-typed-text-recreate-on-test-activity.md`.

## Điều test chứng minh
Trên **Tecno KJ7 (`115333744A005844`, Android 14)**, với `ComponentActivity` test + `ViewModelProvider` thật (`ViewModelStore` thật) + `SubscribeDialog` thật: gõ chuỗi thử **một lần** qua giao diện, rồi `recreate()` (một lần, và hai lần liền): Activity khác instance, **cùng một `SubscribeViewModel`**, ViewModel còn đúng chữ, hộp thoại dựng lại có đúng chữ và ô vẫn sửa được.

Hai test: `recreate_keepsTheDialogOpenAndTheTypedText`, `recreateTwice_keepsTheDialogOpenAndTheTypedText`.

## Vì sao thay 02d cũ
Bản `MainActivity` đỏ khoảng 25% lượt vì quảng cáo toàn màn hình (đo 2026-11-18: 10 trong 11 lượt đỏ có `AdActivity` trên cùng, 0 trong 29 lượt xanh). Owner chọn: chuyển sang Activity test, bỏ test xoay thật, gõ qua giao diện và **không gõ lại**.

## Số đo (Tecno, bản cuối, không gõ lại, không tăng timeout, không sleep)
| Vòng | Lượt | Xanh | Đỏ | Đỏ có `AdActivity` trên cùng | Xanh có `AdActivity` |
|---|---|---|---|---|---|
| bản cuối, vòng 1 | 20 | 18 | 2 | 2 | 0 |
| bản cuối, vòng 2 | 20 | 19 | 1 | 1 | 0 |
| **Cộng** | **40** | **37** | **3** | **3** | **0** |

- Cả 3 lượt đỏ báo "Never saw: the link field" với "No compose hierarchies found" (chẩn đoán lượt chẩn đoán: `lifecycle=STARTED`, `windowFocus=false`, tức Activity test mất cửa sổ vì `AdActivity` đè lên). Không lượt nào là "chữ vào rồi mất" hay lỗi gõ chữ.
- Trước đó còn các lượt thử bản nháp và lượt chẩn đoán (25 lượt, 2 đỏ cùng nguyên nhân quảng cáo); chẩn đoán tạm đã gỡ khỏi mã.
- `user_rotation=0`, `accelerometer_rotation=1` sau **mọi** lượt (0 lệch); DataStore băm `9ca1c202050c258b4aac9a063e27def350d737fb` trùng trước và sau; sao lưu/khôi phục byte-exact OK; gói test được gỡ cuối mỗi vòng.
- **Nói thẳng:** 3/40 đỏ vượt quy tắc "từ 2 lượt đỏ trong 40 thì dừng". Đã dừng, điều tra theo systematic-debugging, tìm ra gốc (quảng cáo của SDK app, khởi động từ `RApp`, hiện cả khi không có `MainActivity`), hỏi owner; owner chọn **chấp nhận đỏ do quảng cáo và ghi rõ**. Không đổi cấu hình quảng cáo để né (FRONT-02f vẫn deferred).

## Mutation chạy trên máy (đổi tạm, build, cài, chạy, khôi phục, `cmp`)
| Đột biến | Kết quả | Chỗ đỏ |
|---|---|---|
| base | xanh `OK (2 tests)` (2 lần: đầu và cuối) | |
| s1 `init()` xóa `linkContent` | đúng 2 test đỏ | bước kiểm sau recreate (`recreateAndShowAgain`) |
| s2 `inputLink` bỏ chữ | đúng 2 test đỏ | bước gõ (`openDialogAndTypeOnce`) |
| s3 `showDrawer` đặt `visible=false` | đúng 2 test đỏ | bước chờ ô (`openDialogAndTypeOnce`) |
| s4 (trong test) ViewModel dựng ngoài `ViewModelProvider` | đúng 2 test đỏ | `assertSame` (`recreateAndShowAgain` dòng 114) |

Mã sau mỗi đột biến khôi phục giống hệt (`cmp`), `git diff app/src/main` rỗng.

## Kiểm khác (đo lại)
- JVM `testDevDebugUnitTest --rerun-tasks`: **3.797 test, 0 lỗi, 0 bỏ qua**; `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không quét `androidTest` nên không kiểm tệp test mới.
- Chỉ thay đổi trong `app/src/androidTest`: `SubscribeDialogRecreateDeviceTest.kt`.

## Mất so với 02d cũ (nói thẳng)
Nút `+` thật của `FeedsPage`; `hiltViewModel()` trong `FeedsPage`/`HomeEntry`; xoay thật (và `assertSame` do `configChanges`). Bằng chứng xoay thật chỉ còn là số đo cũ của bản `MainActivity` (Pixel ~190 lượt; Tecno 0/80 lệch xoay), không làm mới.

## Không kiểm / nợ
- Xuống nền, process bị giết (chữ mất, đã biết), đổi ngôn ngữ/chế độ tối thật, `MainActivity`.
- **Còn ~7% đỏ do quảng cáo** (3/40). **FRONT-02e và FRONT-02r cũng chạy trên Activity test trong cùng tiến trình app nên có thể chịu cùng rủi ro; chưa đo.**
- Lỗi gõ chữ chập chờn của FRONT-02m (~1%): không thấy lượt nào trong số đo này, nhưng 40 lượt không đủ để loại trừ.
- Hai khoảng trống chưa rõ: vì sao quảng cáo hiện cả khi không có `MainActivity` (giả thuyết: `RApp.setupAdmob`, chưa kiểm); gốc việc OS đôi khi để `user_rotation=1` sau `UNFREEZE` (02e).
