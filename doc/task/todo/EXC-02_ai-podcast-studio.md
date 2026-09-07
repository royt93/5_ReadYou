# [EXC-02] "AI Podcast Studio" — Biến Tin Tức Thành Cuộc Trò Chuyện Audio 2 Người

- **Type:** Exclusive Killer Feature / Audio Innovation
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** [`infrastructure/audio/TtsManager.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt), [`infrastructure/ai/`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/)

## Vấn đề thực tế
Nghe TTS thông thường chỉ là đọc văn bản đơn điệu rất buồn ngủ. "AI Podcast Studio" đưa nội dung 3-5 bài báo vào prompt biến thành kịch bản đối thoại sinh động giữa 2 MC (Alex & Sam: "Chào Sam, cậu đã nghe tin Google vừa ra mắt Gemini thế hệ mới chưa?" — "Ồ, mình vừa xem xong, quả là một bước ngoặt..."). Hệ thống TTS sử dụng 2 voice pitch khác nhau để luân phiên thể hiện 2 MC. `TtsManager.kt` hiện chỉ hỗ trợ đọc tuyến tính 1 giọng, chưa có khả năng chia kịch bản theo nhân vật và đổi pitch.

## User Story
> Là người lái xe đi làm hoặc chạy bộ buổi sáng,
> Tôi muốn nghe tin tức dưới dạng một chương trình podcast trò chuyện tự nhiên dí dỏm,
> Để việc cập nhật tin tức trở nên cuốn hút và thú vị như nghe đài phát thanh.

## Acceptance Criteria (Gherkin)
- **Given** người dùng chọn 1 hoặc nhiều bài viết trong danh sách
- **When** chọn menu "🎙️ Phát Podcast Đối Thoại"
- **Then** Gemini tạo ra kịch bản đối thoại hai người có cảm xúc, chuyển ý tự nhiên
- **And** `TtsManager` chia kịch bản theo nhân vật và đổi giọng (Tone/Pitch) tương ứng
- **And** hiển thị thanh Player sóng âm (waveform animation) đồng bộ khi giọng đọc phát.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-02] "AI Podcast Studio — Biến Tin Tức Thành Cuộc Trò Chuyện Audio 2 Người" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/EXC-02_ai-podcast-studio.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
