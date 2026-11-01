# ECHO-03 — Thẻ hai góc nhìn (Two-Headline Card) 9:16: Design

**Ngày:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Cho người dùng xuất một ảnh 9:16 chia đôi để chia sẻ: nửa trên là bài đang đọc, nửa dưới là một bài **từ nguồn khác trong kho của chính họ** mà ECHO-02 đã tìm thấy có từ ngữ trái chiều trong tiêu đề.

## Vì sao khác todo gốc
Todo `ECHO-03_dual-perspective-quote-card.md` giả định nửa dưới là "luận điểm phản biện của AI" lấy từ ECHO-02. ECHO-02 thực tế (owner đã duyệt) là tìm bài trong kho, **offline, không AI, không sinh lập luận**; nó chỉ nói "tiêu đề này có từ trái chiều", không nói bài kia phản bác bài này. Vì vậy:
- Nửa dưới là **tiêu đề + nguồn + ngày + từ khoá trái chiều** của bài được chọn, không phải lập luận.
- Tên trong giao diện và trên ảnh là "hai góc nhìn / hai tiêu đề", **không** dùng chữ "phản biện" hay "debate". Hashtag `#Debate` chỉ nằm trong nội dung chia sẻ (EXTRA_TEXT) theo yêu cầu của todo.

## Quyết định của owner (AskUserQuestion)
1. **Nửa dưới:** tiêu đề bài từ nguồn khác, ghi rõ nguồn.
2. **Nửa trên:** tiêu đề + nguồn + ngày của bài đang đọc, không cần chọn đoạn văn.
3. **Số bài:** đúng một bài, người dùng chạm chọn trong thẻ ECHO-02.
4. **Chú thích:** có dòng "không phải kiểm chứng sự thật" trên ảnh và hashtag trong EXTRA_TEXT.

## Sự thật từ code (đã đọc)
- Đã có chuỗi xuất ảnh trích dẫn: `QuoteCardRenderer` (thuần `android.graphics`, 1080×1350), `QuoteCardSharer.writePng/buildShareIntent` (cacheDir `quote_cards`, FileProvider, giữ 24h), `QuoteCardViewModel` (`Idle/Rendering/Ready/Error`, recycle bitmap), `QuoteCardSheetContent`, và nhánh `showQuoteSheet`/`quoteSpec` trong `ReadingPage` dùng chung `BottomDrawer`.
- ECHO-02: `ContrastingCoverageState.Ready(phrases, selectedPhrase, items: List<ContrastingArticle>)`, `ContrastingArticle(articleId, title, feedName, date, cue)`; thẻ `ContrastingCoverageCard` nằm trong `Content.kt`, chỉ hiện khi `headlineSignalsEnabled`.
- `QuoteCardSharer.buildShareIntent` hiện không có EXTRA_TEXT; test hiện có kiểm action, type, stream và quyền đọc.

## Thiết kế
### Luồng
- Mỗi hàng bài trên thẻ ECHO-02 thêm một nút "Xuất thẻ". Chạm thì mở sheet xem trước (kiểu `QuoteCardSheetContent`).
- Ảnh 1080×1920, chia đôi trên/dưới, mẫu tương phản đen–neon (trên) / trắng–đỏ (dưới) như todo.
- Một dòng nhỏ trên ảnh: "Hai tiêu đề từ kho đọc của tôi. Không phải kiểm chứng sự thật." và watermark `RSS Cat Hub`.
- Chia sẻ qua share sheet; `EXTRA_TEXT` = `#RSSCatHub #Debate`.
- Nút chỉ tồn tại khi thẻ ECHO-02 hiện; tắt `Headline signals` thì cả thẻ và nút ẩn.

