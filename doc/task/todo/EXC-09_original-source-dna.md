# [EXC-09] Original Source DNA — Truy Vết Nguồn Gốc & Cây Sao Chép Của Một Cụm Tin

- **Type:** Exclusive Killer Feature / Media Literacy
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** [`infrastructure/ai/clustering/StoryClusteringEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngine.kt), [`domain/model/cluster/StoryCluster.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/cluster/StoryCluster.kt) (mở rộng model), `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/cluster/` (nơi hiển thị cụm hiện có)

## Cơ hội / Động lực
`StoryClusteringEngine.cluster()` đã gom được các bài viết cùng chủ đề vào `StoryCluster(articles, sourceCount, articleCount, ...)` và chọn `leadArticle` (hiện dùng heuristic đơn giản, không phải "bài gốc" thực sự). Với 1 sự kiện nóng, 8-10 nguồn có thể cùng đưa tin nhưng chỉ 1-2 nguồn là tác giả gốc còn lại chủ yếu sao chép/diễn giải lại (rewrite). App hiện chưa có cách nào cho người dùng biết bài nào đáng tin/là nguồn gốc, bài nào chỉ là "xào lại". Đây là cơ hội mở rộng ngay trên dữ liệu `StoryCluster.articles` đã có sẵn (mỗi `ArticleWithFeed` có `article.date`, `feed.id`), dùng heuristic thời gian đăng sớm nhất + độ tương đồng nội dung cao với các bài khác để suy luận cây nguồn gốc, không cần gọi AI cloud.

## User Story
> Là người muốn biết mình đang đọc tin từ nguồn gốc hay bản sao chép lại,
> Tôi muốn xem cây phả hệ nguồn tin của một cụm sự kiện (ai đưa tin đầu tiên, ai sao chép),
> Để tôi ưu tiên đọc bài gốc chất lượng cao thay vì các bài rewrite hời hợt.

## Acceptance Criteria (Gherkin)
- **Given** một `StoryCluster` có `sourceCount >= 3`
- **When** người dùng mở chi tiết cụm sự kiện và bấm "🧬 Xem Nguồn Gốc (Source DNA)"
- **Then** hệ thống phân tích `article.date` (thời gian đăng) kết hợp độ tương đồng câu chữ với các bài khác trong cụm để xác định: (a) bài được đăng sớm nhất VÀ có nội dung độc lập (similarity thấp với các bài khác) → gắn nhãn "🌱 Nguồn Gốc", (b) các bài đăng sau và có độ tương đồng câu chữ cao (> ngưỡng) với bài gốc → gắn nhãn "📋 Sao Chép Lại"
- **And** hiển thị dưới dạng cây/timeline: gốc trên cùng, các bản sao chép bên dưới theo thứ tự thời gian, kèm % tương đồng với bài gốc
- **And** nếu không xác định được rõ nguồn gốc (nhiều bài đăng cùng lúc, độ tương đồng thấp đều) thì hiển thị "Không xác định được nguồn gốc rõ ràng" thay vì suy đoán sai
- **And** kết quả phân tích không gọi API AI cloud, tính toán dưới 200ms cho cụm tối đa 20 bài.

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
