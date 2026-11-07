# EXC-11 — Temporal Ask My Archive

- **Ngày:** 2026-11-07.
- **Nhánh:** `dev`.
- **Trạng thái:** owner duyệt spec và yêu cầu lập implementation plan ngày 2026-11-07; chưa bắt đầu implementation.
- **Thiết bị duy nhất:** Tecno BG6 `118743744X002560`.
- **Task gốc:** `doc/task/todo/EXC-11_temporal-ask-my-archive.md`.

## 1. Mục tiêu và quyết định đã chốt

Nhập câu hỏi về chủ đề trong kho RSS đã sync; xem diễn biến theo tháng, trích dẫn nguyên văn từ thân bài và tín hiệu từ ngữ đối lập giữa hai giai đoạn. Toàn bộ xử lý trong app, không Gemini, API key, cloud hoặc tải lại bài.

Quyết định owner ngày 2026-11-07:

1. Phạm vi gồm dấu hiệu đổi quan điểm, không bỏ AC này để làm lát cắt nhỏ.
2. Mỗi giai đoạn có 1–3 dẫn chứng lấy từ body, không thay bằng metadata rồi gọi đó là trích dẫn body.
3. Tín hiệu đổi: từ đối lập xuất hiện ở giai đoạn sau nhưng không ở trước; mỗi bên có ít nhất 2 bài liên quan.
4. Mặc định 12 tháng gần nhất, nhóm theo tháng lịch.
5. Viết và commit spec; dừng chờ owner review trước khi lập plan hoặc sửa code.

Các chi tiết kỹ thuật dưới đây là đề xuất trong spec, chưa phải kết quả triển khai hoặc kiểm chứng hiệu năng.

## 2. Sự thật từ code

- `SemanticSearchEngine.rank(query, articles: List<ArticleWithFeed>, minScoreThreshold, limit)` là suspend function, mặc định ngưỡng `0.22f`, giới hạn 50 kết quả. Engine không tự chọn dispatcher và không lọc ngày.
- Ranking dùng `ArticleEmbeddingRecord.documentText(title, shortDescription)`; description cắt 300 ký tự. Cache embedding hiện có được tái sử dụng. Không đọc body để tìm ứng viên.
- `ArticleListItemWithFeed.toArticleWithFeed()` chuyển metadata sang kiểu engine cần mà không đọc body. Không ghi entity thiếu body này ngược vào DB.
- `ARTICLE_LIST_COLUMNS` loại `rawDescription` và `fullContent`, phù hợp FIX-17.
- `ArticleDao.queryArticleBody(accountId, articleId)` đọc từng bài bằng chunk byte 64 KiB. Chunk bảo vệ CursorWindow, **không** giới hạn tổng bộ nhớ của toàn body.
- `Article.date` không nullable, là ngày xuất bản. `readAt` là lúc người dùng đọc, không dùng để lập timeline này.
- `Context.currentAccountId` là extension hiện có; không có dependency tên `CurrentAccountId`. Mẫu ViewModel hiện có dùng application context và `@IODispatcher`.
- `ContrarianCueLexicon.find()` chỉ trả cue đầu tiên như “however”, “nhưng”, “risk”. Đây không phải cặp từ trái nghĩa; không thể dùng một cue mới xuất hiện làm bằng chứng rằng quan điểm đã đảo chiều.
- Jsoup đã có trong project. Không cần thêm dependency để lấy văn bản từ HTML.

## 3. Phương án và lý do chọn

**Chọn: semantic rank metadata, sau đó đọc body có giới hạn.** Tái sử dụng engine và cache hiện có; không bulk-load body; có dẫn chứng thật. Giới hạn: bỏ sót bài chỉ nhắc chủ đề trong body, chất lượng tìm kiếm tùy khả năng engine.

Hai phương án không chọn:

- Đọc và xếp hạng mọi body: tăng I/O, bộ nhớ; đi ngược mục đích FIX-17, không cần cho thiết kế này.
- Gửi kho bài cho Gemini: thêm phụ thuộc mạng/key, rủi ro riêng tư và nội dung sinh sai; trái AC on-device.

Không thêm Room table, migration, worker, service nền, dependency, abstraction một implementation hoặc tính năng ad/IAP. Room giữ v20.