### Thành phần
- `domain/steelman/DualPerspectiveSpec` (thuần): `topTitle, topSource, topDate, bottomTitle, bottomSource, bottomDate, cue`; `DualPerspectiveTruncator` cắt tiêu đề theo hằng số `MAX_HEADLINE_CHARS` và báo `wasCut`.
- `infrastructure/quote/DualCardRenderer` (thuần): vẽ bằng `android.graphics`, không cần Context; kích thước và bảng màu là hằng số có tên.
- `QuoteCardSharer`: thêm tham số tuỳ chọn `shareText: String? = null` và tham số tên tệp để thẻ mới không lẫn với thẻ trích dẫn; mặc định giữ nguyên hành vi cũ.
- `DualCardViewModel` (Hilt, đúng một `@Inject` constructor, tiêm `defaultDispatcher`) và `DualCardSheetContent`, theo mẫu thẻ trích dẫn.
- `ReadingViewModel`/`ReadingPage`: `openDualCard(item)` dựng spec từ bài đang đọc và `ContrastingArticle`; tái dùng drawer, nhánh đóng và `BackHandler` hiện có.

### Luồng dữ liệu và lỗi
- Spec dựng từ dữ liệu đã có trong bộ nhớ: không truy vấn DB thêm, không mạng, không AI.
- Render trên `defaultDispatcher`, ghi PNG trên IO. Lỗi render hay ghi file thì state `Error` kèm nút thử lại; bitmap được recycle khi đổi trạng thái hoặc đóng sheet.
- Tiêu đề quá dài bị cắt có dấu "…" kèm ghi chú "đã rút gọn".

### Chuỗi giao diện
Khoảng 6 chuỗi mới ở 38 locale (EN và VI viết tay, còn lại tiếng Anh), kiểm bằng `StringResourceParityTest`.

## Giới hạn nói thẳng
1. Hai tiêu đề đặt cạnh nhau vẫn có thể bị người xem hiểu là "nguồn này phản bác nguồn kia"; dòng chú thích giảm nhưng không loại bỏ điều đó.
2. Chỉ xuất được khi kho có từ hai nguồn trở lên và có bài chứa từ khoá trái chiều, nên phần lớn người dùng sẽ không thấy nút (kho Pixel hiện chỉ có một feed).
3. Đây là khớp từ vựng, không phải kiểm chứng; tên tiêu đề của nguồn khác được in công khai trên ảnh do người dùng tự chọn chia sẻ.
4. Một số ứng dụng bỏ `EXTRA_TEXT` khi nhận kèm ảnh nên hashtag có thể không đến nơi.

## Kiểm thử
- Unit: `DualPerspectiveTruncatorTest`, `DualPerspectiveSpecTest`; Robolectric `DualCardRendererTest` (kích thước 1080×1920, hai nửa có màu nền khác nhau, không crash với tiêu đề rỗng/dài/Unicode).
- Unit: `QuoteCardSharerTest` thêm ca có `EXTRA_TEXT` và ca mặc định không có; `DualCardViewModelTest` (Idle→Rendering→Ready, Error khi ghi lỗi, recycle bitmap); `ReadingViewModel` dựng spec đúng.
- Compose/Robolectric: nút "Xuất thẻ" trên thẻ ECHO-02 gọi đúng callback với đúng bài; sheet hiện preview/chú thích rút gọn; nút ẩn khi tắt `Headline signals`.
- Instrumentation trên Pixel `2B051FDH3006MU` bằng `am instrument` (không dùng `connectedDevDebugAndroidTest`).
- Smoke Pixel: bản sao DB có feed thứ hai như ECHO-02 (SQL đã có trong plan ECHO-02); backup cả Room (3 file) và DataStore trước, khôi phục và `cmp` sau; tự đóng quảng cáo toàn màn hình theo quy tắc và báo cáo; mở sheet, xem ảnh preview, mở share sheet (không gửi đi đâu).
- ReadingViewModel test phải tiêm `defaultDispatcher` = dispatcher test để observer `init` không nhảy sang `Dispatchers.Default`.

## Ngoài phạm vi
AI sinh luận điểm phản biện, ô nhập ghi chú tự do, nhiều bài trong một thẻ, đổi Room, quảng cáo, IAP, API key, chia sẻ tự động.
