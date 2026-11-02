# FRONT-03 — Ngân Hà Tri Thức (Knowledge Galaxy): Design

**Ngày:** 2026-11-02 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Cho người dùng nhìn toàn bộ các bài đã đọc như một dải ngân hà: mỗi bài là một ngôi sao, các bài có chung khái niệm được nối bằng đường mảnh, xoay và thu phóng được, chạm một sao để mở lại bài.

## Vì sao khác todo gốc
Todo `FRONT-03_3d-knowledge-galaxy.md` mô tả một tab "Vũ Trụ Tri Thức" riêng. **Second Brain (BOUNTY-03)** đã có sẵn màn hình đồ thị với dữ liệu khái niệm, zoom/pan, chạm nút, thanh thời gian (`SecondBrainPage`, `SecondBrainSv`, `BrainGraphBuilder`, `GraphViewport`). Hai màn hình song song gần giống nhau dễ gây bối rối và phải bảo trì hai nơi. Khác biệt thật chỉ là: nút là **bài viết** (không phải khái niệm) và có xoay 3D. Owner chọn: **thêm "Ngân Hà" làm chế độ xem thứ hai trong trang Second Brain**, không tạo tab riêng.

## Quyết định của owner (AskUserQuestion)
- FRONT-03 là **chế độ xem thứ hai trong Second Brain**, dùng chung dữ liệu khái niệm và thanh thời gian.

## Sự thật từ code (đã đọc)
- `SecondBrainSv.load(accountId, from, to)` lấy tối đa 500 bài mới nhất, đổi mỗi bài thành `BrainArticle(id, title, date, concepts)` bằng `SemanticSearchEngine.detectConceptsStrict` + tag trong `aiSummary`, rồi `BrainGraphBuilder.build` gom thành tối đa 200 **khái niệm** và 300 cạnh. Đồ thị này **không giữ danh sách bài**, nên Ngân Hà cần dựng riêng từ danh sách bài.
- `GraphViewport` là giá trị thuần (scale 1..6, pan, kẹp biên); `BrainGraphLayout` bố trí "hướng dương" thuần; `SecondBrainUiState`/`SecondBrainStateReducer` thuần.
- `ArticleListItem` có `isUnread`, `readAt`; truy vấn danh sách phải dùng `ARTICLE_LIST_COLUMNS` (FIX-17: không đọc cột body).
- Room v20, không đổi.

## Thiết kế
### Dữ liệu
- Thêm `ArticleDao.queryReadArticleListWithFeed(accountId, limit)` chỉ cột metadata (`ARTICLE_LIST_COLUMNS`), `isUnread = 0`, `ORDER BY date DESC, id DESC`, `LIMIT :limit`.
- `GalaxyStar(id, title, feedName, date, concepts)`; `GalaxyLine(a, b, sharedConcepts)`; `GalaxyGraph(stars, lines)`.
- `GalaxyGraphBuilder.build(articles, from, to)` (thuần): lọc theo khoảng ngày, đặt trần `MAX_STARS = 600` (bài mới nhất), cạnh khi hai bài chung **ít nhất một** khái niệm, xếp theo số khái niệm chung rồi theo id, giữ `MAX_LINES = 400`. Một khái niệm có quá nhiều bài chỉ nối từng bài với `MAX_NEIGHBORS_PER_STAR` láng giềng gần nhất theo ngày để không sinh bùng nổ O(n²).
- Khái niệm lấy từ `SemanticSearchEngine.detectConceptsStrict` như Second Brain (offline, không AI).

### Dựng 3D (không dùng GPU)
- `GalaxyLayout.compute(stars)`: mỗi sao một toạ độ `(x, y, z)` cố định theo id (deterministic), phân bố trên đĩa xoắn ốc, `z` nhỏ trong `±GALAXY_THICKNESS`; sao cùng khái niệm chính gần nhau.
- `GalaxyCamera` (giá trị thuần): `yaw`, `pitch` (kẹp), `zoom` (kẹp); `project(point, width, height)` trả vị trí màn hình, độ sâu và cỡ theo phối cảnh; `rotateBy(dx, dy)`, `zoomBy(factor)`. Vẽ sao xa trước sao gần.
- Vẽ bằng Compose `Canvas`. Đây là **chiếu phối cảnh 2D, không phải engine 3D GPU**; không OpenGL, không cảm biến.
- Độ sáng nền và độ dày dải tăng theo số sao với trần, để "càng nhiều sao càng rực rỡ".

