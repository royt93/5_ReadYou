# [KNOW-03] Hệ Thống Sổ Tay Highlight & Xuất Sơ Đồ Tư Duy (Mindmap to Notion/Obsidian)

- **Type:** Productivity / Knowledge Management
- **Priority:** `P2 (Medium)` — ưu tiên cao trong nhóm còn TODO vì effort thấp hơn dự kiến (xem ghi chú hạ tầng tái dùng bên dưới)
- **Estimation:** `5 Story Points`
- **Epic:** [06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức](06_NEXT_GEN_AI_KNOWLEDGE.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/notebook/` (Gói mới, chưa tồn tại)

## Vấn đề thực tế
Người đọc chuyên sâu thường muốn lưu lại các câu trích dẫn đắt giá, thêm ghi chú cá nhân và tổng hợp các kiến thức đã học vào các công cụ quản lý tri thức như Notion, Obsidian, Logseq. Đã audit trực tiếp source code (grep `notebook`, `Notion`, `Obsidian` trong toàn bộ `app/src/main/java`) và xác nhận: **không có** package `ui/page/notebook/`, không có bất kỳ text/logic nào liên quan đến "Notion" hay "Obsidian" (chuỗi `URL_POLICY_NOTION` trong `ui/ext/Context.kt` chỉ là link chính sách bảo mật lưu trên Notion, không liên quan tính năng). Tính năng này vẫn thực sự chưa được implement — vẫn là TODO.

### Ghi chú hạ tầng đã có sẵn (giảm effort thực tế)
Không cần xây từ số 0. Các hạ tầng AI liên quan sau **đã tồn tại** và có thể tái dùng trực tiếp:
- `infrastructure/ai/ArticleDeepReadEngine.kt` + `ui/page/home/read/DeepReadChatSheet.kt` — hạ tầng Deep Read/Q&A theo bài viết đã hoạt động, có thể tái dùng pattern lưu trữ tương tác + UI sheet cho phần "ghi chú phản biện" của highlight.
- `infrastructure/ai/ArticleMindMapExtractor.kt` + `ui/page/home/read/MindMapSheet.kt` + `domain/model/article/ArticleMindMap.kt` — bộ trích xuất sơ đồ tư duy từ nội dung bài viết đã có sẵn; phần "xuất Markdown/Mermaid Mindmap" trong AC có thể build trên output có sẵn của extractor này thay vì viết mới logic trích xuất cấu trúc từ bài viết.
- `infrastructure/ai/ArticleHighlightsExtractor.kt` — lưu ý: đây là bộ trích xuất **AI tóm tắt highlight tự động** (structured summary từ Gemini), KHÔNG phải tính năng người dùng tự bôi đen/lưu highlight thủ công mà KNOW-03 yêu cầu. Hai khái niệm khác nhau — không nhầm lẫn khi implement, nhưng có thể tham khảo model `ArticleHighlights` đã có để thiết kế entity Room cho highlight thủ công.

Vì vậy, phần việc còn thiếu thực sự chỉ là: (1) lớp lưu trữ highlight thủ công của người dùng (Room entity + DAO mới), (2) UI bôi đen văn bản trong `ReadingPage` + trang "Sổ Tay Tri Thức", (3) lớp export (Markdown/Mermaid, tuỳ chọn Notion API).

## User Story
> Là người học tập và nghiên cứu suốt đời qua RSS,
> Tôi muốn bôi đậm highlight nhiều màu trong bài báo và xuất toàn bộ ghi chú sang Notion hoặc Markdown,
> Để tích hợp mượt mà vào kho tri thức Second Brain của tôi.

## Acceptance Criteria (Gherkin)
- **Given** người dùng bôi đen một đoạn văn bản trong `ReadingPage`
- **When** chọn màu highlight (Vàng, Xanh lam, Hồng) và gõ ghi chú phản biện
- **Then** highlight được lưu vĩnh viễn và hiển thị đồng bộ khi mở lại bài báo
- **And** cung cấp trang "Sổ Tay Tri Thức" quản lý tập trung toàn bộ trích dẫn đã lưu
- **And** hỗ trợ nút xuất 1-chạm: Xuất ra Markdown/Mermaid Mindmap hoặc đồng bộ trực tiếp lên Notion qua Notion API.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [KNOW-03] "Hệ Thống Sổ Tay Highlight & Xuất Sơ Đồ Tư Duy (Mindmap to Notion/Obsidian)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/KNOW-03_notebook-highlight-export.md trước khi bắt đầu, đặc biệt phần "Ghi chú hạ tầng đã có sẵn" — PHẢI tái dùng infrastructure/ai/ArticleMindMapExtractor.kt cho phần xuất Mindmap, không viết lại logic trích xuất cấu trúc từ đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Đọc thêm infrastructure/ai/ArticleDeepReadEngine.kt, infrastructure/ai/ArticleMindMapExtractor.kt, domain/model/article/ArticleMindMap.kt, domain/model/article/ArticleHighlights.kt để hiểu pattern lưu trữ/model đã có, tái dùng tối đa.
2. Implement fix/feature đúng theo Acceptance Criteria:
   - Room entity + DAO mới cho highlight thủ công (màu, đoạn text, ghi chú, articleId, timestamp) — thêm migration Room đúng chuẩn (không phá schema cũ), export schema vào app/schemas.
   - UI bôi đen trong ReadingPage (chọn màu Vàng/Xanh lam/Hồng) + trang "Sổ Tay Tri Thức" (notebook) mới trong ui/page/notebook/.
   - Lớp export: Markdown trước (ưu tiên, đơn giản), Mermaid Mindmap (tái dùng ArticleMindMapExtractor), Notion API là optional/stretch nếu còn thời gian trong estimation 5 SP.
   Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng (Room, export file) chạy Dispatchers.IO (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) cho mọi text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (schema Room mới, export flow), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [KNOW-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [KNOW-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

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
