# EXC-02 — AI Podcast Studio, bản một bài (DONE)

**Audit score: 7/10.** Podcast Studio chạy trên Pixel 7 Pro thật với kịch bản **ngoại tuyến**. Đường Gemini đã được chạy thật **một lần** và lần đó **thất bại**: Gemini trả lời nhưng JSON hỏng nên app tự rơi về kịch bản ngoại tuyến. Bản sửa (JSON mode) đã có test và đã commit nhưng **chưa được xác nhận lại trên thiết bị**. Phạm vi cũng thu hẹp so với todo gốc (xem Giới hạn).

## Hành vi

- Menu ⋮ của trang đọc có mục **Podcast Studio**. Lần đầu mở, một hộp thoại hỏi có cho gửi tiêu đề và phần xem trước (tối đa 250 ký tự) tới Google Gemini hay không. Đồng ý thì Gemini viết đối thoại Alex/Sam; từ chối, không có khoá hoặc lỗi thì dùng kịch bản cục bộ. Lựa chọn được nhớ trong DataStore và đổi được ở Cài đặt › Quyền riêng tư.
- Không bao giờ gửi: toàn văn bài, liên kết, tên hay URL nguồn, mã tài khoản. Unit test kiểm trực tiếp prompt và request body.
- Phát bằng `CommuteAudioPlayer` dùng chung (hai giọng thật khi máy có, nếu không thì một giọng đổi cao độ). Sheet ghi rõ **loại giọng** và **nguồn kịch bản** (AI hay ngoại tuyến).
- Kịch bản **không** được lưu vào `CommuteEpisodeStore`, nên không ghi đè bản tin sáng CommuteCast. ViewModel không phụ thuộc store đó.
- Nếu player đang phát bản tin khác, app hỏi xác nhận trước khi thay.
- Đóng sheet chỉ tạm dừng, không huỷ player dùng chung.
- Thanh sóng âm là **hiển thị theo trạng thái phát**, không đo âm thanh thật (TTS của Android không đưa ra luồng PCM). Đứng yên khi tạm dừng, đổi màu theo người đang nói.
- Room giữ v20; không thêm dependency; không thêm thông báo hệ thống.

## Thay đổi

- Mô hình `PodcastScriptSource`, `PodcastStudioEpisode`; khoá DataStore `PodcastStudioGeminiConsent` (chưa có khoá nghĩa là chưa hỏi).
- `PodcastStudioScriptService` (qua `AiRequestGateway`, có failover và fallback cục bộ, trả về nguồn kịch bản).
- `PodcastStudioViewModel`, `PodcastStudioUiState`, `PodcastStudioSheet`, `PodcastStudioWaveform`.
- Mục menu trong `TopBar`, nối vào `ReadingPage`; công tắc consent trong `PrivacySettingsPage`.
- 18 chuỗi mới trong cả 38 file locale (EN và VI viết tay, 36 locale còn lại dùng tiếng Anh).

## Kiểm chứng

- `./gradlew testDevDebugUnitTest --rerun-tasks`: **362 suite, 2.494 test, 0 bỏ qua, 0 lỗi** (đọc từ XML, không có file cũ).
- Test mới: models 4, script service 13, ViewModel 8, waveform 6, sheet 16, menu 3, privacy ViewModel +1, parity 6/6.
- Mutation đã chứng minh các test bắt được lỗi: đổi mặc định consent, bỏ giới hạn 250 ký tự, nuốt `CancellationException`, bỏ nhánh xác nhận xung đột, cho sóng âm chạy khi tạm dừng, trả `Modifier.clickable` về sheet. Mỗi lần đúng các test liên quan đỏ, rồi khôi phục.
- Lint: **0 lỗi, 2.463 cảnh báo** (bằng baseline), **0 hit EXC-02**.
- `assembleDevDebug` và `assembleDevDebugAndroidTest`: thành công.
- `PodcastStudioDeviceTest` (khởi tạo TTS thật, chọn giọng) **OK (1 test)** trên Samsung S24 Ultra `R5CX613VZBR` và trên Pixel 7 Pro `2B051FDH3006MU`: 474 voice, `REAL_DUAL`.
- Smoke trên Pixel 7 Pro (kho BBC 214 bài): mở bài › ⋮ › Podcast Studio › hộp thoại consent (tiếng Việt) › chọn "Dùng kịch bản ngoại tuyến" › sheet hiện đúng bốn lượt thoại, nhãn "Hai giọng riêng của thiết bị • Kịch bản ngoại tuyến", sóng âm, điều khiển; media session `PLAYING`. Nhấn tạm dừng: media session `PAUSED`, hai ảnh chụp cách nhau một giây **giống hệt**. Đóng sheet: vẫn `PAUSED`.
- Room và DataStore của Pixel được sao lưu trước, khôi phục sau và `cmp` khớp 100% cả 4 file; `PRAGMA integrity_check` là `ok`, 214 bài còn nguyên. Smoke đã đổi DataStore từ 20 lên 127 byte (lưu lựa chọn consent) và đã được khôi phục.

