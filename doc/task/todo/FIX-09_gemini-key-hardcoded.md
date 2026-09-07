# [FIX-09] Gemini API Key Hardcode Trong APK (Chỉ Obfuscate, Không Bảo Mật Thật)

- **Type:** Bug / Security Defect
- **Priority:** `P0 (Blocker)`
- **Estimation:** `5 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`infrastructure/ai/GeminiConfig.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiConfig.kt#L1-L59)

## Vấn đề thực tế
`GeminiConfig.kt` (dòng 1-59) chứa 6 API key Gemini dưới dạng `ENCODED_KEYS` (dòng 31-38) — mỗi key chỉ được XOR với một `PAD` cố định (`"rHub!2026#ReadYou\$Gemini^Pad.v1"`, dòng 28) rồi Base64-encode, giải mã bằng hàm `deobfuscate()` (dòng 48-59) ngay trong app lúc runtime. Đây **chỉ là obfuscation tự chế**, không phải mã hoá thật — comment trong chính file (dòng 8-16) đã tự thừa nhận: "kẻ tấn công quyết tâm vẫn có thể decompile lấy PAD + thuật toán để khôi phục key". Vì `PAD` và thuật toán XOR đều nằm trong cùng file APK (dạng bytecode có thể decompile bằng `jadx`/`apktool` trong vài phút), toàn bộ 6 key thực chất **có thể khôi phục 100%** từ APK release mà không cần reverse-engineer phức tạp. Rủi ro: kẻ tấn công lấy được key, gọi Gemini API thay mặt app → tốn quota, phát sinh chi phí bill AI ngoài kiểm soát, hoặc bị Google khoá key vì lạm dụng làm sập tính năng AI Summary/Deep Read/Mind Map cho toàn bộ người dùng thật.

## User Story
> Là chủ sở hữu ứng dụng,
> Tôi muốn API key Gemini không thể bị trích xuất nguyên vẹn từ APK đã publish,
> Để tránh bị đánh cắp quota, phát sinh chi phí ngoài ý muốn, hoặc bị Google vô hiệu hoá key do lạm dụng.

## Acceptance Criteria (Gherkin)
- **Given** một kẻ tấn công decompile file APK release (dùng `jadx`, `apktool`, hoặc đọc trực tiếp bytecode)
- **When** họ tìm và phân tích `GeminiConfig.kt`/class tương ứng đã biên dịch
- **Then** họ **không thể** khôi phục được bất kỳ API key Gemini nào dạng plaintext dùng được ngay (không còn cả `PAD` lẫn `ENCODED_KEYS` cùng nằm trong APK)
- **And** app gọi Gemini API thông qua một trong các cơ chế an toàn thật sự: (a) backend proxy do chủ app kiểm soát (app gửi request tới proxy, proxy giữ key và forward tới Gemini), hoặc (b) Firebase AI Logic / Vertex AI kết hợp Firebase App Check (key không ship trong app, mỗi request được xác thực qua App Check attestation), hoặc (c) remote config đã ký số (signed) mà app chỉ tải key khi cần và verify chữ ký trước khi dùng — không chấp nhận giải pháp obfuscation client-side thuần túy (XOR/Base64/AES với key hardcode cùng APK) vì vẫn recoverable 100%.
- **And** nếu chọn phương án proxy/remote-config, key Gemini gốc được thu hồi (revoke) khỏi Google Cloud Console và thay bằng key mới chỉ tồn tại phía backend.
- **And** tính năng AI Summary/Deep Read/Mind Map/Semantic Search vẫn hoạt động bình thường sau khi đổi cơ chế, có xử lý lỗi thân thiện khi backend/proxy không khả dụng (không crash, hiển thị `SummaryState.Error`/tương đương).
- **And** trong lúc chưa có backend, README/`doc/AD.MD` (hoặc file doc mới) ghi rõ đây là rủi ro đã biết, kèm hướng dẫn giới hạn key trong Google Cloud Console (Application restriction = Android app package + SHA-1, API restriction = Generative Language API) như một lớp phòng thủ tạm thời — không thay thế cho việc gỡ key khỏi APK.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-09] "Gemini API Key Hardcode Trong APK (Chỉ Obfuscate, Không Bảo Mật Thật)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-09_gemini-key-hardcoded.md trước khi bắt đầu.

Đây là task ARCHITECTURE lớn (P0 security), không phải fix nhỏ — bắt buộc tham khảo ý kiến độc lập trước khi triển khai (xem bước 3).

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (GeminiConfig.kt) và toàn bộ nơi gọi GeminiConfig.API_KEYS/GeminiConfig.MODEL (grep trong infrastructure/ai/GeminiSummaryService.kt và bất kỳ nơi nào khác), xác nhận vấn đề còn tồn tại (không giả định) — key vẫn còn trong ENCODED_KEYS hay đã chuyển ra ngoài APK.
2. Implement fix đúng theo Acceptance Criteria. Vì không có backend server sẵn có trong repo này, ưu tiên phương án khả thi nhất trong phạm vi 1 task:
   - Nếu user/team có backend riêng: thiết kế 1 endpoint proxy đơn giản (app gọi proxy, proxy forward Gemini) — cập nhật GeminiSummaryService.kt để gọi proxy URL thay vì gọi Gemini trực tiếp bằng key local.
   - Nếu chưa có backend và không thể tạo mới trong scope task: cân nhắc Firebase AI Logic (Vertex AI in Firebase) + App Check làm giải pháp không cần tự vận hành server, hoặc Firebase Remote Config đã ký số làm bước trung gian giảm rủi ro trong lúc chờ backend thật.
   - Ghi rõ trong PR/commit phương án đã chọn và lý do, vì đây là quyết định kiến trúc ảnh hưởng chi phí vận hành lâu dài.
   Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới (ví dụ thông báo lỗi khi proxy/backend không khả dụng).
3. BẮT BUỘC tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt kiến trúc (đây là thay đổi bảo mật/kiến trúc quan trọng):
   - `codex exec -s workspace-write "Review approach cho task FIX-09 trong repo RSS Cat Hub: <tóm tắt ngắn phương án bạn định làm để gỡ Gemini key khỏi APK — proxy/Firebase AI Logic/remote config ký số>. Chỉ ra rủi ro/cách tốt hơn nếu có, đặc biệt về chi phí vận hành và khả năng bị lạm dụng nếu backend không có rate-limit/auth."`
   - `claude -p "Review approach cho task FIX-09 trong repo RSS Cat Hub: <tóm tắt ngắn phương án bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm. Đặc biệt kiểm tra: sau khi decompile lại APK debug đã build, có còn tìm thấy key Gemini plaintext-recoverable hay không (thử decompile thủ công bằng `apktool d` hoặc đọc class file nếu có công cụ).
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — ví dụ proxy trả lỗi 401/429/timeout được xử lý đúng thành `SummaryState.Error` tương ứng.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — nếu có UI báo lỗi mới khi backend không khả dụng.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (gọi proxy/Firebase thật hoặc mock server giả lập, xác nhận app không còn gọi trực tiếp `generativelanguage.googleapis.com` bằng key local nếu chọn phương án proxy).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công bấm Tóm tắt AI/Deep Read/Mind Map, xác nhận vẫn hoạt động qua cơ chế mới, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm, và **xác nhận rõ ràng key Gemini gốc đã được revoke khỏi Google Cloud Console** nếu áp dụng).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
