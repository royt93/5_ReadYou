# [EXC-09] Original Source DNA — Truy Vết Nguồn Gốc & Cây Sao Chép Của Một Cụm Tin

- **Type:** Exclusive Killer Feature / Media Literacy
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** [`infrastructure/ai/clustering/StoryClusteringEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngine.kt), [`domain/model/cluster/StoryCluster.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/cluster/StoryCluster.kt) (mở rộng model), `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/cluster/` (nơi hiển thị cụm hiện có)

## Cơ hội / Động lực
`StoryClusteringEngine.cluster()` đã gom các bài cùng chủ đề vào `StoryCluster.articles`; mỗi `ArticleWithFeed` có ngày do feed báo cáo và nội dung văn bản. Có thể giúp người đọc thấy thứ tự thời gian và những bài có câu chữ tương đồng. Dữ liệu này không chứng minh bài nào là tác giả gốc hoặc bài nào sao chép; feed có thể syndicate hoặc cập nhật timestamp trễ. Vì vậy sản phẩm chỉ trình bày quan hệ thời gian/similarity dạng heuristic, có disclaimer và trạng thái không chắc chắn.

## User Story
> Là người muốn biết mình đang đọc tin từ nguồn gốc hay bản sao chép lại,
> Tôi muốn xem cây phả hệ nguồn tin của một cụm sự kiện (ai đưa tin đầu tiên, ai sao chép),
> Để tôi ưu tiên đọc bài gốc chất lượng cao thay vì các bài rewrite hời hợt.

## Acceptance Criteria (Gherkin)
- **Given** một `StoryCluster` có ít nhất 3 feed duy nhất
- **When** người dùng mở chi tiết cụm sự kiện và bấm "🧬 Source DNA"
- **Then** hệ thống phân tích `article.date` do feed báo cáo và độ tương đồng câu chữ cục bộ; nhãn diễn đạt đây là heuristic, không xác nhận nguồn gốc hay cáo buộc sao chép
- **And** bài có timestamp sớm nhất duy nhất được gắn "Đăng sớm nhất trong cụm"; bài sau nối với bài trước có similarity cao nhất (>= 0.75), nếu đủ ngưỡng thì gắn "Tương đồng cao với [feed]" kèm phần trăm
- **And** hiển thị graph có thể cuộn dọc, các node sắp theo thời gian, kèm feed, thời gian, tiêu đề, relation và similarity; bấm node mở bài
- **And** hòa timestamp sớm nhất, tất cả timestamp bằng nhau, hoặc không có cạnh đủ ngưỡng thì hiển thị trạng thái không rõ nguồn; không tự chọn root
- **And** không gọi API AI/cloud; cụm trên 20 bài chỉ phân tích 20 bài sớm nhất và báo rõ việc cắt ngưỡng; analyzer tối đa 190 phép so sánh; mục tiêu warm median dưới 200 ms trên Tecno.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-09] "Original Source DNA — Truy Vết Nguồn Gốc & Cây Sao Chép Của Một Cụm Tin" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Cơ hội / Động lực" + "Acceptance Criteria" trong file doc/task/todo/EXC-09_original-source-dna.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (đặc biệt StoryClusteringEngine.kt và StoryCluster.kt), xác nhận cấu trúc dữ liệu hiện có khớp với mô tả (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria, tái sử dụng thuật toán độ tương đồng đã có trong StoryClusteringEngine thay vì viết lại từ đầu. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-09] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-09] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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

## Completion Report (2026-10-14)

**Status:** Implemented locally; no push.

- Added pure `SourceDnaAnalyzer`: deterministic timestamp graph, strictly-earlier candidate parents, `>= 0.75` similarity edge, tied timestamp abstention, invalid score rejection, duplicate/blank ID handling, unknown source fallback, and oldest-20 cap.
- Added on-demand Source DNA view in `StoryClusterSheet`; original perspectives/lead action unchanged. Labels and disclaimer consistently say the graph is a heuristic, not proof of copying or verified attribution. Node tap delegates to the existing article route.
- Localized all strings in maintained resource folders; primary en/vi/zh-rCN/ja/fr/de translations; English fallback elsewhere.
- **Unit:** `SourceDnaAnalyzerTest` 11; `StoryClusteringEngineTest` 12; full `testDevDebugUnitTest` green.
- **Widget/integration on Tecno `115333744A005844`:** `StoryClusterSheetWidgetTest` 6/6; `SourceDnaFlowIntegrationTest` 1/1. Existing lead article action still routes to lead.
- **Performance on Tecno:** 20 articles; cold 84 ms, warm median 22 ms, warm worst 24 ms; 190 similarity calls.
- **Build/lint:** `testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest lintDevDebug` all green.
- **Manual smoke limitation:** Flow on Tecno had no naturally available cluster with >=3 distinct sources; opened article list showed BBC single-source items only. No synthetic cluster or feed data was inserted. Therefore tapping Source DNA on a real eligible cluster could not be manually smoke-tested; widget and Flow integration tests cover that flow. No full-screen ad appeared during instrumentation/device checks.

**Audit:** 9/10. Heuristic and uncertainty handling are explicit; live device smoke could not exercise eligible-cluster entry because current device feed data lacked such a cluster.
