# EXC-05d — Tự nhận khi mở màn hình Đồng bộ, nếu đúng một máy đã ghép đang phát (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** EXC-05a (đồng bộ thủ công, mã hóa đầu-cuối), 05b (ghép đôi + NSD), 05c (hai lượt thủ công + đổi vai); task gốc `doc/task/todo/EXC-05_local-p2p-sync.md` vẫn mở

> Trạng thái: owner đã chốt trong brainstorm (2026-11-19): (1) "tự nhận" nghĩa là **khi mở màn hình Đồng bộ** (không chạm khởi động app, không chạy nền); (2) **vẫn hiện xem trước và người dùng bấm Apply**, không tự ghi vào Room; (3) chỉ tự nhận khi **đúng một máy đã ghép hợp lệ VÀ đúng một máy đang phát gần đây**, mọi trường hợp khác để người dùng bấm tay như hiện nay; (4) **chỉ JVM**, chưa chạm máy nào. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Hôm nay người dùng mở màn hình Đồng bộ rồi phải bấm "nhận" ở dòng máy đã ghép. Nay, nếu rõ ràng chỉ có một khả năng, app tự gọi đúng việc đó một lần và hiện hộp xem trước (như khi bấm tay). Không đổi giao thức, NSD, Room (v20), DataStore, mã hóa, dependency, chuỗi (nếu tránh được), và không tự áp dụng dữ liệu.

## Sự thật đã đọc (2026-11-19)
- `HomeEntry.kt:434` (route `LOCAL_SYNC`): `LaunchedEffect(syncViewModel)` gọi `refreshPeers()` và `startFindingPeers()`; `pairedPeers` và `discovered` được thu vào Compose. Dòng máy đã ghép (`LocalSyncPage.kt:361`) chỉ bấm được khi `nearby.isNotEmpty() && !peer.needsRepair`, và bấm gọi `receiveFromPaired(peerId, nearby.first())`.
- `receiveFromPaired` (`LocalSyncViewModel.kt:372`): chỉ chạy khi trạng thái là `Idle`/`Error`/`Done`; kiểm quyền mạng cục bộ; lấy secret của máy ghép; kiểm host là IPv4 riêng; dựng token từ secret và nonce; rồi `fetchAndPreview(...)` (tải, giải mã bằng secret, dựng xem trước). Secret sai thì bước tải/xác thực thất bại và hiện lỗi như nhận tay.
- NSD chỉ có **phiên bản + nonce** (`NsdTxtCodec`, quyết định gốc của 05b: không tên máy, không định danh). App **không biết** máy phát là máy ghép nào; với đúng một máy ghép hợp lệ và đúng một máy phát thì ghép đôi là rõ. `PairedPeer` có `peerId`, `label`, `status` (UNCONFIRMED/CONFIRMED), `needsRepair`.
- `_state` là máy trạng thái duy nhất (`Idle`, `Starting`, `Sending`, `Receiving`, `Preview`, `Done`, `Error`...); `onScreenStopped()` dọn khi ProcessLifecycle ON_STOP hoặc rời màn hình.

## Thiết kế
- Hàm mới `LocalSyncViewModel.maybeAutoReceive(localAccountId: Int)`: đủ **mọi điều kiện** thì gọi `receiveFromPaired(localAccountId, peer.peerId, nearby.host, nearby.port, nearby.nonce)`; không đủ thì **không làm gì** (không lỗi, không đổi trạng thái).
- Điều kiện (một chỗ duy nhất, hàm thuần `AutoReceivePolicy.pick(paired, discovered, state, autoTried)` trả `Pick?`): trạng thái đang `Idle`; **đúng một** máy trong `pairedPeers` có `!needsRepair`; `discovered.size == 1`; **chưa tự nhận cho nonce này** (nhớ nonce đã thử trong phiên màn hình, không thử lại sau khi người dùng bấm Cancel/Apply hay sau lỗi); tài khoản hiện tại là Local (như nhận tay: `isLocal`). Máy chưa xác nhận (`UNCONFIRMED`) vẫn tính, như nhận tay (nhận tay đã `confirmPeerId`); nếu owner không muốn thì chỉ máy `CONFIRMED` (**quyết định mở, xem Câu hỏi mở**).
- Kích hoạt: trong `HomeEntry` thêm một `LaunchedEffect(pairedPeers, nearbyPeers, syncState)` gọi `maybeAutoReceive`; không đổi `LocalSyncPage`, không thêm chuỗi (hộp xem trước và các lỗi dùng chuỗi có sẵn).
- `autoTried` là tập nonce trong bộ nhớ của ViewModel, xóa khi `onCleared`. Không lưu đĩa.
- Không tự Apply; `applyPreview` và `cancelPreview` giữ nguyên.
- Hằng có tên (R5); không magic number.

