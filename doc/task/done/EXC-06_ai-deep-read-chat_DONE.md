# [EXC-06] "AI Deep Read" — Trò Chuyện & Hỏi Đáp Tương Tác Với Bài Báo (Chat with Article)

- **Type:** Exclusive Killer Feature / Conversational AI
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** [`ui/page/home/read/ReadingPage.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt), [`infrastructure/ai/GeminiSummaryService.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt)

## Vấn đề thực tế
Đọc một bài phân tích chuyên sâu về kinh tế/chính trị/công nghệ thường có thuật ngữ khó hiểu hoặc số liệu phức tạp. Thay vì chỉ tóm tắt một chiều, người dùng có thể mở khung chat với bài báo: "Giải thích khái niệm X trong bài bằng ví dụ đời thường", "Tại sao tác giả lại phản đối đề xuất này?", "Liệt kê các rủi ro được nêu trong bài".

## User Story
> Là người thích nghiên cứu sâu kiến thức,
> Tôi muốn đặt câu hỏi trực tiếp cho AI về những điểm tôi chưa hiểu trong bài báo,
> Để tôi nắm bắt kiến thức trọn vẹn và đa chiều chỉ trong vài giây.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang ở giao diện đọc bài (`ReadingPage`)
- **When** bấm vào biểu tượng "💬 Hỏi đáp AI" trên BottomBar
- **Then** xuất hiện BottomSheet chat tương tác với bài báo kèm 3 gợi ý câu hỏi thông minh được sinh tự động
- **And** Gemini trả lời chính xác dựa trên ngữ cảnh toàn văn bài báo mà không bịa đặt (grounded answers)
- **And** người dùng có thể gõ thêm câu hỏi tiếp nối tự do.

## ✅ Completion Report

Task này đã được audit và xác nhận **đã triển khai đầy đủ trong code**, không cần đưa vào `todo/`.

- **Điểm tự chấm:** `9/10` — implementation đầy đủ đúng Acceptance Criteria, có unit test + widget test + integration test, localize đủ 38 locale (vượt yêu cầu tối thiểu 6 ngôn ngữ). Trừ 1 điểm vì chưa xác nhận độc lập bằng loop review 2-AI-agent theo quy trình chuẩn của `_TEMPLATE_TASK.md` (task được audit lại sau khi đã merge, không phải quy trình loop gốc).
- **Commit liên quan:** `d64bfee` — `feat(ai): implement AI Deep Read interactive article Q&A assistant (Loop 11)` (branch `dev`).
- **Implementation:**
  - [`domain/model/article/ArticleDeepRead.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleDeepRead.kt) — model `DeepReadMessage`, `DeepReadSender`, `DeepReadSession`.
  - [`infrastructure/ai/ArticleDeepReadEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/ArticleDeepReadEngine.kt) — sinh câu hỏi gợi ý theo ngữ cảnh + trả lời offline có căn cứ (grounded).
  - [`infrastructure/ai/GeminiSummaryService.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt) — mở rộng `askArticleQuestion`, grounded prompt, key rotation khi lỗi.
  - [`ui/page/home/read/DeepReadChatSheet.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/DeepReadChatSheet.kt) — M3 BottomSheet chat bubble + suggested prompt chips.
  - Nối dây vào `ReadingViewModel.kt`, `ReadingPage.kt`, `TopBar.kt` (menu), `BottomBar.kt` (nút hành động), `SummarySheet.kt`.
  - 15 string resource mới, phủ đủ 38 locale (512 key/locale, 100% key parity).
- **Test đã có:**
  - Unit test: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/ArticleDeepReadEngineTest.kt` (157 dòng, phủ tiếng Anh + tiếng Việt, cả nhánh câu hỏi gợi ý và trả lời offline).
  - Unit test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelDeepReadTest.kt` (240 dòng).
  - Widget test: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/DeepReadChatSheetWidgetTest.kt` (153 dòng).
  - Integration test: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/DeepReadIntegrationTest.kt` (81 dòng, luồng end-to-end sinh câu hỏi → trả lời → render chat).
  - Commit message ghi nhận đã smoke-test trên thiết bị thật + `emulator-5554` (Pixel 10 Pro XL, Android 17).
- **Gap còn sót (nếu làm lại/mở rộng sau này):**
  - Chưa thấy bằng chứng review độc lập qua `codex exec` / `claude -p` như quy trình Loop Prompt chuẩn yêu cầu (không bắt buộc hồi tố).
  - Acceptance Criteria không yêu cầu nhưng có thể cân nhắc thêm: giới hạn độ dài lịch sử chat để tránh vượt context window Gemini khi bài báo rất dài.
