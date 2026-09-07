# [DJ-06] "Dual-Voice TTS" Thực Chất Chỉ 1 Giọng Đổi Pitch — Sai Sự Thật So Với Tuyên Bố Tính Năng

- **Type:** Bug / Architecture
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`infrastructure/audio/CommuteAudioPlayer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt#L47) (dòng 47: 1 instance `TextToSpeech`), [`infrastructure/audio/CommuteAudioPlayer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt#L159-L177) (dòng 159-177: `speakCurrentDialogue`)

## Vấn đề thực tế
Phát hiện khi audit lại completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` (tuyên bố "Dual-Voice TTS" đã hoàn thành, epic đạt 9.9/10).

`CommuteAudioPlayer.kt` khai báo:
```kotlin
// dòng 47
private var tts: TextToSpeech? = null
```
Chỉ **1 instance `TextToSpeech` DUY NHẤT** cho toàn bộ episode. Hàm `speakCurrentDialogue()` (dòng 159-177) "giả lập" 2 MC bằng cách đổi `pitch`/`speechRate` trên chính instance đó mỗi khi đổi speaker:
```kotlin
// dòng 164-173
when (currentDialogue.speaker) {
    CommuteSpeaker.ALEX -> {
        tts?.setPitch(0.92f)        // Giọng nam trầm ấm, điềm tĩnh
        tts?.setSpeechRate(1.0f)     // Tốc độ bình thường
    }
    CommuteSpeaker.SAM -> {
        tts?.setPitch(1.28f)        // Giọng nữ năng động, tươi sáng
        tts?.setSpeechRate(1.06f)    // Nhịp độ nhanh hơn đôi chút
    }
}
```
Đây là kỹ thuật đổi pitch/rate trên cùng 1 voice engine mặc định của hệ thống — **không phải "2 giọng đọc thật khác nhau"** như tên tính năng "Dual-Voice TTS" và các mô tả trong epic/README ("chất giọng... tự động thay đổi", "Nam trầm ấm & Nữ năng động") ngụ ý. Trên nhiều thiết bị Android, đổi pitch trên cùng 1 voice tạo ra hiệu ứng nghe rất giả/robotic, không đạt được sự phân biệt "male/female" thật như tuyên bố. Đây là vấn đề tính trung thực trong mô tả tính năng (feature over-claiming), không chỉ là thiếu polish kỹ thuật.

## User Story
> Là người dùng nghe CommuteCast,
> Tôi muốn nghe 2 giọng đọc thực sự khác biệt (không chỉ đổi cao độ giả tạo) HOẶC được biết rõ giới hạn thực tế của tính năng,
> Để trải nghiệm nghe đúng như những gì được quảng bá, không bị đánh lừa bởi tên gọi "Dual-Voice".

## Acceptance Criteria (Gherkin)
- **Given** thiết bị người dùng có sẵn ≥ 2 giọng đọc TTS khác nhau (kiểm tra qua `TextToSpeech.getVoices()`/`getEngines()`)
- **When** CommuteCast phát và chuyển giữa Host Alex và Co-Host Sam
- **Then** hệ thống sử dụng 2 `Voice` khác nhau thực sự (ví dụ giọng nam + giọng nữ hệ thống, hoặc 2 engine khác nhau) thay vì chỉ đổi pitch trên cùng 1 voice
- **And** nếu thiết bị KHÔNG có ≥ 2 giọng phù hợp, hệ thống fallback về pitch/rate như hiện tại NHƯNG UI/mô tả tính năng phải nêu rõ đây là chế độ "giả lập 2 giọng" (ví dụ tooltip/subtitle "Chế độ giọng đơn — thiết bị không hỗ trợ đa giọng"), không quảng cáo là "Dual-Voice" đầy đủ trong trường hợp này
- **And** cập nhật mô tả tính năng trong epic/README/completion report cho khớp với khả năng thực tế đã implement, không tuyên bố quá mức

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-06] ""Dual-Voice TTS" Thực Chất Chỉ 1 Giọng Đổi Pitch — Sai Sự Thật So Với Tuyên Bố Tính Năng" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/DJ-06_real-dual-voice-tts-or-honest-labeling.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (CommuteAudioPlayer.kt dòng 47 và 159-177), xác nhận vấn đề còn tồn tại (không giả định).
2. Quyết định hướng giải quyết theo Acceptance Criteria — 2 lựa chọn hợp lệ:
   a) Implement thực sự 2 giọng khác nhau: dùng `tts.voices` để tìm 2 `Voice` khác nhau (ưu tiên khác giới tính/locale nếu có), gán `tts.voice = ...` riêng cho từng speaker trước khi `speak()`, xử lý gracefully khi thiết bị chỉ có 1 voice khả dụng.
   b) Nếu (a) không khả thi ổn định trên nhiều thiết bị, giữ cơ chế pitch/rate hiện tại NHƯNG sửa toàn bộ text/UI liên quan (mô tả trong CommuteCastSheet.kt, string resources, epic doc) để không dùng từ "Dual-Voice" gây hiểu lầm — đổi thành mô tả chính xác như "2 phong cách giọng đọc" hoặc tương tự, đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de).
   Ưu tiên (a) nếu khả thi vì giữ đúng giá trị tính năng đã hứa hẹn với user. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton.
3. Với thay đổi kiến trúc/thiết kế quan trọng, tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (thiết bị chỉ có 1 voice, thiết bị không có voice nào phù hợp, danh sách voices rỗng).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có thêm label/tooltip mô tả chế độ giọng.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
