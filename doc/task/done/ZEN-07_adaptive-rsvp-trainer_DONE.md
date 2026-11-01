# ZEN-07 — Gợi ý tốc độ đọc RSVP (Adaptive RSVP Trainer) — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Spec gốc nói app "đo khả năng đọc-hiểu" rồi đề xuất WPM tối ưu. Sáu câu trắc nghiệm chỉ là một mẫu rất nhỏ (đoán mò 1 câu 4 lựa chọn đúng 25%, đúng cả 2 câu 6,25%), nên owner duyệt bản trung thực hơn:
- **Có:** hiệu chỉnh 3 vòng cố định ở 300 / 450 / 600 WPM, mỗi vòng một đoạn ngắn (EN hoặc VI, ngôn ngữ khác dùng EN) và 2 câu trắc nghiệm 4 lựa chọn. **Gợi ý** = WPM của vòng cao nhất trả lời đúng cả 2 câu (không vòng nào đạt thì gợi ý 250). Gợi ý được lưu bền (DataStore) và áp dụng làm mặc định cho các lần mở RSVP sau; người dùng vẫn ghi đè bằng tay và chạy lại bất cứ lúc nào.
- **Không có:** đo mức hiểu bài, AI, mạng, API key, quảng cáo, mua hàng, lịch sử hay biểu đồ, thay đổi Room (DB vẫn v20). Giao diện gọi đây là "ước lượng thô từ 6 câu hỏi, không phải thước đo hiểu bài sâu".
- Độ tin cậy chỉ có `LOW` hoặc `MEDIUM`, không bao giờ `HIGH`: `MEDIUM` khi có ít nhất một vòng đạt và mọi vòng chậm hơn vòng đạt cao nhất cũng đạt; còn lại là `LOW` (màn kết quả thêm ghi chú "câu trả lời chưa ổn định").
- Hệ số trễ dấu câu (0,85 / 1,0 / 1,25 / 1,5 theo tỉ lệ đúng) chỉ nhân các khoảng dừng cuối đoạn, cuối câu và dấu phẩy; trễ từ dài không bị nhân. Hệ số chưa có bằng chứng cho thấy thực sự giúp ích.

## Cấu trúc mã
- `domain/rsvp/`: `RsvpCalibrationPlan` (hằng số, kiểu dữ liệu), `RsvpCalibrationScorer`, `RsvpCalibrationContent` (bài đọc và câu hỏi EN/VI nằm trong Kotlin vì mỗi câu hỏi gắn với đúng một đoạn), `RsvpTiming` (công thức trễ mỗi từ dùng chung cho reader và calibration).
- `RsvpTokenizer`: thêm tham số cuối `punctuationDelayScale = 1f`; ở `1f` kết quả y hệt cũ.
- `RsvpCalibrationStore` + `DataStoreRsvpCalibrationStore` + `RsvpCalibrationModule` (Hilt `@Binds`), hai khóa DataStore mới `RsvpSuggestedWpm` và `RsvpPunctuationDelayScale`.
- `RsvpViewModel`: giữ nguyên constructor không đối số cho test cũ; store được inject bằng field (Hilt từ chối hai `@Inject` constructor), có constructor `internal` nhận store cho test. Tốc độ đã lưu được áp dụng một lần mỗi lần tải; tốc độ người dùng đã kéo trong phiên thắng giá trị đã lưu.
- `RsvpCalibrationViewModel` (máy trạng thái Intro, Reading, Questions, Result, Finished; thoát giữa chừng không lưu gì), `RsvpCalibrationDialog`/`RsvpCalibrationScreen`.
- Hai điểm vào: nút "Hiệu chỉnh tốc độ" cạnh thanh WPM trong `RsvpReaderDialog` và một hàng trong `ZenSettingsPage`.
- 14 chuỗi `zen_calibration_*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh dự phòng).

## Phát hiện khi làm
1. **Hilt không cho hai `@Inject` constructor** (`@HiltViewModel annotated class should contain exactly one @Inject ... constructor`). Thiết kế plan ban đầu (hai constructor) không build được; đổi sang field injection kèm constructor `internal` cho test, giữ nguyên `RsvpViewModel()` cho test cũ và test instrumentation cũ.
2. **Test store dùng chung DataStore thật của Robolectric:** test ghi trước làm test "chưa hiệu chỉnh" thấy giá trị cũ; xử lý bằng cách xóa DataStore trong `@Before`.
3. **Test dialog không bấm được lựa chọn** vì danh sách nằm trong cột cuộn; cần `performScrollTo()` trước khi bấm (3 test).
4. Hai ngoại lệ đáng ghi của tôi trong lúc smoke: một lần chạm vào dải "Tiếp tục đến ứng dụng" của quảng cáo khi dump giao diện đã cũ nên chạm trúng thanh địa chỉ Chrome phía dưới và mở cài đặt Chrome; tôi dừng Chrome và app ngay (`am force-stop`), không nhập gì, rồi chỉ chạm nút đóng quảng cáo sau khi lấy tọa độ từ một dump mới.

## Kiểm chứng
- **Unit:** 1.746 test, 0 lỗi, 0 skip (nền trước task 1.702, thêm 44): `RsvpCalibrationScorerTest` 11, `RsvpTimingTest` 3, `RsvpCalibrationContentTest` 5, `RsvpTokenizerTest` +3, `RsvpCalibrationStoreTest` 3, `RsvpViewModelTest` +6, `RsvpCalibrationViewModelTest` 6, `RsvpCalibrationDialogTest` 7. Toàn bộ test RSVP và Zen cũ (93 test nhóm Rsvp/Zen) vẫn xanh. `StringResourceParityTest` 6/6.
- **Lint:** 0 lỗi, 2.459 cảnh báo (không đổi); không có cảnh báo nào nằm trong file hay chuỗi của task. **Build:** `assembleDevDebug` (đã qua kiểm tra Hilt) và `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** 14/14 (`RsvpCalibrationDeviceTest` 2 và `ZenSpeedReadingIntegrationTest` hiện có).
- **Smoke thật trên Pixel:** Cài đặt -> Đọc siêu tốc & Tập trung Zen -> hàng "Hiệu chỉnh tốc độ đọc" hiện đúng dưới thẻ RSVP. Intro hiện kèm dòng giới hạn trung thực. Chạy cả ba vòng thật: 300 WPM, 450 WPM, 600 WPM (bài tiếng Việt vì máy dùng tiếng Việt), trả lời đúng cả 6 câu, kết quả "Tốc độ đề xuất: ~600 WPM" kèm lưu ý ước lượng thô. Bấm "Áp dụng": DataStore tăng từ 20 lên 86 byte. Mở lại bài báo, RSVP Reader hiện **"Tốc độ: 600 WPM"** với nút "Hiệu chỉnh tốc độ" cạnh nhãn.

