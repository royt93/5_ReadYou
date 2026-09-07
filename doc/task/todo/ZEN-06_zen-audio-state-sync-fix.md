# [ZEN-06] Zen Audio không đồng bộ trạng thái thật

- **Type:** Bug
- **Priority:** `P2 (Medium)`
- **Estimation:** `2 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`infrastructure/audio/ambient/ZenAudioManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/audio/ambient/ZenAudioManager.kt#L96-L103), [`infrastructure/audio/ambient/ZenSoundSynthesizer.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/audio/ambient/ZenSoundSynthesizer.kt#L207-L221)

## Vấn đề thực tế
Hai lỗi đồng bộ trạng thái độc lập:

1. **Bỏ qua kết quả `requestAudioFocus()`**: `ZenAudioManager.play()` (dòng 96-103) gọi `requestAudioFocus()` nhưng không kiểm tra giá trị `Boolean` trả về — dòng tiếp theo `_isPlaying.value = true` và `synthesizer.start(...)` chạy vô điều kiện dù audio focus có bị từ chối (ví dụ khi app khác đang giữ focus độc quyền `AUDIOFOCUS_GAIN` mà hệ thống không cho `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`). Kết quả: UI báo "đang phát" nhưng có thể không phát âm thanh thực sự (hoặc phát chồng lên use-case khác không mong muốn).

2. **`isPlaying` không đồng bộ khi write lỗi**: Trong `ZenSoundSynthesizer.runSynthesisLoop()` (dòng 207-221), khi `audioTrack?.write(...)` trả về giá trị âm hoặc ném exception, vòng lặp `break` để thoát thread nền — nhưng biến `isPlaying` (trường nội bộ của synthesizer, dòng 22-23) **không được set lại thành `false`**. Đồng thời `ZenAudioManager._isPlaying` (StateFlow hiển thị lên UI) hoàn toàn không có cơ chế lắng nghe/callback từ synthesizer để phản ánh việc phát nhạc đã dừng do lỗi — nó chỉ được set `false` trong `ZenAudioManager.stop()` (gọi thủ công bởi user hoặc audio focus loss). Kết quả: nếu AudioTrack ghi lỗi (device audio bận, buffer lỗi, thiết bị bị rút tai nghe đột ngột gây lỗi driver, v.v.), `ZenSoundSheet` UI vẫn hiển thị "đang phát" dù thực tế đã im lặng hoàn toàn.

Task ZEN-02 (Ambient Soundscapes) đã được tuyên bố DONE trong `doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md` dù các lỗi trên tồn tại từ commit implement ban đầu (`a129f71`).

## User Story
> Là người dùng bật âm thanh nền khi đọc,
> Tôi muốn trạng thái hiển thị trên UI (nút play/pause, icon "đang phát") luôn khớp chính xác với việc âm thanh có thực sự đang phát hay không,
> Để tôi không bị nhầm lẫn tưởng đang có nhạc nền trong khi thực tế đã im lặng do lỗi.

## Acceptance Criteria (Gherkin)
- **Given** `ZenAudioManager.play()` được gọi và `requestAudioFocus()` trả về `false` (bị từ chối)
- **When** hệ thống từ chối audio focus
- **Then** `_isPlaying` KHÔNG được set `true`, và cần có phản hồi rõ ràng cho UI (ví dụ trạng thái lỗi/toast) thay vì âm thầm coi như đã phát thành công
- **And** khi `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` được cấp nhưng sau đó mất focus vĩnh viễn (`AUDIOFOCUS_LOSS`), hành vi `stop()` hiện tại vẫn phải hoạt động đúng (không phá vỡ logic đang có)

- **Given** `ZenSoundSynthesizer` đang chạy `runSynthesisLoop()` và `audioTrack.write()` gặp lỗi (trả về giá trị âm hoặc ném exception) khiến vòng lặp `break`
- **When** thread nền dừng lại do lỗi
- **Then** biến `isPlaying` nội bộ của synthesizer phải được set lại `false` ngay tại điểm dừng do lỗi
- **And** `ZenAudioManager` phải có cơ chế (callback, polling `isCurrentlyPlaying`, hoặc listener) để đồng bộ `_isPlaying` (StateFlow hiển thị UI) xuống `false` khi synthesizer dừng ngoài ý muốn, không chỉ dựa vào lệnh `stop()` gọi thủ công
- **And** có unit test giả lập `audioTrack.write()` trả về lỗi và assert rằng trạng thái `isPlaying`/`isCurrentlyPlaying` phản ánh đúng là đã dừng.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-06] "Zen Audio không đồng bộ trạng thái thật" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-06_zen-audio-state-sync-fix.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Cụ thể: xác nhận `ZenAudioManager.play()` bỏ qua kết quả `requestAudioFocus()`, và `ZenSoundSynthesizer.runSynthesisLoop()` không reset `isPlaying` khi `write()` lỗi.
2. Implement fix:
   - `ZenAudioManager.play()`: kiểm tra kết quả `requestAudioFocus()`, chỉ set `_isPlaying.value = true` và gọi `synthesizer.start(...)` khi focus được cấp (hoặc khi `audioManager == null`, giữ hành vi hiện tại là fallback cho phép phát — xem code `requestAudioFocus()` return `true` trong case này). Nếu bị từ chối, không phát và đảm bảo trạng thái nhất quán.
   - `ZenSoundSynthesizer`: set `isPlaying = false` ngay khi vòng lặp `break` do lỗi ghi AudioTrack.
   - Thêm cơ chế đồng bộ giữa `ZenSoundSynthesizer.isCurrentlyPlaying` và `ZenAudioManager._isPlaying` (callback đơn giản truyền vào `start()`, hoặc polling coroutine nhẹ trong `scope`) để UI luôn phản ánh đúng trạng thái phát thực tế kể cả khi dừng ngoài ý muốn.
3. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
4. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
5. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
6. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
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
