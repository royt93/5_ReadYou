# FRONT-03b — Đường nối "từ chung trong tiêu đề" cho Ngân Hà — DONE

**Hoàn tất:** 2026-11-03 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Ngân Hà (FRONT-03) chỉ nối hai bài khi chung một khái niệm của `SemanticSearchEngine`, nên trên 152 bài của Tecno chỉ có **5 đường**. Bản này thêm **một quan hệ thứ hai** theo quyết định của owner:
- **Từ chung trong tiêu đề:** hai bài được nối khi tiêu đề cùng có **ít nhất 2 từ hiếm** (từ >= 4 ký tự, bỏ stop-word và token toàn chữ số, chỉ từ xuất hiện trong <= 8 bài của tập đang xem). Điểm cặp = tổng `1 / số bài chứa từ`, mỗi sao tối đa 3 đường loại này, tổng đường vẫn <= 400.
- **Không đổi nét vẽ** (owner chọn không thêm kiểu nét). Lý do nối được ghi trên **thẻ sao**: mục "Bài liên quan" liệt kê tối đa 3 sao láng giềng với dòng "Chung từ: a, b"; chạm một hàng để chuyển sang chọn sao đó (không mở bài), nút "Mở bài" vẫn riêng. Sao rời thì thẻ không hiện mục này.
- Không đổi `GalaxyLayout`, Room (vẫn v20), không AI/mạng/dependency mới. 2 chuỗi mới ở 38 locale.

## Số đo thật (Tecno KJ7 `115333744A005844`, bản sao DB đánh dấu cả 152 bài đã đọc)
| | Trước | Sau lần đầu | Sau khi sửa từ chức năng |
|---|---|---|---|
| Đường nối | **5** | **48** | **43** |
| Sao có đường (mô phỏng) | ít | 56/152 | 45/152 |
| Khung giật (3 lượt kéo) | 2,65% / 0,41% / 0,54% | 4,41% / 0,55% / 0,96% | 1,69% / 0,68% / 0,55% |
| p90 | 18 / 11 / 10 ms | 21 / 11 / 12 ms | 16 / 11 / 11 ms |
| p99 | 30 / 19 / 15 ms | 34 / 25 / 24 ms | 27 / 20 / 19 ms |
- Thêm khoảng 38 đường nối **không làm khung hình xấu đi rõ ràng** trên Tecno: ba lượt đều nằm trong dao động của chính số "trước" (lượt đầu của mỗi nhóm luôn tệ nhất vì máy vừa mở Ngân Hà). Đây là 3 lượt mỗi nhóm trên **một máy**, không phải kết luận thống kê.
- **A50s không đo được** (đang offline); số giật 65–71% của A50s ở FRONT-03 vẫn chưa được giải quyết và không so sánh được với Tecno.

## Lỗi thật tìm thấy bằng smoke trên máy
Lần đầu cài, thẻ của "Why does Prince Harry want…" hiện bài "Why does Elon Musk want…" với **"Chung từ: does, want"**: hai từ chức năng nối hai chuyện không liên quan. Mô phỏng Python trước đó (cả đầu và cuối bảng) không bắt được vì chỉ nhìn **tiêu đề đầu và cuối bảng**, không in toàn bộ từ chung của 49 cặp. Sau khi đã in toàn bộ, tôi thấy thêm các từ "against", "inside", "third", "leave", "weekend", "back". Đã thêm một danh sách từ chức năng vào `TitleWords`, một test hồi quy `dropsQuestionAndAuxiliaryWordsThatLinkUnrelatedHeadlines` (tái hiện đúng lỗi, đỏ trước khi sửa, đã mutation-check) và kiểm lại trên máy: số đường từ 48 xuống 43, không còn cặp "does, want".
Còn lại sau sửa nhưng **vẫn là nối yếu**: "front, line" nối "BBC joins police on front line against car theft" với "Russian strikes leave 600,000 without power in Ukraine… front line". Hai bài cùng có cụm "front line" (một bài về cảnh sát, một về chiến tuyến), nên đây là **nối đúng từ nhưng sai nghĩa**, đúng như giới hạn "trùng từ không phải hiểu nghĩa" bên dưới.

## Cấu trúc mã
- `domain/model/galaxy/TitleWords` (thuần): tách từ, ngưỡng độ dài, stop-word riêng cho tiêu đề (`StopWordsTest` đảm bảo danh sách này chứa mọi stop-word >= 4 ký tự của `SemanticSearchEngine`, nên `STOP_WORDS` được mở `internal`).
- `GalaxyLine` thêm `kind: GalaxyLineKind` và `sharedTerms: List<String>`, **cả hai có mặc định** nên mọi lời gọi ba tham số cũ vẫn dịch và vẫn bằng nhau; không phải sửa test cũ.
- `GalaxyGraphBuilder`: chỉ mục ngược từ -> sao chỉ cho từ hiếm (mỗi danh sách <= 8 sao nên số cặp nhỏ), điểm `sum(1/df)`, một cặp một đường (cặp đã nối theo khái niệm giữ `kind = CONCEPT` nhưng được điền `sharedTerms`), giữ thứ tự xếp hạng khi cắt `MAX_LINES`.
- `GalaxyNeighbours.of(graph, starId, limit)` (thuần) cho thẻ sao; `GalaxyStarSheet` thêm `neighbours`/`onSelectNeighbour` ở cuối với mặc định; `GalaxyPanel` nối `GalaxyNeighbours` với `onSelectStar`. Thẻ cuộn được (tối đa 280dp) để màn hình thấp, edge-to-edge không bị tràn.

