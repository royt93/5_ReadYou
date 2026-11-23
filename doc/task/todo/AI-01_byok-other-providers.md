# [AI-01] Nối đúng OpenAI, DeepSeek, Groq (hoặc bỏ hẳn khỏi giao diện)

- **Type:** Feature / Cleanup
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points` (nối thật); `1 Story Point` (bỏ hẳn)
- **Epic:** 06. KNOWLEDGE — AI (BYOK)
- **Status:** ⏸️ **Deferred** (owner chọn 2026-11-21: "defer làm sau"). **Giảm nhẹ đã làm:** 2026-11-21 ba chip bị làm mờ; **2026-11-23 owner chọn ẩn hẳn, nay `AiProviderChoice.NAMES` chỉ còn `["Gemini"]`** (ba chip không còn hiển thị; chip thứ tư từng bị ép thành cột chữ dọc trên máy 411 dp). Giá trị đã lưu bởi bản cũ vẫn hiển thị là Gemini. Khi nối thật AI-01, thêm lại các tên **sau** Gemini để chỉ số 0 giữ nguyên.
- **Location:** [`AiSettingsPage.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/setting/ai/AiSettingsPage.kt), [`AiProviderChoice.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/setting/ai/AiProviderChoice.kt), [`ExtDataStore.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt) (`customOpenAiApiKey`, `customDeepSeekApiKey`, `customGroqApiKey`, `AiProvider`), [`GeminiSummaryService.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt) (`resolveApiKeys`)

## Vấn đề thực tế (đã đọc mã, 2026-11-21)
- Màn hình AI cho chọn Gemini, OpenAI, DeepSeek, Groq, nhưng **không mã nào ngoài chính màn hình đó đọc `aiProvider`**; grep `api.openai.com`, `api.deepseek.com`, `groq`, `chat/completions` ngoài màn hình cài đặt: 0 kết quả.
- Nút Lưu luôn ghi vào `CustomGeminiApiKey`, bất kể chip nào đang chọn, và `resolveApiKeys()` chỉ dùng khóa Gemini. Trước giảm nhẹ: người dùng chọn OpenAI, dán khóa OpenAI, bấm Lưu thì khóa OpenAI được lưu vào ô Gemini rồi gửi tới `generativelanguage.googleapis.com` kèm `?key=`.
- Ba khóa `CustomOpenAiApiKey`, `CustomDeepSeekApiKey`, `CustomGroqApiKey` có định nghĩa trong DataStore nhưng giao diện không bao giờ ghi chúng.

## User Story
> Là người dùng có khóa API riêng,
> Tôi muốn chọn nhà cung cấp mà tôi có khóa và thấy tính năng AI chạy bằng nhà cung cấp đó,
> Để dùng AI không phụ thuộc hạn mức khóa chung.

## Phương án (cần owner chọn khi quay lại)
1. **Bỏ hẳn ba chip** khỏi giao diện (1 SP): xóa khỏi `AiProviderChoice.NAMES`, giữ nguyên giá trị lưu. Giao diện khớp thực tế.
2. **Nối thật ba nhà cung cấp** (8 SP): client riêng cho từng nơi, lưu khóa theo nhà cung cấp, chọn nhà cung cấp trong `resolveApiKeys`/`AiRequestGateway`, thêm điểm đến dữ liệu mới vào Data safety (OpenAI, DeepSeek, Groq nhận nội dung bài), cần khóa thật để kiểm, và chuỗi cho 37 ngôn ngữ.
3. **Giữ trạng thái hiện tại** (chip mờ): không đổi mã; chip mờ vẫn gây hiểu lầm vì không có lời giải thích.

## Acceptance Criteria (Gherkin)
- **Given** người dùng chọn nhà cung cấp X và lưu khóa **When** chạy một tính năng AI **Then** yêu cầu đi tới máy chủ của X, không tới Google.
- **Given** khóa của nhà cung cấp Y đang lưu **When** chọn X **Then** ô khóa hiện khóa của X, không phải khóa của Y.
- **Given** bản cũ đã lưu `AiProvider` khác Gemini **When** mở màn hình **Then** hiển thị đúng nhà cung cấp đã nối, hoặc Gemini nếu chưa nối.
- **And** Data safety khai đúng các điểm đến dữ liệu.

## Phát hiện bố cục trên máy thật (2026-11-23, Tecno KJ7 1080x2436)
Hàng chip nhà cung cấp là một `Row` cố định 4 chip (`AiSettingsPage.kt:217`); trên máy rộng ~411 dp chip thứ tư `Groq` **bị ép thành cột chữ dọc và cắt ở mép phải** (ảnh `~/exc-flaky-evidence/tecno-ui-20261123/ai.png`). Cấu trúc này có từ commit `10ced53a`, không do việc làm mờ chip. Phương án 1 (bỏ hẳn 3 chip) giải quyết luôn; nếu giữ chip thì cần `FlowRow` hoặc hàng cuộn ngang.

## Ghi chú cho người thực thi
- Hiện có `AiProviderChoice` (6 test JVM) và `AiProviderChipsDeviceTest` (3 test máy); khi nối thật phải đổi cả hai.
- Quyết định này gắn với Data safety (`doc/task/REL-03_data-safety-audit.md`): thêm điểm đến thì phải khai thêm.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [AI-01] "Nối đúng OpenAI, DeepSeek, Groq" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md). Hỏi owner chọn một trong ba phương án trước khi sửa.

Mỗi vòng lặp:
1. Đọc AiSettingsPage.kt, AiProviderChoice.kt, GeminiSummaryService.kt, xác nhận vấn đề còn tồn tại (không giả định).
2. TDD: test đỏ trước, rồi sửa. Chỉ nhánh dev, không nhánh phụ.
3. Build: ./gradlew assembleDevDebug; full JVM với --rerun-tasks (kiểm tuổi XML).
4. Lặp tới khi Acceptance Criteria thỏa.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
1. Audit `git diff` theo Acceptance Criteria, chấm thang 10; chỉ commit nếu > 9/10.
2. Test JVM và test máy (Pixel: gate, sao lưu byte-exact, khôi phục) đều xanh, đột biến bị bắt.
3. Cập nhật `doc/feature.md` và Data safety; commit không attribution AI (R6); push khi owner yêu cầu.
