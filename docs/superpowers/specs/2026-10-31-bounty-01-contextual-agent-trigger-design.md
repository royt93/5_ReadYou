# Thiết Kế: [BOUNTY-01] Kích Hoạt Agent Điều Tra 1-Chạm Trên Màn Hình Đọc

- **Mã tính năng:** BOUNTY-01
- **Loại:** Feature / AI Agent Integration
- **Độ ưu tiên:** P1 (High)
- **Ước lượng:** 5 Story Points
- **Epic:** [15. BOUNTY — AI Bounty Hunter Agent & Obsidian Graph](15_BOUNTY_HUNTER_AGENT_AND_GRAPH.md)
- **Ngày lập:** 2026-10-31
- **Tác giả:** Claude Code & roy
- **Nhánh thực hiện:** `dev` (duy nhất, không tạo worktree)
- **Thiết bị mục tiêu:** TECNO BG6 (serial `118743744X002560`)

---

## 1. Mục tiêu & Bối cảnh

### 1.1 Vấn đề thực tế
Khi người đọc lướt qua một bài báo trong `ReadingPage`, họ thường bắt gặp các thực thể mới (tên công ty, nhân vật, thuật toán công nghệ, dự luật chính trị). Hiện tại, thanh menu chọn chữ (`QuoteTextToolbar`) chỉ cung cấp chức năng sao chép hệ thống và chip "Chia sẻ trích dẫn" (IDEA-03). Người dùng muốn đào sâu phải tự chuyển qua trình duyệt ngoài hoặc tìm kiếm thủ công, làm đứt gãy mạch đọc.

### 1.2 Giải pháp BOUNTY-01
Bổ sung nút 1-chạm **"🕵️ Gửi Agent Điều Tra"** xuất hiện ngay cạnh chip trích dẫn khi người dùng bôi đen văn bản trong `ReadingPage`. Chạm vào nút sẽ kích hoạt `BountyInvestigationSheet`:
1. **Giai đoạn quét (~700ms):** Hiển thị hoạt ảnh sóng Radar quét dữ liệu mượt mà bằng Canvas Compose.
2. **Giai đoạn kết quả:** Tổng hợp tức thời các bài viết khác trong kho tin Room Database có cùng đề cập thực thể này (dòng thời gian, nguồn đưa tin, độ tương đồng), cho phép chạm mở đọc ngay.
3. **Phân tích bối cảnh nâng cao:** Nếu người dùng đã cài đặt API key Gemini riêng (BYOK), cung cấp thêm tùy chọn AI tóm tắt nguồn gốc và bối cảnh thực thể.

---

## 2. Kiến trúc & Luồng Dữ Liệu

```
[Người dùng bôi đen chữ trong ReadingPage]
                   │
                   ▼
       [SelectionActionToolbar]
       ├── [💬 Chia sẻ trích dẫn] (IDEA-03)
       └── [🕵️ Gửi Agent Điều Tra] (BOUNTY-01)
                   │
                   ▼ (Chạm 'Điều tra')
       [BountyQuerySanitizer]
       ├── Cắt tối đa 100 ký tự / câu đầu tiên
       ├── Loại bỏ dấu câu biên, chuẩn hóa khoảng trắng
       └── Trích xuất query: String
                   │
                   ▼
     [BountyInvestigationViewModel]
       ├── Kích hoạt trạng thái Scanning (700ms radar wave)
       ├── Truy vấn Room: ArticleDao.queryRelatedArticlesByKeyword(query, accountId, currentArticleId)
       │     └── Lọc trên `title` và `shortDescription` (KHÔNG đọc body lớn - tuân thủ FIX-17)
       ├── Tính toán thống kê: số bài, số nguồn, khoảng thời gian
       └── Kiểm tra tính sẵn sàng của Gemini BYOK key (không dùng key default - tuân thủ FIX-09)
                   │
                   ▼
     [BountyInvestigationSheet (UI)]
       ├── State 1: Scanning (Canvas Radar ripples + rotating beam)
       └── State 2: Ready
             ├── Header: Chip thực thể + Thống kê (X bài • Y nguồn • Thời gian)
             ├── Danh sách bài liên quan (LazyColumn) -> Chạm để đọc bài
             ├── Empty State: Gợi ý khi kho chưa có bài liên quan
             └── Nút tùy chọn: [✨ AI Phân Tích Bối Cảnh] (chỉ hiện khi có BYOK key)
```