## 4. Câu hỏi và khoảng thời gian

### 4.1 Mặc định

12 tháng gồm tháng hiện tại và 11 tháng trước, từ đầu ngày 1 của tháng sớm nhất đến thời điểm tìm kiếm. Ví dụ ngày 2026-11-07: bắt đầu 2025-12-01 theo timezone thiết bị, kết thúc ở thời điểm truy vấn ngày 2026-11-07. Không tính ngày tương lai.

Dùng `java.time`, `ZoneId` và clock có thể inject/test. Mỗi lần tìm chụp một `now`, timezone, account và khoảng ngày; mọi bước dùng cùng snapshot này. Nhóm theo `article.date`, không theo ngày sync hoặc `readAt`.

### 4.2 Mốc thời gian trong câu hỏi

AC gốc có câu hỏi kèm mốc thời gian. Chỉ chạy cửa sổ mặc định rồi bỏ qua “từ tháng 1 đến giờ” sẽ **không** đáp ứng AC. Spec đề xuất parser xác định nhỏ, không dùng LLM:

- VI: `từ tháng M [năm YYYY] đến giờ` / `đến nay`.
- EN: `since <English month name> [YYYY]` / `from <English month name> [YYYY] to now`.
- Thiếu năm: dùng lần xuất hiện gần nhất của tháng đó không nằm trong tương lai.
- Mốc hợp lệ đã nhận diện được loại khỏi phần chủ đề trước khi rank. Hiển thị rõ chủ đề và khoảng ngày đã hiểu trước kết quả.
- Câu chứa mốc thời gian rõ nhưng ngoài grammar hỗ trợ không được âm thầm dùng mặc định: yêu cầu người dùng xác nhận/chỉnh ngày qua trường ngày bắt đầu/kết thúc. Câu không có mốc dùng mặc định đã hiển thị.
- Trường ngày tường minh ưu tiên hơn parser. Validate ngày bắt đầu không sau ngày kết thúc, không tương lai; khoảng tối đa `MAX_RANGE_MONTHS = 12`. Khoảng vượt giới hạn báo rõ, không âm thầm cắt.
- Query trim, tối đa `MAX_QUERY_CHARS = 500`; chủ đề trống không chạy DB. Grammar hỗ trợ được ghi trong trợ giúp; không hứa hiểu mọi câu tự nhiên.

Đây là chi tiết đề xuất để bảo toàn AC, cần owner review cùng spec. Không tuyên bố đã duyệt riêng parser hoặc date controls.

## 5. Đọc metadata và xếp hạng

Thêm một query metadata có account predicate, khoảng ngày và keyset pagination `(date, id)` ổn định. Mỗi trang `CANDIDATE_PAGE_SIZE = 200`; `ORDER BY date DESC, id DESC`. Các giá trị được bind qua Room, không ghép SQL từ query người dùng.

- Duyệt hết metadata trong khoảng yêu cầu, không lấy 500 bài mới nhất rồi gọi đó là toàn kho.
- Mỗi trang chuyển metadata qua adapter hiện có và gọi `rank` với `limit = page.size`, ngưỡng mặc định của engine. Không để default limit 50 làm mất bài trước khi nhóm tháng.
- Đếm mọi bài vượt ngưỡng theo tháng; chỉ giữ top `MAX_BODY_CANDIDATES_PER_MONTH = 3` cho dẫn chứng. Tie-break: score giảm dần, date giảm dần, id ổn định.
- Chỉ giữ metadata trang hiện tại, bộ đếm và top-3 mỗi tháng; không tích lũy mọi body/kết quả engine.
- Body của bài khác account không bao giờ được đọc. Mở bài cũng phải resolve article trong account tương ứng; bài bị xóa thì báo không còn tồn tại.
- Kho thay đổi lúc truy vấn không phải snapshot nguyên tử dài hạn. Nếu account đổi, hủy và xóa kết quả cũ; sync/xóa đồng thời có thể khiến kết quả thiếu bài, không được suy diễn đó là biến động quan điểm.

Room chạy trên IO; CPU ranking và phân tích văn bản chạy dispatcher thích hợp ngoài Main, có cancellation giữa trang/bài. Không bọc toàn bộ truy vấn trong một transaction kéo dài.

