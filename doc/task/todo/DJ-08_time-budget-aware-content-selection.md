# [DJ-08] Chọn Nội Dung Chưa Theo Ngân Sách Thời Gian Người Dùng

- **Type:** Enhancement / Architecture
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`domain/sv/CommuteWorker.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteWorker.kt#L84) (dòng 84: `limit = 5` cố định), [`domain/sv/CommuteScriptService.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteScriptService.kt#L47) (dòng 47: `if (isDeepDive) articles.take(10) else articles.take(5)`), [`ui/component/commute/CommuteCastViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/component/commute/CommuteCastViewModel.kt#L61) (dòng 61: `val limit = if (isDeepDive) 10 else 5`)

## Vấn đề thực tế
Phát hiện khi audit lại completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` (tuyên bố epic hoàn thành 9.9/10, mô tả episode "4 phút giữa 2 MC").

`CommuteWorker.kt` hiện chọn **cố định 5 bài** (bản thường) hoặc **10 bài** (Deep Dive) mới nhất — hoàn toàn dựa trên số lượng bài viết, không tính đến:
1. **Ngân sách thời lượng thực tế** người dùng chọn/mong muốn (ví dụ "4 phút" như epic mô tả, hoặc tùy chọn "15 phút" cho Deep Dive) — không có bất kỳ ước lượng thời lượng đọc nào (word count / speech rate) để đảm bảo tổng độ dài script vừa khít khung thời gian đã hứa hẹn trong notification ("Bản tin sáng CommuteCast 4 phút của bạn đã sẵn sàng!").
2. **Đa dạng nguồn/chủ đề**: `articleDao.queryLatestUnread(accountId, limit)` chỉ `ORDER BY date DESC`, không group theo `feedId`/chủ đề, nên hoàn toàn có thể 5 bài được chọn đều đến từ cùng 1 nguồn RSS nếu nguồn đó đăng bài dồn dập, làm giảm giá trị "đa dạng tin tức" mà 1 bản tin buổi sáng nên có.

Vì `CommuteScriptService.generateHeuristicScript`/`generateScriptWithGemini` không nhận tham số ngân sách thời gian, không có cách nào đảm bảo output thực sự dài ~4 phút khi đọc — có thể ngắn hơn nhiều (nếu 5 bài đều có mô tả ngắn) hoặc dài hơn nhiều (nếu AI sinh script dài dòng), khiến tuyên bố "4 phút" trong notification không đáng tin cậy.

## User Story
> Là người dùng có khung thời gian di chuyển cố định mỗi sáng (ví dụ 4 phút đi bộ ra bến xe buýt, hoặc 15 phút lái xe),
> Tôi muốn CommuteCast chọn đủ số bài viết vừa khít với thời lượng tôi có, ưu tiên đa dạng nguồn tin,
> Để tôi nghe trọn vẹn bản tin đúng lúc tới nơi, không bị cắt ngang hoặc quá ngắn so với thời gian rảnh.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đã chọn (hoặc dùng mặc định) một ngân sách thời gian cho CommuteCast (ví dụ 4 phút bản thường, 15 phút Deep Dive)
- **When** `CommuteWorker`/`CommuteCastViewModel` chọn bài viết để đưa vào script
- **Then** thuật toán ước lượng thời lượng đọc của từng bài (dựa trên độ dài text mô tả/tiêu đề quy đổi ra số từ / tốc độ đọc trung bình ~150 từ/phút) và chọn đủ số bài để tổng thời lượng ước tính khớp với ngân sách đã chọn (dừng chọn thêm khi đã đạt hoặc vượt nhẹ ngân sách)
- **And** trong số các bài đủ điều kiện, ưu tiên đa dạng nguồn (`feedId` khác nhau) thay vì chỉ lấy theo thứ tự thời gian thuần túy — ví dụ tối đa N bài liên tiếp từ cùng 1 feed trước khi phải xen bài từ feed khác
- **And** nếu không đủ bài để lấp đầy ngân sách (ví dụ inbox có < 3 bài chưa đọc), hệ thống vẫn hoạt động bình thường với số bài hiện có, không lỗi

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-08] "Chọn Nội Dung Chưa Theo Ngân Sách Thời Gian Người Dùng" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/DJ-08_time-budget-aware-content-selection.md trước khi bắt đầu. Task này liên quan tới gap tương tự đã ghi nhận ở DJ-01 (tiêu chí chọn bài theo "điểm tương tác cao nhất" chưa đúng) — cân nhắc giải quyết đồng bộ nếu hợp lý, nhưng không bắt buộc gộp 2 task.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (CommuteWorker.kt, CommuteScriptService.kt, CommuteCastViewModel.kt), xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria: viết hàm ước lượng thời lượng đọc (word count / ~150 từ/phút, có thể đặt trong domain/sv hoặc 1 util riêng, có unit test riêng), sửa logic chọn bài trong CommuteWorker/ViewModel để chọn theo ngân sách thời gian thay vì `limit` cố định, thêm logic ưu tiên đa dạng `feedId` (ví dụ round-robin theo feed hoặc giới hạn tối đa liên tiếp cùng feed). Cân nhắc thêm 1 `*Pref.kt` mới cho ngân sách thời gian nếu cần cho user tùy chỉnh (theo pattern `infrastructure/pref/Settings.kt` mô tả trong CLAUDE.md), hoặc dùng hằng số mặc định 4 phút/15 phút nếu không cần UI cấu hình ở task này. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng, tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên: inbox rỗng, chỉ 1 nguồn duy nhất, bài viết rất dài/rất ngắn).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có thêm UI chọn ngân sách thời gian.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
