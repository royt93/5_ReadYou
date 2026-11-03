# FRONT-03b — Đường nối "từ chung trong tiêu đề" cho Ngân Hà: Design

**Ngày:** 2026-11-03 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Ngân Hà hiện chỉ nối hai bài khi chúng chung một khái niệm của `SemanticSearchEngine`. Trên 152 bài của Tecno kết quả là **5 đường**, nên ngân hà trông như một đám sao rời. Thêm một loại quan hệ thứ hai, dựa trên các **từ hiếm xuất hiện trong cả hai tiêu đề**, để các bài cùng sự kiện nối được với nhau, và thẻ sao cho người dùng biết *vì sao* hai bài được nối.

## Quyết định của owner (AskUserQuestion)
- Quan hệ mới: **từ chung trong tiêu đề** (không phải nguồn chung, không phải gần thời gian).
- Ngân sách: giữ **tối đa 400 đường**, xếp theo từ hiếm, **ghi từ chung trong thẻ sao**; không thêm kiểu nét vẽ.
- Độ chính xác: **cần ít nhất 2 từ chung**; chấp nhận ngân hà thưa hơn để không nối nhầm.

## Số đo thật (không phải dự đoán)
Đo trên bản sao DB Tecno (152 bài, 1 feed BBC) bằng `/tmp/galaxy-density*.py`, chỉ đọc:

| Quy tắc | Số cặp | Số đường sau giới hạn | Sao có đường |
|---|---|---|---|
| Hiện tại (khái niệm) | — | 5 | ít |
| 1 từ chung, mọi từ ≥ 4 ký tự | 495 | — | 139 |
| 1 từ chung, từ xuất hiện ≤ 8 bài | 320 | 160 | 136 |
| **≥ 2 từ chung, từ xuất hiện ≤ 8 bài (chọn)** | **51** | **45** | **56** |

- Quy tắc 1 từ cho đường **nối nhầm** ở cuối bảng: một từ như "police", "city", "trump", "russia" nối các bài khác chủ đề (ví dụ một bài cảnh sát Thổ Nhĩ Kỳ với một bài robot bị cảnh sát Đức bắt giữ). Vì vậy owner chọn ≥ 2 từ chung.
- Với ≥ 2 từ, cả đầu và cuối bảng đều là cùng sự kiện (Russell Brand xét xử, đười ươi thả về rừng Borneo, hổ tấn công công viên, Trump–Zelensky–Putin, Pope ung thư, Burnham–Man City).
- Giá phải trả: **96 sao vẫn rời rạc**. Đây là hệ quả có chủ đích của việc ưu tiên chính xác.
- Chi phí tính: quét mọi cặp của 152 bài mất khoảng 3 ms (Python); trường hợp xấu nhất 600 sao là 179.700 cặp, cần đo trên máy thật.
- Tổng số đường vẽ: 45 (từ) + 5 (khái niệm) ≈ 50, **thấp hơn nhiều** ngân sách 400, nên chi phí vẽ gần như không đổi so với hiện tại.

## Sự thật từ code (đã đọc)
- `GalaxyGraphBuilder.build(articles, from, to)` (thuần) nhận `GalaxyStar(id, title, feedName, date, concepts)`, lọc theo khoảng ngày, tối đa `MAX_STARS = 600`, nối theo khái niệm với `MAX_NEIGHBORS_PER_STAR = 3` và `MAX_LINES = 400`, trả `GalaxyGraph(stars, lines)`.
- `GalaxyLine(a, b, sharedConcepts)` chỉ biết **số** khái niệm chung, không biết là khái niệm nào.
- `GalaxyLayout` đặt vị trí một sao chỉ theo id và khái niệm chính của nó; không phụ thuộc đường nối.
- `SemanticSearchEngine.tokenize` đã có danh sách `STOP_WORDS` (44 từ), nhưng `tokenize` giữ từ ≥ 2 ký tự; Ngân Hà cần ngưỡng riêng (≥ 4) nên không dùng thẳng `tokenize` mà dùng chung danh sách stop-word.
- Thẻ sao hiện (`GalaxyStarSheet`) có tiêu đề, nguồn, ngày và nút "Mở bài"; `GalaxyUiState.Ready` có `selectedStarId`.
- Room v20, không đổi.

## Thiết kế
### Dữ liệu và thuật toán (thuần, test trên JVM)
- Mở rộng `GalaxyLine` thêm trường **`sharedTerms: List<String>`** (mặc định rỗng) và **`kind`** (`CONCEPT` hoặc `TITLE_WORDS`) để biết lý do nối. Đường cũ giữ `kind = CONCEPT`.
- Thêm `TitleWords` (object thuần): `extract(title): Set<String>`: chữ thường theo `Locale.ROOT`, chỉ chữ/số Unicode, **độ dài ≥ `MIN_WORD_CHARS = 4`**, bỏ stop-word của app, **bỏ token toàn chữ số**.
- `GalaxyGraphBuilder`:
  1. Tính tần suất tài liệu (`df`) của mỗi từ trên tập sao đã lọc ngày.
  2. Từ "hiếm" là từ có `df ≤ MAX_WORD_DOC_FREQ = 8`.
  3. Ứng viên là cặp có **≥ `MIN_SHARED_TERMS = 2`** từ hiếm chung. Điểm = tổng `1 / df` của các từ chung; xếp điểm giảm dần, rồi theo id để ổn định.
  4. Giữ cặp theo thứ tự đó, mỗi sao **tối đa `MAX_NEIGHBORS_PER_STAR` đường từ-tiêu-đề**, tổng đường (cả hai loại) **≤ `MAX_LINES`**.
  5. Một cặp chỉ có **một** đường. Nếu cặp đã nối theo khái niệm và cũng chung ≥ 2 từ hiếm, đường đó giữ `kind = CONCEPT` nhưng vẫn điền `sharedTerms`, để thẻ sao hiển thị từ chung. Đường khái niệm không tính vào giới hạn láng giềng của đường từ-tiêu-đề.
