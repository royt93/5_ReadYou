# FRONT-02c — Tên hồ sơ cho nguồn Nostr (thiết kế, CHƯA ĐƯỢC DUYỆT)

**Ngày:** 2026-11-15 · **Nhánh:** `dev` · **Nền:** FRONT-02b (`docs/superpowers/specs/2026-11-14-front-02b-nostr-design.md`), `NostrRelayClient`, `NostrFeedMapper.feedName`, `SubscribeViewModel.search()`

> Trạng thái: **bản nháp để owner duyệt.** Chưa có plan, chưa có mã. Owner đã chọn hướng "tên hồ sơ Nostr" ở lượt chọn việc tiếp theo, nhưng **chưa duyệt các quyết định mở ở cuối tài liệu**.

## Mục tiêu
Nguồn Nostr hiện tên `Nostr · npub1mlc…7a0a`, khó nhận ra. Nếu tài khoản có hồ sơ công khai (loại 0), dùng tên trong hồ sơ làm tên nguồn, ví dụ `Nostr · Alice`. Không có hồ sơ thì giữ tên cũ.

## Sự thật đã đo (2026-11-15, relay thật, chỉ đếm và tên trường)
- Hồ sơ là một sự kiện **loại 0**, `content` là một chuỗi JSON `{name, about, picture, …}` (NIP-01). Có thể thêm trường như `display_name`, `nip05`, `banner`, `lud16` (NIP-24).
- Hỏi `nos.lol` và `relay.primal.net` về hồ sơ của một khóa công khai có tên: **cả hai trả đúng 1 sự kiện** trong khoảng 0,9 đến 1,2 giây, cỡ khoảng 2 KB, có `name` và `display_name`.
- Một khóa công khai khác: **cả hai trả 0 sự kiện** (không có hồ sơ). Vậy phải có đường lui về tên cũ.
- Nội dung hồ sơ **do chủ tài khoản đặt tự do**: có thể rất dài, có ký tự lạ hay thẻ HTML, hoặc giả tên người khác.

## Phạm vi trung thực
- **Chỉ lấy tên.** Không ảnh đại diện (`picture`), không mô tả, không xác minh `nip05`, không địa chỉ thanh toán.
- Chỉ đọc, không đăng nhập, không lưu khóa. Không dependency mới, không Room v23, không chuỗi mới.
- Tên là chữ **không đáng tin**: người dùng có thể thấy tên giả người nổi tiếng. App **không** xác minh danh tính (không kiểm `nip05`); báo cáo phải nói thẳng điều này. Nên giữ tiền tố `Nostr ·` để người dùng luôn thấy đây là tên tự khai trên mạng Nostr.

## Thiết kế đề xuất
1. **Cùng một lần kết nối, thêm một yêu cầu.** `NostrRelayClient.fetch` hiện gửi một `REQ` cho loại 1. Đề xuất gửi **hai bộ lọc trong cùng một `REQ`**: `{"kinds":[1],…,"limit":50}` và `{"kinds":[0],…,"limit":1}`. Không thêm kết nối, không thêm lần chờ. Giữ `EOSE` làm tín hiệu kết thúc.
2. **Kiểm chặt như ghi chú:** sự kiện loại 0 phải qua đúng `NostrEvent.isValid` (id, chữ ký, `pubkey`), chỉ khác là cho phép `kind == 0`. Hồ sơ sai chữ ký thì bỏ.
3. **Đọc tên an toàn:** `content` phải là JSON; lấy `display_name`, không có thì `name`; bỏ khoảng trắng đầu cuối, gộp khoảng trắng, **cắt tối đa 40 ký tự theo điểm mã Unicode**, loại ký tự điều khiển; nếu trống thì coi như không có tên. Giá trị không phải chuỗi thì bỏ.
4. **Tên chỉ là chữ thuần khi hiển thị** (Compose `Text`, không HTML), nên không cần thoát; nhưng phải được lưu và chia sẻ đúng như `Feed.name` hiện nay.
5. **Kết quả:** `fetch` trả `NostrFetchResult(notes: List<NostrEvent>, profileName: String?)` (thay cho `List<NostrEvent>`). `SubscribeViewModel` và `LocalRssSv` dùng `profileName ?: feedName(npub)` cho bản xem trước.
6. **Chỉ đặt tên lúc tìm và xem trước.** Đồng bộ nền **không** đổi tên nguồn (người dùng có thể đã đổi tên tay). Hồ sơ đổi sau đó không cập nhật tên; ghi vào giới hạn.

## Quyết định mở (cần owner)
1. **Có chấp nhận hỏi relay thêm hồ sơ không?** Relay không biết thêm `npub` nào (đã biết), nhưng nhận thêm một bộ lọc. Đề xuất: **có**, vì cùng kết nối.
2. **Giữ tiền tố `Nostr ·`?** Đề xuất: **giữ** (`Nostr · Alice`) để nhận ra là tên tự khai trên Nostr, và vì dễ phân biệt với nguồn RSS cùng tên.
3. **Tên giả người khác:** chấp nhận hiển thị tên tự khai, kèm tiền tố, không xác minh? Đề xuất: **chấp nhận** và ghi rõ trong báo cáo.
4. **Giới hạn 40 ký tự** có hợp lý không? Đề xuất 40.

## Kiểm chứng (khi được duyệt)
- JVM TDD đỏ→xanh, mutation có backup và `cmp`: đọc `name`/`display_name`, ưu tiên `display_name`, trống, quá dài, emoji ở biên 40, ký tự điều khiển, JSON hỏng, trường sai kiểu, hồ sơ sai chữ ký bị bỏ, hồ sơ của `pubkey` khác bị bỏ, nhiều hồ sơ chỉ lấy mới nhất (`created_at`), không có hồ sơ thì tên cũ.
- `NostrRelayClientTest` cập nhật: `REQ` gửi **hai** bộ lọc đúng nội dung; relay chỉ trả hồ sơ; chỉ trả ghi chú; cả hai.
- Mạng xấu và rò rỉ: các test hiện có phải còn xanh với `REQ` mới.
- Tecno KJ7: helper tạm gọi relay thật với một khóa công khai có hồ sơ và một khóa không có, chỉ ghi số lượng và độ dài tên (không ghi tên); `am instrument`, không chạm màn hình; sao lưu và khôi phục byte-exact.
- **Không kiểm được:** tên thật hiển thị trong hộp thoại trên màn hình; hồ sơ đổi theo thời gian; relay khác hai relay cố định.

## Giới hạn đã biết
- Tên chỉ được đặt một lần, lúc tìm; không tự cập nhật.
- Tên là tự khai, có thể giả; không xác minh `nip05`.
- Hai relay có thể có hồ sơ cũ hoặc không có.
- Một khóa không có hồ sơ vẫn hiện `Nostr · npub1…`.
