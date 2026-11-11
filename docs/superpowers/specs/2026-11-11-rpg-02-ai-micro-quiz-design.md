# RPG-02 — Quiz cuối bài do AI soạn (Gemini), chỉ từ tiêu đề và mô tả ngắn

**Ngày:** 2026-11-11  
**Trạng thái:** Ba phần thiết kế đã được owner duyệt qua AskUserQuestion; bản spec này chờ owner duyệt trước implementation plan.  
**Nhánh:** `dev`, không worktree, không push. Không sửa hoặc commit `.idea`.  
**Thứ tự feature của owner:** EXC-05c xong, FRONT-04a xong (DHU chưa kiểm được), **RPG-02 (đây)**, rồi FRONT-01. Mỗi feature một brainstorm, spec và plan riêng.

## 1. Mục tiêu và hiện trạng

Card quiz cuối bài (`BrainQuizCard`) phải có câu hỏi do AI soạn, thay cho quiz "tiêu đề nào thuộc bài này" đang sinh xác định.

### Hiện trạng đã đọc trong mã

- `QuizGeneratorService.generateQuiz` **không gọi AI**. Sau RPG-05 nó dựng câu hỏi tiêu đề xác định: đáp án đúng là tiêu đề bài, nhiễu là tiêu đề các bài khác lấy từ Room (`ArticleDao.queryQuizPeers`). Tệp `doc/task/todo/RPG-02_ai-micro-quiz-engine.md` ghi rõ khoảng trống này; RPG-05 đã sửa phần nhiễu cố định, **không** thêm AI.
- Luồng dựng quiz: `Content.kt` (dòng 105–110) gọi `BrainRpgViewModel.buildQuiz(articleId, title, content, author)` trong `LaunchedEffect`; card xuất hiện sau bài qua `quiz?.let { item { BrainQuizCard(...) } }` (dòng ~247). `content` ở đây là **thân bài đã dựng** (tham số `target` của `ReadingPage`), không phải mô tả ngắn.
- `Content` **không nhận `shortDescription`**. `ReadingPage` có `readingUiState.articleWithFeed.article.shortDescription` nhưng chưa truyền xuống. Đây là điểm dễ sai: không được lấy 250 ký tự đầu của thân bài và gọi nó là "mô tả".
- `BrainQuizCard(quiz: QuizQuestion, doubleAlreadyClaimed, onAnswerSubmitted, onDoubleXpRequested, onRetryQuizRequested)`; `QuizQuestion(articleId, question, options, correctAnswerIndex, explanation, category)`. XP x3 (150), nút Rewarded Ad "thử lại" và "nhân đôi" đã chạy và **không đổi** (không ad/IAP trong lát này).
- Hạ tầng AI có sẵn: `AiRequestGateway.execute(useCase, requestBody, keys)` (single-flight theo SHA-256 của body, failover nhiều key, timeout 60 s); `GeminiSummaryService.resolveApiKeys()` (key tuỳ chọn của người dùng, rồi key mặc định); mẫu gần nhất là `PodcastStudioScriptService` (chỉ gửi tiêu đề + `shortDescription` ≤ 250 ký tự, `responseMimeType=application/json`, ranh giới consent riêng).
- Consent riêng cho từng tính năng: `Context.podcastStudioGeminiConsent: Boolean?` (null = chưa trả lời), công tắc ở `PrivacySettingsPage`, hộp thoại ở `PodcastStudioViewModel` (`AwaitingConsent`).
- Ràng buộc FIX-17: truy vấn danh sách không đọc cột thân bài. Highlights/MindMap/AskArticle hiện gửi thân bài (≤ 12.000 ký tự) **không** hỏi riêng; RPG-02 đã được owner chọn đi theo ranh giới chặt hơn của Podcast Studio.

## 2. Quyết định đã duyệt

1. **Dữ liệu gửi:** chỉ tiêu đề + mô tả ngắn ≤ 250 ký tự. Không thân bài, link, tên feed, account, tên người dùng.
2. **Dạng câu hỏi:** AI viết cả câu hỏi, 4 đáp án (1 đúng, 3 nhiễu hợp lý) và giải thích.
3. **Khi AI không có:** card hiện "chưa có câu hỏi AI" kèm nút Thử lại. **Không** còn quiz tiêu đề dự phòng.
4. **Consent:** riêng cho quiz, hỏi lần đầu ngay trong card, từ chối thì card hướng dẫn bật trong Quyền riêng tư.
5. **Thời điểm gọi:** khi card quiz thực sự xuất hiện (cuối bài), một lần mỗi bài, cache trong bộ nhớ theo `articleId`.
6. **Code RPG-05:** xoá `generateQuiz`, `QuizPeer`, `queryQuizPeers` và test của chúng; giữ `detectCategory`.
7. **An toàn AI:** nhãn "Do AI soạn" và kiểm tra cấu trúc nghiêm; lỗi thì thử lại.

