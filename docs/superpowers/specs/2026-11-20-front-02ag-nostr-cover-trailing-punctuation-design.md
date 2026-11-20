# FRONT-02ag — Ảnh bìa Nostr không mất khi dấu câu đứng ngay sau đuôi ảnh

**Ngày:** 2026-11-20 · **Nhánh:** `dev` · **Nền:** FRONT-02aa (`coverImageUrl`), 02ac (`linkSpans`, cắt dấu câu cuối), 02af (regex có giới hạn độ dài)

> Trạng thái: đã sửa trong `84dbbe64`, đúng lựa chọn của owner ngày 2026-11-20. +7 test (6 đỏ trên mã cũ), 4/4 đột biến bị bắt; full JVM `--rerun-tasks`: 3.928 test, 0 lỗi, 0 bỏ qua. Chỉ JVM, không thiết bị. Kết quả và giới hạn: [báo cáo](../../../doc/task/done/FRONT-02ag_nostr-cover-trailing-punctuation_DONE.md).

## Vấn đề (đo thật trên JDK, 2026-11-20)
`see https://x.example/a.jpg.` (câu kết thúc bằng dấu chấm ngay sau đuôi ảnh) cho **không có ảnh bìa**. Regex ảnh bìa đòi sau đuôi ảnh là khoảng trắng hoặc hết chữ (`(?=\s|$)`), nên dấu chấm làm trượt. Trong cùng bài, link bấm được (02ac) cắt dấu chấm cuối đúng. Hai luật không nhất quán. Báo cáo 02aa đã ghi giới hạn "link dính dấu câu bị bỏ sót"; đây là sửa giới hạn đó.

## Thiết kế
- Một hằng dùng chung `TRAILING_PUNCTUATION = ".,;:!?)]}'\""` (đổi tên `LINK_TRAILING_PUNCTUATION` hiện có, giữ nguyên giá trị) cho **cả hai** luật, để không chép hai danh sách.
- Regex ảnh bìa: đổi phần kiểm sau đuôi từ `(?=\s|$)` thành `(?=[.,;:!?)\]}'"]*(?:\s|$))` (cho phép một chuỗi dấu câu rồi khoảng trắng hoặc hết chữ). Vẫn giữ `\S{1,2040}?` (tuyến tính, 02af).
- `coverImageUrl`: cắt dấu câu cuối khỏi địa chỉ khớp (`trimEnd { it in TRAILING_PUNCTUATION }`), vì phần query `\S*` tham lam có thể nuốt dấu câu (`a.jpg?w=1.`). Rồi mới áp bộ lọc độ dài 2.048 như cũ.
- **Không đổi:** chỉ `https`, đuôi `jpg jpeg png gif webp`, giới hạn 2.048, bài đã lưu (chỉ bài mới có ảnh bìa mới), `escapeToHtml`, trang đọc, UI, chuỗi, Room, DataStore, manifest, `app/build.gradle`; không dependency.

## Phạm vi trung thực
- **Chứng minh (JVM):** các ca dấu câu (`.` `,` `)` `!` `?` `"`) sau đuôi ảnh và sau query; `a.png.exe` và `a.pngx` vẫn **không** là ảnh; link giữa câu vẫn như cũ; thời gian bài độc hại vẫn nhanh (test 02af giữ nguyên, phải còn xanh); độ dài đúng 2.048 vẫn được, 2.049 vẫn bị bỏ.
- **Không chứng minh:** ảnh hiện trên máy (không có thiết bị dùng được: chỉ Tecno, và test máy 02ab đã kiểm đường danh sách).
- **Tác động hành vi (nói thẳng):** bài mới có link ảnh kết thúc câu sẽ có ảnh bìa mà trước đây không có; bài đã lưu không đổi (so theo link khi đồng bộ). Link hai ảnh dính nhau không khoảng trắng vẫn là một link dài (giới hạn cũ, không đổi).

## Kiểm chứng
- TDD: test mới trong `NostrCoverImageTest` đỏ trước, đúng lý do (ca dấu chấm cuối trả `null`), rồi mã, rồi xanh; toàn bộ test ảnh bìa cũ và test thời gian 02af phải còn xanh.
- Đột biến JVM (dự đoán suy luận từng test): bỏ phần dấu câu trong lookahead (về `\s|$`); bỏ bước `trimEnd` (dấu câu sau query lọt vào địa chỉ); đổi hằng dùng chung khiến luật link lệch (`)` bỏ khỏi hằng); cho phép dấu câu mà không đòi khoảng trắng/hết chữ sau đó (làm `a.png.exe` thành ảnh).
- JVM full `--rerun-tasks` đo lại (số trước 3.921). Diff production: đúng một tệp `NostrFeedMapper.kt`.
