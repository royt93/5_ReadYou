# EXC-05d (partial; EXC-05 remains in progress)

**Date:** 2026-11-19
**Branch:** `dev`
**Device:** không chạm thiết bị nào (chỉ JVM, theo lựa chọn của bạn).
**Scope:** mã production đổi **ba tệp**: `ui/page/p2psync/AutoReceivePolicy.kt` (mới, hàm thuần), `LocalSyncViewModel.kt` (`maybeAutoReceive`, tập nonce đã thử), `ui/page/common/HomeEntry.kt` (một `LaunchedEffect`). Không đổi `LocalSyncPage`, giao thức, NSD, mã hóa, Room (v20), DataStore, manifest, `app/build.gradle`, chuỗi; không dependency mới. Task gốc `doc/task/todo/EXC-05_local-p2p-sync.md` **vẫn mở**.
**Spec:** `docs/superpowers/specs/2026-11-19-exc-05d-auto-receive-on-sync-screen-design.md`. **Không có plan riêng** (viết spec rồi làm thẳng test, mã, đột biến, JVM đầy đủ).

## Việc đã làm
Trước đây người dùng mở màn hình Đồng bộ rồi phải bấm "nhận" ở dòng máy đã ghép. Nay màn hình tự gọi đúng việc đó **một lần cho mỗi lượt phát** khi **không phải đoán**: màn hình đang rảnh (`Idle`) và tài khoản là Local; **đúng một** máy đã ghép dùng được (không `needsRepair`); **đúng một** máy đang phát; và lượt phát đó (theo nonce) chưa được thử. Máy ghép chưa xác nhận (`UNCONFIRMED`) cũng được (đúng như nhận tay, nhận tay xác nhận ghép khi giải mã thành công). Kết quả chỉ là **hộp xem trước** như khi bấm tay: không tự Apply, không ghi Room trước khi bạn bấm Apply. Mọi trường hợp khác (0 hoặc nhiều máy ghép, 0 hoặc nhiều máy phát, màn hình đang bận) không làm gì, dòng máy ghép vẫn bấm tay như cũ.

## Kiểm chứng (đo thật)
- **TDD:** `AutoReceivePolicyTest` (10 test, bảng đủ tổ hợp) và 9 test ViewModel mới trong `LocalSyncPairingViewModelTest` (47 test cả lớp). Đỏ trước vì lỗi biên dịch (chưa có `maybeAutoReceive`), rồi xanh.
- **Đột biến JVM 8/8 khớp sau khi sửa test (xem dưới):** k1 bỏ "đúng một" (đỏ 4 test), k3 bỏ lọc `needsRepair` (đỏ đúng một), k4a bỏ kiểm `idle` (đỏ 2), k4b bỏ kiểm `isLocal` (đỏ 2), k5 bỏ nhớ nonce trong chính sách (đỏ 2), k6 bỏ nhớ nonce ở ViewModel (đỏ 2), k7 ViewModel truyền `idle = true` (đỏ 1), k8 ViewModel truyền `isLocal = true` (đỏ 1). Mã khôi phục giống hệt (`cmp`), `git diff` production rỗng.
- **Hai đột biến sống sót lần đầu, đã đọc lý do và sửa test (không bỏ qua):** (1) bỏ kiểm `idle` ở ViewModel (k7) không làm test nào đỏ vì `receiveFromPaired` có cổng trạng thái riêng nên che lỗi; thêm `autoReceive_doesNotReplaceAPreviewOnScreen_andLeavesTheNewBroadcastUntried` (hộp xem trước đang hiện, một lượt phát mới đến cùng tên dịch vụ: không được thay, và sau Cancel phải nhận một lần). (2) Test "một lần sau lỗi" ban đầu sai ý (lỗi giữ trạng thái `Error`, nên cổng trạng thái đã chặn thay cho bộ nhớ nonce); sửa cho về `Idle` trước khi gọi lại, nên chỉ bộ nhớ nonce mới chặn được. Đột biến k4 (bản gộp) làm trình biên dịch Kotlin lỗi nội bộ nên đổi thành k4a và k4b. Dự đoán của mô hình tay ban đầu cũng sai ở k6 (chỉ đỏ một test, vì lỗi giữ `Error`); bảng đã sửa theo lý do đó.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.908 test, 0 lỗi, 0 bỏ qua** (3.889 + 19), BUILD SUCCESSFUL.

## Giới hạn (nói thẳng)
- **Chưa phải "mở app trên máy thứ hai là xong":** phải mở màn hình Đồng bộ (không chạm khởi động app, không nền) và máy kia phải **đang phát** (người dùng bấm Serve). Tiêu chí 3 giây của EXC-05 gốc **chưa đo**.
- **Chỉ JVM:** chưa kiểm hai máy thật, NSD thật, hộp thoại quyền mạng cục bộ Android 17, ba máy.
- **Một yêu cầu mạng tự động khi mở màn hình** (một GET tới máy có IPv4 riêng, chỉ khi đã ghép, đã là hành vi của nút bấm tay). Máy lạ cùng mạng phát khi bạn chỉ có đúng một máy ghép làm app thử tải một lần với token dựng từ secret đã ghép; sai thì thất bại ở bước xác thực và hiện lỗi như nhận tay, rồi nhớ nonce để không thử lại.
- Hộp xem trước tự hiện có thể làm gián đoạn khi bạn đang gõ URL/mã ghép trong cùng màn hình; Cancel là đủ (nhớ nonce nên không bật lại).
- Task gốc EXC-05 còn: tự phát nền, ACK giữa hai máy, đo 3 giây, hai máy thật.
