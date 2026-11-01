# BOUNTY-01 — Kích Hoạt Agent Điều Tra 1-Chạm Trên Màn Hình Đọc — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Tính năng
Bổ sung nút 1-chạm **"🕵️ Điều tra"** (`SelectionActionChips`) xuất hiện song song với chip "Chia sẻ trích dẫn" khi người dùng bôi đen bất kỳ cụm từ/thực thể nào trong màn hình đọc bài (`ReadingPage` / `Content.kt`):
- **Chuẩn hoá truy vấn (`BountyQuerySanitizer`):** cắt lấy câu đầu tiên, tối đa 100 ký tự (an toàn với cặp surrogate UTF-16, không cắt đôi emoji), tỉa dấu câu biên, từ chối chuỗi < 2 ký tự. Tự động escape ký tự đặc biệt của SQL LIKE (`\`, `%`, `_`) kèm mệnh đề `ESCAPE '\'`.
- **Truy vấn Room an toàn (FIX-17):** quét metadata bài viết trong kho lưu trữ của cùng tài khoản qua `ArticleDao.queryRelatedArticleListWithFeed`. Chỉ đọc các cột thuộc `ARTICLE_LIST_COLUMNS`, trả về `ArticleListItemWithFeed`, loại trừ bài viết đang đọc. Tuyệt đối không nạp cột body lớn (`rawDescription`, `fullContent`) vào CursorWindow.
- **BottomSheet kết quả (`BountyInvestigationSheetContent`):**
  - **Trạng thái quét (`Scanning`):** hoạt ảnh sóng tròn radar và tia quét 360° tự vẽ bằng Compose `Canvas` (`rememberInfiniteTransition`), kéo dài tối thiểu 700 ms để đảm bảo trải nghiệm trực quan.
  - **Trạng thái kết quả (`Results`):** chip thực thể được điều tra, thanh thống kê (`X bài liên quan • Y nguồn tin`, mốc thời gian sớm nhất – mới nhất theo locale), danh sách bài liên quan với từ khóa truy vấn được tô nổi (`AnnotatedString`/`SpanStyle`). Chạm vào bài viết sẽ đóng sheet và mở bài đọc tương ứng (`navController.navigate("${RouteName.READING}/$id")`).
  - **Trạng thái rỗng (`Empty`):** thông báo rõ ràng khi kho chưa có bài viết nào khác đề cập tới thực thể này.
- **Ràng buộc bảo mật AI (Tuân thủ FIX-09):** Nút `[✨ AI tóm tắt bối cảnh]` chỉ hiển thị khi `context.customGeminiApiKey.isNotBlank()` (người dùng tự nhập key Gemini riêng). Khi gọi API, hàm `GeminiSummaryService.summarizeBountyContext` CHỈ dùng duy nhất key riêng đó, tuyệt đối không gọi `resolveApiKeys()` và không fallback về các default key của hệ thống.
- **Đa ngôn ngữ:** bổ sung 13 khóa chuỗi `bounty_*` đồng bộ trên toàn bộ 38 thư mục `values*/strings.xml`.

## Quyết định của owner (AskUserQuestion)
1. **Bố cục kích hoạt:** Hàng 2 chip nổi cạnh menu bôi đen văn bản (`[💬 Chia sẻ trích dẫn]` và `[🕵️ Điều tra]`).
2. **Nguồn dữ liệu:** Quét kho Room cục bộ + tùy chọn AI chỉ khi có key riêng (BYOK).
3. **Giao diện Sheet:** 2 giai đoạn — Radar quét sóng tròn (~700ms) → Bảng kết quả bài viết đối chiếu.
4. **Phạm vi tìm kiếm:** Tìm trong `title` và `shortDescription`, loại trừ bài đang đọc.
5. **Chuyển đổi thiết bị thử nghiệm:** Do TECNO BG6 chưa kết nối tại thời điểm smoke, owner chỉ thị đổi khóa thiết bị dài hạn sang **Google Pixel 7 Pro (`2B051FDH3006MU`)** cho mọi phiên tiếp theo; quy tắc đã được cập nhật vào bộ nhớ dài hạn.

## Cấu trúc mã
- `domain/model/bounty/`:
  - `BountyQuerySanitizer.kt`: chuẩn hoá câu đầu tiên, cắt 100 ký tự an toàn surrogate, escape SQL LIKE.
  - `BountyInvestigationResult.kt`: DTO kết quả (truy vấn, danh sách bài, số nguồn, mốc thời gian min/max).
- `domain/repository/`:
  - `ArticleDao.kt`: bổ sung `@Query` `queryRelatedArticleListWithFeed` với `ARTICLE_LIST_COLUMNS`, `ESCAPE '\'`, giới hạn 20 bài.
  - `BountyInvestigationRepository.kt`: gọi DAO, tính toán thống kê nguồn và ngày tháng.
- `infrastructure/ai/`:
  - `GeminiSummaryService.kt`: bổ sung `hasCustomApiKey()`, constructor phụ hỗ trợ test, và hàm `summarizeBountyContext` chạy thuần trên key riêng của người dùng.
- `ui/component/quote/`:
  - `QuoteTextToolbar.kt`: hỗ trợ 2 hành động song song (`onShareQuote` + `onInvestigate`), giữ tương thích ngược hoàn toàn với chỗ gọi cũ.
  - `QuoteShareChip.kt`: hiển thị hàng 2 chip `AssistChip` trong popup nổi bám theo vùng chọn.
- `ui/page/home/read/`:
  - `bounty/BountyInvestigationState.kt`: mô hình trạng thái (`Scanning`, `Results`, `Error`, `AiState`).
  - `bounty/BountyInvestigationSheetContent.kt`: nội dung BottomSheet gồm Canvas Radar, chip thực thể, thống kê, LazyColumn bài đối chiếu và nút AI.
  - `ReadingViewModel.kt`: hàm `openBountyInvestigation`, `dismissBountyInvestigation`, `requestBountyAiContext`, quản lý coroutine job độc lập.
  - `ReadingPage.kt` & `Content.kt`: nối chip điều tra, nhận callback, điều hướng bài đọc từ kết quả sheet qua `BottomDrawer` có sẵn.

## Kiểm chứng
- **Unit test suite:** `./gradlew testDevDebugUnitTest --rerun` đạt **1.526 test, 0 lỗi, 0 skip** (tăng 45 test mới):
  - `BountyQuerySanitizerTest` (7 test): cắt 100 ký tự, an toàn surrogate UTF-16, tách câu đầu tiên, escape `\`, `%`, `_`.
  - `BountyInvestigationRepositoryTest` (8 test in-memory Room): tìm kiếm theo title/shortDescription, loại trừ bài đang đọc, giới hạn cùng account, xử lý ký tự LIKE literal, tính đúng số nguồn và mốc thời gian, không tìm trong body.
  - `GeminiSummaryServiceBountyTest` (5 test): ném `MissingApiKey` khi không có key riêng, chỉ truyền key riêng cho Gateway, không thử lại bằng key mặc định, xử lý phản hồi rỗng, prompt giới hạn trong title và shortDescription.
  - `ReadingViewModelBountyTest` (6 test): quét tối thiểu 700ms, hủy tác vụ khi đóng sheet, tự xử lý lỗi không crash reader, ẩn/hiện nút AI theo tính khả dụng của key riêng.
  - `SelectionActionChipsTest` (4 test): hiển thị cả 2 chip khi có vùng chọn, ẩn khi không có vùng chọn, chạm nút nào gọi đúng hành vi nút đó.
  - `BountyInvestigationSheetContentTest` (10 test Compose): radar hiển thị ở pha scanning, danh sách và thống kê ở pha results, ẩn/hiện nút AI, highlight từ khóa, chạm bài gọi callback.
  - 100% các bộ test regression (Quote, ReadingViewModel, Room DAO, Parity 38 locale) tiếp tục pass.
- **Lint:** `./gradlew lintDevDebug --rerun-tasks` đạt **0 lỗi, 2.453 cảnh báo** (không có lỗi hay cảnh báo mới).
- **Build APK:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Android instrumentation test (trên Pixel 7 Pro `2B051FDH3006MU`):**
  - `SelectionActionChipsDeviceTest` (3 test): pass 3/3 trên phần cứng thực tế.
  - `BountyInvestigationSheetDeviceTest` (3 test): pass 3/3 (radar Canvas render thành công, chạm bài điều hướng, nút AI theo dõi đúng trạng thái key).
  - Toàn bộ bộ 21 instrumentation tests hồi quy (RPG, EXC-04, BOUNTY) pass 21/21 trên Pixel.
- **Smoke test thực tế (trên Pixel 7 Pro `2B051FDH3006MU`):**
  - Đã sao lưu cơ sở dữ liệu Room trước khi thử nghiệm.
  - Mở bài viết thực tế trong kho (bài về bão Storm Boelo của BBC News).
  - Bôi đen từ khóa "across" trong thân bài -> xuất hiện thanh công cụ chọn chữ cùng 2 chip nổi `[Chia sẻ trích dẫn]` và `[🕵️ Điều tra]`.
  - Chạm chip `[🕵️ Điều tra]` -> BottomSheet mở lên với tiêu đề "🕵️ Agent điều tra thực thể".
  - Hoạt ảnh sóng radar quét kho tin hiển thị mượt mà; sau đó xuất hiện bảng kết quả:
    - Chip thực thể: "across"
    - Thống kê: "3 bài liên quan • 1 nguồn tin", mốc thời gian từ 11/10/2026 đến 26/10/2026.
    - Danh sách 3 bài viết khác trong kho tin có chứa từ khóa "across" với từ khóa được bôi đậm và tô màu chủ đề.
    - Chạm vào bài viết đầu tiên ("Elation across Serbia...") -> sheet đóng và Reader điều hướng ngay tới bài viết đó.
    - Nút `[✨ AI tóm tắt bối cảnh]` hiển thị do thiết bị có cấu hình key Gemini riêng trong DataStore.
  - Banner quảng cáo kiểm thử ở đáy màn hình không che giao diện; không gặp quảng cáo toàn màn hình che UI (R4).
  - Khôi phục nguyên vẹn bản sao lưu Room gốc của người dùng (`ok`, 176 bài, 1 feed) và gỡ bỏ gói kiểm thử.

## Giới hạn đã biết (nói thẳng)
1. **Phạm vi điều tra là kho tin nội bộ (Local News Archive):** Agent chỉ đối chiếu với các bài báo người dùng đã lưu trữ trong RSS Cat Hub, không tự động cào trang web bên ngoài để đảm bảo quyền riêng tư và hoạt động 100% offline.
2. **Không tự động sinh hồ sơ phân tích đa trang:** BOUNTY-01 tập trung vào kích hoạt 1-chạm và đối chiếu nhanh; việc tổng hợp thành hồ sơ phân tích điều tra chuyên sâu (Executive Intelligence Dossier) là phạm vi của task kế tiếp **BOUNTY-02**.
3. **Phân tích AI bối cảnh yêu cầu key riêng:** Để tuân thủ chính sách bảo mật FIX-09 (không lộ/dùng key mặc định), tính năng tóm tắt bối cảnh chỉ hoạt động khi người dùng tự cấu hình Gemini API key trong phần cài đặt AI.

## Commits trên nhánh `dev`
- `54f61efa`: `docs: design BOUNTY-01 contextual agent trigger`
- `f501cef8`: `docs: save implementation plans for EXC-04, RPG-01, and BOUNTY-01`
- `4b04b9cf`: `feat(bounty-01): sanitize selected investigation queries`
- `858c6b15`: `feat(bounty-01): search related article metadata locally`
- `7160f07f`: `feat(bounty-01): add investigation action beside quote chip`
- `df6daefd`: `feat(bounty-01): add local investigation state and BYOK context`
- `1bad29b6`: `feat(bounty-01): show radar results in reader drawer`
- `9a6c9b0a`: `feat(bounty-01): complete contextual local investigation agent`
