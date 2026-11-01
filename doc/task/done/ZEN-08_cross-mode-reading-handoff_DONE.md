# ZEN-08 — Chuyển chế độ đọc giữ nguyên vị trí (Cross-mode Reading Handoff) — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Trước task này: RSVP luôn bắt đầu từ từ đầu tiên, TTS luôn đọc từ đầu bài, đóng RSVP không làm trang đọc cuộn theo. Todo gốc còn nói "chưa có cơ chế lưu vị trí", nhưng thực tế đã có `ArticleReadPosition` + DAO + đồng bộ WebDAV, nên task này **tái dùng** chúng.
- **Có:** đọc thường → RSVP bắt đầu ở từ tương ứng vị trí đang đọc; đóng RSVP (đã chạy ≥ 20 từ) → trang đọc tự cuộn tới vị trí RSVP dừng và lưu `progress`; đọc thường → TTS đọc từ đầu đoạn gần nhất với vị trí đang đọc.
- **Không có:** ghi ngược tiến độ nghe TTS (TTS không báo tiến độ; owner chọn không sửa `TtsManager`), chính xác tới câu, cột vị trí theo ký tự, migration Room (vẫn v20), chuỗi giao diện mới (không đụng 38 locale), AI, mạng, API key, quảng cáo, mua hàng.
- Vị trí gần 0–2% hoặc từ 98% trở lên coi như "chưa đọc / đã đọc hết": RSVP và TTS bắt đầu từ đầu.

## Cấu trúc mã
- `domain/readposition/ReadingPositionMapper` (thuần Kotlin): `bodyFractionFromList`, `scrollTargetFromBodyFraction` (trả `BodyScrollTarget(listIndex, withinItem)`), `listFractionFromBodyFraction`, `tokenIndexFromFraction`, `fractionFromTokenIndex`, `paragraphStartOffset`, `shouldHandoffFromRsvp` và các hằng số có tên (`RESTART_BELOW_FRACTION` 0,02, `TREAT_AS_FINISHED_FRACTION` 0,98, `MIN_RSVP_WORDS_TO_SCROLL` 20).
- `RsvpViewModel.loadContent(content, startFraction = 0f)` và `RsvpReaderDialog(startFraction)`.
- `ReadingViewModel.togglePlayAudio(startFraction = 0f)`: sau khi đổi HTML sang văn bản thuần (vẫn trên `defaultDispatcher`) cắt từ đầu đoạn gần nhất rồi gọi `TtsManager.play` không đổi.
- `Content(bodyRange)`: `BodyItemRange` dùng chung được nâng từ `Content` lên `ReadingPage`; `ReadingPage` tính vị trí, mở RSVP với `startFraction`, xử lý đóng RSVP và truyền vị trí cho TTS.
- Không đổi `TtsManager`, `RsvpTokenizer`, `HtmlToComposable`, Room, WebDAV.

## Phát hiện khi làm
1. **Thân bài toàn chữ chỉ là MỘT item `LazyColumn`:** `htmlFormattedText` gộp các đoạn văn liền nhau vào một item (chỉ ảnh, code, danh sách, bảng mới cắt item). Test tích hợp báo `bodyRange.count == 1` thay vì 12, nên quy đổi theo chỉ số item không phân biệt được 5% với 90%. Owner chọn thêm **độ lệch cuộn trong item** (`firstVisibleItemScrollOffset / kích thước item`); spec và plan đã sửa kèm theo (`a6c2e4c4`).
2. Bản `progress` lưu bền vẫn theo chỉ số item toàn danh sách (giữ hợp đồng WebDAV), nên bản ghi lưu thô hơn vị trí trong phiên.
3. Test `ReadingViewModel` mới truyền sẵn `defaultDispatcher = dispatcher` (bài học từ lỗi chập chờn của ECHO-02); bộ test đầy đủ chạy sạch ở lần này.

