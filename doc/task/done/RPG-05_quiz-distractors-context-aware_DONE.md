# RPG-05 — Quiz distractor theo ngữ cảnh — DONE

**Hoàn tất:** 2026-10-26 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Vấn đề gốc
`QuizGeneratorService.generateQuiz()` dùng distractor là chuỗi tĩnh giống hệt ở mọi bài cùng loại câu hỏi, và đáp án đúng
(`title.take(65)` / câu đầu cắt 65 ký tự) gần như luôn là lựa chọn dài nhất.

## Thiết kế đã chọn (owner chọn qua AskUserQuestion)
- **Câu hỏi:** "Which headline belongs to this article?" / "Tiêu đề nào thuộc về bài viết này?"
- **Đáp án đúng:** tiêu đề của chính bài, giữ nguyên, không bao giờ bị cắt.
- **Distractor:** tiêu đề các bài khác trong Room (cùng account, mới nhất trước), ưu tiên cùng category, rồi chọn theo độ
  gần về độ dài với đáp án. Thiếu bài thì dùng câu khác trong chính bài; cực kỳ thiếu thì dòng "Not supported: …".
- Tiêu đề rỗng/quá ngắn thì rơi về câu đầu thân bài.
- **Không còn chuỗi cố định nào.** Sinh quiz vẫn xác định theo `articleId` + nội dung + danh sách peer.

## Thay đổi
- `QuizGeneratorService.kt`: viết lại `generateQuiz(..., peers: List<QuizPeer> = emptyList())`; chọn peer theo category
  rồi độ dài; cắt distractor dài hơn `đáp án + 12` ký tự; không đụng `detectCategory` hay hằng `CATEGORY_*` (là khóa Room).
- `QuizPeer.kt` (mới): `title` + `shortDescription`, không có cột body.
- `ArticleDao.queryQuizPeers(articleId, limit)`: chỉ `SELECT title, shortDescription`, cùng account với bài hiện tại,
  loại chính nó và bài không có mô tả. Không đọc `rawDescription`/`fullContent` (giữ ràng buộc FIX-17).
- `BrainRpgRepository.quizPeers()` (IO) và `BrainRpgViewModel.buildQuiz()` (peer ở IO, sinh quiz ở Default; lỗi DB thì
  tự hạ về quiz chỉ từ bài).
- `Content.kt`: bỏ `generateQuiz` đồng bộ trên main thread trong `remember`; dùng `remember` + `LaunchedEffect`, thẻ quiz
  hiện sau bài một nhịp. Không dùng `produceState` vì lint `ProduceStateDoesNotAssignValue` từ chối (cùng quy tắc FIX-21).

## Test thêm
- `QuizGeneratorServiceTest`: 23 test, gồm: distractor khác nhau giữa 2 bài cùng category; không còn chuỗi tĩnh cũ;
  đáp án dài nhất < 40% trên 40 mẫu (cả nhánh có peer); đáp án ngắn nhất < 40% trên 60 mẫu; đáp án là tiêu đề nguyên vẹn
  không bị cắt (60 lần); đáp án không bị cắt/đệm khi mọi peer dài hơn nhiều; xác định theo cùng đầu vào; bài rỗng/ngắn/lặp;
  tiêu đề rỗng; câu hỏi tiếng Việt.
- `ArticleDaoQuizPeersTest` (Room in-memory): loại chính nó + account khác + mô tả rỗng, mới nhất trước, giới hạn, bài không tồn
  tại, và không đọc cột body (bài có `rawDescription` 2 MB).
- `BrainRpgViewModelTest`: `buildQuiz` dùng tiêu đề bài thật trong DB; hạ cấp khi bài không có trong DB.

## Kiểm chứng
- `./gradlew testDevDebugUnitTest --rerun`: **1.342 test, 0 lỗi.**
- `./gradlew lintDevDebug`: **0 lỗi** (2.445 warning, 8 info; không đổi so với trước).
- `./gradlew assembleDevDebug`: pass.
- **Smoke Pixel 7 Pro (`2B051FDH3006MU`)**, mở 3 bài khác nhau: mỗi bài có quiz đúng dạng tiêu đề, đáp án đúng nguyên vẹn,
  distractor là tiêu đề thật của bài khác, độ dài các lựa chọn gần nhau (ví dụ 56/54/59, 72/70/74, 85/86/85 ký tự).
  Bấm đáp án đúng: `baseTotalXp` 850→1000, `quizzesAttempted/Passed` +1, thêm 1 reward `QUIZ` và 1 `xp_event` (150).
  Sau test đã restore snapshot Room về 850 XP, 3 reward, 3 event, 168 bài, `integrity_check = ok`.
- Banner quảng cáo thử nghiệm hiện ở cạnh dưới màn Reader trong smoke nhưng không che UI; owner đã đồng ý tiếp tục.

## Giới hạn đã biết (nói thẳng)
- **Category gần như vô tác dụng với tin BBC:** cả 3 bài smoke đều thành `General Knowledge` vì `detectCategory` chỉ khớp từ
  khóa. "Cùng category" nên thực tế là cả kho tin; distractor đúng loại "tiêu đề thật" nhưng chưa chắc cùng chủ đề.
  Chưa cải thiện phân loại (owner không chọn hướng này).
- Quiz kiểm tra nhận diện tiêu đề, ít kiểm tra hiểu nội dung hơn thiết kế cũ.
- Kho ít bài (< 4 bài khác) thì rơi về câu trong bài hoặc dòng "Not supported: …".
- Chưa chạy androidTest (`BrainQuizCardWidgetTest` không đổi vì card không đổi); chưa nghe/đọc bằng TalkBack.
- Chưa đo độ lệch độ dài ngoài các corpus tổng hợp trong unit test và 3 bài thật.
