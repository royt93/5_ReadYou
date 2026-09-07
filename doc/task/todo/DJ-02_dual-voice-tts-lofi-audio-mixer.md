# [DJ-02] Động Cơ Phát Âm 2 Giọng Kèm Nhạc Nền Lofi (Dual-Voice TTS & Audio Mixer)

- **Type:** New Feature
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`infrastructure/audio/CommuteAudioPlayer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt#L38-L177)

## Vấn đề thực tế
Tích hợp bộ phát TTS linh hoạt chuyển đổi giữa 2 chất giọng (Nam trầm ấm & Nữ năng động), hòa âm cùng track nhạc nền lofi acoustic nhẹ nhàng có bản quyền CC0 ở mức âm lượng 15%. Cung cấp giao diện sóng âm thanh trực quan dạng nhịp tim (Audio Visualizer) trong Compose.

> **⚠️ Audit note (2026-09-06):** Task này CHƯA đạt Acceptance Criteria, dù completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` tuyên bố epic hoàn thành 9.9/10. Đọc `CommuteAudioPlayer.kt` cho thấy:
> - Chỉ có **1 instance `TextToSpeech` duy nhất** (dòng 47: `private var tts: TextToSpeech? = null`), "2 giọng" thực chất là đổi `pitch`/`speechRate` trên cùng 1 voice hệ thống (dòng 159-177: `ALEX` → pitch 0.92f, `SAM` → pitch 1.28f). Không phải "2 chất giọng" thật như tuyên bố "Dual-Voice TTS". Xem gap chi tiết ở [`DJ-06`](DJ-06_real-dual-voice-tts-or-honest-labeling.md).
> - **Không có nhạc nền lofi, không có audio ducking, không có audio visualizer** — grep toàn bộ `app/src/main/java` cho từ khóa "lofi" không có kết quả nào; `CommuteAudioPlayer.kt` không có `MediaPlayer`/`SoundPool`/`AudioMixer` nào khác ngoài `TextToSpeech`. Xem gap chi tiết ở [`DJ-07`](DJ-07_lofi-audio-mixing-media3-integration.md).
>
> Task giữ nguyên trong `todo/`, KHÔNG chuyển `done/`.

## User Story
> Là người dùng nghe CommuteCast trên đường đi làm,
> Tôi muốn nghe 2 giọng MC rõ ràng khác biệt trên nền nhạc lofi êm dịu,
> Để trải nghiệm giống một chương trình radio thật thay vì giọng máy đơn điệu.

## Acceptance Criteria (Gherkin)
- **Given** người dùng bắt đầu phát CommuteCast
- **When** đoạn thoại chuyển từ Host A sang Host B
- **Then** chất giọng, cao độ và ngữ điệu TTS tự động thay đổi mượt mà không có khoảng lặng quá 300ms
- **And** nhạc nền lofi tự động giảm âm lượng (Audio Ducking) khi MC đang nói

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-02] "Động Cơ Phát Âm 2 Giọng Kèm Nhạc Nền Lofi (Dual-Voice TTS & Audio Mixer)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" (bao gồm audit note) + "Acceptance Criteria" trong file doc/task/todo/DJ-02_dual-voice-tts-lofi-audio-mixer.md trước khi bắt đầu. Task này liên quan chặt tới DJ-06 (giọng đọc) và DJ-07 (audio mixing/Media3) — kiểm tra 2 file đó tránh làm trùng lặp công sức.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria: chuyển tốc độ chuyển đổi giọng mượt (<300ms), thêm phát nhạc nền lofi (asset CC0 trong res/raw hoặc tải về) hòa trộn với TTS bằng `MediaPlayer`/`AudioTrack` riêng, giảm âm lượng nhạc nền còn ~15% khi TTS đang nói (audio ducking qua `AudioFocusRequest` hoặc điều chỉnh volume thủ công theo `UtteranceProgressListener.onStart/onDone`). Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng, tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