### 2.1 Chuẩn hóa truy vấn (`BountyQuerySanitizer`)
- Cắt lấy tối đa 100 ký tự đầu tiên của đoạn chọn.
- Nếu đoạn chọn có nhiều câu, lấy câu đầu tiên trước dấu chấm/chấm phẩy/xuống dòng.
- Bỏ khoảng trắng thừa, dấu ngoặc kép, dấu chấm than/hỏi ở hai đầu.
- Trả về `null` nếu chuỗi sau chuẩn hóa dưới 2 ký tự (không hợp lệ để điều tra).

### 2.2 Truy vấn kho lưu trữ Room (`ArticleDao`)
Thêm truy vấn tìm kiếm các bài viết liên quan theo từ khóa trong cùng tài khoản, loại trừ bài đang đọc:
```kotlin
@Query(
    """
    SELECT id, title, feedId, date, shortDescription, img, isStarred, isUnread FROM article
    WHERE accountId = :accountId AND id != :currentArticleId
      AND (title LIKE '%' || :keyword || '%' OR shortDescription LIKE '%' || :keyword || '%')
    ORDER BY date DESC LIMIT 20
    """
)
suspend fun queryRelatedArticlesByKeyword(
    accountId: Int,
    currentArticleId: String,
    keyword: String
): List<ArticleListItem>
```
*Ghi chú:* Chỉ nạp metadata danh sách, không nạp cột `rawDescription` hay `fullContent` để tuân thủ triệt để quy tắc **FIX-17 CursorWindow safety**.

### 2.3 Ràng buộc bảo mật AI & Quản lý API Key (Tuân thủ FIX-09)
- Tính năng điều tra cốt lõi chạy **100% offline**, không yêu cầu bất kỳ API key nào.
- Nút `[✨ AI Phân Tích Bối Cảnh]` chỉ hiển thị khi `GeminiSummaryService.hasUserApiKey()` là `true` (người dùng đã tự điền Gemini API key của họ trong trang cài đặt AI).
- Tuyệt đối **KHÔNG** fallback về các default system keys trong `GeminiConfig.kt` để tránh tiêu hao hạn ngạch hoặc kích hoạt lỗ hổng bảo mật P0 đang hoãn.

---

## 3. Thiết kế Giao Diện (Jetpack Compose)

### 3.1 Nút kích hoạt trên Selection Toolbar
- Mở rộng cơ chế `QuoteShareChip` hiện có thành `SelectionActionChips`:
  - Đặt trong `Popup` bám theo toạ độ `anchorRect` của đoạn văn bản đang bôi đen.
  - Hiển thị 2 `AssistChip` cạnh nhau với khoảng cách 6.dp:
    - Chip 1: Icon `FormatQuote` + text "Trích dẫn"
    - Chip 2: Icon `Search` (hoặc emoji 🕵️) + text "Điều tra"
  - Bấm "Điều tra" -> kích hoạt copy tạm để lấy text chuẩn xác, khôi phục clipboard cũ, đóng popup, mở `BountyInvestigationSheet`.

### 3.2 Giao diện `BountyInvestigationSheet`

#### Trạng thái 1: Scanning (~700ms)
- Bố cục ở giữa sheet:
  - Vòng tròn Radar trung tâm vẽ bằng Compose `Canvas`:
    - 3 vòng tròn đồng tâm bán kính tăng dần và alpha mờ dần (hiệu ứng sóng tỏa ra).
    - Một tia quét quay 360 độ sử dụng `rememberInfiniteTransition` và `sweepGradient`.
  - Bên dưới: Dòng chữ `🕵️ Agent đang điều tra thực thể: "<query>"` chạy animation nhấp nháy nhẹ.

#### Trạng thái 2: Results
- **Header:**
  - Chip thực thể: Nền `primaryContainer`, text in đậm tên thực thể.
  - Thanh thông tin: `X bài liên quan` • `Y nguồn tin` • `Mốc: [ngày/tháng sớm nhất] – [mới nhất]`.
- **Nội dung:**
  - Nếu `X > 0`: `LazyColumn` hiển thị danh sách bài liên quan:
    - Tiêu đề bài viết (từ khóa truy vấn được tô sáng bằng `SpanStyle` màu primary).
    - Tên nguồn feed + thời gian xuất bản (dạng tương đối: "Hôm nay", "3 ngày trước" hoặc ngày cụ thể).
    - Khi chạm vào hàng bài viết: đóng sheet và điều hướng `navController.navigate("${RouteName.READING}/$articleId")`.
  - Nếu `X = 0`: Thẻ rỗng với biểu tượng kính lúp, thông báo: *"Chưa tìm thấy bài viết nào khác trong kho tin đề cập đến thực thể này."*
- **Footer:**
  - Nút `[✨ AI Phân Tích Bối Cảnh]` (chỉ hiện khi có BYOK key): mở rộng một thẻ tóm tắt bối cảnh thực thể do Gemini sinh ra.
  - Nút `Đóng` để quay lại bài đọc.

