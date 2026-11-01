# ZEN-08 — Cross-mode Reading Handoff: Design

**Ngày:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Đọc thường (`ReadingPage`), RSVP (`RsvpReaderDialog`/`RsvpViewModel`) và nghe TTS (`TtsManager`) hiện không chia sẻ vị trí: RSVP luôn bắt đầu từ token 0, TTS luôn phát từ đầu bài, đóng RSVP không làm trang đọc cuộn theo. ZEN-08 làm cho ba chế độ tiếp tục gần đúng chỗ người dùng đã dừng.

## Quyết định của owner (AskUserQuestion)
1. **Lưu trữ:** dùng lại `ArticleReadPosition.progress` (0..1) hiện có. Không đổi Room (vẫn v20), không đổi ngữ nghĩa cột đang được đồng bộ WebDAV.
2. **Độ chính xác:** quy đổi theo tỉ lệ item thân bài (`bodyRange`), gọi rõ là "gần đúng", không hứa chính xác tới câu.
3. **TTS:** phát từ vị trí đã đọc; **không** ghi ngược tiến độ nghe (không sửa `TtsManager`).
4. **RSVP → đọc thường:** tự cuộn tới vị trí RSVP dừng khi đóng dialog.

## Sự thật từ code (đã đọc)
- `progress` đang lưu = `firstVisibleItemIndex / (totalItemsCount - 1)` của `LazyColumn` toàn trang (`ReadingPage.kt`), tức tính cả item đầu trang, metadata, thẻ cuối bài. Khôi phục bằng `scrollToItem((progress * lastIndex).toInt())` qua `restoreScrollPositionEvent`.
- `Content.kt` đã có `ItemCountingScope` và `bodyRange` (do `ReadingCompletionTracker` dùng) đánh dấu đúng các item mà `Reader` thêm vào; `Reader` → `htmlFormattedText` (`HtmlToComposable.kt`) thêm item theo khối HTML (đoạn, tiêu đề, ảnh, code, danh sách, trích dẫn).
- `RsvpViewModel.loadContent(content)` luôn đặt `currentIndex = 0`; đã có `seekTo(index)`. Nội dung RSVP lấy từ `readingUiState.content` (HTML) trong `ReadingPage`, token hoá bằng `RsvpTokenizer.tokenize`.
- `ReadingViewModel.playCurrentContent()` đổi HTML sang văn bản thuần bằng `HtmlCompat.fromHtml(...).toString()` rồi gọi `TtsManager.play(text, title, feedName)`; `TtsManager` chỉ phát từ đầu chuỗi và không báo tiến độ theo đoạn.
- Todo gốc nói "chưa có cơ chế lưu vị trí"; thực tế đã có `ArticleReadPosition` + DAO + WebDAV syncer, nên ZEN-08 tái dùng, không tạo bảng mới.

## Thiết kế
### 1. `ReadingPositionMapper` (thuần Kotlin, `domain/readposition/`)
Hàm thuần, không phụ thuộc Android:
- `bodyFractionFromList(firstVisibleIndex, bodyStart, bodyEndExclusive): Float` — tỉ lệ 0..1 trong thân bài, kẹp biên; ngoài thân bài thì 0 (trước) hoặc 1 (sau).
- `listIndexFromBodyFraction(fraction, bodyStart, bodyEndExclusive): Int` — item cần cuộn tới.
- `tokenIndexFromFraction(fraction, tokenCount): Int` và `fractionFromTokenIndex(index, tokenCount): Float`.
- `paragraphStartOffset(plainText, fraction): Int` — vị trí ký tự bắt đầu của đoạn gần nhất (ranh giới là dòng trống hoặc xuống dòng) tại tỉ lệ ký tự tương ứng.
- Hằng số có tên, không số ma thuật: `RESTART_BELOW_FRACTION = 0.02f`, `TREAT_AS_FINISHED_FRACTION = 0.98f`, `MIN_RSVP_WORDS_TO_SCROLL = 20`.

### 2. Luồng
- **Đọc thường → RSVP:** khi mở dialog, `ReadingPage` tính `bodyFraction` từ `listState` + `bodyRange`. Nếu `< 0.02` hoặc `≥ 0.98` thì RSVP bắt đầu từ đầu; ngược lại `RsvpViewModel` nhận `startFraction` và `seekTo` token tương ứng sau `loadContent`. `RsvpViewModel.loadContent(content, startFraction = 0f)`: tham số mới đặt cuối, mặc định giữ hành vi cũ.
- **RSVP → đọc thường:** khi đóng dialog, nếu `currentIndex - startIndex ≥ 20` thì tính `bodyFraction` từ token hiện tại, đổi sang chỉ số item, `listState.scrollToItem`, rồi gọi `saveReadPosition` với tỉ lệ toàn danh sách. Dưới 20 từ thì không cuộn.
- **→ TTS:** `playCurrentContent()` nhận vị trí hiện tại (tỉ lệ thân bài), cắt `plainText` từ `paragraphStartOffset` rồi gọi `TtsManager.play`. Dưới `RESTART_BELOW_FRACTION` hoặc trên `TREAT_AS_FINISHED_FRACTION` thì đọc từ đầu. Không ghi ngược tiến độ nghe.
- Handoff trong phiên dùng thẳng `listState`, nên vẫn chạy khi `readPositionSyncEnabled` tắt; việc ghi xuống DB vẫn tôn trọng cờ đó.

