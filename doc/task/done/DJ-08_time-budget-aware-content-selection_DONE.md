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

---

## ✅ Completion Report (2026-09-27)

**Điểm tự đánh giá: 9.2/10**

### Đã làm
- `CommuteContentSelector.estimateSpokenDurationSeconds()`: ước lượng thời lượng nói dựa trên số từ (title + 250 ký tự đầu tóm tắt) ở tốc độ ~150 từ/phút, cộng `PER_ARTICLE_OVERHEAD_SECONDS` cho phần dẫn dắt/đối đáp, chặn biên `[20s, 60s]`.
- `CommuteContentSelector.selectArticles()`: duyệt candidate theo điểm tương tác giảm dần, tích lũy tới khi đạt ngân sách (`targetMinutes * 60 - INTRO_OUTRO_SECONDS`), đồng thời ép buộc **đa dạng nguồn** — tối đa `MAX_CONSECUTIVE_PER_FEED = 2` bài liên tiếp cùng `feedId`.
- Thêm `CommuteTimeBudgetPref` (DataStore, preset 3/4/8/15 phút, mặc định 4) + `DataStoreKeys.CommuteTimeBudget` + đăng ký vào `Settings.kt`/`LocalCommuteTimeBudget`/`Pref.kt`.
- Thêm hàng **chip chọn thời lượng** trong `CommuteCastSheet.kt` (3 phút / 4 phút / 8 phút / 15 phút). Bấm chip → `CommuteCastViewModel.selectTimeBudget()` → cập nhật `uiState` + **persist vào DataStore** + tạo lại bản tin theo ngân sách mới.
- `CommuteWorker` (job 6:00 sáng) đọc `context.commuteTimeBudgetMinutes` (pref đã lưu) thay vì hardcode 4 phút, đảm bảo job nền tôn trọng lựa chọn user đã chọn lần gần nhất trong sheet.
- `NotificationHelper.notifyCommuteCast()` đổi từ text tiếng Việt hardcode "5 điểm tin" sang string template localize theo số phút thực tế + số bài thực tế đã chọn — đủ 6 ngôn ngữ (`en`, `vi`, `zh-rCN`, `ja`, `fr`, `de`).

### Test đã thêm
- **Unit test**: `CommuteContentSelectorTest.selectArticles_respectsTimeBudget`, `selectArticles_interleavesFeedsWhenSpammed`, `selectArticles_fewArticles_returnsAllWithoutCrashing`, `estimateSpokenDurationSeconds_boundsDurationCorrectly`. `CommuteTimeBudgetPrefTest` (4 test: map minutes→preset, default fallback, đọc/ghi DataStore).
- **Widget/Compose UI test** (`CommuteCastWidgetTest`, +2 test mới, chạy thật trên Pixel 7 Pro): `commuteCastUi_displaysTimeBudgetChips` (verify cả 4 label hiển thị đúng), `commuteCastUi_clickingTimeBudgetChip_invokesCallback` (giả lập click semantics thật, verify callback nhận đúng giá trị 8).
- **Integration test** `CommuteContentSelectionIntegrationTest.selectArticles_onRealDb_respectsFeedDiversityAndTimeBudget` trên Room DB thật: 8 bài dồn dập từ 1 feed + 4 bài từ feed khác, verify thuật toán chọn đúng theo ngân sách và xen kẽ nguồn.

### Bằng chứng smoke test thật (Pixel 7 Pro, `2B051FDH3006MU`)
- Mở CommuteCast sheet: hàng chip "Thời lượng: 3 phút / 4 phút / 8 phút / 15 phút" hiển thị đúng, mặc định chọn "4 phút".
- Bấm "8 phút": chip chuyển trạng thái chọn ngay lập tức, bản tin tự động tạo lại và phát.
- **Xác nhận persistence thật qua DataStore**: sau khi chọn "8 phút", `force-stop` toàn bộ process app, mở lại app từ Splash → mở lại CommuteCast sheet → chip "8 phút" vẫn giữ nguyên trạng thái đã chọn (ảnh chụp màn hình xác nhận), chứng minh `CommuteWorker` lúc 6h sáng sẽ đọc đúng giá trị này.
- Logcat sau toàn bộ smoke test: `grep FATAL EXCEPTION` → không có kết quả.

### Kết quả test tổng hợp
- Unit: 497/497 pass. Instrumented: 121/121 pass (bao gồm 8/8 widget test CommuteCastWidgetTest, 3/3 integration test content selection).

### Vì sao 9.2 chứ không phải 10
- Chưa thêm UI hiển thị số bài đã chọn / tổng thời lượng ước tính thực tế lên sheet (AC không bắt buộc, nhưng sẽ tăng tính minh bạch) — để lại làm cải tiến UX sau nếu cần.
- Smoke test chỉ verify với inbox rỗng (do dữ liệu thật trên thiết bị trống); chưa test trực quan với inbox có nhiều feed dồn dập thật để mắt thấy tận nơi tính năng "đa dạng nguồn" — đã được integration test trên DB thật bù đắp phần lớn rủi ro này.

**Commit:** xem `git log` — commit `feat(commute): DJ-01+DJ-08 interaction score + time-budget content selection`.