## 6. Dẫn chứng từ body

Mỗi tháng đọc tuần tự tối đa 3 bài top rank qua `queryArticleBody(accountId, articleId)`. Với cửa sổ tối đa 12 tháng: không quá `MAX_BODY_READS = 36` bài; không giữ toàn bộ body sau khi trích dẫn.

- Trước khi load body, dùng helper độ dài hiện có để kiểm tổng raw/full byte. `MAX_BODY_BYTES = 1_048_576` cho một bài trong tính năng này. Body lịch sử quá giới hạn bị bỏ qua với trạng thái “nội dung quá lớn”, không cố load rồi mới cắt. Chunk 64 KiB vẫn cần cho CursorWindow.
- Ưu tiên `fullContent` không blank, nếu không có dùng `rawDescription`.
- Dùng Jsoup có sẵn, loại script/style và node không hiển thị; lấy text, decode entity, chuẩn hóa khoảng trắng. Không WebView, không tải ảnh/URL trong HTML. Không sửa helper snapshot của EXC-10.
- Dùng `BreakIterator` EN/VI và giới hạn quét `MAX_EVIDENCE_TEXT_CHARS = 40_000`, cắt an toàn ở ranh giới Unicode. Tách câu có thể sai với viết tắt; không tuyên bố đây là phân tích ngôn ngữ hoàn chỉnh.
- Chọn tối đa 1 câu/bài: ưu tiên câu chứa token chủ đề khớp nguyên từ/cụm; hòa chọn câu xuất hiện sớm hơn. Nếu không có, có thể lấy câu đầu đủ nội dung của bài đã rank, ghi rõ đó là trích đoạn không có từ khớp, không giả làm câu trả lời trực tiếp.
- Hiển thị tối đa `MAX_QUOTE_CHARS = 300`, cắt tại ranh giới từ/Unicode. Phần hiện bên trong dấu trích là substring nguyên văn của text body đã chuẩn hóa; dấu lược bỏ nằm ngoài phần trích. Không nối hai câu rời thành một trích dẫn.
- Mỗi dẫn chứng mang article ID, tiêu đề, feed, ngày và thông tin đoạn bị cắt. Chạm mở bài lưu trong app, không tự mở URL ngoài.
- Không dùng title/shortDescription thay body khi body thiếu. Tháng vẫn hiện tiêu đề nguồn và dòng “Không có đủ dẫn chứng từ nội dung đã lưu”.

## 7. Tín hiệu đối lập giữa giai đoạn

Tên UI: **“Có thể có thay đổi quan điểm”**, kèm giải thích luôn nhìn thấy. Không khẳng định tòa soạn/cá nhân đổi ý, không xác định đúng sai hay mối quan hệ nhân quả.

Domain dùng tập cặp đối lập nhỏ, tĩnh, EN/VI, ví dụ:

| Chiều A | Chiều B |
|---|---|
| increase / rising | decrease / falling |
| support / supports | oppose / opposes |
| approve / approves | reject / rejects |
| tăng / tăng lên | giảm / giảm xuống |
| ủng hộ | phản đối |
| chấp thuận | bác bỏ |

Các cụm có thể cùng chiều; nhóm bằng named constants/model, không hardcode rải rác. Matching không phân biệt hoa/thường, khớp nguyên từ/cụm, không khớp substring. Không dùng `ContrarianCueLexicon` thay cho bảng này.

Điều kiện gắn cờ, áp dụng cho **hai tháng lịch liền nhau**:

1. Mỗi tháng có ít nhất `MIN_ARTICLES_PER_SIDE = 2` bài liên quan, đếm theo article ID khác nhau.
2. Trong dẫn chứng body đã chọn có ít nhất một câu ở tháng trước mang chiều A và một câu tháng sau mang chiều B của cùng cặp, hoặc ngược lại.
3. Chiều mới không xuất hiện trong tập dẫn chứng đã kiểm của tháng trước. Đây chỉ là “không thấy trong dẫn chứng đã kiểm”, **không phải** khẳng định không có trong mọi bài của tháng.
4. Câu chứa cả hai chiều, hoặc có dấu hiệu phủ định trực tiếp trước cue (`not`, `never`, `no`, `không`, `chưa`, `chẳng`) bị loại khỏi phát hiện; không cố suy luận phủ định kép.
5. Không nối qua tháng thiếu dữ liệu. Không cờ khi một phía thiếu body/dẫn chứng đủ điều kiện.