---

## 4. Đa Ngôn Ngữ (Localization)

Thêm các khóa chuỗi sau vào `strings.xml` và duy trì đầy đủ trên toàn bộ 38 locale:
1. `bounty_action_investigate`: "🕵️ Điều tra" / "🕵️ Investigate"
2. `bounty_sheet_title`: "Agent Điều Tra Thực Thể" / "Entity Investigation Agent"
3. `bounty_scanning_status`: "Đang rà soát kho lưu trữ tin tức về: %1$s..." / "Scanning news archive for: %1$s..."
4. `bounty_stats_summary`: "%1$d bài viết liên quan • %2$d nguồn tin" / "%1$d related articles • %2$d sources"
5. `bounty_empty_results`: "Không tìm thấy bài viết nào khác trong kho tin đề cập đến thực thể này." / "No other articles in your archive mention this entity."
6. `bounty_ai_context_action`: "✨ AI Phân Tích Bối Cảnh" / "✨ AI Context Summary"
7. `bounty_ai_context_title`: "Bối Cảnh Thực Thể (Gemini)" / "Entity Context (Gemini)"
8. `bounty_ai_context_no_key`: "Cần nhập API Key Gemini riêng để dùng tính năng AI." / "Custom Gemini API key required."
9. `bounty_time_range`: "Mốc: %1$s – %2$s" / "Timeline: %1$s – %2$s"
10. `bounty_invalid_query`: "Cụm từ được chọn quá ngắn để điều tra." / "Selected text is too short to investigate."

---

## 5. Kế Hoạch Kiểm Thử & TDD

1. **Unit Tests (Thuần JVM / Robolectric):**
   - `BountyQuerySanitizerTest`: Cắt chuỗi 100 ký tự, xử lý dấu câu, tách câu đầu tiên, từ chối chuỗi quá ngắn (<2 ký tự) hoặc toàn khoảng trắng/ký tự đặc biệt.
   - `BountyInvestigationServiceTest` (in-memory Room):
     - Truy vấn đúng các bài viết chứa từ khóa trong `title` hoặc `shortDescription`.
     - Loại trừ bài viết hiện tại đang đọc (`currentArticleId`).
     - Tính đúng số lượng bài viết, số lượng feed duy nhất, và khoảng thời gian min/max.
     - Trả về danh sách rỗng khi không có bài nào khớp.
   - `BountyAiContextTest`: Kiểm tra chỉ kích hoạt khi có BYOK key, ném lỗi rõ ràng khi không có key.
2. **Compose UI Tests (Robolectric & AndroidTest):**
   - `SelectionActionChipsTest`: Hiển thị cả 2 chip "Trích dẫn" và "Điều tra" khi có văn bản được chọn.
   - `BountyInvestigationSheetTest`:
     - Trạng thái Scanning hiển thị đúng text và Canvas radar.
     - Trạng thái Results hiển thị đúng danh sách bài, thông tin thống kê và highlight từ khóa.
     - Kiểm tra nút AI tự ẩn khi `hasUserApiKey = false` và hiện khi `hasUserApiKey = true`.
3. **Parity Tests (`StringResourceParityTest`):**
   - Đảm bảo toàn bộ 10 khóa chuỗi mới có mặt đồng bộ ở cả 38 thư mục locale.
4. **Smoke Test Thực Tế Trên Phần Cứng:**
   - **Thiết bị:** TECNO BG6 (`118743744X002560`) — tuân thủ khóa thiết bị của owner.
   - Mở bài viết bất kỳ trong Reader, bôi đen cụm từ (ví dụ: tên một chính trị gia hoặc công nghệ).
   - Xác nhận xuất hiện chip "Điều tra" cạnh chip "Trích dẫn".
   - Chạm vào "Điều tra" -> quan sát hoạt ảnh Radar -> xem danh sách bài liên quan -> chạm vào một bài để chuyển trang.
   - Tuân thủ R4: nếu xuất hiện interstitial quảng cáo, dừng ngay và chờ xác nhận.

---

## 6. Ranh Giới & Giới Hạn Đã Biết
- **Không tự động sinh Dossier đa trang:** BOUNTY-01 là điểm kích hoạt (trigger) và rà soát kho cục bộ. Việc xuất hồ sơ tài liệu chuyên sâu đa nguồn (Executive Dossier) thuộc về task kế tiếp **BOUNTY-02**.
- **Không tự động cào web ngoài:** Việc tìm kiếm phụ thuộc vào kho bài người dùng đã lưu trữ trong RSS Cat Hub (bảo vệ quyền riêng tư 100%, không bị giới hạn mạng/CORS).
