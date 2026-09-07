# [FIX-07] Kiểm tra Runtime Permission `POST_NOTIFICATIONS` trên Android 13+ (API 33+)

- **Type:** Compatibility / OS Standard
- **Priority:** `P1 (High)`
- **Estimation:** `2 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`infrastructure/android/NotificationHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt)

## Vấn đề thực tế
Từ Android 13, việc gọi `notify()` mà chưa được người dùng cấp quyền `android.permission.POST_NOTIFICATIONS` sẽ bị hệ thống âm thầm chặn. Đồng thời mã hiện tại dùng `Random().nextInt() + article.id.hashCode()` tạo ID ngẫu nhiên không quản lý được.

## Acceptance Criteria
- **Given** thiết bị chạy Android 13 trở lên
- **When** người dùng bật thông báo cho một feed trong Settings
- **Then** app hiển thị hộp thoại xin quyền `POST_NOTIFICATIONS` theo chuẩn Material 3
- **And** notification ID được sinh theo mã hash cố định của bài viết để có thể cập nhật hoặc huỷ khi đã đọc.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-07] "Kiểm tra Runtime Permission POST_NOTIFICATIONS trên Android 13+ (API 33+)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-07_post-notifications-permission.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra AndroidManifest.xml có khai báo `POST_NOTIFICATIONS` chưa (đã có ở thời điểm audit), có luồng runtime request permission (ActivityResultContracts.RequestPermission) ở đâu chưa, và NotificationHelper.kt còn dùng `Random().nextInt()` cho notification ID hay chưa (4 vị trí tại thời điểm audit gần nhất).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới (ví dụ dialog xin quyền, rationale text).
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-07 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task FIX-07 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — ví dụ hàm sinh notification ID từ hash bài viết cho kết quả ổn định (deterministic), không trùng giữa 2 bài khác nhau trong tập mẫu.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — dialog xin quyền hiển thị đúng.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật (Android 13+)**: `./gradlew installDevDebug`, thao tác thủ công bật thông báo cho 1 feed, xác nhận dialog xin quyền hiện ra và thông báo thực sự hiển thị sau khi cấp quyền, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.

---
> **Ghi chú audit (2026-09-06):** Task này **CHƯA được implement** — `AndroidManifest.xml` đã khai báo `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` nhưng chưa tìm thấy luồng runtime-request permission nào trong `app/src/main`. `NotificationHelper.kt` vẫn dùng `Random().nextInt() + article.id.hashCode()` ở 4 vị trí (không phải mã hash cố định). File `NotificationHelper.kt` cũng đang có thay đổi chưa commit theo `git status` — kiểm tra kỹ `git diff` trước khi bắt đầu để tránh làm việc trùng lặp.