## 3. Luồng

1. Người dùng cuộn tới cuối bài, item card quiz được compose.
2. Nếu `quizGeminiConsent == null`: card hiện hai nút **Đồng ý** / **Để sau**. "Để sau" không lưu gì và hỏi lại ở bài khác; chỉ công tắc trong Quyền riêng tư lưu `true`/`false`. "Đồng ý" lưu `true`.
3. Nếu `false`: card hiện hướng dẫn "Bật câu hỏi AI trong Cài đặt → Quyền riêng tư" và nút mở trang đó. Không gọi mạng.
4. Nếu `true` và chưa có trong cache: trạng thái `Loading` ("AI đang soạn câu hỏi"), gọi Gemini một lần, kiểm tra, rồi `Ready` hoặc `Failed`.
5. `Failed` (offline, hết quota, key lỗi, JSON hỏng, không qua kiểm tra): hiện "Chưa có câu hỏi AI" và nút **Thử lại**. Thử lại chỉ chạy khi bấm, không tự thử lại.
6. `Ready`: `BrainQuizCard` với nhãn "Do AI soạn từ tiêu đề và mô tả, có thể sai". XP và Rewarded Ad như hiện nay.
7. Cache: bản đồ trong bộ nhớ theo `articleId`, tối đa khoảng 20 bài, loại bỏ bài dùng lâu nhất. Không Room, không DataStore. Đóng app là mất cache.

## 4. Kiểm tra hợp lệ câu hỏi (hàm thuần, test JVM)

Từ chối kết quả nếu:

- không phải JSON hoặc thiếu trường (`question`, `options`, `correctIndex`, `explanation`);
- không đúng 4 lựa chọn;
- có lựa chọn rỗng, hoặc hai lựa chọn trùng nhau sau khi bỏ khác biệt hoa/thường và dấu câu;
- `correctIndex` không phải số nguyên hoặc ngoài 0..3;
- câu hỏi rỗng hoặc dài hơn một ngưỡng;
- đáp án đúng giống nguyên văn câu hỏi (lộ đáp án);
- lựa chọn dài hơn một ngưỡng, hoặc lệch độ dài quá nhiều so với các lựa chọn còn lại (để "chọn cái dài nhất" không thành mẹo);
- giải thích rỗng.

Thứ tự lựa chọn được xáo theo hạt giống từ `articleId` rồi quy đổi lại chỉ số đúng, để vị trí đáp án đúng không dồn về chỗ AI hay đặt. Ngưỡng cụ thể (độ dài tối đa của câu hỏi, của lựa chọn, tỷ lệ lệch) là hằng số có tên, chọn trong plan và chứng minh bằng test biên.

## 5. Prompt và ranh giới riêng tư

- Prompt theo mẫu `PodcastStudioScriptService.buildPrompt`: chỉ có `Title` và `Preview`; yêu cầu JSON đúng cấu trúc ở mục 4; "chỉ dùng dữ kiện đã cho, không bịa"; ngôn ngữ đích là ngôn ngữ app (`Locale.getDefault().toLanguageTag()`).
- Body request gồm `contents` và `generationConfig` với `responseMimeType=application/json` (đã chứng minh cần ở EXC-02). Không có trường nào khác.
- **Mô tả lấy từ `article.shortDescription`**, cắt bằng một hàm sanitize 250 ký tự. `Content` và `BrainRpgViewModel.buildQuiz` được đổi chữ ký để nhận mô tả riêng; tuyệt đối không dựng "mô tả" từ `content`.
- Chỉ gọi Gemini khi consent bằng `true`. Không có "dùng ngầm". Dùng `AiRequestGateway` và `GeminiSummaryService.resolveApiKeys()` hiện có.
- Không ghi log tiêu đề, mô tả hay câu hỏi (chỉ ghi độ dài, mã lỗi và articleId đã rút gọn nếu cần).
- Rỗng cả tiêu đề lẫn mô tả: không gọi, trạng thái `Failed` kèm lý do "bài chưa có nội dung để ra câu hỏi".

## 6. Thay đổi dự kiến

