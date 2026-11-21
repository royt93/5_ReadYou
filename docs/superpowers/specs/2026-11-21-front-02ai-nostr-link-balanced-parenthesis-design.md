# FRONT-02ai — Link Nostr giữ dấu `)` khi ngoặc trong link cân bằng (đặc tả ngắn, CHƯA có mã, chờ owner duyệt)

**Ngày:** 2026-11-21 · **Nhánh:** `dev` · **Nền:** FRONT-02ac (`linkSpans`), 02ag (`TRAILING_PUNCTUATION` dùng chung)

> Trạng thái: chỉ là đặc tả. Chưa sửa mã, chưa viết test. Một agent rà todo từng nói "đã có logic cân bằng ngoặc trong `coverImageUrl`"; đã đọc mã và **không đúng**: `coverImageUrl` chỉ gọi `trimEnd { it in TRAILING_PUNCTUATION }`, không có logic cân bằng. Đây là logic mới.

## Vấn đề (đọc từ mã, chưa đo trên máy)
`linkSpans` (`NostrFeedMapper.kt`) lấy mỗi đoạn `https://\S+` rồi cắt mọi ký tự thuộc `TRAILING_PUNCTUATION = ".,;:!?)]}'\""` ở cuối. Nên link có ngoặc thật, ví dụ `https://vi.wikipedia.org/wiki/Foo_(bar)`, mất dấu `)` cuối: link bấm được trỏ tới `.../Foo_(bar`, thường là trang sai. FRONT-02ac đã ghi giới hạn này ("link kết thúc bằng `)` bị cắt mất `)`").

## Quy tắc đề xuất (chỉ dấu `)`)
Sau khi cắt các dấu câu như hiện nay, **riêng `)`** chỉ bị cắt khi **số `)` trong link lớn hơn số `(`**. Cách làm: lặp, khi ký tự cuối là `)` và `count(')') > count('(')` thì bỏ nó; các ký tự dấu câu khác vẫn cắt như cũ và có thể xen kẽ (`.` sau `)` cũng bị cắt trước).

Ví dụ (kỳ vọng):
| Văn bản | Link hiện nay | Link đề xuất |
|---|---|---|
| `see https://x.example/a.` | `…/a` | `…/a` (không đổi) |
| `(https://y.example/b).` | `…/b` | `…/b` (không đổi: `)` thừa) |
| `https://vi.wikipedia.org/wiki/Foo_(bar)` | `…/Foo_(bar` | `…/Foo_(bar)` |
| `(xem https://vi.wikipedia.org/wiki/Foo_(bar)).` | `…/Foo_(bar` | `…/Foo_(bar)` (một `)` cân, một `)` thừa bị cắt) |
| `https://x.example/a_(b))` | `…/a_(b` | `…/a_(b)` |

## Không đổi
`]`, `}`, `'`, `"` và các dấu câu khác; chỉ `https`; giới hạn 2.048 ký tự; ảnh bìa (`coverImageUrl`, đuôi ảnh luôn kết thúc chuỗi nên ngoặc giữa link không ảnh hưởng); bài đã lưu (chỉ bài mới có thẻ `<a>` mới); UI, chuỗi, Room, DataStore; không dependency.

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

## Cần owner chọn
Duyệt quy tắc (chỉ `)`, theo số đếm), hoặc muốn rộng hơn (`]`, `}`), hoặc giữ nguyên hành vi hiện nay.
