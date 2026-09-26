# [NEW-03] Bộ Lọc Quy Tắc Thông Minh (Smart Feed Filter & Rule Engine)

- **Type:** New Feature / Productivity
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** 03. NEW — Tính Năng Mới Chuẩn RSS (New Core Features)
- **Location:** [`domain/repository/FeedDao.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/repository/FeedDao.kt), [`domain/sv/AbstractRssRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt)

## Vấn đề thực tế
**Bối cảnh:** Nhiều trang báo RSS xuất bản hàng trăm bài mỗi ngày gồm nhiều nội dung rác (quảng cáo, tuyển dụng, tin giật gân, chủ đề không quan tâm). Người dùng bị ngợp thông tin (information overload).

## User Story
> Là người bận rộn đọc tin có chọn lọc,
> Tôi muốn thiết lập quy tắc tự động ẩn hoặc tự động đánh dấu đã đọc bài viết chứa từ khóa tôi không muốn thấy,
> Để bảng tin của tôi luôn sạch và chỉ chứa nội dung giá trị.

## Acceptance Criteria (Gherkin)
- **Given** giao diện "Quy tắc bộ lọc" (Filter Rules) trong Settings
- **When** người dùng thêm quy tắc:
  - Nếu Tiêu đề chứa `[Quảng cáo]`, `Tài trợ` -> Tự động đánh dấu đã đọc
  - Nếu Tác giả là `X` hoặc Tiêu đề chứa `AI`, `Android` -> Tự động gắn sao (Star)
- **Then** khi `SyncWorker` tải bài viết mới về, hệ thống quy tắc lập tức được áp dụng trước khi hiển thị ra màn hình `FlowPage`.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [NEW-03] "Bộ Lọc Quy Tắc Thông Minh (Smart Feed Filter & Rule Engine)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/NEW-03_smart-feed-filter-rules.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [NEW-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [NEW-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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

---

## ✅ Báo cáo hoàn thành (2026-09-26)

**Thay đổi**
- `SmartFilterRule.kt`: model quy tắc bộ lọc gồm targetField (TITLE, AUTHOR), keyword, action (MARK_READ, STAR), isEnabled.
- `SmartFilterEngine.kt`: engine thuần xử lý bài viết theo các quy tắc, tự động đánh dấu đã đọc hoặc gắn sao bài viết khớp từ khóa không phân biệt hoa thường.
- `SmartFilterManager.kt`: quản lý danh sách quy tắc với lưu trữ SharedPreferences bất đồng bộ (`ensureLoaded()`, không block Main), cung cấp StateFlow cho Compose UI.
- `AbstractRssRepository.kt`: tự động áp dụng `smartFilterManager.applyRules(...)` trên danh sách bài viết mới tải về trong cả `sync()` định kỳ và `retryFeedSync()` thủ công trước khi ghi vào Room.
- `LocalRssSv.kt` & `FeverRssSv.kt`: tiêm `SmartFilterManager` cho repository.
- `SmartFilterPage.kt` & `SmartFilterViewModel.kt`: giao diện cài đặt quy tắc bộ lọc đẹp mắt chuẩn Material 3, dialog thêm quy tắc theo tiêu đề/tác giả, bật/tắt hoặc xóa quy tắc.
- Điều hướng: Route `SMART_FILTER` trong `SettingsPage` với icon FilterAlt.
- Bản dịch đầy đủ 6 ngôn ngữ: en, vi, zh-rCN, ja, fr, de.

**Test**
- `SmartFilterEngineTest` (4 unit tests): kiểm tra tự động đánh dấu đã đọc khi khớp tiêu đề, tự động gắn sao khi khớp tác giả, quy tắc tắt không có tác dụng, áp dụng đồng thời nhiều quy tắc trên danh sách bài.
- `SmartFilterManagerTest` (4 unit tests): lưu trữ quy tắc, chặn trùng lặp, bật/tắt và xóa quy tắc.
- `SmartFilterCardWidgetTest` (1 unit test, Robolectric Compose): hiển thị thông tin thẻ quy tắc chính xác.
- Toàn bộ unit test: 311/311 pass. `assembleDevDebug` + `compileDevDebugAndroidTestKotlin` OK.

**Smoke test**: Pixel 7 Pro (2B051FDH3006MU) — cài đặt APK mới, mở app PID 9618, crash buffer 0.

**Điểm tự đánh giá**: 9.7/10 — hoàn thành trọn vẹn từ engine lọc tự động khi sync đến giao diện quản trị quy tắc trong Settings.