- `domain/model/rpg/QuizQuestion.kt`: thêm nguồn câu hỏi (AI) nếu cần cho nhãn; giữ các trường hiện có.
- `domain/sv/` (hoặc `infrastructure/ai/`): `AiQuizService` (prompt, gọi `AiRequestGateway`, parse, kiểm tra, xáo trộn); phần thuần tách ra để test JVM.
- `ui/page/rpg/BrainRpgViewModel.kt`: `QuizUiState` (`AwaitingConsent`, `Loading`, `Ready`, `Failed`, `ConsentDeclined`), cache bộ nhớ, nhận mô tả, thử lại.
- `ui/ext/ExtDataStore.kt`, `PrivacySettingsViewModel/Page`: khoá `QuizGeminiConsent` (Boolean?), công tắc trong Quyền riêng tư (theo mẫu Podcast Studio).
- `ui/component/rpg/BrainQuizCard.kt` và `Content.kt`: các trạng thái card, nhãn nguồn, hai nút consent, truyền `shortDescription` từ `ReadingPage`.
- Xoá `generateQuiz`, `QuizPeer.kt`, `ArticleDao.queryQuizPeers`, `BrainRpgRepository.quizPeers` và các test của chúng; giữ `detectCategory` (XP theo danh mục).
- Chuỗi mới trong **cả 38 locale** (EN và VI viết riêng), parity test.
- Không đổi Room (v20), không migration, không dependency mới, manifest không đổi.

## 7. Kiểm chứng

- **JVM:** validator (từng nhánh từ chối và biên), parser, prompt (chỉ title và preview, độ dài 250), xáo trộn theo hạt giống, `AiQuizService` với `AiRequestGateway` giả (không request khi chưa consent hoặc consent false, đúng một request khi đồng ý, body chỉ chứa title và preview), `BrainRpgViewModel` (các trạng thái, cache, thử lại, huỷ coroutine).
- **Compose:** từng trạng thái card; nhãn nguồn; nút consent và nút mở cài đặt.
- **Robolectric:** khoá consent đọc ghi qua DataStore.
- **androidTest:** mở rộng `BrainQuizCardWidgetTest`; chạy bằng `adb -s <serial> shell am instrument` có `timeout` (KHÔNG `connected*`).
- **Mutation** cho: kiểm tra consent, giới hạn 250, từng nhánh validator, cache, nguồn mô tả.
- Full suite `--rerun-tasks`, lint, `assembleDevDebug`, `assembleDevDebugAndroidTest`.
- **Thiết bị thật (Pixel `2B051FDH3006MU` và Tecno KJ7 `115333744A005844`, chỉ đọc cho tới khi có plan được duyệt):** backup byte-exact trước, mở bài thật, thấy card AI thật với key mặc định, đếm request bằng logcat. Nếu không có mạng hoặc key hết quota thì ghi trung thực, không bịa kết quả. Không đụng DataStore; consent thử trên máy phải khôi phục bằng restore byte-exact.

## 8. Giới hạn nói thẳng (sẽ ghi vào báo cáo DONE)

- Chỉ có tiêu đề và tối đa 250 ký tự mô tả, nên câu hỏi hẹp và nhiều bài chỉ có tiêu đề; không thể kiểm "hiểu bài" thật.
- Không có cách kiểm đúng/sai về nội dung; kiểm tra chỉ ở cấu trúc. AI có thể sinh hai đáp án cùng đúng hoặc đáp án "đúng" sai về sự thật.
- Offline, hết quota hoặc từ chối consent: **mất card và XP quiz** cho tới khi có AI (đã được owner chọn, không có dự phòng).
- Cache chỉ trong bộ nhớ: mở lại app thì gọi lại Gemini cho bài đã có câu hỏi; một câu hỏi cũ không được giữ giữa các lần chạy.
- LazyColumn có thể compose item quiz sớm một chút nhờ prefetch, nên request có thể bắt đầu khi item sắp hiện, không đúng từng pixel "tới cuối".
- Key mặc định nhúng trong APK dùng chung quota (đã biết, FIX-09 owner hoãn).
- Quiz tiêu đề của RPG-05 bị xoá; nếu sau này cần dự phòng, khôi phục từ git.

## 9. Ngoài phạm vi

Ad/IAP/money, đổi logic XP hoặc skill tree, lưu câu hỏi vào Room, gửi thân bài, quiz nhiều câu, độ khó thích ứng, đổi nhà cung cấp AI, FRONT-01 (AI Deep Dive).

## 10. Tự soát spec

- Đã đối chiếu `Content`/`ReadingPage`: mô tả phải đi từ `shortDescription`, không từ `content`; ghi thành ràng buộc ở mục 5 và 6.
- Consent: khoá mới, không đụng khoá của Podcast Studio.
- Không có chuỗi cố định nào còn cần quiz cũ; `detectCategory` vẫn được dùng cho XP.
- Chưa có số cụ thể cho ngưỡng độ dài: chủ ý để plan chốt bằng hằng số có tên và test biên.
- Không còn placeholder triển khai.