## Phạm vi trung thực
- **Chứng minh (JVM):** `AutoReceivePolicy.pick` đúng với mọi tổ hợp (0/1/2 máy ghép, có `needsRepair`, 0/1/2 máy phát, trạng thái khác `Idle`, nonce đã thử, không Local); `maybeAutoReceive` chỉ gọi `receiveFromPaired` đúng một lần với đúng tham số; không ghi Room trước Apply; secret sai cho lỗi xác thực như nhận tay; quyền mạng cục bộ thiếu thì lỗi quyền như nhận tay.
- **Không chứng minh:** hai máy thật, NSD thật, khởi động app, chạy nền, tự phát ở máy kia (máy kia vẫn phải đang phát: người dùng bấm Serve), ba máy, ACK giữa hai máy, thời gian 3 giây.
- **Nói thẳng:** đây **chưa** là "mở app trên máy thứ hai là xong": phải mở màn hình Đồng bộ và máy kia phải đang phát; tiêu chí 3 giây của EXC-05 gốc chưa đo.

## Kiểm chứng
- TDD: test JVM cho `AutoReceivePolicy` (bảng đủ tổ hợp) và cho `maybeAutoReceive` trên `LocalSyncViewModel` thật với `NsdPlatform` giả, `PairingStore` giả, `repository` giả (theo mẫu các test `LocalSyncViewModel*Test` hiện có: kiểm bằng đọc test thật khi lập plan).
- Đột biến JVM với bảng dự đoán (suy luận từng test): bỏ điều kiện "đúng một máy ghép"; bỏ điều kiện "đúng một máy phát"; bỏ lọc `needsRepair`; bỏ nhớ nonce (thử lại mãi); bỏ kiểm trạng thái `Idle`; bỏ kiểm `isLocal`.
- JVM full `--rerun-tasks` đo lại số test (số trước 3.889). Diff production: `LocalSyncViewModel.kt`, một tệp mới `AutoReceivePolicy.kt` (domain/p2psync), `HomeEntry.kt` (một `LaunchedEffect`). Không Room, DataStore, manifest, `app/build.gradle`, chuỗi, dependency; **không thiết bị**.

## Rủi ro đã biết
- **Tự gọi mạng khi mở màn hình** (tải gói xem trước từ máy phát): chỉ tới máy có IPv4 riêng, chỉ khi đã ghép, và đã là hành vi của nút bấm tay; vẫn mới là một yêu cầu mạng tự động khi mở màn hình (nói thẳng).
- **Máy lạ cùng mạng phát khi đúng một máy ghép:** app sẽ thử tải với token dựng từ secret đã ghép; sai nonce/secret thì thất bại ở bước xác thực và hiện lỗi mạng/xác thực (một lần, rồi nhớ nonce). Không rò dữ liệu (không gửi gì thêm ngoài một GET).
- **HiOS/Android 17:** quyền mạng cục bộ đã có đường lỗi `LAN_PERMISSION`; test JVM không chứng minh hộp thoại quyền (chưa chạm máy).
- **Hiệu ứng xem trước tự bật khi người dùng đang gõ URL/mã ghép trong cùng màn hình:** hộp xem trước hiện lên; người dùng Cancel (nhớ nonce nên không bật lại).

## Câu hỏi mở (cần owner chọn trước khi lập plan)
1. Máy ghép `UNCONFIRMED` (đã ghép, chưa xác nhận lần nào) có được tự nhận không? Đề xuất: **có** (đúng như nhận tay, nhận tay xác nhận ghép khi thành công).

## Giới hạn đã biết
- Chỉ JVM; một lát nhỏ của EXC-05, task gốc vẫn mở (còn tự phát nền, ACK, đo 3 giây, hai máy thật).