## Kiểm chứng
- **Unit:** 2.091 test, 0 lỗi, 0 skip (nền trước task 2.052). Thêm `TitleWordsTest` 10, `StopWordsTest` 2, `GalaxyTitleLinksTest` 13, `GalaxyNeighboursTest` 8, `GalaxyNeighbourCardTest` 5, `GalaxyPageTest` +1. Bộ galaxy chạy 3 lần liên tiếp, 99/99 mỗi lần, không chập chờn.
- **Lint:** 0 lỗi, 2.463 cảnh báo (không đổi), không cảnh báo nào của galaxy. **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Instrumentation** `KnowledgeGalaxyDeviceTest` 3/3 trên Tecno bằng `am instrument -e class ...` (không dùng `connectedDevDebugAndroidTest`).
- **Mutation-check:** ngưỡng 2 từ, ngưỡng `df` (cả `+1` và `<` ở biên), điểm theo độ hiếm, giới hạn láng giềng, đường trùng cho cặp khái niệm, mất `sharedTerms`, sắp xếp từ chung, lọc đường không có từ, giới hạn láng giềng, hai đầu đường, `limit` không dương, mục luôn hiện, hàng mở bài thay vì chọn, quên nối callback ở panel, chỉ hiện một từ, từ chức năng. **Bốn đột biến không bị bắt ngay lần đầu** và tôi sửa test rồi chạy lại tới khi đỏ: (1) test xếp hạng theo độ hiếm ban đầu dùng hai cặp mà cặp hiếm cũng có nhiều từ hơn nên không phân biệt được "điểm theo hiếm" với "đếm từ"; đã đổi thành cặp 3 từ phổ biến so với cặp 2 từ hiếm; (2) thứ tự xếp hạng bị `sortedWith` cuối cùng theo id phá mất khiến đường mạnh nhất không đứng đầu (một lỗi thật của mã, không chỉ của test), đã sửa mã để giữ thứ tự; (3) hai đột biến bị lỗi biên dịch/regex do tôi viết sai mẫu đột biến, đã viết lại cho hợp lệ về kiểu.
- **Smoke thật trên Tecno** với bản sao DB 152 bài đã đọc: thấy 48 rồi 43 đường; chạm một sao có láng giềng thì thẻ hiện "Related articles" và "Shared words: front, line"; chạm hàng đó thì **sao được chọn đổi sang bài kia** (vẫn ở Ngân Hà, không mở bài); chạm một sao rời thì thẻ chỉ có tiêu đề, nguồn, ngày và "Open article".

## Giới hạn nói thẳng
1. **Chỉ là trùng từ, không hiểu nghĩa.** Hai tiêu đề chung 2 từ hiếm gần như chắc chắn cùng chủ đề, nhưng không bảo đảm (ví dụ "front line" ở trên).
2. **Danh sách từ chức năng viết tay cho tiếng Anh**, rút ra từ một kho BBC 152 bài. Kho khác hoặc ngôn ngữ khác sẽ có từ khác lọt qua; chưa đo.
3. **Ngân hà vẫn thưa:** 43 đường, chỉ khoảng 45 trên 152 sao có đường; **~107 sao vẫn rời rạc**. Đây là cái giá của việc chọn "chính xác hơn, chấp nhận thưa".
4. Số đo lấy từ **một nguồn** (BBC). Nhiều nguồn và nhiều ngôn ngữ chưa đo.
5. Ngưỡng độ hiếm tính trên tập đang hiển thị: kéo thanh thời gian làm một từ "phổ biến" thành "hiếm" hoặc ngược lại, nên đường nối có thể xuất hiện hay biến mất.
6. **Tiếng Việt chưa kiểm:** ngưỡng 4 ký tự hợp tiếng Anh nhưng nhiều từ tiếng Việt ngắn hơn, nên tiêu đề tiếng Việt có thể rất thưa; chưa đo.
7. Hiệu năng trên A50s chưa giải quyết (xem trên).
8. Đường nối vẫn **cùng một kiểu nét**; lý do nối chỉ thấy khi chọn một sao.

## Dữ liệu máy
Tecno: sao lưu mới Room (3 file) và `settings.preferences_pb` (194 byte) trước khi nạp bản sao đánh dấu 152 bài đã đọc (chỉ bản sao, `integrity_check` ok); sau smoke nạp lại bản gốc và `cmp` **giống hệt cả 4 file**, `integrity_check` ok, 152 bài/1 feed/9 đã đọc, không còn gói `com.mckimquyen.reader.test`. Không bao giờ xoá DataStore. Quảng cáo toàn màn hình xuất hiện mỗi lần mở app, tự đóng hoặc đóng bằng nút có nhãn từ dump mới.