### Tương tác
- Kéo một ngón: xoay; hai ngón: thu phóng; chạm một sao (`hitTest` theo toạ độ đã chiếu, sao gần nhất trong bán kính): mở sheet nhỏ với tiêu đề, nguồn, ngày và nút "Mở bài" (điều hướng `"${RouteName.READING}/$id"`).
- Nút chuyển "Khái niệm | Ngân Hà" trong `SecondBrainPage`; Ngân Hà dùng chung thanh thời gian (`from`..`to`).
- Nút "Đặt lại góc nhìn" đưa camera về mặc định.

### Thành phần
- `domain/model/galaxy/`: `GalaxyStar`, `GalaxyLine`, `GalaxyGraph`, `GalaxyGraphBuilder`, `GalaxyLayout`, `GalaxyCamera` (toàn bộ thuần Kotlin).
- `domain/sv/GalaxySv`: truy vấn bài đã đọc trên IO, chuyển thành `GalaxyStar` trên Default.
- `ui/page/secondbrain/galaxy/GalaxyViewModel`, `GalaxyCanvas`, `GalaxyStarSheet`; sửa `SecondBrainPage` để thêm nút chuyển chế độ.
- Chuỗi mới ở 38 locale (EN và VI viết tay, còn lại tiếng Anh).

### Trạng thái và lỗi
- `Loading | Empty | Error | Ready`. Empty: chưa đọc bài nào hoặc không bài nào trong khoảng ngày đã chọn, kèm lời nhắc. Mọi I/O ngoài Main; huỷ khi rời màn hình; không giữ `Context` hay `Activity`.

## Giới hạn nói thẳng
1. Phối cảnh trên Canvas, không phải 3D GPU; số sao tối đa 600 và 400 đường để giữ mượt. Mức mượt thật phải **đo trên Tecno** (số ms mỗi khung hình), không khẳng định trước.
2. Quan hệ giữa bài chỉ dựa trên bộ khái niệm của `SemanticSearchEngine`; nhiều bài không có cạnh nào, ngân hà có thể thưa với kho chủ đề lạ.
3. Chỉ tính bài đã đánh dấu đọc (`isUnread = 0`); kho ít bài đã đọc sẽ ít sao.
4. Không có OpenGL, không gyro, không VR, không lưu bố cục (tính lại mỗi lần).
5. Hiệu ứng "càng nhiều sao càng rực rỡ" chỉ là độ sáng và độ dày có trần.

## Kiểm thử
- Unit thuần: `GalaxyGraphBuilderTest` (cạnh theo khái niệm chung, trần sao/đường, lọc ngày, rỗng, một bài, khái niệm quá phổ biến), `GalaxyLayoutTest` (ổn định theo id, trong biên, không NaN), `GalaxyCameraTest` (xoay, kẹp pitch/zoom, chiếu, sắp sao theo độ sâu, `hitTest`).
- Room integration (in-memory): truy vấn chỉ trả bài đã đọc, đúng thứ tự và trần, chỉ cột metadata.
- ViewModel (tiêm dispatcher thử), Compose/Robolectric (nút chuyển chế độ, sheet khi chọn sao, trạng thái Empty), instrumentation trên Tecno `115333744A005844`.
- Smoke Tecno: backup Room (3 file) và DataStore, mở Second Brain → Ngân Hà, xoay/thu phóng/chạm một sao, mở bài, **đo thời gian khung hình**; Tecno khoá màn hình thì nhờ owner mở; đóng quảng cáo bằng nút có nhãn từ dump mới, thử lại khoảng một phút, không chạm creative, khôi phục và `cmp`.

## Ngoài phạm vi
Tab "Vũ Trụ Tri Thức" riêng, OpenGL/Vulkan, cảm biến chuyển động, chia sẻ ảnh ngân hà, AI, API key, quảng cáo, IAP, đổi Room.
