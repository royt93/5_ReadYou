# ECHO-01 — Local Hype & Sentiment Signals (Bias Radar & Sentiment Gauge) — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Spec gốc yêu cầu Gemini chấm 3 trục: Hype/PR, cảm xúc, **thiên kiến**. Owner đã duyệt bản thu hẹp, trung thực hơn:
- **Có:** hai tín hiệu từ ngữ **cục bộ** trên thân bài: **Hype/PR (0–100)** và **sắc thái từ vựng** (Tích cực / Chưa rõ / Tiêu cực).
- **Không có:** trục "thiên kiến chính trị". Từ điển cục bộ không thể phân biệt trung thực giữa phản ánh sự kiện và đứng về một phía, nên không đo và không tuyên bố điều đó. Sheet chi tiết nói rõ điều này.
- **Không dùng Gemini/API/mạng/key.** Không chạm key BYOK, nên không dính FIX-09.
- "Chưa rõ" nghĩa là chưa đủ dấu hiệu từ vựng, **không phải trung lập**.
- Đây không phải kiểm chứng sự thật, điểm tin cậy hay kết luận về ý đồ tác giả.

## Tính năng
- **Hai thanh mảnh dưới `Metadata`** (`EchoSignalGauges`): Hype % và sắc thái. Chỉ hiện khi Hype ≥ 40 hoặc sắc thái rõ rệt; bài tin thường giữ giao diện sạch. Hype ≥ 70: thanh cam + biểu tượng + dòng cảnh báo (không chỉ dựa vào màu).
- **Chạm mở sheet** (`EchoSignalDetailsSheet`, trong `BottomDrawer` sẵn có): điểm Hype, sắc thái, lời tuyên bố giới hạn, tối đa 8 đoạn bị đánh dấu (mỗi đoạn ≤ 240 ký tự) kèm nhãn cue (cụm quảng bá, cường điệu, tuyên bố tuyệt đối, dấu câu/chữ hoa).
- **Dùng chung công tắc `Headline signals` của EXC-04**; mô tả công tắc đã cập nhật ở cả 38 locale để nói đúng phạm vi mới (tiêu đề + thân bài, không AI, không mạng).
- Phân tích tối đa 12.000 ký tự đầu, chạy trên `Dispatchers.Default`, hủy được khi đổi bài, chỉ lưu trong bộ nhớ.
- Trợ năng: cả khối gauge là một nút TalkBack có mô tả, vùng chạm ≥ 48dp.

## Cấu trúc mã
- `domain/headline/ArticleSignal.kt`, `ArticleSignalAnalyzer.kt`: model và bộ phân tích thuần Kotlin.
- `domain/headline/HeadlineLexicon.kt`: thêm từ vựng Hype/tuyên bố tuyệt đối cho EN, VI và ES/FR/DE (thử nghiệm).
- `domain/headline/HeadlineAnalyzer.kt`: mở API `internal` (`detectBodyLanguage`, `toneOf`, `normalizeText`, `tokenize`, `bodyIntensifiers`); `analyze()` giờ gọi chung `toneFrom` nên chỉ có một nguồn sự thật cho ngưỡng tone.
- `ui/page/headline/`: `EchoSignalsState.kt`, `EchoSignalGauges.kt` (gồm `EchoSignalSection`, nơi enforce công tắc), `EchoSignalDetailsSheet.kt`.
- `ui/page/home/read/ReadingViewModel.kt`: `echoSignalState` + một observer duy nhất theo `(articleId, content)`; `openEchoSheet`/`dismissEchoSheet`.
- `ui/page/home/read/Content.kt`, `ReadingPage.kt`: nối vào Reader và drawer.
- `ui/ext/ExtDataStore.kt`, `ui/page/common/HomeEntry.kt`: `headlineSignalsFlow()` reactive (xem "Lỗi thật" bên dưới).
- 14 khóa `echo_*` + cập nhật `headline_signals_desc` ở 38 locale.

## Lỗi thật tìm thấy khi làm (đều đã sửa)
1. **Công tắc không có hiệu lực ngay (lỗi có sẵn từ EXC-04):** `HomeEntry` đọc preference một lần lúc khởi động qua `context.headlineSignalsEnabled`, nên tắt công tắc trong Cài đặt không ẩn badge cho tới khi khởi động lại app. Phát hiện bằng smoke trên máy thật, không phải bằng test. Đã đổi sang `DataStore.headlineSignalsFlow()` và thêm 4 test.
2. **False positive trên tin thật:** lần đầu chạy trên 176 bài BBC, 4 bài bị chấm Hype 100 vì khớp chuỗi con ("disrupt UK democracy", "number one" trong quần vợt, "Islamic **Revolutionary** Guard"). Đã sửa bằng khớp nguyên từ, bỏ các cue quá chung, sàn 40 từ cho mật độ, và quy tắc đối chứng (một cue đơn lẻ không thể đạt ngưỡng đáng chú ý).
3. `SnapshotNormalizer.toPlainText` làm mất ranh giới đoạn nên đoạn được đánh dấu không dựng lại được; đánh dấu ranh giới trước khi chuẩn hóa, không sửa `toPlainText` vì hash của Radar phụ thuộc vào nó.

