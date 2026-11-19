# FRONT-02ac — Link https trong bài Nostr bấm được ở trang đọc (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02aa (`coverImageUrl`), `NostrFeedMapper.escapeToHtml`, trang đọc `HtmlToComposable` (thẻ `a` thành chú thích `URL`, bấm gọi `openURL`)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-19): **không tự tải ảnh nào ở trang đọc**; **mọi link https trong bài bấm được**, mở bằng trình duyệt có sẵn (`openURL`, đã tôn trọng cài đặt trình duyệt và bỏ tham số theo dõi). Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Hiện link trong bài Nostr chỉ là chữ thường: người đọc muốn xem ảnh hay trang khác phải chép link ra ngoài. Nay link `https` trong chữ bài thành thẻ `<a>` an toàn; trang đọc đã sẵn cách vẽ link bấm được. **Không tải gì khi mở bài**, nên không lộ IP khi chỉ đọc. Chỉ đổi `NostrFeedMapper`.

## Sự thật đã đọc (2026-11-19)
- `escapeToHtml` hiện thoát `& < > " '`, đổi `\n` thành `<br>`, bỏ `\r`; không sinh thẻ nào khác. Chữ bài là dữ liệu không tin cậy.
- `HtmlToComposable` (dòng 459 đến 463): thẻ `a` được vẽ bằng `linkTextStyle` và gắn chú thích `"URL"` = `element.attr("abs:href")`; `ParagraphText` gọi `onLinkClick(it.item)` khi chạm; `Content.kt:239` nối tới `context.openURL(it, openLink, openLinkSpecificBrowser)`.
- `openURL` làm sạch tham số theo dõi (`UrlTrackingCleaner`) khi cài đặt bật, rồi mở tùy `OpenLinkPref` (hỏi, trình duyệt mặc định, hoặc trình duyệt chỉ định).
- Trang đọc dùng `fullContent ?: rawDescription`; với nguồn Nostr `fullContent` luôn rỗng (`RssHelper.parseFullContent` trả `""`), nên nội dung là `rawDescription` của mapper.
- Tiêu đề lấy từ `titleFromBody(html)` (văn bản thuần sau `Html.fromHtml`); thẻ `<a>` không đổi văn bản, nên tiêu đề, `shortDescription` và `Article.link` không đổi.
- `abs:href` dùng `baseUrl` của bài (`https://njump.me/note1…`); href `https://…` đã tuyệt đối nên không bị ghép lại.

## Thiết kế
- `escapeToHtml` tách chữ gốc thành đoạn **link** và đoạn **không phải link**. Đoạn không phải link thoát như cũ. Đoạn link thành `<a href="URL_ĐÃ_THOÁT">URL_ĐÃ_THOÁT</a>` (cùng hàm thoát, nên không thể thoát khỏi dấu `"` của thuộc tính).
- **Luật link:** bắt đầu bằng `https://` (không phân biệt hoa thường), chạy tới khoảng trắng, **cắt dấu câu cuối** (`. , ; : ! ? ) ] } ' "`), tối đa `MAX_LINK_URL_LENGTH = 2048` (quá dài thì **giữ là chữ thường**, không phải link), phải có ít nhất một ký tự sau `https://`. Không `http`, không `javascript:`, không `data:`, không `mailto:`, không `nostr:`.
- Hàm thuần công khai `NostrFeedMapper.linkSpans(content): List<IntRange>` (hoặc tương đương) để test được luật link riêng; `escapeToHtml` dùng nó. Hằng có tên (R5): `LINK_SCHEME = "https://"`, `LINK_TRAILING_PUNCTUATION`, `MAX_LINK_URL_LENGTH`.
- `\n` vẫn thành `<br>`, `\r` vẫn bỏ; link không chứa khoảng trắng nên không bao giờ bị cắt ngang bởi xuống dòng.
- Không đổi UI, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency; không đổi REQ/relay; không đổi chữ ký `toArticles`.

## Phạm vi trung thực
- **Chứng minh (JVM):** luật link đúng; `rawDescription` có thẻ `<a href>` đúng và an toàn (không thể chèn thuộc tính hay thẻ từ chữ bài); tiêu đề, `shortDescription` không đổi; bài không link giống hệt trước.
- **Không chứng minh:** bấm link trên máy thật, mở trình duyệt, cài đặt trình duyệt (đã có từ trước), jsoup đọc `href` (đã có từ trước, không kiểm lại).
- **Rủi ro riêng tư/an toàn (nói thẳng):** link do tác giả bài chọn có thể là trang lừa đảo hoặc theo dõi; người đọc **tự chịu khi bấm**; app không tải gì khi mở bài; tham số theo dõi được cắt theo cài đặt có sẵn.
- **Không làm:** tự tải/hiện ảnh ở trang đọc, `http`, link `nostr:`/`npub`, thẻ `imeta`, xem trước link, công tắc mới.
- **Thay đổi cho bài đã lưu:** bài đã lưu giữ `rawDescription` cũ (chữ thường); chỉ bài mới có link bấm được (so theo link khi đồng bộ).

## Kiểm chứng
- TDD: test mới đỏ trước (lỗi biên dịch vì chưa có `linkSpans`, hoặc khẳng định thẻ `<a>`), rồi xanh. Robolectric (vì `toArticles` gọi `titleFromBody`).
- Mutation JVM với bảng dự đoán: nhận `http`; bỏ cắt dấu câu cuối; bỏ giới hạn độ dài; không thoát URL trong `href`; link vẫn thoát nhưng không bọc thẻ; bỏ không-phân-biệt-hoa-thường; dùng tiền tố sai. Mỗi đột biến làm **đúng** test dự định đỏ (suy luận từng test trước khi chốt).
- JVM full `--rerun-tasks` đo lại số test (số trước 3.874, không dùng làm bằng chứng). Diff production: đúng **một** tệp `NostrFeedMapper.kt`.
- Không đụng thiết bị (chỉ JVM). Nếu owner muốn kiểm bấm link trên Tecno thì là việc riêng sau.

## Rủi ro đã biết
- **Chèn thuộc tính/thẻ từ chữ bài:** chặn bằng cách thoát URL bằng đúng hàm thoát cũ và chỉ bọc đoạn đã khớp luật `https://\S+`; test có ca `"` và `<script>` trong URL.
- **Dấu câu cuối:** cắt dấu `.` `,` `)` cuối là phỏng đoán; link hợp lệ kết thúc bằng `)` (Wikipedia) bị cắt mất `)`: chấp nhận, nói thẳng.
- **Cắt link dài hơn 2048:** giữ là chữ, không bấm được.
- **`rawDescription` lớn hơn một chút** (thẻ `a`): không đáng kể (giới hạn 64 KiB mỗi khung đã có).

## Giới hạn đã biết
- Chỉ JVM; không kiểm máy thật.
- Link `http` và các giao thức khác vẫn là chữ.

## Điều chỉnh khi thực thi
- Làm đúng plan: một tệp production (`NostrFeedMapper.kt`); hàm thoát cũ đổi tên `escapePlain`, `escapeToHtml` mới bọc link; test trong lớp mới `NostrTappableLinksTest` (11 test, dùng Jsoup đọc `abs:href` như trang đọc).
- Không có test hiện có nào chứa link trong chữ bài nên không test cũ đổi kết quả.
- **Mutation:** 9/9 khớp dự đoán, không sửa bảng.
- **JVM đo lại:** 3.885 test, 0 lỗi (đúng kỳ vọng 3.874 + 11).
