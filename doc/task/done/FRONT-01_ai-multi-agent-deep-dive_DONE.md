# FRONT-01 (AI Deep Dive, bản thu hẹp)

**Date:** 2026-11-12  
**Branch:** `dev` (không push)  
**Scope:** Menu ⋮ của màn đọc có mục `🔬 AI Deep Dive`. Gemini soạn một báo cáo một trang gồm **Bối cảnh**, **Góc nhìn đối lập** và **Ma trận tác động** (ngắn hạn / dài hạn) từ **tiêu đề và tối đa 6.000 ký tự nội dung bài**. Báo cáo xem trong sheet và xuất được PDF A4 một trang.  
**Spec / plan:** `docs/superpowers/specs/2026-11-11-front-01-ai-deep-dive-design.md`, `docs/superpowers/plans/2026-11-11-front-01-ai-deep-dive.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn), vì đây là bản thu hẹp và phần Gemini thật chưa kiểm được trên máy (xem "Chưa kiểm").

## Nói thẳng về phạm vi

- **"3 agent" là 3 vai trong MỘT request** Gemini trả JSON ba phần (`context`, `critical`, `impact`). Không phải chuỗi agent: phần ma trận không đọc đầu ra đã kiểm của hai phần trước. Owner chọn cách này để tiết kiệm quota (key chung đang hỏng).
- **Không tra cứu web.** "Bối cảnh lịch sử" chỉ là kiến thức của mô hình. Prompt cấm bịa số liệu, trích dẫn, tên và ngày.
- **Kiểm tra chỉ là cấu trúc**, không kiểm sự thật. Sheet và PDF ghi nhãn "Do AI soạn từ tiêu đề và một đoạn trích; không tìm kiếm web; có thể sai".
- **Một phần thân bài rời máy** (tối đa 6.000 ký tự, đã bỏ HTML) khi người dùng đồng ý; khác RPG-02 chỉ gửi tiêu đề + mô tả. Consent nói rõ điều này. Không gửi đường dẫn, tên nguồn, tài khoản.
- Khác BOUNTY-02 (tổng hợp cơ học cục bộ, không AI) và EXC-06 (chat nhiều lượt).

## Đã làm

- `domain/ai/agents/DeepDivePolicy` (thuần): giới hạn trích 6.000 ký tự (không cắt giữa surrogate), prompt, body JSON mode, kiểm cấu trúc: 3–5 ý bối cảnh, 2–4 góc nhìn, 2–5 dòng ma trận, từng chuỗi có giới hạn ký tự; từ chối khi không phải JSON, thiếu phần, rỗng, sai số lượng, quá dài. `DeepDiveReport` là model.
- `domain/sv/AiDeepDiveService`: một request qua `AiRequestGateway` (use case `deep_dive`); nhận 3 chuỗi chứ không nhận bài; log chỉ in loại exception hoặc lý do từ chối.
- Consent riêng `deep_dive_gemini_consent` (`Boolean?`, null = chưa hỏi), khác khóa quiz và Podcast Studio; hỏi trong sheet, "Để sau" không lưu; công tắc ở Cài đặt → Quyền riêng tư.
- `DeepDiveViewModel` (riêng, không đụng constructor `ReadingViewModel`): sáu trạng thái; chỉ gọi Gemini khi consent đúng bằng `true`; một request mỗi bài; lỗi không cache và chỉ thử lại khi bấm; đổi bài hoặc đóng sheet huỷ request.
- `DeepDiveCache`: Hilt `@Singleton`, LRU 10 báo cáo (cache phải sống lâu hơn ViewModel, bài học RPG-02).
- PDF: `DeepDivePdfLayout` (thuần: thử cỡ chữ 10 rồi 9 pt, nếu vẫn tràn thì cắt từ cuối theo thứ tự ma trận → bối cảnh → góc nhìn, có tối thiểu, và ghi dòng "đã rút gọn"), `DeepDivePdfRenderer` (`android.graphics.pdf.PdfDocument`, A4 595×842 pt, một trang, dừng ở lề dưới), `DeepDiveSharer` (`cacheDir/deepdive/`, slug ASCII ≤ 40, giữ 24 giờ, `FileProvider` có sẵn). Không thêm dependency.
- UI: `DeepDiveSheet`/`DeepDiveSheetContent`, mục menu trong `TopBar`, nối trong `ReadingPage` (đóng sheet khi đổi bài).
- 24 chuỗi `deep_dive_*` × 38 locale (EN và VI viết tay, 36 bản là tiếng Anh), thêm bằng một script chạy "tất cả hoặc không".
- Room vẫn v20, không migration, không dependency, manifest và `file_paths.xml` không đổi.

## Kiểm chứng (JVM)

- Full suite chạy lại sạch (`--rerun-tasks`, HEAD `790928fe`): **2.901 test, 0 lỗi, 0 skip**, 403 tệp kết quả, không tệp cũ (nền trước: 2.791, thêm 110). Lint: **0 lỗi, 2.465 cảnh báo** (nền 2.463; tăng 2 nhưng **0 cảnh báo nằm trong tệp FRONT-01**). `assembleDevDebug` và `assembleDevDebugAndroidTest` thành công.
- Test mới: consent (4 + 2 công tắc), prompt/trích (12), kiểm cấu trúc (13), service (12), cache (6), layout PDF (7), renderer (5), sharer (6), view model (20), sheet (10), menu (3), nối dây (5), chuỗi (5).
- Mutation (sửa, test phải đỏ, khôi phục bằng `cmp`): giới hạn và biên của từng luật, mọi nhánh của service (khóa, use case, huỷ, nhận diện `MissingApiKey`), thứ tự và độ cắt của LRU, `@Singleton`, từng quyết định của view model, từng trạng thái của sheet, nút menu, nối dây, lề trang PDF, thư mục/mime/thời hạn của sharer, chuỗi (thiếu khoá ở một locale, `%d`, mất chữ "text" trong consent).

## Kiểm chứng (Tecno KJ7 `115333744A005844`, Android 14, 2026-11-12)

Cổng thiết bị trước (không instrumentation, màn hình mở khóa, không app lạ ở foreground), sao lưu byte-exact Room, `-shm`, `-wal`, DataStore và hai `shared_prefs`. (Owner chọn KJ7 vì Pixel đang mở `loyalty.skyjoy.vn.dev`.)

- **androidTest `DeepDiveSheetWidgetTest`:** 7/7 (consent, "Để sau", đang tải, lỗi + Thử lại, báo cáo đủ ba khối + ma trận, Xuất PDF, và `PdfRenderer` mở lại PDF thật: đúng 1 trang A4). Ba mutation trên máy đều bị bắt (nút Đồng ý gửi sai, mất nhãn AI, thêm trang thứ hai).
- **Smoke tay với app thật:** menu ⋮ có `🔬 AI Deep Dive` ngay dưới AI Deep Read; sheet consent nêu "6,000 characters"; trước khi trả lời, log **0** dòng `deep_dive`; **"Not now"** đóng sheet và DataStore giữ nguyên 194 byte; **"Agree"** ghi khoá (DataStore thành 226 byte) và gọi đúng **1 request** (gateway thử lần lượt 6 key).
- Quảng cáo toàn màn hình khi mở app: chỉ dùng Back (lần đầu chưa đóng, lần sau đóng được); không chạm "Continue to app".
- **Khôi phục:** Room, `-shm`, `-wal`, DataStore (về 194 byte, khoá consent biến mất), hai `shared_prefs` đều **BYTE IDENTICAL**; test package đã gỡ; `cache/deepdive` đã xoá; app dừng, bản HEAD sạch được cài.

## Phát hiện khi làm

1. **`PdfDocument` không chạy được trên JVM:** Robolectric đóng nó ngay khi tạo ("document is closed"). Tôi bỏ test giả của `render`, thay bằng test đọc nguồn kiểm đúng một `startPage` và một `finishPage`, và đưa việc kiểm PDF thật lên androidTest (đã qua trên KJ7).
2. **Test của tôi làm `DossierSharerTest` (BOUNTY-02) đỏ khi chạy chung:** `androidx FileProvider` giữ cache đường dẫn tĩnh theo từng sandbox Robolectric. Full suite lần đầu có 1 lỗi; chạy riêng thì xanh. Đã vá trong test của tôi (xoá map cache trước và sau), mutation chứng minh test bắt được; mã sản xuất không đổi.
3. **Test quá yếu lộ ra qua mutation (đã vá):** các test so với chính hằng số nên không bắt được việc đổi giá trị (giới hạn 6.000, 240 ký tự, tên thư mục `deepdive`), và test "tràn trang" chỉ tràn ở bảng ma trận chứ không ở phần bullet. Đã thêm test khoá số cứng và test tràn ở bullet.
4. **Hai đợt mutation cho kết quả không hợp lệ** (script truyền sai đối số; một đột biến bị ngắt giữa chừng). Tôi không tính chúng là "đã bắt", chạy lại từng ca và kiểm file đã khôi phục.
5. Một lỗi cú pháp trong test của tôi (`*/` trong KDoc đóng comment sớm), và hai lỗi test của tôi trên màn Compose (báo cáo cuộn nên nút Xuất PDF nằm ngoài màn hình): sửa test, mã sản xuất không đổi.

## Chưa kiểm (nói thẳng)

- **Báo cáo Ready thật từ Gemini:** key mặc định chung không dùng được (2 key `RateLimited`, 4 key `InvalidApiKey`, FIX-09 owner hoãn), nên request duy nhất của smoke thất bại và sheet hiện "Chưa có báo cáo AI" + Thử lại. Owner chọn bỏ qua phần này. Vì vậy chưa thấy trên máy: báo cáo thật hiển thị ra sao, **cache khi mở lại cùng bài** (JVM chỉ chứng minh ViewModel mới dùng lại cache singleton), **PDF sinh từ báo cáo thật** (androidTest dùng dữ liệu mẫu), tỉ lệ AI trả JSON hợp lệ (RPG-02 từng bị 2/25 câu bị từ chối "không phải JSON", nguyên nhân chưa rõ, nên Deep Dive có thể gặp lại), chế độ máy bay + Thử lại, tắt công tắc rồi mở bài mới, và ngôn ngữ nào ngoài tiếng Anh.
- Chưa mở PDF xuất bằng ứng dụng đọc PDF thật, và chưa xem ảnh trang để đánh giá bố cục bằng mắt; chỉ kiểm bằng `PdfRenderer`: đúng 1 trang, đúng kích thước A4.
- Chưa kiểm trên Pixel (chỉ KJ7), chưa kiểm font CJK/RTL trong PDF (dùng font hệ thống).
- Task gốc yêu cầu "3 agent chạy chuỗi"; bản này không làm vậy (xem phạm vi).

## Giới hạn

- AI có thể sai hoặc bịa chi tiết; kiểm cấu trúc không phát hiện được. Nhãn nói rõ điều đó.
- Cache chỉ ở bộ nhớ: mất khi đóng app (PDF đã xuất vẫn còn trong 24 giờ).
- Một phần thân bài (≤ 6.000 ký tự) rời máy khi có consent.
- Key mặc định chung và có key hỏng (FIX-09), nên tính năng có thể thất bại do quota hoặc key, không phải do mã.
- 36 locale dùng chuỗi tiếng Anh.

## Commits trên `dev`

- `943a96b0` docs: thiết kế · `0b93175b` docs: kế hoạch
- `e5673943` consent riêng
- `49187c10` model, giới hạn, prompt
- `7f98810e` kiểm cấu trúc
- `6202c56f` service
- `533307df` cache singleton
- `b7216851` layout PDF
- `524464b2` renderer và sharer PDF
- `405b8222` view model
- `71767d7f` chuỗi 38 locale và công tắc
- `38549c33` sheet, menu, nối `ReadingPage`
- `d8058ef1` androidTest trên máy
- `790928fe` vá test sharer rò rỉ FileProvider