## Kiểm chứng
- **Unit:** 1.799 test, 0 lỗi, 0 skip (nền trước task 1.782, thêm 17): `ReadingPositionMapperTest` 11, `ReadingViewModelPositionHandoffTest` 3, `RsvpViewModelTest` +2, `ReadingPositionHandoffTest` 1 (Compose/Robolectric).
- **Lint:** 0 lỗi, 0 fatal, 2.463 cảnh báo (không đổi). **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** `ReadingPositionHandoffDeviceTest` 1/1 (`am instrument`, không dùng `connectedDevDebugAndroidTest`).
- **Smoke thật trên Pixel** (bài BBC dài nhất kho, ~7,6 nghìn ký tự, 826 từ RSVP):
  - Cuộn giữa bài rồi mở RSVP: thanh hiện `375 / 826 từ` (khoảng 45%), từ đầu tiên là "Colossus", đúng đoạn đang nằm trong màn hình; không bắt đầu từ đầu bài.
  - Chạy RSVP tới `418 / 826` (43 từ, qua ngưỡng 20) rồi đóng: trang đọc nằm ở đoạn phía sau chỗ trước khi mở RSVP (từ vùng "Stargate" sang vùng "Meta's Hyperion"), tức đã cuộn theo.
  - Bấm "Nghe báo" khi đang ở giữa bài: log `TtsManager play text length: 2290` trong khi văn bản thuần của bài khoảng 7.600 ký tự, tức TTS bắt đầu từ đoạn giữa bài chứ không từ đầu. Bấm lần hai dừng TTS bình thường.

## Giới hạn đã biết
1. **Gần đúng.** Mỗi item coi như cùng trọng số: bài có ảnh/đồ họa/code lớn xen kẽ chữ sẽ lệch (bài smoke có ảnh đầu bài và một đồ họa dài). UI không hứa chính xác.
2. **Chưa quan sát trên máy:** (a) nhánh đóng RSVP dưới 20 từ không cuộn (chỉ có unit test của `shouldHandoffFromRsvp`); (b) `progress` có được ghi xuống Room sau khi đóng RSVP hay không (tôi khôi phục sao lưu mà chưa đọc lại `article_read_position`); (c) vị trí RSVP/trang sau khi đóng chỉ được so bằng mắt qua ảnh chụp, không đo số liệu; (d) TTS chỉ kiểm bằng log độ dài văn bản, tôi không nghe được âm thanh nên không xác nhận câu đầu tiên được đọc.
3. Nghe TTS xong quay lại đọc thường không biết đã nghe tới đâu (đã chọn không ghi ngược).
4. Bài có thân chỉ là một đoạn dài không ảnh dùng toàn bộ độ lệch cuộn trong một item; item rất cao chưa đo đủ trong lúc cuộn nhanh có thể làm vị trí lệch một chút (item chưa được đo thì tính là chưa cuộn).
5. **Ads:** `AdActivity` toàn màn hình xuất hiện một lần khi mở app; đã tự đóng bằng nút "Tiếp tục đến ứng dụng" lấy từ dump mới, không chạm nội dung quảng cáo. Banner nhỏ ở đáy không che nội dung.

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu cả Room (`Reader`, `Reader-shm`, `Reader-wal`) và DataStore (20 byte, không rỗng; `integrity_check` ok trên bản sao; 214 bài, 1 feed, 0 vị trí lưu). Sau smoke khôi phục cả hai bằng `run-as sh -c 'cat > ...'`; `cmp` xác nhận bốn file giống hệt, `integrity_check` ok, 214 bài, gói test đã gỡ. Không xóa DataStore.

## Commits trên nhánh `dev`
- `6b16203d` docs: thiết kế ZEN-08
- `741f3a12`, `a5c4aefb` docs: kế hoạch ZEN-08
- `bd46cb07` feat(zen-08): `ReadingPositionMapper`
- `a42d3b4c` feat(zen-08): RSVP bắt đầu theo vị trí chung
- `4b460839` feat(zen-08): TTS đọc từ đoạn gần nhất
- `57d6f373` feat(zen-08): nối đọc thường, RSVP, TTS
- `a6c2e4c4` docs(zen-08): điều chỉnh thiết kế cho bài một item
- `c92d02f3` feat(zen-08): quy đổi vị trí trong item và test bao phủ
- commit hoàn tất (tài liệu) nằm ngay sau các commit trên
