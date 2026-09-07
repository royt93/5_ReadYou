# [BOUNTY-02] Trình Tạo Hồ Sơ Báo Cáo Phân Tích (Executive Intelligence Dossier)

- **Type:** Feature
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [15. BOUNTY — AI Bounty Hunter Agent & Obsidian Graph](15_BOUNTY_HUNTER_AGENT_AND_GRAPH.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/domain/` (module Agent/Dossier mới, phụ thuộc kết quả từ BOUNTY-01)

## Vấn đề thực tế
Sau khi Agent (BOUNTY-01) thu thập dữ liệu đa nguồn, hiện chưa có bước xử lý/tổng hợp và hiển thị kết quả dưới dạng tài liệu chuẩn hóa. Cần Agent xử lý dữ liệu và xuất ra một tài liệu Dossier chuẩn bao gồm 4 phần:
1. *Executive Summary:* Tóm tắt bản chất trong 3 gạch đầu dòng.
2. *Timeline Sự Kiện:* Các cột mốc quan trọng trong quá khứ liên quan đến chủ đề.
3. *Key Players:* Những nhân vật, công ty, hoặc repo liên quan.
4. *Tranh Luận Cộng Đồng:* Góc nhìn trái chiều từ Reddit/Hacker News.

Hỗ trợ xuất sang file Markdown hoặc PDF để chia sẻ.

## User Story
> Là người đọc tin tức muốn hiểu sâu một chủ đề,
> Tôi muốn nhận một bản báo cáo Dossier có cấu trúc rõ ràng sau khi Agent điều tra xong,
> Để tôi nắm bắt nhanh bối cảnh, các bên liên quan và tranh luận trái chiều mà không cần đọc dàn trải nhiều nguồn.

## Acceptance Criteria (Gherkin)
- **Given** Agent đã thu thập đủ dữ liệu
- **When** quá trình tổng hợp hoàn tất
- **Then** hiển thị tài liệu Dossier định dạng giao diện thẻ tab Material 3 chuyên nghiệp
- **And** cho phép người dùng lưu vào thư viện yêu thích hoặc xuất ra Markdown

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [BOUNTY-02] "Trình Tạo Hồ Sơ Báo Cáo Phân Tích (Executive Intelligence Dossier)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/BOUNTY-02_executive-intelligence-dossier.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [BOUNTY-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [BOUNTY-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
