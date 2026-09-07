# [REEL-02] Trình Xem Video RSS & YouTube PiP Không Quảng Cáo (Picture-in-Picture)

- **Type:** Media / Video RSS
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [09. REEL — Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)](09_VISUAL_REELS_AND_MEDIA.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/media/video/` (Gói mới)

## Vấn đề thực tế
Rất nhiều kênh tin tức đính kèm video hoặc kênh YouTube có cấp feed RSS. Khi bấm vào video, người dùng bị văng sang app YouTube hoặc xem trên webview nặng nề nhiều quảng cáo rác.

**Xác nhận qua audit code (2026-09-06):** không tồn tại package `infrastructure/media/video/` hay bất kỳ tích hợp ExoPlayer/PiP nào cho video nhúng trong repo (`find app/src/main -iname "*video*" -o -iname "*exoplayer*" -o -iname "*pip*"` không trả về implementation nào ngoài file task doc này; không có dependency ExoPlayer/Media3 nào trong `app/build.gradle`). Task **chưa được implement**.

## User Story
> Là người theo dõi các kênh tin tức video (TED Talks, VTV24, Kurzgesagt, Bloomberg),
> Tôi muốn xem video ngay trong ứng dụng với khung hình nhỏ nổi (Picture-in-Picture),
> Để tôi vừa có thể xem video vừa tiếp tục lướt danh sách bài đọc khác.

## Acceptance Criteria (Gherkin)
- **Given** bài viết có nhúng video YouTube hoặc thẻ video MP4
- **When** người dùng ấn phát video
- **Then** video phát mượt mà không quảng cáo rác thông qua Native Player / ExoPlayer
- **And** khi người dùng vuốt back hoặc chuyển trang, video tự động thu nhỏ thành cửa sổ nổi Picture-in-Picture (PiP) ở góc màn hình.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [REEL-02] "Trình Xem Video RSS & YouTube PiP Không Quảng Cáo" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/REEL-02_youtube-video-pip.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [REEL-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [REEL-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

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
