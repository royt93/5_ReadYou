# [EXC-08] Second Brain Graph — Đồ Thị Tri Thức Cá Nhân Hợp Nhất

- **Type:** Exclusive Killer Feature / Knowledge Visualization
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/secondbrain/` (Gói mới), [`infrastructure/ai/clustering/StoryClusteringEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngine.kt), [`infrastructure/ai/search/SemanticSearchEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt), [`infrastructure/ai/ArticleMindMapExtractor.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/ArticleMindMapExtractor.kt), [`domain/model/cluster/StoryCluster.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/cluster/StoryCluster.kt)

## Cơ hội / Động lực
App đã có 3 mảnh ghép AI on-device rời rạc và không có màn hình nào kết nối chúng lại với nhau: `StoryClusteringEngine` gom bài trùng chủ đề thành `StoryCluster` (đã có `keywords`, `sourceCount`), `SemanticSearchEngine` chiếu bài viết vào không gian vector 64 chiều theo `CONCEPT_CLUSTERS` (AI, Semiconductor, Clean Energy...), và `ArticleMindMapExtractor` trích xuất khái niệm/quan hệ trong từng bài (`ArticleMindMap`). Hiện tại 3 kết quả này chỉ hiển thị rời rạc: cluster hiện ở `FlowPage`, semantic search chỉ ở thanh tìm kiếm, mind map chỉ ở từng bài riêng lẻ (`ReadingPage`). Không có nơi nào cho người dùng thấy "tôi đã đọc về AI, bán dẫn, năng lượng sạch trong 30 ngày qua, và các chủ đề đó liên kết với nhau ra sao theo thời gian" — đây là cơ hội tái sử dụng 100% hạ tầng AI đã có để tạo ra một màn hình "Second Brain" độc quyền mà không cần train/gọi model AI mới.

## User Story
> Là người đọc tin tức lâu dài muốn hiểu bức tranh tổng thể mối quan tâm của mình,
> Tôi muốn mở 1 màn hình đồ thị duy nhất cho thấy các chủ đề tôi đã đọc kết nối với nhau và tiến triển theo thời gian,
> Để tôi nhận ra các mẫu hình tri thức cá nhân mà việc đọc rời rạc từng bài không bao giờ cho thấy được.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đã đọc/lưu đủ số bài để có ít nhất 2 `StoryCluster` và các khái niệm từ `ArticleMindMap` trong 30 ngày gần nhất
- **When** mở màn hình mới "🧠 Second Brain" từ menu Settings hoặc FlowPage
- **Then** hiển thị một đồ thị (node-edge graph) trong đó: node = chủ đề/concept (từ `StoryCluster.keywords` và `ArticleMindMap` concepts hợp nhất qua `SemanticSearchEngine.CONCEPT_CLUSTERS`), cạnh nối = mức độ liên quan ngữ nghĩa (cosine similarity) hoặc cùng xuất hiện trong 1 cluster
- **And** chạm vào 1 node mở panel chi tiết liệt kê các `StoryCluster`/bài viết liên quan, sắp xếp theo thời gian (timeline)
- **And** có thanh trượt thời gian (time slider) để xem đồ thị "phát triển" ra sao qua các tuần/tháng
- **And** toàn bộ tính toán chạy on-device dựa trên dữ liệu đã có sẵn từ `StoryClusteringEngine`/`SemanticSearchEngine`/`ArticleMindMapExtractor`, không gọi thêm API AI mới, render đồ thị dưới 1 giây với tối đa 200 node.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-08] "Second Brain Graph — Đồ Thị Tri Thức Cá Nhân Hợp Nhất" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Cơ hội / Động lực" + "Acceptance Criteria" trong file doc/task/todo/EXC-08_second-brain-graph.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (đặc biệt StoryClusteringEngine.kt, SemanticSearchEngine.kt, ArticleMindMapExtractor.kt), xác nhận API/model hiện có khớp với mô tả (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria, ưu tiên TÁI SỬ DỤNG engine AI đã có thay vì viết lại logic phân cụm/embedding mới. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