- Không đụng `GalaxyLayout` (vị trí vẫn theo khái niệm/id), nên đọc thêm bài không xáo trộn ngân hà.
- Độ phức tạp: O(n²) theo số sao với n ≤ 600 (179.700 cặp), dùng chỉ mục ngược (từ → danh sách sao) để chỉ duyệt các cặp thực sự chung từ, tránh quét mù.

### Giao diện
- Khi người dùng chọn một sao, `GalaxyStarSheet` thêm một dòng **"Chung từ: trump, zelensky"** cho mỗi sao láng giềng của nó (tối đa 3 dòng, mỗi dòng có tiêu đề bài kia và từ chung). Chạm một dòng đặt lại sao được chọn sang bài kia (không mở bài, giữ thao tác "Mở bài" riêng).
- Đường vẽ nối hai bài **không đổi kiểu nét** (đã loại ở quyết định của owner), nên tooltip này là nơi duy nhất nói lý do.
- Sao không có láng giềng: thẻ không hiện mục này (không hiện dòng rỗng).
- Chuỗi mới ở 38 locale (EN và VI viết tay, còn lại tiếng Anh): nhãn "Chung từ: %1$s" (không dùng dạng số + từ để khỏi dính lint `PluralsCandidate`) và tiêu đề mục "Bài liên quan". Mọi chuỗi phải được dùng (lint `UnusedResources`).
- Giữ nguyên nút Back ở action bar, inset edge-to-edge và Material You đã có ở `SecondBrainPage`; thẻ sao đã cuộn được để thêm mục không làm tràn màn hình thấp.

### Trạng thái và lỗi
Không thêm trạng thái mới. Không có từ chung nào thì không có đường mới; kết quả không đổi so với hiện tại. Tiêu đề rỗng hoặc chỉ stop-word cho tập từ rỗng, không gây lỗi.

## Giới hạn nói thẳng
1. **Chỉ là trùng từ, không phải hiểu nghĩa.** Hai tiêu đề chung 2 từ hiếm gần như chắc chắn cùng chủ đề, nhưng không đảm bảo; một số cặp cùng sự kiện dùng từ khác nhau sẽ không được nối.
2. **Chỉ hoạt động tốt với tiêu đề cùng ngôn ngữ.** Từ khoá theo dạng chữ, không dịch, không gốc từ: "released" và "release" là hai từ khác nhau (trong số đo ở trên, "release" và "released" xuất hiện riêng).
3. **Ngân hà vẫn thưa**: 56 trong 152 sao có đường. Đây là cái giá của ≥ 2 từ chung.
4. Số đo lấy từ **một nguồn** (BBC, 152 bài). Với nhiều nguồn và nhiều ngôn ngữ, tỉ lệ nối sẽ khác; chưa đo.
5. Ngưỡng `MAX_WORD_DOC_FREQ = 8` tính trên tập hiện hiển thị: khi người dùng thu hẹp khoảng thời gian, từ "phổ biến" đổi theo; đường nối có thể xuất hiện hoặc biến mất theo thanh thời gian.
6. Tiếng Việt chưa kiểm: ngưỡng "≥ 4 ký tự" hợp với tiếng Anh, nhưng nhiều từ tiếng Việt ngắn, nên nối tiêu đề tiếng Việt có thể rất thưa; chưa đo.
7. Hiệu năng trên A50s (giật khi kéo) **chưa giải quyết** ở task này; task này chỉ thêm khoảng 45 đường nên không đổi đáng kể chi phí vẽ, nhưng sẽ đo lại bằng `dumpsys gfxinfo` trước/sau trên Tecno và báo số thật. A50s đang offline.

## Kiểm thử
- Thuần: `TitleWordsTest` (độ dài ≥ 4, stop-word, số, Unicode/tiếng Việt, viết hoa, dấu câu, rỗng), `GalaxyGraphBuilderTest` mở rộng (đúng 2 từ chung mới nối; 1 từ chung không nối; từ phổ biến `df > 8` bị bỏ; xếp hạng theo từ hiếm; tối đa 3 đường từ-tiêu-đề mỗi sao; tổng ≤ `MAX_LINES`; cặp đã nối bằng khái niệm không trùng đường; ổn định theo thứ tự đầu vào; `GalaxyLine.sharedTerms` đúng từ và sắp xếp).
- Hồi quy mutation-check: ngưỡng `MIN_SHARED_TERMS`, ngưỡng `df`, giới hạn láng giềng, chống trùng đường.
- ViewModel/Sv: `GalaxySvTest` kiểm `GalaxyStar.title` đi qua; không đổi `GalaxyViewModel`.
- Compose/Robolectric: thẻ sao hiện "Chung từ" cho láng giềng, không hiện khi sao rời, chạm dòng đổi sao được chọn; `GalaxyPageTest` cũ vẫn xanh.
- Instrumentation trên Tecno `115333744A005844`; smoke thật trên Tecno với **bản sao** DB đánh dấu 152 bài đã đọc, so số đường trước/sau, đo `dumpsys gfxinfo`, khôi phục DB gốc từ `/tmp/exc13-tecno-backup`, `cmp` cả 4 file, không xoá DataStore, đóng quảng cáo toàn màn hình chỉ bằng nút có nhãn từ dump mới.

## Ngoài phạm vi
Nối theo nguồn hoặc thời gian, nét vẽ phân biệt, gốc từ/lemmatization, dịch, AI, mạng, đổi Room, tối ưu hiệu năng A50s, đổi `GalaxyLayout`.
