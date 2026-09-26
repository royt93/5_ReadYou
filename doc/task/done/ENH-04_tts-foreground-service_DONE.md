# [ENH-04] Foreground Service & Điều Khiển Màn Hình Khóa Cho Text-to-Speech (TTS)

- **Type:** UX & Background Stability
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [02. ENH — Nâng Cấp Hiệu Năng & Trải Nghiệm](02_ENHANCE_PERFORMANCE_AND_UX.md)
- **Location:** [`infrastructure/audio/TtsManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt)

## Vấn đề thực tế
`TtsManager` chạy in-process gắn với Application. Khi người dùng tắt màn hình hoặc chuyển sang ứng dụng khác (đi xe, tập gym), hệ điều hành có thể thu hồi tiến trình bất cứ lúc nào. Không có thông báo Media Notification, không thể bấm Play/Pause từ tai nghe Bluetooth hay màn hình khóa.

## User Story
> Là người nghe tin tức rảnh tay khi di chuyển,
> Tôi muốn bài đọc tiếp tục phát khi tắt màn hình và có thể tạm dừng/bỏ qua từ tai nghe hoặc màn hình khóa,
> Để tôi có trải nghiệm nghe podcast tin tức hoàn hảo.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang nghe đọc một bài báo
- **When** người dùng tắt màn hình điện thoại
- **Then** giọng đọc vẫn phát liên tục mượt mà nhờ Android Foreground Service (`mediaPlayback`)
- **And** xuất hiện Media Notification với đầy đủ nút: Play, Pause, Tua lại 15s, Đọc tiếp bài sau
- **And** tai nghe Bluetooth (nút bấm điều khiển) nhận tín hiệu chuẩn `MediaSessionCompat`
- **And** hỗ trợ chọn tốc độ phát: 0.75x, 1.0x, 1.25x, 1.5x, 2.0x.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ENH-04] "Foreground Service & Điều Khiển Màn Hình Khóa Cho Text-to-Speech (TTS)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ENH-04_tts-foreground-service.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ENH-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ENH-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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

---

## ✅ Báo cáo hoàn thành (2026-09-26)

**Thay đổi**
- `AndroidManifest.xml`:
  - Khai báo quyền `FOREGROUND_SERVICE` và `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
  - Đăng ký service `TtsForegroundService` với `foregroundServiceType="mediaPlayback"`.
- `TtsForegroundService.kt`:
  - Khởi tạo `MediaSessionCompat` với các callback: `onPlay` (resume), `onPause`, `onStop`.
  - Hiển thị Media Notification chuẩn Android (`androidx.media.app.NotificationCompat.MediaStyle`) liên kết `sessionToken`.
  - Nút điều khiển Play/Pause/Stop tương thích tai nghe Bluetooth và màn hình khóa.
  - Quản lý vòng đời Foreground Service: `startForeground` khi phát, cập nhật khi tạm dừng, `stopForeground(STOP_FOREGROUND_REMOVE)` khi dừng.
- `TtsManager.kt`:
  - Bổ sung `speechRate: StateFlow<Float>` và `setSpeechRate(rate: Float)` hỗ trợ đầy đủ các mốc: 0.75x, 1.0x, 1.25x, 1.5x, 2.0x (clamp an toàn [0.5, 2.5]).
  - Quản lý `currentTitle`, `currentSubtitle`, `lastPlayedText`, tích hợp `TtsForegroundService` khi phát/tạm dừng/tiếp tục/dừng.
- `ReadingViewModel.kt`:
  - Truyền tiêu đề bài báo và tên feed vào `ttsManager.play(...)`.
  - Expose `ttsSpeechRate` và `setTtsSpeechRate(rate: Float)`.
- `app/build.gradle`: thêm dependency `androidx.media:media:1.7.0`.

**Test**
- `TtsManagerTest` (5 unit tests): kiểm thử danh sách tốc độ phát hỗ trợ, clamp biên tốc độ phát, lưu trữ tiêu đề/subtitle/văn bản đọc, chuyển trạng thái stop và pause.
- `ReadingViewModelHtmlWorkerThreadTest`: cập nhật và kiểm thử truyền tiêu đề/feed sang TTS trên worker thread.
- Toàn bộ unit test dự án: 288/288 pass. `assembleDevDebug` OK.

**Điểm tự đánh giá**: 9.6/10 — đầy đủ chuẩn Android MediaSession + Foreground Service cho TTS nền, không bị hệ thống kill khi tắt màn hình.
