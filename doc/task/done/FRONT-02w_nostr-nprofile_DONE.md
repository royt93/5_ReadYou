# FRONT-02w (Nhận `nprofile` khi thêm nguồn Nostr)

**Date:** 2026-11-18
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng ba tệp**: `Bech32.kt` (+tham số độ dài), `NostrKeys.kt` (+đọc `nprofile`), `SubscribeViewModel.kt` (một dòng). Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`, `LocalRssSv`, `NostrRelayClient`; không dependency mới; không thêm lệnh gọi mạng. **Chỉ JVM, không chạm thiết bị nào.**
**Spec / plan:** `docs/superpowers/specs/2026-11-18-front-02w-nostr-nprofile-design.md`, `docs/superpowers/plans/2026-11-18-front-02w-nostr-nprofile.md`.

## Việc đã làm
Dán `nprofile1…` (hoặc `nostr:nprofile1…`) vào ô **Thêm nguồn** thì app hiểu đó là cùng một người như `npub` và chạy đúng luồng `npub`: xem trước, "Đã đăng ký" nếu trùng, nguồn lưu dạng `nostr:<npub>` (không bao giờ lưu `nprofile`).
- `Bech32.decode(text, maxLength = 90)`: `npub` và `note1` không đổi; `nprofile` được đọc với giới hạn 1000 ký tự (NIP-19 cho phép dài hơn 90; `nprofile` thật dài 110 đến 131 ký tự trở lên).
- `NostrKeys.nprofileToHex`: đọc danh sách TLV của NIP-19; lấy **đúng một** khóa 32 byte; **gợi ý relay và loại lạ chỉ được đọc để bỏ qua**. Cụt, thiếu khóa, hai khóa, khóa sai độ dài, quá 1000 ký tự, sai loại định danh: bị từ chối.
- `NostrKeys.authorHexFrom` = `npub` hoặc `nprofile`; `SubscribeViewModel.search()` gọi hàm này.
- **Gợi ý relay không bao giờ được dùng:** `NostrRelayClient.fetch(authorHex: String)` chỉ nhận khóa hex, không có chỗ nào nhận địa chỉ relay (test cấu trúc khóa điều này). Người gửi `nprofile` không thể bắt app nối tới máy chủ họ chọn.
- `isSecretKey` vẫn chạy trước mọi đường (kể cả `nostr:nsec…`).

## Kiểm chứng (đo thật)
- **Vector chính thức NIP-19** (131 ký tự) → khóa `3bf0c63f…` đúng. Mọi chuỗi mẫu khác do **bộ mã hóa Python độc lập** tính (checksum không do app tạo); một mô hình Python của thuật toán khớp cả 13 chuỗi trước khi viết mã.
- **TDD:** mỗi task có bước đỏ (lỗi biên dịch cho Task 1 và 2; năm test đỏ đúng cho Task 3) rồi mới sửa mã. Ở Task 3, thêm test `theNostrAnchorExists` vì test đọc mã nguồn cũ **xanh giả** khi mất neo (`substringAfter` trả nguyên chuỗi khi không thấy).
- **Test mới:** `NostrNprofileTest` 18, `SubscribeViewModelNostrNprofileTest` 4, `NostrKeysTest` +2, `SubscribeViewModelNostrSourceTest` +1 (test neo); **25 test mới**.
- **Mutation JVM: 13/13 bị bắt**, so sánh tự động tập test đỏ (mọi tệp khôi phục giống hệt, `cmp`; `git diff` main rỗng sau batch). Dự đoán của plan **sai 4 chỗ**, đều đã đọc lý do và sửa bảng, không phải test yếu: `n1` còn làm đỏ thêm `aLongProfileUnderTheLimit_isAccepted` và test B1 (đổi giới hạn mặc định cũng đổi giới hạn dùng cho test dài); `n3` (cho phép hai khóa) đỏ `twoKeys_areRejected` chứ không phải `aKeyThatIsNot32Bytes` (đính chính nhầm tên của plan); `n16` đỏ thêm `authorHexFrom_takesBothAndRefusesJunk` (nó cũng dùng `nevent`); `n11` đỏ thêm test đọc mã `newNostrCodeDoesNotLogKeysOrNotes` (đúng: neo mất thì test đó cũng đỏ, đã nằm trong nhóm "ít nhất").
- JVM `testDevDebugUnitTest --rerun-tasks`: **3.822 test, 0 lỗi, 0 bỏ qua** (3.797 + 25). `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không đổi (không chuỗi, không tệp res).

## Không làm / không kiểm (nói thẳng)
- **Chưa dán thật vào ô trên màn hình**, chưa kiểm trên máy hay mạng thật; chỉ JVM (ViewModel với relay giả).
- **Không** link `njump.me/...`, không `nevent`/`naddr`, không dùng gợi ý relay, không ảnh, không trả lời, không đăng nhập, không đăng bài.
- **Hệ quả cần biết:** người dán `nprofile` của người chỉ có ở relay khác sẽ thấy xem trước trống (đúng như `npub` hiện nay): app chỉ hỏi hai relay cố định.
- `nprofile` giả có khóa đúng vẫn hợp lệ: app không xác minh danh tính, chỉ đọc bài ký đúng khóa (như FRONT-02b).
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn ảnh và trả lời Nostr, đăng nhập, đăng bài.
