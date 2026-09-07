# [FIX-14] Dọn Dẹp TODO Rải Rác & Tab Chết Không Có Hành Vi

- **Type:** Bug / Code Quality
- **Priority:** `P2 (Medium)`
- **Estimation:** `2 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`RApp.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/RApp.kt#L50-L51), [`ui/component/base/BaseSwitch.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/component/base/BaseSwitch.kt#L69), [`ui/ext/ExtLazyListState.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/ext/ExtLazyListState.kt#L38), [`ui/page/home/read/drawer/FeedOptionDrawer.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/drawer/FeedOptionDrawer.kt#L54)

## Vấn đề thực tế
Nhiều `TODO` và UI chết rải rác trong codebase, gây khó hiểu ý định thật sự hoặc để lại hành vi UI vô nghĩa cho người dùng:
1. `RApp.kt` dòng 50-51: `//TODO finger print` và `//TODO why you see ad` — 2 comment không có ngữ cảnh, không rõ ý định ban đầu của tác giả (liên quan tính năng vân tay hay debug lý do hiển thị quảng cáo?), cần làm rõ hoặc xoá nếu không còn liên quan.
2. `ui/component/base/BaseSwitch.kt` dòng 69: `// TODO: inactivated colors` — switch ở trạng thái disabled (`enabled = false`) đang thiếu bộ màu riêng biệt, có thể khiến switch bị disable trông giống hệt switch bình thường, gây nhầm lẫn UX.
3. `ui/ext/ExtLazyListState.kt` dòng 38: `TODO: To be improved` — nợ kỹ thuật mơ hồ, không mô tả cụ thể cần cải thiện điều gì.
4. `ui/page/home/read/drawer/FeedOptionDrawer.kt` dòng 54 và 62: cả 2 đều có `Tab(selected = true, onClick = { /*TODO*/ })` — 2 Tab Compose có `onClick` rỗng, tức là bấm vào tab này **không có bất kỳ hành vi nào xảy ra**, đây là dead UI khiến người dùng bấm mà không thấy phản hồi.

## User Story
> Là người dùng sử dụng ứng dụng,
> Tôi muốn mọi thành phần UI tôi tương tác được (tab, switch) đều có hành vi rõ ràng và nhất quán,
> Để không bị bối rối khi bấm vào thứ gì đó mà không có phản hồi.

## Acceptance Criteria (Gherkin)
- **Given** `FeedOptionDrawer.kt` có 2 `Tab` với `onClick = { /*TODO*/ }` (dòng 54, 62)
- **When** rà soát lại UI hiện tại của `FeedOptionDrawer` (sheet tuỳ chọn feed khi đọc bài)
- **Then** hoặc (a) implement hành vi thật cho các tab này nếu chúng có mục đích rõ ràng (ví dụ chuyển đổi giữa các nhóm tuỳ chọn), hoặc (b) xoá hẳn các Tab chết này nếu chúng không thuộc về thiết kế hiện tại — không được để nguyên `onClick` rỗng
- **And** `BaseSwitch.kt` bổ sung bộ màu riêng cho trạng thái `enabled = false` (disabled), tuân theo Material 3 color token chuẩn (ví dụ `MaterialTheme.colorScheme.onSurface.copy(alpha = ...)`), có thể phân biệt trực quan với trạng thái enabled/checked/unchecked
- **And** với `RApp.kt` dòng 50-51 và `ExtLazyListState.kt` dòng 38: hoặc làm rõ ý định bằng comment cụ thể mô tả đúng vấn đề kỹ thuật còn tồn đọng (không còn "TODO" mơ hồ), hoặc xoá nếu xác nhận không còn liên quan/không có kế hoạch triển khai
- **And** không phát sinh regression cho các tính năng liên quan (feed option drawer, switch settings) sau khi dọn dẹp.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-14] "Dọn Dẹp TODO Rải Rác & Tab Chết Không Có Hành Vi" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-14_dead-todo-ui-cleanup.md trước khi bắt đầu.

Task này gồm 4 vị trí độc lập nhau — xử lý từng vị trí, xác nhận riêng biệt trước khi chuyển sang vị trí tiếp theo:

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (cả 4 file), xác nhận vấn đề còn tồn tại (không giả định).
2. Với FeedOptionDrawer.kt: đọc toàn bộ context xung quanh 2 Tab (không chỉ 2 dòng lẻ) để hiểu Tab này định làm gì (xem tên biến, layout xung quanh, có TabRow cha không, các Tab khác trong cùng TabRow đã có hành vi gì) — quyết định implement hay xoá dựa trên bằng chứng thực tế trong code, không đoán mò.
3. Với BaseSwitch.kt: thêm màu disabled riêng theo Material 3, đối chiếu với các component switch khác trong ui/component/base/ để giữ nhất quán style.
4. Với RApp.kt và ExtLazyListState.kt: đọc git blame/lịch sử nếu cần để hiểu ý định gốc, sau đó quyết định làm rõ hoặc xoá.
5. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
6. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string/comment).
7. Lặp lại tới khi Acceptance Criteria thỏa mãn 100% cho cả 4 vị trí.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — nếu Tab implement hành vi mới có logic, viết test cho logic đó.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — test Tab trong FeedOptionDrawer có hành vi đúng khi click, test BaseSwitch hiển thị màu disabled đúng.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) nếu áp dụng.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công mở FeedOptionDrawer bấm thử các tab, mở 1 màn hình Settings có switch disabled để quan sát màu, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được, screenshot nếu cần) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
