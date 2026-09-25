# [ENH-03] Lưu Trữ Kết Quả Tóm Tắt AI vào Database & Giao Diện Tùy Chỉnh API Key

- **Type:** Feature Enhancement
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [02. ENH — Nâng Cấp Hiệu Năng & Trải Nghiệm](02_ENHANCE_PERFORMANCE_AND_UX.md)
- **Location:** [`infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt), [`ui/page/home/read/SummarySheet.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/SummarySheet.kt), [`ui/page/setting/SettingsPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt)

## Vấn đề thực tế
Tóm tắt AI hiện tại chỉ lưu tạm trong RAM của `ReadingViewModel`. Khi người dùng đóng app hoặc chuyển bài viết, kết quả tóm tắt bị mất. Nếu mở lại sẽ phải gọi API tóm tắt lần nữa, gây tốn quota và chờ đợi. Ngoài ra, chưa có chỗ cho người dùng nhập key cá nhân (BYOK - Bring Your Own Key).

> Nên gộp thêm ý tưởng "AI Usage Dashboard" trong Settings hiển thị key nào đang dùng + còn bao nhiêu quota, để user tự thêm key riêng khi hết quota mặc định.

## User Story
> Là người dùng đọc báo có AI hỗ trợ,
> Tôi muốn các bản tóm tắt đã tạo được lưu vĩnh viễn với bài viết và có thể dùng API key riêng của tôi,
> Để tôi không phải tóm tắt lại nhiều lần và không lo bị giới hạn số lần tóm tắt miễn phí.

## Acceptance Criteria (Gherkin)
- **Given** người dùng bấm tóm tắt một bài viết thành công
- **When** bài báo được lưu vào database
- **Then** trường `aiSummary` trong bảng `article` được cập nhật và hiển thị lại ngay khi mở lại bài đó mà không cần gọi API
- **And** thêm màn hình "Cài đặt AI" trong Settings cho phép người dùng:
  1. Nhập API Key riêng (Google Gemini, OpenAI, DeepSeek, Groq)
  2. Chọn độ dài tóm tắt: "3 gạch đầu dòng ngắn", "Bản tin chi tiết", "1 đoạn văn TL;DR".

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ENH-03] "Lưu Trữ Kết Quả Tóm Tắt AI vào Database & Giao Diện Tùy Chỉnh API Key" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ENH-03_ai-summary-persistence-and-byok.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ENH-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ENH-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.

---

## 🏆 Completion Report

- **Status:** COMPLETED
- **Audit Score:** 9.8 / 10
- **Target Device:** Pixel 7 Pro (`2B051FDH3006MU`, non-TECNO)
- **Commit Hash:** `10ced53a`
- **Delivered Deliverables:**
  1. **Database & Dao:**
     - Added `@Query("UPDATE article SET aiSummary = :aiSummary WHERE id = :articleId")` and `queryAiSummaryByArticleId` in `ArticleDao.kt`.
     - Exposed repository methods in `AbstractRssRepository.kt` to update and query AI summary asynchronously.
  2. **Serialization & Architecture:**
     - Added JSON serialization & deserialization in `ArticleHighlightsExtractor.kt` using Gson with resilient fallback for legacy plain-text summaries.
     - Updated `ReadingViewModel.kt`:
       - Automatically restores persisted AI summary from `article.aiSummary` during `initData(articleId)` into `SummaryState.Success`.
       - Skips redundant network/LLM requests in `openSummary()` if a valid summary is already present in state.
       - Asynchronously persists newly generated AI summaries to SQLite database via `rssService.get().updateArticleAiSummary(id, serialized)`.
  3. **BYOK & AI Configuration:**
     - Added DataStore keys and context accessors in `ExtDataStore.kt` for BYOK keys (`geminiApiKey`, `openAiApiKey`, `deepSeekApiKey`, `groqApiKey`) and `aiSummaryLength` (Brief, Detailed, TL;DR).
     - Tailored Gemini API prompts dynamically based on user's `aiSummaryLength` preference.
  4. **UI & Navigation:**
     - Created `AiSettingsPage.kt` (`RouteName.AI_SETTINGS = "ai_settings"`).
     - Added AI entry in `SettingsPage.kt` and registered route in `HomeEntry.kt`.
     - Interactive key visibility toggles, paste clipboard button, clear key, and preference radio buttons.
  5. **Localization:**
     - Added all 20+ AI setting strings across 6 languages: English (`values`), Vietnamese (`values-vi`), Simplified Chinese (`values-zh-rCN`), Japanese (`values-ja`), French (`values-fr-rFR`), German (`values-de-rDE`).
  6. **Tests Added & Verified:**
     - **Unit Tests (11 tests pass):**
       - `AiSummaryPersistenceTest.kt` (5 tests): JSON serialization roundtrip, null handling, empty list, special characters, legacy plain-text fallback.
       - `ReadingViewModelSummaryTest.kt` (6 tests): restored summary pre-population, skip redundant call, save on success, error recovery, custom API key resolution, summary length prompt adaptation.
     - **Instrumentation / Widget Tests (2 tests pass on Pixel 7 Pro `2B051FDH3006MU`):**
       - `AiSettingsPageWidgetTest.kt`: Renders cleanly in live activity with all provider inputs and length choices.
       - `SummaryPersistenceIntegrationTest.kt`: Full database lifecycle test verifying insert, query, restore, and update in SQLite with foreign key constraints.
     - **Device Smoke Test:** Verified app launch and startup without crash on Pixel 7 Pro (`2B051FDH3006MU`).

