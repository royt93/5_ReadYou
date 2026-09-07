# [FRONT-01] AI Multi-Agent Deep Dive — Báo Cáo Nghiên Cứu Đa Chiều Chuẩn McKinsey

- **Type:** Frontier AI / Agentic Intelligence
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [10. FRONTIER — Ý Tưởng Đỉnh Cao & Mở Rộng Hệ Sinh Thái](10_FRONTIER_AND_ECOSYSTEM.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/` (Gói mới)

## Vấn đề thực tế
Đối với những tin tức kinh tế, địa chính trị hoặc công nghệ phức tạp (ví dụ: biến động lãi suất ngân hàng trung ương, đột phá bán dẫn, khủng hoảng chuỗi cung ứng), một bài báo thông thường chỉ đưa ra một góc nhìn phiến diện. Các lãnh đạo và chuyên gia cần một bản báo cáo phân tích toàn diện đa chiều.

## User Story
> Là nhà đầu tư hoặc chuyên gia phân tích,
> Tôi muốn bấm nút "Nghiên cứu chuyên sâu (Deep Dive)" trên bất kỳ bài báo nào,
> Để bầy AI Agent tự động điều tra bối cảnh lịch sử, thu thập các quan điểm trái chiều và xuất ra một báo cáo nghiên cứu 1 trang chuyên nghiệp.

## Acceptance Criteria (Gherkin)
- **Given** người dùng mở một bài báo phức tạp
- **When** bấm nút "🔬 AI Deep Dive"
- **Then** hệ thống khởi chạy chuỗi 3 Agent phân tích:
  1. *Context Agent:* Phân tích nguyên nhân cội rễ và diễn biến trong quá khứ
  2. *Critical Thinking Agent:* Tổng hợp các phản biện và góc nhìn đối lập từ các trường phái khác nhau
  3. *Synthesis Agent:* Lập bảng ma trận Tác động ngắn hạn & Dài hạn (Impact Matrix)
- **And** xuất ra giao diện báo cáo đẹp như tài liệu tư vấn chiến lược McKinsey, có thể xuất thành file PDF 1 trang.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FRONT-01] "AI Multi-Agent Deep Dive — Báo Cáo Nghiên Cứu Đa Chiều Chuẩn McKinsey" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FRONT-01_ai-multi-agent-deep-dive.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [FRONT-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [FRONT-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
