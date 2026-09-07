# [ORACLE-02] Giao Diện Đặt Cược Intel Points & Sàn Giao Dịch Tin Tức (Compose Prediction Sheet)

- **Type:** New Feature
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [13. ORACLE — The Oracle Feed (Thị Trường Dự Đoán Tin Tức)](13_THE_ORACLE_PREDICTION_MARKET.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/ui/component/cluster/StoryClusterSheet.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/component/cluster/StoryClusterSheet.kt), [`app/src/main/java/com/mckimquyen/reader/ui/page/home/read/SummarySheet.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/SummarySheet.kt), [`app/src/main/java/com/mckimquyen/reader/domain/model/article`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/model/article)

## Vấn đề thực tế
Xây dựng BottomSheet Material 3 trực quan cho phép người dùng đặt cược 50, 100, 500 Intel Points vào cửa "Sẽ Xảy Ra (Yes)" hoặc "Không Xảy Ra (No)". Hiển thị tỷ lệ cược động (Odds Ratio) dựa trên tỷ lệ phiếu bầu của cộng đồng người đọc nội bộ. Repo đã có nhiều BottomSheet Compose tương tự làm mẫu (`StoryClusterSheet.kt`, `SummarySheet.kt`, `DeepReadChatSheet.kt`, `MindMapSheet.kt`) — cần thiết kế thêm bảng `user_wallet` để lưu số dư Intel Points và bảng `oracle_market` (từ ORACLE-01) để đọc câu hỏi/tỷ lệ cược.

## User Story
> Là người đọc tin tức muốn tương tác sâu hơn với nội dung,
> Tôi muốn đặt cược điểm ảo Intel Points vào các dự đoán được AI trích xuất từ bài viết,
> Để việc đọc tin trở nên hấp dẫn, có yếu tố dự đoán và cạnh tranh với cộng đồng.

## Acceptance Criteria (Gherkin)
- **Given** người dùng mở card dự đoán của bài viết
- **When** chọn cửa "Yes" và bấm "Đặt cược 200 Intel Points"
- **Then** hệ thống trừ điểm trong bảng `user_wallet`, phát âm thanh chip đặt cược
- **And** hiển thị phần trăm đồng thuận của cộng đồng (ví dụ: 68% Yes - 32% No)

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ORACLE-02] "Giao Diện Đặt Cược Intel Points & Sàn Giao Dịch Tin Tức (Compose Prediction Sheet)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ORACLE-02_intel-points-betting-sheet.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ORACLE-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ORACLE-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