## Kiểm chứng
- **Unit:** 1.580 test, 0 lỗi, 0 skip. Gồm `ArticleSignalAnalyzerTest` 20, `ReadingViewModelEchoTest` 7, `EchoSignalUiTest` 13, `EchoSignalSectionTest` 7, `HeadlineSignalsFlowTest` 4, thêm 3 test cho API mới của `HeadlineAnalyzer`. Tất cả test EXC-04 cũ vẫn xanh.
- **Lint:** 0 lỗi, 2.453 cảnh báo (không tăng). **Build:** `assembleDevDebug` + `assembleDevDebugAndroidTest` pass. **Parity:** `StringResourceParityTest` 6/6.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** 20/20 (ECHO, chip trích dẫn/điều tra, sheet BOUNTY, `ArticleItemTest`, RPG).
- **Kiểm tra dữ liệu thật:** chạy bộ phân tích trên 176 bài BBC trong kho Pixel: phân tích 175, Hype tối đa 39, 0 cảnh báo, sắc thái {Chưa rõ 173, Tích cực 2}.
- **Smoke thật trên Pixel (bài PR giả được chèn tạm vào bản sao DB):** bài PR hiện `Độ thổi phồng: 100%` màu cam kèm cảnh báo và `🟢 Tích cực`; chạm mở sheet với 2 đoạn đánh dấu ("100% • Cụm từ quảng bá • Tuyên bố tuyệt đối") và lời tuyên bố giới hạn; bài BBC thường không hiện thanh nào; tắt công tắc sau bản vá thì danh sách 0 badge và Reader 0 thanh Hype, 0 badge tone.

## Giới hạn đã biết
1. **Chỉ đo từ ngữ.** Không đo thiên kiến, sự thật, độ tin cậy hay ý đồ. Mỉa mai, trích dẫn hay phủ định không được hiểu.
2. **Recall chưa được đo.** Không có kho văn bản quảng cáo thật trong máy, nên khả năng bắt đúng PR thật chỉ được kiểm bằng ví dụ tổng hợp. Số liệu thật chỉ chứng minh tỉ lệ dương tính giả thấp trên tin BBC.
3. Hầu hết tin trong kho đều ngắn (trung bình ~244 ký tự), nên ít bài có đủ dữ liệu để ra tín hiệu; điều này làm tín hiệu thiên về "không hiện".
4. ES/FR/DE là từ điển thử nghiệm, chưa có người bản ngữ kiểm duyệt. Locale khác dùng chuỗi tiếng Anh dự phòng.
5. Chỉ phân tích 12.000 ký tự đầu của bài.
6. Bản vá `HomeEntry` được kiểm bằng test cho `headlineSignalsFlow` và smoke thật, chưa có test Compose cho riêng `HomeEntry` (quá nặng vì kéo theo toàn bộ NavHost).

## Sự cố trong lúc smoke (cần owner biết)
Để bật lại công tắc, tôi đã xóa `settings.preferences_pb` trên Pixel khi mới chỉ sao lưu Room. **Toàn bộ cài đặt của app trên máy Pixel đã mất và không khôi phục được** (Gemini API key riêng, các công tắc quyền riêng tư, kiểu giao diện). Room thì nguyên vẹn (176 bài). Tôi đã chọn lại Cloudflare DoH và công tắc `Headline signals` đã bật; owner cần nhập lại Gemini key và các tuỳ chọn khác nếu muốn. Quy tắc mới đã ghi vào memory: sao lưu DataStore cùng Room trước mọi smoke, không bao giờ `rm`.

Ngoài ra quảng cáo toàn màn hình xuất hiện vài lần trong smoke; theo chỉ thị mới của owner, tôi tự đóng và tiếp tục.

## Commits trên nhánh `dev`
- `40410f65` docs: design · `80def9c8` docs: plan
- `acb8bd54` analyzer · `90c8c742` ViewModel state · `096dc80c` strings · `7e58b885` gauges + sheet · `eafa2cc9` wiring