Marker hiện cặp cue và link hai câu dẫn chứng làm căn cứ. Hai bài mỗi tháng là mức tối thiểu dữ liệu, không có nghĩa hai bài đều khẳng định cùng chiều. Giới hạn này và việc chỉ kiểm top-3 được ghi rõ trong UI. Từ đối lập có thể nói về đối tượng khác hoặc câu trích của người khác; marker vẫn chỉ là heuristic.

## 8. Cấu trúc tối thiểu

- `domain/archivequery/`: model dữ liệu không phụ thuộc `SemanticSearchResult` hạ tầng; logic khoảng ngày, chọn câu và cặp đối lập thuần/test được. Không định dạng nhãn UI trong domain.
- `infrastructure/archivequery/ArchiveQueryRepository.kt`: orchestration DAO, semantic engine, HTML text và giới hạn đọc. Reuse cache embedding hiện có; không persist query/result mới. Application context nếu cần resolve account chỉ dùng tại lớp thích hợp, không Activity context.
- `ui/page/archivequery/ArchiveQueryViewModel.kt`: nhận account theo pattern hiện có, dependency/dispatcher thật; một search Job có thể hủy. State `Idle`, `Searching`, `Ready`, `NoResults`, `InvalidInput`, `Error`.
- `ui/page/archivequery/ArchiveQueryPage.kt`: stateless content để Compose test, wrapper điều hướng và collection theo lifecycle.

Submit mới hủy job cũ và dùng generation ID/account snapshot để kết quả cũ không ghi đè mới. Query blank hủy và về Idle. `CancellationException` được rethrow, không chuyển thành Error. Lỗi DB chung báo thông điệp localize, không đưa exception/body/query vào UI log. Bài bị xóa/thiếu body thì bỏ dẫn chứng bài đó, không làm hỏng toàn tìm kiếm.

Không observer/timer/service tự tạo. Coroutine thuộc ViewModel scope; account observer và công việc tìm kiếm được hủy khi scope kết thúc. Back rời route hủy tìm kiếm. Không giữ Activity, composable, navigation controller hoặc UI state object trong repository.

## 9. UI và localization

Entry trong Settings, route mới theo pattern `HomeEntry`/`RouteName`. Dùng Material You, `BaseScaffold`, nút Back, safe drawing/IME insets, bố cục một cột cuộn được.

- Text field nhiều dòng vừa đủ, IME Search và nút tìm có label, ngày bắt đầu/kết thúc cùng khoảng đã hiểu.
- Progress có semantics; lỗi input tại trường; lỗi tìm kiếm có retry.
- Timeline đủ mọi tháng trong khoảng, cũ đến mới. Tháng không có bài ghi “Không đủ dữ liệu lưu trữ cho giai đoạn này”. Một tháng có bài vẫn hiển thị nhưng ghi chưa đủ để so sánh hai giai đoạn.
- Card tháng: nhãn tháng/năm định dạng theo locale bằng API platform, số bài liên quan, 1–3 dẫn chứng nếu có, tiêu đề/feed/ngày và trạng thái body thiếu/quá lớn.
- Marker nằm giữa hai tháng liền nhau, có cặp cue, dẫn chứng hai phía và disclaimer; không chỉ phân biệt bằng màu hoặc icon.
- Phần giới hạn ngắn: tìm theo title/description; dẫn chứng tối đa 3 bài/tháng; ngữ pháp ngày và cặp đối lập EN/VI hạn chế; không phải kết luận đổi quan điểm.
- TalkBack đọc được label, trạng thái và link nguồn; touch target đạt chuẩn, font lớn không che controls.

Mọi chuỗi mới thêm đủ 38 locale: EN/VI viết tay, locale khác English fallback. Reuse resource chung đã có. Không khóa trước danh sách key chưa chắc dùng; mọi key mới phải được dùng và parity test kiểm. Count dùng dạng “Articles: %1$d” / “Số bài: %1$d” hoặc plurals đúng cách, không thêm `PluralsCandidate`. Ngày dùng formatter thay vì format specifier khác kiểu giữa EN/VI.

