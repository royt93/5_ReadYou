# [ORACLE-03] Agent Tự Động Thẩm Định & Chốt Kèo (Automated Arbiter Agent)

- **Type:** New Feature
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [13. ORACLE — The Oracle Feed (Thị Trường Dự Đoán Tin Tức)](13_THE_ORACLE_PREDICTION_MARKET.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/domain/sv/DailyEditionWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/DailyEditionWorker.kt), [`app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt), [`app/src/main/java/com/mckimquyen/reader/infrastructure/di/WorkerModule.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/di/WorkerModule.kt)

## Vấn đề thực tế
Định kỳ mỗi ngày, một tác vụ nền WorkManager kiểm tra các kèo dự đoán đã đến ngày đáo hạn. Agent quét các RSS feed mới hoặc truy vấn tin tức để xác định kết quả thực tế, đóng kèo và tự động chia thưởng điểm Intel Points cho người thắng kèm thông báo đẩy vinh danh. Repo đã có tiền lệ worker nền chạy theo lịch (`DailyEditionWorker.kt`, `SyncWorker.kt`) đăng ký qua `WorkerModule.kt` + `HiltWorkerFactory` — task này cần thêm một `Worker` tương tự, phụ thuộc vào bảng `oracle_market` (ORACLE-01) và `user_wallet` (ORACLE-02).

## User Story
> Là người dùng đã đặt cược vào một kèo dự đoán,
> Tôi muốn hệ thống tự động xác định kết quả và chia thưởng khi kèo đến hạn,
> Để tôi không cần tự theo dõi tin tức và luôn được thông báo kết quả kèo kịp thời.

## Acceptance Criteria (Gherkin)
- **Given** một kèo dự đoán đã tới ngày giải quyết
- **When** Arbiter Agent tìm thấy bài báo xác nhận sự kiện đã thành công
- **Then** trạng thái kèo chuyển thành RESOLVED_YES
- **And** cộng điểm thưởng tỷ lệ tương ứng cho tất cả người dùng chọn đúng
- **And** gửi Push Notification: "Chúc mừng! Kèo dự đoán của bạn đã thắng +850 Intel Points"

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ORACLE-03] "Agent Tự Động Thẩm Định & Chốt Kèo (Automated Arbiter Agent)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ORACLE-03_automated-arbiter-agent.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ORACLE-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ORACLE-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
```
