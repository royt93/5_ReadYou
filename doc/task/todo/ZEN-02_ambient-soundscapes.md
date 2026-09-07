# [ZEN-02] Không Gian Âm Thanh Nền Tập Trung (Zen Focus & Ambient Soundscapes)

- **Type:** Audio / Mindfulness UX
- **Priority:** `P3 (Low)`
- **Estimation:** `3 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/audio/ambient/`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/audio/ambient/)

## Vấn đề thực tế
Môi trường xung quanh (quán cà phê ồn, văn phòng, tiếng còi xe) thường làm người đọc phân tâm. Nghe nhạc có lời lại gây xao nhãng việc hiểu văn bản.

> **Ghi chú audit (2026-09-06):** Task này ĐÃ được implement (xem `ZenAudioManager.kt`, `ZenSoundSynthesizer.kt`, `ZenSoundType.kt`, `ZenSoundSheet.kt`, commit `a129f71`) và ban đầu được tuyên bố DONE trong `doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md`. Lưu ý: implementation dùng **synthesizer tổng hợp âm thanh runtime (Brown/Pink noise, binaural beats, Tibetan bowl)** thay vì file OPUS đóng gói sẵn như Acceptance Criteria gốc mô tả — cách này thực ra tối ưu hơn về dung lượng APK (0 byte audio asset) nhưng lệch khỏi đặc tả "file âm thanh nén OPUS < 2MB". Audit code cũng phát hiện 2 lỗi đồng bộ trạng thái phát — xem task fix riêng **[ZEN-06]**.

## User Story
> Là người thích đọc sách trong không gian tĩnh lặng,
> Tôi muốn bật âm thanh nền thư giãn (tiếng mưa rơi, tiếng lò sưởi, tiếng sóng biển, Lofi) khi đọc bài,
> Để tôi chìm đắm hoàn toàn vào dòng suy nghĩ và đọc tập trung hơn.

## Acceptance Criteria (Gherkin)
- **Given** người dùng mở bài đọc
- **When** bật icon "🎧 Zen Audio" trên thanh công cụ
- **Then** phát âm thanh nền vòng lặp chất lượng cao (Mưa rào, Quán cà phê Paris, Sóng biển đêm, Tiếng lửa bập bùng, Tiếng ồn trắng)
- **And** có thanh trượt điều chỉnh âm lượng riêng biệt không ảnh hưởng tới âm lượng hệ thống
- **And** file âm thanh được nén tối ưu (OPUS format) chiếm < 2MB dung lượng app.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-02] "Không Gian Âm Thanh Nền Tập Trung (Zen Focus & Ambient Soundscapes)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-02_ambient-soundscapes.md trước khi bắt đầu.

LƯU Ý: task này đã có implementation (`app/src/main/java/com/mckimquyen/reader/infrastructure/audio/ambient/`) dùng synthesizer runtime thay vì file OPUS đóng gói sẵn. Trước khi code, đối chiếu code hiện tại với Acceptance Criteria, quyết định có cần đổi sang asset OPUS thật hay giữ synthesizer (ghi rõ lý do), đồng thời tham khảo task con [ZEN-06] (bug đồng bộ trạng thái) đã tồn tại hay chưa trước khi sửa trùng lặp.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
