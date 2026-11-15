# FRONT-02c — Tên hồ sơ cho nguồn Nostr (thiết kế, đã chốt quyết định, chờ duyệt spec)

**Ngày:** 2026-11-15 · **Nhánh:** `dev` · **Nền:** FRONT-02b (`docs/superpowers/specs/2026-11-14-front-02b-nostr-design.md`), `NostrRelayClient`, `NostrFeedMapper.feedName`, `SubscribeViewModel.search()`

> Trạng thái: **owner đã chốt bốn quyết định ngày 2026-11-15** (xem "Quyết định của owner"). Spec còn chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

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

## Quyết định của owner (2026-11-15)
1. **Hỏi hồ sơ cùng một kết nối:** có. Một `REQ` mang hai bộ lọc (ghi chú và hồ sơ); không thêm kết nối, không thêm lần chờ.
2. **Tiền tố:** giữ `Nostr · ` trước tên (ví dụ `Nostr · Alice`).
3. **Tên tự khai:** hiển thị như người đó khai, **không xác minh** (không kiểm `nip05`). Báo cáo phải nói thẳng app không xác minh danh tính.
4. **Độ dài:** tối đa **40 ký tự**, cắt theo điểm mã Unicode (không cắt giữa emoji).

## Kiểm chứng
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

## Điều chỉnh khi thực thi
- **Chọn tên trong một relay:** "hồ sơ mới nhất **có tên dùng được**", không chỉ "mới nhất": một hồ sơ mới nhất mà `display_name` và `name` đều trống thì rơi về hồ sơ cũ hơn có tên (đã đo bằng một sự kiện ký thật).
- **Giữa hai relay:** nếu relay đầu có ghi chú thì dừng; nếu không, nhớ tên của nó và thử relay kế; tên cuối là `tên của relay có ghi chú` hoặc, nếu relay đó không có tên, tên của relay đầu.
- **Làm sạch tên:** dùng `Character.getType == CONTROL` và `Character.isSpaceChar`; `isWhitespace` đã gỡ vì mọi ký tự nó bắt (không phải điều khiển) đều đã nằm trong `isSpaceChar`; `U+2007` và `U+202F` chỉ `isSpaceChar` bắt.
- **Một ranh giới an ninh:** `NostrEvent.isValid` (ghi chú) không bị nới; kind 0 đi qua `isValidProfile`. Một đột biến cho `isValid` nhận cả kind 0 làm 2 test đỏ.
- **Bằng chứng relay:** hôm 2026-11-15 `nos.lol` lỗi bắt tay từ máy Mac với mọi dạng yêu cầu; `relay.primal.net` xác nhận hai bộ lọc trong một `REQ` trả cả kind 0 và kind 1 dưới một EOSE.
- **Máy kiểm thật:** Pixel 7 Pro thay A50s (A50s không còn trên adb lúc kiểm; owner cho phép Pixel).

