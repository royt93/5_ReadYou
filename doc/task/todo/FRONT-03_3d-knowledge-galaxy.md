# [FRONT-03] Ngân Hà Tri Thức 3D — Trực Quan Hóa "Bộ Não Số" (3D Knowledge Galaxy)

- **Type:** Visual Innovation / Gamified Knowledge
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [10. FRONTIER — Ý Tưởng Đỉnh Cao & Mở Rộng Hệ Sinh Thái](10_FRONTIER_AND_ECOSYSTEM.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/galaxy/` (Gói mới)

## Vấn đề thực tế
Đọc nhiều bài viết nhưng không thấy được bức tranh tổng thể các kiến thức liên kết với nhau như thế nào. Trực quan hóa tri thức theo dạng mạng lưới ngân hà 3D mang lại cảm giác thành tựu và khích lệ người dùng tích cực học tập.

## User Story
> Là người ham học hỏi và thích trực quan hóa tư duy,
> Tôi muốn xem không gian 3D biểu diễn toàn bộ các bài viết tôi đã đọc như một dải ngân hà,
> Để tôi chiêm ngưỡng kho tàng kiến thức của mình và thấy được mối liên hệ thú vị giữa các chủ đề.

## Acceptance Criteria (Gherkin)
- **Given** người dùng mở tab "Vũ Trụ Tri Thức"
- **When** màn hình Canvas 3D (sử dụng Compose Canvas hoặc OpenGL Shader siêu nhẹ) khởi chạy
- **Then** mỗi bài viết đã đọc được biểu diễn như 1 vì sao phát sáng; các bài viết cùng chủ đề hoặc cùng tag nối với nhau bằng các đường tơ sáng (Constellations)
- **And** người dùng có thể dùng 2 ngón tay xoay, thu phóng và chạm vào từng ngôi sao để mở lại bài viết
- **And** số lượng sao càng nhiều thì ngân hà càng rực rỡ (tăng dopamine tích cực khi đọc sách).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FRONT-03] "Ngân Hà Tri Thức 3D — Trực Quan Hóa "Bộ Não Số" (3D Knowledge Galaxy)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FRONT-03_3d-knowledge-galaxy.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [FRONT-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [FRONT-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