## Giới hạn đã biết
1. **Chỉ là gợi ý thô từ 6 câu hỏi, không phải đo mức hiểu.** Một lần may mắn ở vòng nhanh nâng gợi ý (6,25% mỗi vòng chỉ do đoán mò); nhãn `LOW` và việc ghi đè thủ công giảm nhưng không loại bỏ điều này.
2. Bài đọc tĩnh nên người chạy lại sẽ nhớ bài, làm điểm các lần sau cao ảo. Chỉ tối ưu cho EN và VI.
3. Gợi ý không bao giờ vượt 600 WPM vì đó là vòng nhanh nhất; người đọc rất nhanh vẫn phải tự kéo.
4. **Smoke chỉ trả lời đúng toàn bộ** (nhánh 600 WPM, hệ số 0,85, độ tin cậy `MEDIUM`). Các nhánh còn lại (trả lời sai, hệ số 1,25 hoặc 1,5, ghi chú `LOW`, thoát giữa chừng) chỉ được kiểm bằng unit test và test Compose, chưa thấy trên máy thật.
5. Hệ số trễ dấu câu được lưu và áp dụng khi tải nội dung nhưng chưa được quan sát trực tiếp trên màn hình (ở smoke hệ số là 0,85 nên khác biệt chỉ vài chục mili-giây mỗi dấu câu).
6. **Ads:** `AdActivity` toàn màn hình xuất hiện mỗi lần mở app trong lúc smoke; đã tự đóng bằng nút "Tiếp tục đến ứng dụng", không chạm nội dung quảng cáo (trừ sự cố chạm nhầm thanh Chrome nêu ở phần phát hiện).

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu cả Room (`Reader`, `Reader-shm`, `Reader-wal`) và DataStore (không rỗng, `integrity_check` ok trên bản sao, 214 bài). Sau smoke khôi phục cả hai bằng `run-as sh -c 'cat > ...'`; `cmp` xác nhận cả ba file Room và DataStore giống hệt bản sao lưu, `integrity_check` ok, 214 bài, gói test đã gỡ.

## Commits trên nhánh `dev`
- `b6873b9d`, `a870b0eb` docs: thiết kế ZEN-07
- `97c850e2` docs: kế hoạch ZEN-07
- `ce93d924` feat(zen-07): add pure RSVP calibration plan, scorer and timing
- `03774315` feat(zen-07): add calibration passages and punctuation delay scale
- `8471cfca` feat(zen-07): add RSVP calibration store, DataStore keys and strings across 38 locales
- `f07175b8` feat(zen-07): apply the calibrated speed in RsvpViewModel and add RsvpCalibrationViewModel
- `c519855d` feat(zen-07): add calibration dialog and entry points in RSVP and Zen settings
- commit hoàn tất (test instrumentation và tài liệu) nằm ngay sau các commit trên
