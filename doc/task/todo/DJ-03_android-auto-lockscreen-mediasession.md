# [DJ-03] Tích Hợp Android Auto & Lockscreen MediaSession

- **Type:** New Feature
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`infrastructure/audio/CommuteAudioPlayer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt), [`ui/component/commute/CommuteCastSheet.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/component/commute/CommuteCastSheet.kt)

## Vấn đề thực tế
Xây dựng `MediaSessionService` chuẩn AndroidX Media3. Hỗ trợ hiển thị tên tập, ảnh bìa, nút tua 10s, tạm dừng trên màn hình khóa điện thoại, thanh thông báo hệ thống, và giao diện xe hơi Android Auto khi cắm cáp kết nối.

> **⚠️ Audit note (2026-09-06):** Task này **CHƯA được implement**, dù completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` tuyên bố epic hoàn thành 9.9/10 "hỗ trợ Android Auto và MediaSession màn hình khóa". Kiểm tra thực tế:
> - `grep -rl "MediaSessionService\|androidx.media3" app/src/main/java app/build.gradle` không trả về kết quả nào — hoàn toàn không có dependency Media3/ExoPlayer trong `app/build.gradle`, không có class `MediaSessionService` nào trong codebase.
> - `CommuteAudioPlayer.kt` chỉ dùng `android.speech.tts.TextToSpeech` trực tiếp trong 1 `Singleton` thường (không phải `Service`), không expose qua `MediaSession`, không hiển thị trên thanh thông báo hệ thống hay màn hình khóa, không có Android Auto manifest declaration (`<meta-data android:name="com.google.android.gms.car.application">` không tồn tại trong `AndroidManifest.xml`).
>
> Đây là gap nghiêm trọng nhất so với tuyên bố "DONE" — toàn bộ tính năng cốt lõi của task chưa tồn tại trong code, không chỉ là thiếu polish. Task giữ nguyên `todo/`, KHÔNG chuyển `done/`.

## User Story
> Là người dùng lái xe đi làm mỗi sáng,
> Tôi muốn điều khiển CommuteCast (play/pause/tua) ngay trên màn hình khóa hoặc màn hình xe hơi mà không cần cầm điện thoại,
> Để việc nghe bản tin an toàn và tiện lợi khi đang lái xe.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang lái xe và kết nối điện thoại với xe qua Android Auto
- **When** CommuteCast phát
- **Then** tên bản tin, hình ảnh bìa và các phím điều hướng xuất hiện trực tiếp trên màn hình xe hơi
- **And** thao tác bấm tạm dừng trên vô-lăng xe phản hồi ngay lập tức

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-03] "Tích Hợp Android Auto & Lockscreen MediaSession" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" (bao gồm audit note) + "Acceptance Criteria" trong file doc/task/todo/DJ-03_android-auto-lockscreen-mediasession.md trước khi bắt đầu. Đây là tính năng CHƯA tồn tại trong code (0% implement), không phải chỉnh sửa nhỏ — cần thiết kế và implement từ đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria: thêm dependency `androidx.media3:media3-session` + `media3-common`, tạo `CommuteMediaSessionService : MediaSessionService` bọc quanh `CommuteAudioPlayer`/`ExoPlayer` custom `Player` implementation, expose `MediaMetadata` (tên tập, ảnh bìa), map play/pause/skipNext/skipPrevious vào `MediaSession.Callback`, khai báo `<service>` + car app metadata trong `AndroidManifest.xml`. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng, tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