### 3. Giới hạn nói thẳng
- Gần đúng, sai số vài khối: bảng, ảnh, code, danh sách mỗi thứ chỉ là một item, nên tỉ lệ item lệch khỏi tỉ lệ văn bản khi các khối dài ngắn khác nhau xa.
- Nghe TTS xong rồi quay lại đọc thường không biết đã nghe tới đâu (đã chọn).
- Nhãn UI không thêm chuỗi mới nên không đụng 38 locale.
- Không đổi `TtsManager`, `RsvpTokenizer`, `HtmlToComposable`, Room, quảng cáo, IAP, AI, mạng.

### 4. Rủi ro đã biết
- `bodyRange` nằm trong `Content`, còn `listState` ở `ReadingPage`; cách truyền ra ngoài (callback hoặc state hoist) chốt ở bước lập kế hoạch sau khi đọc `ReadingCompletionTracker`.
- Dialog RSVP có thể mở lại cùng nội dung nhiều lần; `startFraction` phải áp dụng đúng một lần mỗi lần `loadContent`, không ghi đè vị trí người dùng đã tua.
- Nội dung RSVP và nội dung TTS cùng lấy từ `readingUiState.content` nhưng token hoá và `HtmlCompat` khác nhau, nên cả hai chỉ khớp theo tỉ lệ, không theo ký tự.

## Kiểm thử
- Unit `ReadingPositionMapperTest`: biên 0, 0,02, 0,98, 1; một item; thân bài rỗng; tokens rỗng; văn bản không có ranh giới đoạn.
- Unit `RsvpViewModel`: `loadContent(..., startFraction)` đặt đúng `currentIndex`; mặc định giữ nguyên 0; `startFraction` chỉ áp dụng một lần; kẹp biên.
- Unit `ReadingViewModel`: văn bản TTS bị cắt đúng đoạn, đọc từ đầu khi dưới ngưỡng.
- Compose/Robolectric: mở và đóng RSVP (≥20 từ cuộn, <20 từ không cuộn).
- Instrumentation trên Pixel `2B051FDH3006MU` bằng `am instrument` (không dùng `connectedDevDebugAndroidTest`).
- Smoke Pixel: cuộn ~40%, mở RSVP thấy token bắt đầu giữa bài, chạy rồi đóng thấy trang cuộn theo. Backup cả Room (3 file) và DataStore trước, khôi phục và `cmp` sau; tự đóng quảng cáo toàn màn hình theo quy tắc, báo cáo đã thấy quảng cáo.
- Test chuỗi (parity 38 locale) không đổi.

## Ngoài phạm vi
Ghi ngược tiến độ TTS, chính xác tới câu, cột vị trí theo ký tự và migration Room, đồng bộ vị trí RSVP qua WebDAV, chuỗi giao diện mới.

## Điều chỉnh sau khi thực thi (2026-11-01)
Phát hiện khi chạy test tích hợp Task 5: `htmlFormattedText` gộp mọi đoạn văn liền nhau vào **một** item `LazyColumn` (chỉ ảnh, code, danh sách, bảng mới cắt item). Bài toàn chữ nên có `bodyRange.count == 1`, và quy đổi theo chỉ số item không phân biệt được 5% với 90% của bài. Owner chọn thêm độ lệch cuộn trong item:
- Vị trí = `(chỉ số item đầu thấy được - bodyStart + firstVisibleItemScrollOffset / kích thước item) / bodyCount`, kẹp 0..1. Đọc kích thước item từ `layoutInfo.visibleItemsInfo` (item đầu thấy được luôn đã được đo).
- Chiều ngược lại: `scrollTargetFromBodyFraction` trả về `(listIndex, withinItem)`; cuộn tới `listIndex` rồi cuộn thêm `withinItem * kích thước item` (kích thước đọc sau khi item đã được đo).
- Mỗi item vẫn được coi là cùng trọng số; sai số còn khi các item (ảnh, code) khác nhau nhiều về độ dài. Giá trị `ArticleReadPosition.progress` được lưu vẫn theo chỉ số item toàn danh sách (không đổi hợp đồng WebDAV), nên bản ghi lưu bền thô hơn vị trí trong phiên.
