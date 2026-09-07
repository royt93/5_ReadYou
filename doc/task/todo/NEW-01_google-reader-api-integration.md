# [NEW-01] Tích Hợp Đầy Đủ Chuẩn Google Reader API (FreshRSS, Miniflux, Nextcloud News)

- **Type:** New Feature / Ecosystem
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** 03. NEW — Tính Năng Mới Chuẩn RSS (New Core Features)
- **Location:** [`domain/sv/RssSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/RssSv.kt#L22), [`infrastructure/rss/provider/googleReader/`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/rss/provider/googleReader/)

## Vấn đề thực tế
**Bối cảnh:** Hiện tại code `GoogleReader` mới chỉ có file DTO và trong `RssSv.kt` đang bị comment out, ép fallback về `LocalRssSv`. Đa số người dùng RSS chuyên nghiệp trên thế giới hiện nay tự host FreshRSS, Miniflux, Nextcloud News, BazQux – tất cả đều chạy theo chuẩn Google Reader API v1 (`/accounts/ClientLogin`, `/reader/api/0/stream/contents/`).

## User Story
> Là người dùng sở hữu máy chủ FreshRSS hoặc Miniflux riêng,
> Tôi muốn đăng nhập tài khoản của mình trên RSS Cat Hub,
> Để trạng thái đã đọc và bài viết yêu thích của tôi được đồng bộ hai chiều giữa máy tính và điện thoại.

## Acceptance Criteria (Gherkin)
- **Given** người dùng chọn "Thêm tài khoản Google Reader API"
- **When** nhập Endpoint URL, Username, Password / API Token
- **Then** app xác thực thành công và tải đầy đủ danh sách chuyên mục, feeds và bài viết
- **And** khi người dùng đánh dấu đã đọc hoặc gắn sao (starred) trên điện thoại, trạng thái được đồng bộ ngược lên server ngay lập tức
- **And** mở khóa nút chọn FreshRSS/Miniflux trong giao diện `AddAccountsPage.kt`.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [NEW-01] "Tích Hợp Đầy Đủ Chuẩn Google Reader API (FreshRSS, Miniflux, Nextcloud News)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/NEW-01_google-reader-api-integration.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [NEW-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [NEW-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
