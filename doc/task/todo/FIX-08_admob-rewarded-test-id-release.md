# [FIX-08] Sửa AdMob Rewarded Ad Unit ID Test trên Release & Tối Ưu Ad Lifecycle

- **Type:** Monetization / Policy Bug
- **Priority:** `P1 (High)`
- **Estimation:** `1 Story Point`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`app/build.gradle`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/build.gradle#L101), [`RApp.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/RApp.kt)

## Vấn đề thực tế
Trong cấu hình `release` của `app/build.gradle`, `ADMOB_REWARDED_ID` đang trỏ vào Google Test ID (`ca-app-pub-3940256099942544/5224354917`). Nếu cờ `IS_ENABLE_ADMOB` được bật lên `true`, người dùng release xem ad test gây mất 100% doanh thu rewarded ad và vi phạm chính sách AdMob. Đồng thời cần kiểm tra frequency cap và pre-warm ad mượt mà.

## User Story
> Là chủ sở hữu ứng dụng,
> Tôi muốn hệ thống quảng cáo (AppLovin MAX + AdMob) hoạt động chuẩn chỉ, an toàn theo chính sách Google,
> Để mang lại nguồn thu ổn định từ quảng cáo banner và rewarded ad mà không làm phiền quá mức trải nghiệm đọc của người dùng.

## Acceptance Criteria
- **Given** build type là `release`
- **When** `IS_ENABLE_ADMOB = true`
- **Then** build script yêu cầu ID thật và không dùng test ID
- **And** các vị trí banner và rewarded ad tích hợp trong `ReadingPage` và `VipManagementPage` giữ nguyên hoạt động ổn định qua `AdmobApplovinWrapper:1.1.5`.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-08] "Sửa AdMob Rewarded Ad Unit ID Test trên Release & Tối Ưu Ad Lifecycle" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-08_admob-rewarded-test-id-release.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra `app/build.gradle` block `release` xem `ADMOB_REWARDED_ID` (và các ID admob khác) còn là test ID `ca-app-pub-3940256099942544/...` hay đã thay ID thật/placeholder yêu cầu cấu hình qua `keystore.properties`-style local file.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới. KHÔNG hardcode ID thật vào git nếu chưa được xác nhận công khai — cân nhắc đọc từ local.properties/gradle.properties gitignored, tương tự cơ chế keystore.properties.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-08 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task FIX-08 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` và `./gradlew assembleProdRelease` (yêu cầu `keystore.properties` — nếu không có, ít nhất verify `./gradlew assembleDevRelease` hoặc kiểm tra script gradle không lỗi cấu hình).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — nếu có logic Kotlin mới (ví dụ validate ad unit ID không phải test ID ở release), viết test cho nó.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — nếu có thay đổi UI liên quan banner/rewarded.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công xem banner/rewarded ad tại `ReadingPage` và `VipManagementPage`, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
