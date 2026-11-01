# BOUNTY-03 — Second Brain: zoom, pan và làm sáng node kề — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Ý tưởng gốc là một đồ thị mới kiểu Obsidian với node là bài viết và thực thể. EXC-08 đã có sẵn đồ thị khái niệm (Second Brain: chạm chọn node, thanh trượt thời gian, danh sách bài liên quan). Owner chọn **nâng cấp màn hình đó** thay vì làm đồ thị thứ hai.
- **Có:** pinch-zoom (1x-6x), kéo để pan, chạm đúp hoặc nút `Đặt lại khung nhìn` để reset; khi chọn node thì node kề và cạnh nối vẫn sáng, phần còn lại mờ đi; nhãn tên chủ đề trên đồ thị theo mức zoom, không chồng chữ, không đè lên node; bố cục dọc (đồ thị trên, thanh trượt giữa, danh sách bài dưới, luôn hiện cả hai).
- **Không có:** đồ thị bài viết hay thực thể thật, layout force-directed, dữ liệu mới, AI, mạng, thay đổi Room (DB vẫn v20), thư viện mới.
- Node vẫn là khái niệm do bộ phát hiện sẵn có và thẻ tóm tắt AI người dùng đã lưu, **không phải thực thể thật**. Layout vẫn là hình xoắn hoa hướng dương cố định nên **khoảng cách giữa hai node không có nghĩa là liên quan**; chỉ cạnh nối và vùng sáng mới có nghĩa.

## Cấu trúc mã (`ui/page/secondbrain/`)
- `GraphViewport.kt`: giá trị bất biến scale/offset, `zoomBy` giữ điểm dưới ngón tay cố định, `panBy` bị chặn để đồ thị không trôi ra khỏi khung, `toScreen`/`toUnit` là nghịch đảo chính xác. Thuần Kotlin.
- `GraphHighlight.kt`: tập node kề trực tiếp và cạnh đang hoạt động; bỏ qua vòng lặp tự thân.
- `GraphLabels.kt`: chọn nhãn (node chọn, rồi node kề, rồi node nặng nhất theo ngân sách 8 tới 24 tăng theo zoom), kẹp trong khung, bỏ nhãn chồng nhau, chừa khoảng cách bằng bán kính node (`gapFor`).
- `SecondBrainPage.kt`: bố cục `weight` 0,55/0,45, hai khối `pointerInput` riêng (transform và tap) để không triệt tiêu nhau, bán kính chạm 24dp quy đổi theo zoom, viewport nằm trong state Compose và tự reset khi đồ thị đổi.
- 4 chuỗi mới ở 38 locale (`second_brain_select_hint`, `second_brain_reset_view`, `second_brain_zoom_hint`, `second_brain_zoom_level`); EN và VI viết tay.
- Không đổi: `BrainGraphBuilder`, `SecondBrainSv`, `BrainGraphLayout.compute`, `SecondBrainViewModel`, route.

## Phát hiện khi làm
1. **Nhãn đè lên node:** lần smoke đầu trên Pixel cho thấy chữ vẽ xuyên qua chấm tròn vì khoảng cách cố định 6px nhỏ hơn bán kính node. Đã thêm tham số `gapFor` (bán kính node cộng đệm) và 2 test hồi quy; smoke lần hai xác nhận chữ nằm trên chấm.
2. `detectTapGestures` chỉ gọi `onTap` sau khi hết cửa sổ chạm đúp, nên test tap phải `advanceTimeBy` (cả Robolectric lẫn instrumentation); hai test tap cũ của EXC-08 cần thêm đúng một dòng này. Hành vi cho người dùng không đổi.
3. `size.minDimension` không có trên `IntSize` trong `pointerInput`; dùng `minOf(width, height)`.

## Kiểm chứng
- **Unit:** 1.672 test, 0 lỗi, 0 skip (nền trước task 1.640, thêm 32: `GraphViewportTest` 9, `GraphHighlightTest` 4, `GraphLabelsTest` 12, `SecondBrainViewportUiTest` 7). Test layout, builder, ViewModel, reducer của EXC-08 vẫn xanh. `StringResourceParityTest` 6/6.
- **Lint:** 0 lỗi, 2.454 cảnh báo (nền 2.453; không có cảnh báo nào nằm trong file hay chuỗi của task này khi tra báo cáo XML). **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** 12/12 (`SecondBrainPageWidgetTest` có thêm pinch thật rồi chạm đúp về 100%, và `SecondBrainPerformanceTest` 200 node dưới 1 giây).
- **Smoke thật trên Pixel (212 bài BBC):** Cài đặt -> Bộ não thứ hai: bố cục dọc, nhãn trên từng node, chạm `Artificial intelligence` thì node đó có viền, các node khác mờ, danh sách hiện 5 bài; chạm `Space aerospace` thì node kề `Defense geopolitics` và cạnh nối vẫn sáng, các node còn lại mờ, danh sách 4 bài; chạm bài đầu tiên mở đúng bài.

## Giới hạn đã biết
1. **Pinch trên máy thật chưa được thao tác tay.** `adb shell input` không tạo được cử chỉ hai ngón; pinch được kiểm bằng test Robolectric và instrumentation trên Pixel (chạy gesture thật qua test framework), còn trên máy chỉ kiểm chạm, chạm đúp và nút reset. Kéo pan chỉ có tác dụng sau khi zoom nên cũng chưa được thử bằng tay.
2. Kho thử chỉ có 7 khái niệm và 1 cạnh nối, nên cảnh "node kề sáng lên" chỉ thấy ở một cặp node; cảnh nhiều node kề và nhãn dày chưa được thấy trên dữ liệu thật.
3. Nhãn là heuristic (ngân sách và tránh chồng), không đảm bảo mọi node đều có nhãn.
4. Layout cố định, khoảng cách không mang nghĩa liên quan (xem Phạm vi).
5. Ads: khi mở app trong lúc smoke có hai lần `AdActivity` toàn màn hình; đã tự đóng bằng nút "Tiếp tục đến ứng dụng", không chạm nội dung quảng cáo.

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu cả Room và DataStore (không rỗng, `integrity_check` ok trên bản sao). Sau smoke khôi phục cả hai bằng `run-as sh -c 'cat > ...'`; `cmp` xác nhận Room và DataStore giống hệt bản sao lưu, `integrity_check` ok, 212 bài, package test đã gỡ.

## Commits trên nhánh `dev`
- `50832ea1`, `ed17d8c2` docs: thiết kế BOUNTY-03
- `39f5dfda` docs: kế hoạch BOUNTY-03
- `ba5b6f08` feat(bounty-03): add pure zoom and pan viewport for the graph
- `b98d33bf` feat(bounty-03): add neighbour highlight logic for the graph
- `2d3042b4` feat(bounty-03): choose non-overlapping node labels by zoom
- `5841d2e1` feat(bounty-03): add graph zoom strings across 38 locales
- `4a706ada` feat(bounty-03): zoom, pan and highlight neighbours on the second brain graph
- commit hoàn tất (kèm sửa khoảng cách nhãn, test instrumentation và tài liệu) nằm ngay sau các commit trên
