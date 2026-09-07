# [ECHO-04] Sàn Đấu Tranh Luận Giọng Nói (Voice AI Debate Arena) & Rewarded Ads

- **Type:** New Feature / AI Innovation / Monetization
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** "16. EchoChamber Destroyer & Bias Radar (Phá Vỡ Buồng Vang & Đấu Trường Phản Biện)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/` (nút "Vào Sàn Tranh Luận Giọng Nói"), `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/` (hạ tầng TTS/read-aloud hiện có, tái dùng cho phát giọng nói AI), module STT/voice-arena mới, tích hợp Rewarded Ad qua `AdmobApplovinWrapper` (`com.roy.sdkadbmob.AdManager`, xem [`doc/AD.MD`](../../AD.MD) và cách dùng ở `RApp.kt`)

## Vấn đề thực tế
App chưa có bất kỳ tính năng tương tác giọng nói hai chiều nào — `infrastructure/audio/` hiện chỉ phục vụ đọc bài bằng TTS một chiều (read-aloud), chưa có luồng ghi âm + nhận diện giọng nói (STT) + phản hồi AI theo thời gian thực. Tính năng "Sàn Đấu Tranh Luận Giọng Nói" là killer feature cao cấp, đồng thời là điểm mở khóa Rewarded Ad mới (tăng doanh thu) thông qua `AdmobApplovinWrapper` đã tích hợp sẵn trong `RApp.kt`.

## User Story
> Là độc giả muốn thử thách quan điểm của mình,
> Tôi muốn xem một quảng cáo thưởng ngắn để mở khóa phòng tranh luận giọng nói trực tiếp với AI về đề tài bài báo,
> Để tôi rèn luyện tư duy phản biện qua một trải nghiệm tương tác sống động, không chỉ đọc chữ.

## Acceptance Criteria (Gherkin)
- **Given** người dùng muốn tranh luận với AI về đề tài bài báo
- **When** bấm nút "Vào Sàn Tranh Luận Giọng Nói"
- **Then** ứng dụng kích hoạt AdmobApplovinWrapper Rewarded Ad
- **When** xem xong quảng cáo, màn hình Voice Arena mở ra với hiệu ứng sóng âm tương tác

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ECHO-04] "Sàn Đấu Tranh Luận Giọng Nói (Voice AI Debate Arena) & Rewarded Ads" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ECHO-04_voice-ai-debate-arena.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Tham khảo cách RApp.kt setupAdmob() và AdManager (AdmobApplovinWrapper) đã được dùng ở nơi khác trong app cho Rewarded Ad, không tự chế lại flow ad.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới, xin quyền RECORD_AUDIO đúng runtime permission pattern của Android.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ECHO-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ECHO-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
