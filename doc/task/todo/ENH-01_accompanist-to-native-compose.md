# [ENH-01] Chuyển đổi thư viện Accompanist cũ sang Native Compose & Material 3

- **Type:** Refactoring / Modernization
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [02. ENH — Nâng Cấp Hiệu Năng & Trải Nghiệm](02_ENHANCE_PERFORMANCE_AND_UX.md)
- **Location:** [`app/build.gradle`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/build.gradle#L192-L198)

## Vấn đề thực tế
Dự án vẫn đang sử dụng Accompanist bản `0.24.7-alpha` (từ 2022) như `swiperefresh`, `systemuicontroller`, `pager`, `flowlayout`. Các thư viện này đã bị Google deprecate và chứa nhiều lỗi không tương thích với Android 15/16 Predictive Back và Edge-to-Edge.

## User Story
> Là nhà phát triển ứng dụng,
> Tôi muốn sử dụng các API chính thức của Compose 1.7+ và Material 3 hiện đại,
> Để giảm kích thước APK, tăng độ ổn định và tận dụng tối đa animation cử chỉ hệ thống mới.

## Acceptance Criteria (Gherkin)
- **Given** các màn hình `FeedsPage`, `FlowPage`, `ReadingPage`
- **When** người dùng thao tác vuốt để làm mới (Pull-to-refresh)
- **Then** sử dụng Material 3 `PullToRefreshBox` hoặc `pullRefresh` chính thức thay cho Accompanist SwipeRefresh
- **And** thay thế `systemuicontroller` bằng `ComponentActivity.enableEdgeToEdge()` chuẩn Android 15
- **And** gỡ bỏ hoàn toàn các dependency Accompanist alpha khỏi `app/build.gradle`.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ENH-01] "Chuyển đổi thư viện Accompanist cũ sang Native Compose & Material 3" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ENH-01_accompanist-to-native-compose.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ENH-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ENH-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
