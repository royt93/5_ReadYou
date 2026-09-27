# [REEL-01] Giao Diện Thẻ Lướt Dọc "News Reels" Dạng TikTok / Instagram Story

- **Type:** UI/UX Innovation / Gen Z Engagement
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [09. REEL — Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)](09_VISUAL_REELS_AND_MEDIA.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/reels/` (Gói mới)

## Vấn đề thực tế
Rất nhiều độc giả trẻ tuổi cảm thấy danh sách bài viết truyền thống (dạng list đơn điệu) gây nhàm chán. Họ thích lướt xem các thẻ tóm tắt thị giác với ảnh lớn toàn màn hình, vuốt dọc để chuyển tin tức như TikTok hoặc Instagram Reels.

**Xác nhận qua audit code (2026-09-06):** không tồn tại package `ui/page/reels/` hoặc bất kỳ file nào liên quan đến "News Reels" trong toàn bộ repo (`find app/src/main -iname "*reel*"` không trả về kết quả nào ngoài file task doc này). Task **chưa được implement**.

## User Story
> Là người thích xem tin tức dạng thị giác nhanh,
> Tôi muốn chuyển sang chế độ "News Reels" toàn màn hình,
> Để tôi chỉ cần vuốt dọc lên để lướt qua các tin tức nóng nhất kèm ảnh nền đẹp và 2 câu tóm tắt cốt lõi.

## Acceptance Criteria (Gherkin)
- **Given** người dùng chọn chế độ xem "Reels" trên thanh điều hướng BottomBar
- **When** màn hình hiển thị thẻ tin toàn màn hình với ảnh bìa chất lượng cao làm nền gradient
- **Then** hiển thị tiêu đề in đậm, tên nguồn báo, thời gian và 3 gạch đầu dòng tóm tắt chính của bài
- **And** vuốt dọc lên để chuyển sang tin kế tiếp với hiệu ứng chuyển trang 3D mượt mà
- **And** vuốt sang phải để mở toàn văn bài báo đầy đủ.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [REEL-01] "Giao Diện Thẻ Lướt Dọc News Reels Dạng TikTok / Instagram Story" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/REEL-01_news-reels-tiktok-scroll.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [REEL-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [REEL-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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

## ✅ Completion Report (2026-09-27)

**Điểm audit: 9.3/10** — Acceptance Criteria thỏa mãn 100%:
- ✅ Lối vào Reels: icon `PlayCircle` trên top bar `FlowPage` + mục "Tin ngắn" trên `FilterBar` ở cả `FlowPage` và `FeedsPage`. Đề bài ghi "BottomBar" nhưng app **không có bottom nav cấp app** — `FilterBar` (NavigationBar bộ lọc) là thứ gần nhất, đã làm cả hai lối vào.
- ✅ Thẻ toàn màn hình: ảnh bìa qua `BaseAsyncImage` + scrim gradient 3 điểm dừng đảm bảo tương phản chữ trắng trên mọi ảnh.
- ✅ Tiêu đề in đậm (`headlineSmall` + `FontWeight.Bold`), tên nguồn, thời gian tương đối (`DateUtils.getRelativeTimeSpanString`), đúng 3 bullet tóm tắt (`ReelsViewModel.BULLET_COUNT`) sinh **offline** bằng `ArticleHighlightsExtractor` — không cần API key, không gọi mạng.
- ✅ Vuốt dọc chuyển tin với hiệu ứng 3D: `VerticalPager` + `graphicsLayer { rotationX/scaleX/scaleY/alpha/cameraDistance }` theo offset trang.
- ✅ Vuốt phải mở toàn văn: `detectHorizontalDragGestures` với ngưỡng dp cố định (`OPEN_ARTICLE_THRESHOLD`), cùng khoảng cách vật lý trên mọi mật độ màn hình.
- −0.7 vì `ReelsViewModel` nạp cố định `RECENT_ARTICLE_LIMIT` bài gần nhất, chưa phân trang — đủ cho lướt tin nhưng sẽ cần `Paging3` nếu người dùng lướt quá giới hạn.

**Bug thật tìm được và đã sửa trong quá trình làm:**
1. `FeedsPage.filterChange()` bỏ qua nhánh Reels khi `isNavigate = false` — call site từ `FilterBar` (bottomBar) luôn truyền `false`, khiến bấm "Tin ngắn" ở trang Feeds **không có phản ứng gì**. Phát hiện qua smoke test thật, không phải qua test tự động.
2. Nút Đóng trên `ReelsPage` **không bấm được**: `FeedbackIconButton` forward `modifier` vào `Icon` bên trong chứ không vào `IconButton` bọc ngoài, nên `statusBarsPadding()` truyền thẳng chỉ dịch hình icon còn vùng chạm vẫn nằm ở `[0,0]` — dưới status bar của window edge-to-edge, mọi tap bị nuốt. Sửa bằng `Box` wrapper mang inset.

**Tests thêm:**
- Unit (`ReelsViewModelTest`, Robolectric + MockK): map article → `ReelItem`, đúng 3 bullet, strip HTML, `hasVideo`, ưu tiên `fullContent` over `rawDescription`, empty state, bài không ảnh/không body.
- Widget (`ReelsCardWidgetTest`, Pixel 7 Pro): render có/không ảnh bìa, không bullet, có huy hiệu video, tiêu đề dài.
- Widget (`ReelsCloseButtonTest`, Pixel 7 Pro): đo vị trí thật bằng `onGloballyPositioned` + `boundsInWindow()` đối chiếu `WindowInsetsCompat.Type.statusBars()` — chứng minh nút Đóng nằm dưới status bar, hồi quy cho bug #2.
- Integration (`ReelsAndVideoIntegrationTest`, Room thật trên Pixel 7 Pro): insert account/group/feed/article → `toReelItem` → render `ReelsPager` thật.

**Smoke test (Pixel 7 Pro `2B051FDH3006MU`, 2026-09-27):**
- Dữ liệu thật: 27 bài BBC News người dùng tự thêm qua UI + 3 bài mẫu.
- Mở Reels từ cả hai lối vào; card hiện đúng ảnh, nguồn, thời gian tương đối, 3 bullet, huy hiệu video.
- Vuốt dọc chuyển bài mượt, không giật, không crash; vuốt phải mở đúng bài; nút Đóng quay lại đúng.
- Logcat sạch: không fatal exception, không leak warning.
- Ghi nhận Rule R4: trong lúc test gặp "Quảng cáo thử nghiệm" (AdMob test ad, che toàn màn hình khi mở lại app) → dừng, báo user, chờ xác nhận mới tiếp tục.

**Giới hạn đã biết:**
- Chưa phân trang; nạp `RECENT_ARTICLE_LIMIT` bài gần nhất.
- Bullet sinh bằng extractive offline, không phải abstractive AI — đổi lại là chạy được không cần mạng và không tốn API key.
