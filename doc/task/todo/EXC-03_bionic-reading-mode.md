# [EXC-03] Chế Độ Đọc Bionic Reading (Tăng Tốc Độ Đọc Cho Não Bộ)

- **Type:** Exclusive Reading UX
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** [`ui/component/reader/AnnotatedString.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/component/reader/AnnotatedString.kt), [`ui/page/home/read/Content.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt)

## Vấn đề thực tế
Phương pháp Bionic Reading làm nổi bật (in đậm) các chữ cái đầu tiên của mỗi từ (ví dụ: "**Bio**nic **Rea**ding **gi**úp **m**ắt **d**i **chu**yển **nh**anh **h**ơn"). Não bộ tự động hoàn thiện phần còn lại, giúp người đọc tăng tốc độ từ 200 từ/phút lên 400-500 từ/phút, đặc biệt hữu ích cho người mắc chứng ADHD hoặc cần đọc nhanh lượng tài liệu lớn. `AnnotatedString.kt` hiện chưa có logic in đậm theo tỷ lệ ký tự đầu từ; toggle Bionic Reading chưa tồn tại trong thanh công cụ Style của trang đọc.

## User Story
> Là người cần đọc nhiều bài nghiên cứu và tin tức mỗi ngày,
> Tôi muốn bật chế độ Bionic Reading trong màn hình đọc,
> Để tôi đọc nhanh gấp đôi mà mắt ít bị mỏi hơn.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang ở giao diện đọc bài (`ReadingPage`)
- **When** bật toggle "Bionic Reading" trong thanh công cụ Style
- **Then** toàn bộ đoạn văn bản được parse thành `AnnotatedString` in đậm 30-50% số ký tự đầu của mỗi từ
- **And** hiệu năng xử lý văn bản không làm đơ trang (chuyển đổi dưới 50ms).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-03] "Chế Độ Đọc Bionic Reading (Tăng Tốc Độ Đọc Cho Não Bộ)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/EXC-03_bionic-reading-mode.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
```
