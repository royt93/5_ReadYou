# [DJ-07] Thiếu Audio Mixing/Lofi Nền Và Tích Hợp Media3/Android Auto

- **Type:** Architecture / New Feature
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`infrastructure/audio/CommuteAudioPlayer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt) (toàn file — chỉ dùng `TextToSpeech`, không `MediaPlayer`/`Media3`), `app/build.gradle` (không có dependency `androidx.media3.*`), `AndroidManifest.xml` (không có khai báo `MediaSessionService` hay Android Auto car-app metadata)

## Vấn đề thực tế
Phát hiện khi audit lại completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` (tuyên bố epic hoàn thành 9.9/10, "hỗ trợ Android Auto và MediaSession màn hình khóa", nhạc nền lofi mix cùng TTS).

Đặc tả gốc của epic (`TASK-DJ-02`, `TASK-DJ-03`) có nhắc:
- Trộn nhạc nền lofi acoustic CC0 ở mức âm lượng 15% khi TTS đang nói, kèm audio ducking.
- Tích hợp `MediaSessionService` chuẩn AndroidX Media3 để hiển thị trên màn hình khóa và Android Auto.

Thực tế kiểm tra code:
- `grep -rli "lofi" app/src/main/java` → **không có kết quả nào**. Không có file audio nền, không có logic mixing.
- `grep -rl "MediaSessionService\|androidx.media3" app/src/main/java app/build.gradle` → **không có kết quả nào**. Không có dependency Media3/ExoPlayer, không có class `MediaSessionService`.
- `CommuteAudioPlayer.kt` chỉ dùng `android.speech.tts.TextToSpeech` (dòng 4, 47) — phát TTS thô, không có track nhạc nền, không expose qua `MediaSession` nào để hệ thống (lockscreen, notification media control, Android Auto) nhận diện được.

Đây là gap tổng hợp liên quan tới 2 mảnh: audio mixing (liên quan `DJ-02`) và Media3/Android Auto (liên quan `DJ-03`, hiện đã tách task riêng vì mức độ nghiêm trọng — "0% implement"). Task này tập trung vào phần triển khai kỹ thuật audio-mixing + `MediaSessionService` cơ bản.

## User Story
> Là người dùng nghe CommuteCast trong lúc lái xe hoặc đi bộ,
> Tôi muốn nghe nhạc nền lofi êm dịu hòa cùng giọng đọc, và điều khiển play/pause được ngay từ màn hình khóa,
> Để trải nghiệm giống một chương trình radio/podcast thật, không phải giọng đọc máy thô trần trụi.

## Acceptance Criteria (Gherkin)
- **Given** người dùng bắt đầu phát CommuteCast
- **When** TTS đang đọc lời thoại
- **Then** một track nhạc nền lofi (CC0, đóng gói trong `res/raw` hoặc tải về cache) phát song song ở mức âm lượng thấp (~15%)
- **And** khi TTS ngừng nói (giữa các câu hoặc kết thúc), âm lượng nhạc nền tự động tăng trở lại mức bình thường (audio ducking ngược)
- **Given** CommuteCast đang phát
- **When** hệ thống hiển thị `MediaSessionService` cơ bản
- **Then** notification media-style/lockscreen hiển thị tên episode + nút play/pause tối thiểu, điều khiển được từ lockscreen mà không cần mở app

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-07] "Thiếu Audio Mixing/Lofi Nền Và Tích Hợp Media3/Android Auto" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/DJ-07_lofi-audio-mixing-media3-integration.md trước khi bắt đầu. Task này liên quan chặt tới DJ-02 (audio mixer) và DJ-03 (Android Auto/MediaSession đầy đủ) — kiểm tra 2 file đó trước để tránh làm trùng lặp; nếu DJ-03 đã implement MediaSessionService rồi thì task này chỉ cần bổ sung phần lofi mixing/ducking còn thiếu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Kiểm tra tiến độ DJ-02/DJ-03 trước khi bắt đầu.
2. Implement fix/feature đúng theo Acceptance Criteria: thêm asset nhạc nền lofi CC0 vào `res/raw`, dùng `MediaPlayer` hoặc `AudioTrack` riêng biệt với TTS để loop phát nhạc nền, điều chỉnh volume theo trạng thái `UtteranceProgressListener.onStart/onDone` (ducking), thêm `MediaSessionService` tối thiểu (play/pause) nếu DJ-03 chưa làm. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng, tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string). Chú ý kích thước APK khi thêm asset audio — kiểm tra tác động tới `resourceConfigurations` không liên quan (chỉ ảnh hưởng ngôn ngữ, nhưng vẫn kiểm tra dung lượng file audio nền hợp lý).
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
