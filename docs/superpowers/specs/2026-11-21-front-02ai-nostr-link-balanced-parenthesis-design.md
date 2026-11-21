# FRONT-02ai — Link Nostr giữ dấu `)` khi ngoặc trong link cân bằng (đặc tả ngắn; owner đã duyệt 2026-11-21, gồm cả `]` và `}`)

**Ngày:** 2026-11-21 · **Nhánh:** `dev` · **Nền:** FRONT-02ac (`linkSpans`), 02ag (`TRAILING_PUNCTUATION` dùng chung)

> Trạng thái: **owner duyệt 2026-11-21 quy tắc theo số đếm cho `)`, và mở rộng sang `]` và `}`.** Đang làm theo TDD. Một agent rà todo từng nói "đã có logic cân bằng ngoặc trong `coverImageUrl`"; đã đọc mã và **không đúng**: `coverImageUrl` chỉ gọi `trimEnd { it in TRAILING_PUNCTUATION }`, không có logic cân bằng. Đây là logic mới.

## Vấn đề (đọc từ mã, chưa đo trên máy)
`linkSpans` (`NostrFeedMapper.kt`) lấy mỗi đoạn `https://\S+` rồi cắt mọi ký tự thuộc `TRAILING_PUNCTUATION = ".,;:!?)]}'\""` ở cuối. Nên link có ngoặc thật, ví dụ `https://vi.wikipedia.org/wiki/Foo_(bar)`, mất dấu `)` cuối: link bấm được trỏ tới `.../Foo_(bar`, thường là trang sai. FRONT-02ac đã ghi giới hạn này ("link kết thúc bằng `)` bị cắt mất `)`").

## Quy tắc (đã duyệt): `)`, `]`, `}`
Đi từ cuối link về đầu, mỗi ký tự thuộc `TRAILING_PUNCTUATION` bị bỏ, **trừ** ngoặc đóng (`)`, `]`, `}`) khi trong phần link còn lại **số ngoặc mở cùng loại (`(`, `[`, `{`) lớn hơn hoặc bằng số ngoặc đóng**: gặp ngoặc đóng như vậy thì dừng (giữ nó và mọi thứ trước nó). Các dấu câu khác (`. , ; : ! ? ' "`) luôn bị cắt như cũ. **Tuyến tính:** đếm số mở và số đóng của cả đoạn **một lần**, rồi cập nhật khi bỏ từng ký tự (không đếm lại mỗi vòng, vì bài độc hại 65.000 ký tự `)` sẽ thành bậc hai).

Ví dụ (kỳ vọng):
| Văn bản | Link hiện nay | Link đề xuất |
|---|---|---|
| `see https://x.example/a.` | `…/a` | `…/a` (không đổi) |
| `(https://y.example/b).` | `…/b` | `…/b` (không đổi: `)` thừa) |
| `https://vi.wikipedia.org/wiki/Foo_(bar)` | `…/Foo_(bar` | `…/Foo_(bar)` |
| `(xem https://vi.wikipedia.org/wiki/Foo_(bar)).` | `…/Foo_(bar` | `…/Foo_(bar)` (một `)` cân, một `)` thừa bị cắt) |
| `https://x.example/a_(b))` | `…/a_(b` | `…/a_(b)` |
| `https://x.example/a[1]` và `…/a{x}` | mất `]` / `}` | giữ `]` / `}` |
| `https://x.example/a_(b)]` | `…/a_(b` | `…/a_(b)` (`]` thừa bị cắt, `)` cân nên giữ) |

## Không đổi
`'`, `"` và các dấu câu khác (luôn cắt); chỉ `https`; giới hạn 2.048 ký tự; ảnh bìa (`coverImageUrl`, đuôi ảnh luôn kết thúc chuỗi nên ngoặc giữa link không ảnh hưởng); bài đã lưu (chỉ bài mới có thẻ `<a>` mới); UI, chuỗi, Room, DataStore; không dependency.

## Rủi ro nói thẳng
- **Đoán sai ý người viết:** `(xem https://x.example/a_(b)` (người viết quên đóng ngoặc ngoài): theo quy tắc, link giữ `)` vì cân bằng với `(` trong link, nhưng có thể người viết muốn đó là ngoặc đóng của câu. Không có cách đúng cho mọi ca; quy tắc này theo thông lệ phổ biến (GitHub/Markdown).
- **Đổi hành vi của một test hiện có:** `trailingPunctuation_isLeftOutOfTheLink` vẫn đúng (các ca của nó `)` thừa); không phải sửa test cũ, chỉ thêm test mới. Cần kiểm lại bằng chạy thật.
- **Chỉ ảnh hưởng link có `(` bên trong:** link thường không đổi.
- Độ phức tạp: lặp trên đoạn không dài hơn 2.048 ký tự cộng một đếm; vẫn tuyến tính. Không dùng regex mới.

## Kiểm chứng đề xuất
- TDD JVM trong `NostrTappableLinksTest`: các ví dụ ở bảng, `)` thừa vẫn bị cắt, `(` không cân (nhiều `(` hơn `)`) giữ nguyên các `)`, link có `)` ở giữa không bị đổi, dấu `.`/`,` sau `)` vẫn bị cắt trước, giới hạn đúng 2.048 ký tự với ngoặc, bài độc hại 65.000 ký tự toàn `)` vẫn nhanh (test thời gian như 02af).
- Đột biến JVM: bỏ điều kiện đếm (về luật cũ), đếm ngược `(`/`)`, chỉ xét một lần thay vì lặp, đổi `>` thành `>=`.
- Chạy full JVM `--rerun-tasks`. Diff production: một tệp `NostrFeedMapper.kt`.
- Thiết bị (tùy chọn, Pixel): chạm link có ngoặc trong `Reader` như FRONT-02ae; chưa làm, không bắt buộc.

## Quyết định của owner
2026-11-21: duyệt quy tắc theo số đếm và cả `]`, `}`. Ảnh bìa (`coverImageUrl`) giữ nguyên `trimEnd` như 02ag (đuôi ảnh luôn kết thúc, nhưng một query kết thúc bằng ngoặc cân bằng vẫn bị cắt; ghi là giới hạn, không làm trong lát này).
