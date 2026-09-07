# [DJ-05] Episode CommuteCast Không Được Persist — Mất Nội Dung Khi Process Bị Kill

- **Type:** Bug / Architecture
- **Priority:** `P0 (Blocker)`
- **Estimation:** `5 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`infrastructure/audio/CommuteAudioPlayer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt#L37-L56) (`@Singleton` RAM-only `MutableStateFlow`), [`domain/sv/CommuteWorker.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteWorker.kt#L81-L107), [`ui/component/commute/CommuteCastViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/component/commute/CommuteCastViewModel.kt#L47-L79), [`infrastructure/android/NotificationHelper.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt#L132-L151)

## Vấn đề thực tế
Đây là gap phát hiện khi audit lại completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` (đã tuyên bố epic hoàn thành 9.9/10, 57/57 test pass).

Episode CommuteCast do `CommuteWorker.doWork()` sinh ra (dòng 92: `scriptService.generateScript(...)`) hiện chỉ được lưu vào `CommuteAudioPlayer._playerState` — một `MutableStateFlow` nằm hoàn toàn trong RAM của `@Singleton class CommuteAudioPlayer` (dòng 37-56 `CommuteAudioPlayer.kt`). Không có bất kỳ bước persist nào ra Room, DataStore, hay file:
- `CommuteWorker.kt` dòng 94-96: `commuteAudioPlayer.playEpisode(episode, startFromIndex = 0)` rồi `commuteAudioPlayer.pause()` — chỉ update state trong RAM.
- `NotificationHelper.kt` dòng 132-151 (`notifyCommuteCast`) bắn notification NGAY sau khi `playEpisode()` được gọi, không kiểm tra `tts.isInitialized` hay bất kỳ điều kiện "nội dung đã sẵn sàng thực sự" nào.
- `CommuteAudioPlayer.speakCurrentDialogue()` (dòng 159-160): `if (!isInitialized) return` — nếu `TextToSpeech.onInit()` (callback bất đồng bộ) chưa chạy xong tại thời điểm `playEpisode()` được gọi trong `doWork()`, lệnh phát bị **âm thầm bỏ qua**, không log lỗi cho user, không retry, notification vẫn được gửi như thể mọi thứ đã sẵn sàng.
- Nếu hệ thống Android kill tiến trình app sau khi `CommuteWorker` chạy xong (rất thường xảy ra với process chạy nền qua WorkManager) nhưng trước khi user mở notification, thì khi user tap vào notification, `MainActivity` khởi động lại → Hilt tạo **instance `CommuteAudioPlayer` MỚI** với `_playerState` rỗng. `CommuteCastViewModel.prepareOrPlay()` (dòng 47-54) thấy `currentEpisode == null` nên phải **sinh lại script từ đầu** (gọi lại AI, tốn thêm 1 lượt gọi Gemini, có thể ra nội dung khác với những gì notification đã hứa hẹn) thay vì phát nội dung đã chuẩn bị sẵn.

## User Story
> Là người dùng nhận được thông báo "Bản tin sáng CommuteCast đã sẵn sàng",
> Tôi muốn khi mở app luôn nghe được đúng bản tin đã được chuẩn bị, kể cả khi app đã bị hệ thống dọn tiến trình,
> Để không bị hụt hẫng vì thông báo hứa hẹn nhưng mở ra lại trống hoặc phải chờ sinh lại từ đầu.

## Acceptance Criteria (Gherkin)
- **Given** `CommuteWorker` đã sinh xong 1 episode CommuteCast
- **When** episode được tạo thành công
- **Then** episode phải được persist bền vững (Room entity hoặc file JSON trong app-private storage), không chỉ nằm trong RAM singleton
- **And** notification `notifyCommuteCast` chỉ được gửi SAU KHI xác nhận nội dung đã sẵn sàng thực sự để phát (không gửi notification nếu TTS chưa init xong hoặc episode rỗng)
- **Given** tiến trình app đã bị hệ thống kill sau khi episode được tạo
- **When** người dùng mở app từ notification
- **Then** app đọc lại episode đã persist (không gọi lại AI để sinh script mới) và phát đúng nội dung đã hứa hẹn trong notification
- **And** nếu `TextToSpeech` chưa init xong tại thời điểm user bấm play, UI hiển thị trạng thái "đang chuẩn bị" thay vì im lặng không phản hồi, và tự động phát ngay khi `onInit()` hoàn tất

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-05] "Episode CommuteCast Không Được Persist — Mất Nội Dung Khi Process Bị Kill" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/DJ-05_persist-episode-notification-ready-state.md trước khi bắt đầu. Đây là gap P0 phát hiện SAU KHI epic 14 đã bị tuyên bố "DONE 9.9/10" — ưu tiên cao nhất trong nhóm task CommuteCast.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (CommuteWorker.kt, CommuteAudioPlayer.kt, CommuteCastViewModel.kt, NotificationHelper.kt), xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria: thêm 1 Room entity/DAO (hoặc DataStore) để persist `CommuteEpisode` (id, title, date, dialogues, articleIds, isDeepDive) ngay sau khi `CommuteScriptService.generateScript()` trả về trong `CommuteWorker.doWork()`; sửa `CommuteAudioPlayer`/`CommuteCastViewModel` để khi khởi động lại (process mới), load episode gần nhất từ storage thay vì luôn coi `episode == null` là phải sinh lại; đảm bảo `notifyCommuteCast` chỉ gửi khi episode đã persist + không rỗng; xử lý race condition TTS chưa `onInit()` xong bằng cách queue lệnh phát hoặc hiển thị trạng thái loading rõ ràng trên UI thay vì im lặng bỏ qua. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (đây là thay đổi kiến trúc — bắt buộc), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string). Nếu thêm Room entity mới, kiểm tra `app/schemas` export đúng và cân nhắc migration nếu cần.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên, đặc biệt: process kill giữa chừng — mô phỏng bằng cách tạo instance mới của repository/ViewModel và xác nhận đọc lại đúng episode đã persist).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`), bao gồm trạng thái "đang chuẩn bị" khi TTS chưa init xong.
4. Bổ sung **integration test** cho luồng end-to-end: Worker sinh episode → persist → app restart (instance mới) → đọc lại đúng nội dung → phát đúng.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (trigger CommuteWorker qua `enqueueOneTimeWork`, force-stop app, mở lại từ notification), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
