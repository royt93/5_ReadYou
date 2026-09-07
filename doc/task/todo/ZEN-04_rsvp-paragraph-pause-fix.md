# [ZEN-04] RSVP paragraph-pause chết logic

- **Type:** Bug
- **Priority:** `P1 (High)`
- **Estimation:** `2 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizer.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizer.kt#L15-L92)

## Vấn đề thực tế
`RsvpTokenizer.cleanHtml()` (dòng 17-28) gộp mọi khoảng trắng, kể cả `\n\n` ngắt đoạn, thành 1 khoảng trắng đơn qua `whitespaceRegex = Regex("\\s+")` **TRƯỚC KHI** `tokenize()` (dòng 60) cố gắng tách đoạn văn bằng `cleaned.split("\n\n", "\r\n\r\n")`. Vì `cleaned` (kết quả của `cleanHtml`) không còn bất kỳ chuỗi `"\n\n"` nào (đã bị collapse thành `" "` ở bước trước), lệnh `split("\n\n", "\r\n\r\n")` luôn trả về **đúng 1 phần tử** (`paragraphs.size == 1`), khiến:
- `pIndex < paragraphs.lastIndex` luôn là `false`
- `isParagraphEnd` (dòng 68) luôn là `false`
- `isParagraphBreak` trong `RsvpToken` không bao giờ nhận giá trị `true`

Kết quả: tính năng "tự động tạm dừng lâu hơn ở cuối đoạn văn" (đã tuyên bố DONE trong `doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md` cho task ZEN-01) **không hoạt động trong thực tế** — người dùng đọc RSVP tốc độ cao không hề được nghỉ thêm khi qua đoạn mới. Test hiện có `RsvpTokenizerTest` (`app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/`) không cover input có nhiều đoạn văn nối tiếp trực tiếp qua `tokenize()` full pipeline nên không phát hiện ra lỗi này.

## User Story
> Là người dùng đọc RSVP ở tốc độ cao (600-900 WPM),
> Tôi muốn ứng dụng tự động ngừng lâu hơn một chút ở cuối mỗi đoạn văn,
> Để não bộ tôi có thời gian xử lý và chuyển ý trước khi bước sang đoạn tiếp theo.

## Acceptance Criteria (Gherkin)
- **Given** nội dung bài viết HTML có nhiều đoạn văn được ngăn cách bởi `<p>...</p>` hoặc `\n\n`
- **When** `RsvpTokenizer.tokenize(content)` được gọi trên toàn bộ pipeline (không tách riêng `cleanHtml` và tách đoạn)
- **Then** ranh giới đoạn văn phải được xác định và giữ lại TRƯỚC khi whitespace bên trong đoạn bị collapse
- **And** token cuối cùng của mỗi đoạn văn (trừ đoạn cuối cùng của bài) phải có `isParagraphBreak == true`
- **And** `extraDelayMs` của token đó phải cộng thêm phần delay dành cho ngắt đoạn (hiện là 250ms, xem `calculateExtraDelayMs`)
- **And** test case mới trong `RsvpTokenizerTest` phải assert cụ thể `isParagraphBreak == true` đúng tại vị trí từ cuối cùng của đoạn văn, và `false` ở mọi vị trí khác, dùng input có ≥ 2 đoạn văn thực tế (kể cả trường hợp HTML `<p>` tag và trường hợp `\n\n` thuần).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-04] "RSVP paragraph-pause chết logic" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-04_rsvp-paragraph-pause-fix.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Cụ thể: đọc `RsvpTokenizer.kt` toàn bộ (`cleanHtml`, `tokenize`, `calculateExtraDelayMs`) và xác nhận `isParagraphBreak` không bao giờ true bằng cách viết thử 1 test tối thiểu.
2. Implement fix: sửa thứ tự xử lý sao cho ranh giới đoạn văn (từ HTML `<p>`, `<br><br>`, hoặc `\n\n` trong text gốc) được phát hiện và đánh dấu TRƯỚC khi whitespace bị collapse bởi `whitespaceRegex`. Cân nhắc: tách bước "đánh dấu ranh giới đoạn" thành một pass riêng biệt trước `cleanHtml`, hoặc thay `cleanHtml` trả về danh sách đoạn văn đã tách sẵn thay vì 1 chuỗi phẳng. Không được phá vỡ các Acceptance Criteria khác của ZEN-01 (ORP, WPM tùy chỉnh, delay theo dấu câu).
3. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
4. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
5. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
6. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
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