## Smoke Gemini thật và lỗi phát hiện

- Trên Pixel 7 Pro, chọn "Đồng ý dùng AI" cho bài BBC "Hospital admits full responsibility…". Gemini **có trả lời** (kịch bản tiếng Việt 6 lượt), nhưng một dòng thoại chứa dấu nháy kép không được escape, nên `JSONArray` ném `Unterminated object at character 80`. App bắt lỗi, rơi về kịch bản ngoại tuyến và sheet ghi "Kịch bản ngoại tuyến": đúng thiết kế (không sập, nhãn trung thực) nhưng người dùng đã đồng ý dùng AI lại không được dùng AI.
- Nguyên nhân gốc: request không yêu cầu đầu ra JSON. Test đỏ trước (`requestAsksGeminiForJsonOutput…`: `No value for responseMimeType`), rồi thêm `responseMimeType: application/json` vào `generationConfig`; 14/14 test của service xanh. Commit `c28240c8`.
- **Bản sửa chưa được chạy lại trên máy.** Sau khi cài bản mới, quảng cáo toàn màn hình (Flood-It!, quảng cáo thử nghiệm) liên tục chuyển hướng sang Lens Launcher và chặn luồng. Tôi dừng thay vì tiếp tục chạm. Lần chạy thật duy nhất vẫn là lần thất bại ở trên.
- Lựa chọn consent đã lưu `true` trong DataStore Pixel trong lúc smoke, và đã được khôi phục về bản sao lưu (khớp 100%).

## Việc chưa làm / chưa kiểm chứng

- **Bản sửa JSON mode chưa được xác nhận trên thiết bị**: chưa biết Gemini có trả kịch bản dùng được sau sửa hay không (JSON mode giảm nhưng không loại trừ hoàn toàn đầu ra hỏng, và khi đó app vẫn rơi về kịch bản ngoại tuyến).
- **Chưa nghe âm thanh**: kiểm bằng media session và giao diện, không có ai nghe. Chưa kiểm hành vi âm thanh RSS podcast khi hai bên phát cùng lúc (chỉ ghi nhận `CommuteAudioPlayer` xin audio focus kiểu có thể hạ âm).
- Chưa thử nhánh hộp thoại xác nhận xung đột trên máy; chỉ có unit/Compose test.
- Trên S24 Ultra chỉ cài và chạy instrumentation. Máy bị rút giữa chừng nên **app dev và test APK vẫn còn trên user 0 của S24U**, cần gỡ khi cắm lại. Bản sao lưu ban đầu ở `/tmp/exc02-s24-backup` (DB mới cài, không có dữ liệu người dùng).

## Giới hạn

- **Một bài, không chọn nhiều bài** như todo gốc: `FlowPage` chưa có chế độ chọn nhiều, nên làm từ trang đọc để tránh refactor lớn.
- Phát qua `CommuteAudioPlayer` kế thừa hành vi hiện có: luôn bật vòng lofi nền và media notification. Không có công tắc tắt nhạc nền riêng cho podcast một bài.
- Nếu hệ thống thu hồi process khi đang phát, nút play ở notification/Android Auto có thể phát lại **bản tin sáng CommuteCast đã lưu**, không phải podcast bài này (vì kịch bản không được lưu).
- Kịch bản cục bộ chỉ đọc tiêu đề và phần xem trước nên khá ngắn (3–4 lượt thoại), không phải hội thoại phân tích.
- Sóng âm không phải biên độ âm thanh thật.
- `PodcastStudioSheet` dùng `pointerInput` thay cho `clickable` để chặn chạm: `clickable` gộp ngữ nghĩa các phần tử con và làm test không thấy chúng. `CommuteCastDialog` hiện có vẫn dùng cách cũ, chưa sửa.

## Commits

- `de025b1b` spec, `d40c275f` plan
- `920bbd15` models và khoá consent
- `9c746590` script service
- `06eae052` chuỗi 38 locale
- `69b83dba` ViewModel và state
- `687f58c9` waveform
- `ce25a487` sheet, hộp thoại, chuỗi hỗ trợ truy cập
- `968d3c9e` menu, nối ReadingPage và công tắc consent trong Privacy settings
- `59f422aa` test thiết bị TTS
- `c28240c8` yêu cầu Gemini trả JSON (sửa lỗi phát hiện khi smoke thật)

Tất cả nằm local trên `dev`, chưa push.