## 10. Test và bằng chứng bắt buộc

### Unit

- Khoảng ngày mặc định, đầu/cuối tháng/năm, năm nhuận, timezone, mốc tương lai, giới hạn 12 tháng, invalid range.
- Parser EN/VI hỗ trợ, bỏ đúng phần mốc khỏi chủ đề, mốc không hỗ trợ không âm thầm áp mặc định; query blank/quá dài.
- Rank/group: ngưỡng, tie-break, hơn 500 bài và bài cũ liên quan vẫn tìm được, nhiều tháng không bị default top-50 che mất.
- Evidence: body blank, full blank fallback raw, HTML/script/entities, Unicode, câu dài/viết tắt, lược bỏ ngoài trích dẫn, token nguyên từ, không thay bằng metadata.
- Shift: hai chiều, mỗi bên ít hơn 2 bài, cue cả hai bên, phủ định, cùng câu có hai chiều, thiếu body, tháng trống, không nối qua khoảng trống, từ trong từ dài không khớp.
- ViewModel: state thật; submit liên tiếp, blank hủy, cancellation không Error, stale result/account không lọt sang kết quả mới, retry sau lỗi.

### Room/repository integration

In-memory Room dùng native SQLite khi kiểm body lớn. Chạy DAO + semantic engine thật + repository: account isolation, keyset cùng timestamp, boundaries ngày, hơn một trang, cache embedding, body lớn không bulk-load, bài bị xóa giữa tìm và đọc, bài khác account không lấy body. Fixture có ít nhất hai tháng, mỗi tháng hai bài, dẫn chứng và marker kiểm được bằng giá trị cụ thể, không chỉ assert “không crash”. Không migration test vì không đổi schema.

### Compose và flow

Idle/input/range/progress/error/empty/month gap, quotes, marker/disclaimer, mở đúng article ID và account, Back, retry, font lớn/insets. Có flow integration từ route qua repository/Room đến timeline và mở bài, không chỉ mock dữ liệu UI.

### Verification sau khi triển khai

1. Chạy focused tests, full `testDevDebugUnitTest`, `lintDevDebug`, `assembleDevDebug` và build androidTest. Ghi số test, failures và lint warnings thật; không ghi trước pass hoặc latency.
2. List devices trước build/device run; target khóa BG6 `118743744X002560` thôi. Không dùng `connectedDevDebugAndroidTest` hoặc lệnh adb thiếu `-s` cho thao tác thiết bị.
3. Trước smoke, backup mới Room + mọi DataStore liên quan; không xóa DataStore. Fixture nhiều tháng chỉ trên bản sao dữ liệu, không sửa ngày/body bài thật.
4. Chạy instrumentation bằng serial BG6, smoke query, timeline/dẫn chứng/marker, mở bài và Back, thử trường hợp không đủ dữ liệu. Giữ màn sáng theo thiết lập stay-awake `2`, khôi phục giá trị trước test (lần gần nhất là `0`).
5. Full-screen ad: xác nhận foreground và dump UI mới, đóng bằng control có label; banner không che thì bỏ qua. Không chạm creative.
6. Khôi phục Room + DataStore, `cmp` từng file backup, kiểm integrity khi phù hợp. Báo riêng mọi bước chưa xác minh.

Một BG6 đủ kiểm luồng này, không cần điện thoại thứ hai. Không thể từ fixture hoặc một máy suy ra độ chính xác ngữ nghĩa trên mọi kho, hiệu năng mọi thiết bị hoặc độ đúng mọi ngôn ngữ. Không đạt test/flow/smoke thì không đánh dấu feature Implemented.

## 11. Ngoài phạm vi

Cloud/LLM, sinh tóm tắt, tải body từ mạng, tìm web, nhận diện thực thể hay ai đang phát biểu, chứng minh quan hệ nhân quả hoặc người nào đổi ý, query history, export/share, nhóm tuần tùy chọn, khoảng trên 12 tháng, sync mới và thay đổi Room.

Không gộp task EXC-05 vào EXC-11. Không commit `.idea`, không worktree, không push. Task gốc tiếp tục mở đến khi code và bằng chứng đạt AC; spec commit không phải feature completion.
