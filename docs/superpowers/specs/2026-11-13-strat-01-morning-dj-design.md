# STRAT-01 — Bản tin sáng: consent riêng, giờ nghe tự chọn, tự phát khi cắm tai nghe (thiết kế)

**Ngày:** 2026-11-13 · **Nhánh:** `dev` · **Task gốc:** `doc/task/todo/STRAT-01_zero-click-autonomous-podcast-dj.md` · **Nền:** CommuteCast (epic 14, DJ-01..08 đã xong: `CommuteWorker`, `CommuteEpisodePreparer`, `CommuteScriptService`, `CommuteEpisodeStore`, `CommuteAudioPlayer`)

## Mục tiêu
Bản tin sáng đã có (soạn lúc 06:00, đọc bằng giọng máy, thông báo "sẵn sàng"). STRAT-01 thêm bốn thứ: (1) hỏi đồng ý riêng trước khi gửi bài lên Gemini, (2) người dùng chọn giờ nghe, (3) công tắc tự phát khi cắm tai nghe Bluetooth, (4) nếu Android chặn tự phát thì hiện thông báo có nút **Phát**.

## Quyết định của owner (2026-11-13)
- **Phạm vi:** vá riêng tư + chọn giờ nghe + tự phát khi cắm tai nghe Bluetooth. **Không** điều kiện sạc hoặc pin (owner không chọn).
- **Tự phát:** công tắc riêng, **mặc định TẮT**; chỉ lần cắm tai nghe đầu tiên trong **2 giờ** sau khi bản tin sẵn sàng; **tối đa 1 lần mỗi ngày**.
- **Bị Android chặn:** thử tự phát; nếu chặn thì hiện thông báo có nút **Phát** (chạm một lần).
- **Giờ chuẩn bị:** người dùng chọn giờ nghe (mặc định **07:00**), app soạn trước **30 phút**.
- **Riêng tư:** consent Gemini riêng cho Bản tin sáng, mặc định chưa đồng ý; chưa đồng ý thì soạn kịch bản **trong máy** (không gửi gì).
- Thiết bị kiểm: **chỉ Tecno KJ7 `115333744A005844`** (owner: chỉ dùng Tecno; không chạm Pixel).

## Hiện trạng đã xác minh trong mã
- `CommuteWorker.enqueueDailyWork` cố định **06:00**, `ExistingPeriodicWorkPolicy.KEEP`, ràng buộc chỉ cần mạng; gọi ở `RApp.workerInit`.
- `CommuteScriptService.generateScript` **luôn thử Gemini trước** bằng `GeminiConfig.API_KEYS` (tiêu đề + mô tả ≤250 ký tự của 5 hoặc 10 bài) rồi mới rơi về bộ soạn trong máy. **Không có consent nào** (đã grep). Hai nơi gọi: `CommuteEpisodePreparer` (Worker) và `CommuteCastViewModel` (nút trong sheet).
- Không có receiver tai nghe/Bluetooth/boot, không có quyền `BLUETOOTH_CONNECT` trong manifest.
- `CommuteMediaSessionService` là `MediaBrowserServiceCompat`; `CommuteEpisodeStore` lưu một episode (có `date` dạng mili giây) trong SharedPreferences, có bản sao lưu.
- Key Gemini chung đang hỏng (FIX-09 hoãn): thực tế app luôn rơi về bộ soạn trong máy.

## Phạm vi trung thực
- **Tự phát có thể bị Android chặn.** Từ Android 12, app ở nền chỉ được bật foreground service trong vài trường hợp; tín hiệu "thiết bị Bluetooth vừa kết nối" **không** nằm trong danh sách đó ([tài liệu Android](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)). Vì vậy thiết kế **luôn có nhánh thông báo nút Phát** và coi tự phát là "cố gắng", không hứa. Việc tự phát được hay không phụ thuộc máy và phiên bản Android.
- **Không kiểm được trên máy thật nếu thiếu tai nghe Bluetooth.** Tôi chưa biết Tecno KJ7 có tai nghe để thử không. Nếu không, nhánh Bluetooth chỉ kiểm bằng test JVM và (nếu Android cho) tín hiệu giả lập; báo cáo ghi thẳng "chưa thử trên thiết bị thật".
- **Không kiểm được Gemini thật** (key chung hỏng, FIX-09 hoãn); kịch bản trong lúc kiểm là kịch bản soạn trong máy.
- Lịch Android có thể trễ vài phút khi tiết kiệm pin; không dùng báo thức chính xác (cần quyền người dùng) nên **không** hứa đúng từng phút.
- **Không làm:** điều kiện sạc/pin, báo thức chính xác, quảng cáo/mua bán/tiền (DJ-04 đã có phần quảng cáo, không đụng), đổi giọng/nhạc nền, Room v23, dependency mới, Android Auto mới (đã có FRONT-04), tự phát từ khởi động máy.

## Thành phần
1. **Consent riêng** `DataStoreKeys.CommuteScriptGeminiConsent` (Boolean?, mặc định null = chưa đồng ý) với `Context.commuteScriptGeminiConsent` / `setCommuteScriptGeminiConsent`, theo khuôn `OracleClaimsGeminiConsent`.
   - `CommuteScriptService.generateScript` nhận thêm tham số `allowGemini: Boolean`; **false thì không gọi mạng, không đọc key**, đi thẳng bộ soạn trong máy. `CommuteEpisodePreparer` và `CommuteCastViewModel` truyền giá trị đọc từ DataStore tại thời điểm gọi (consent là `true` mới được gửi).
   - Mặc định (`null`/`false`) → soạn trong máy. Người dùng cũ sẽ thấy bản tin đơn giản hơn cho tới khi đồng ý (nói rõ trong báo cáo và mô tả công tắc).
   - UI: một công tắc + lời giải thích ngay trong trang cài đặt riêng tư (`PrivacySettingsPage`, theo khuôn Oracle) nói rõ gửi gì (tiêu đề + mô tả ngắn của tối đa 10 bài) và rằng không gửi lịch sử đọc.
2. **Giờ nghe tự chọn** `DataStoreKeys.CommuteListenMinuteOfDay` (Int?, mặc định 420 = 07:00). Hằng `PREPARE_LEAD_MINUTES = 30`. Hàm thuần `CommuteSchedulePolicy.initialDelayMillis(now, listenMinuteOfDay, zone)` trả độ trễ tới lần soạn kế tiếp (= giờ nghe − 30 phút, cuộn sang ngày sau nếu đã qua; xử lý qua nửa đêm; xử lý đổi giờ mùa hè bằng `java.time`).
   - `CommuteWorker.enqueueDailyWork` dùng chính hàm đó; đổi giờ trong Cài đặt gọi lại với `ExistingPeriodicWorkPolicy.REPLACE` (hiện dùng `KEEP`: đổi giờ sẽ không có tác dụng). `UPDATE` **không có** trong WorkManager `2.8.0-alpha02` của dự án (kiểm bằng `javap`), nên khi khởi động app vẫn dùng `KEEP`, chỉ khi đổi giờ mới dùng `REPLACE`.
   - UI: một dòng chọn giờ mở `TimePickerDialog` hệ thống, theo khuôn dòng chọn giờ của `ZenSettingsPage` (đã có, 24h theo cấu hình máy), đặt trong trang cài đặt riêng tư cùng công tắc consent.
3. **Luật tự phát thuần** `CommuteAutoPlayPolicy.decide(input): Decision` với `Decision = PlayNow | OfferPlayButton | Skip(reason)`. Đầu vào: công tắc, có episode chưa nghe không, `episode.date`, `now`, ngày UTC của lần tự phát gần nhất, thiết bị có phải thiết bị âm thanh không, có đang phát nhạc/audio khác không. Hằng `AUTO_PLAY_WINDOW_MS = 2 giờ`, `AUTO_PLAY_MAX_PER_DAY = 1`. Quy tắc: công tắc tắt → `Skip`; không có episode hoặc đã quá 2 giờ → `Skip`; hôm nay đã tự phát → `Skip`; thiết bị không phải âm thanh → `Skip`; đang phát audio khác → `OfferPlayButton` (không chen ngang); còn lại → `PlayNow`.
4. **Nhận biết kết nối** `BluetoothAudioReceiver` (manifest, `ACTION_ACL_CONNECTED`, `exported=false`): lấy `BluetoothDevice`, chỉ chấp nhận lớp thiết bị âm thanh/video (tai nghe, loa, xe, âm thanh nói chung); đọc trạng thái, gọi luật; `PlayNow` thì thử bật phát qua foreground service có sẵn, **bắt `ForegroundServiceStartNotAllowedException` (và mọi `Exception`, không nuốt `CancellationException`)** rồi chuyển sang `OfferPlayButton`. Đánh dấu "đã tự phát hôm nay" **sau** khi phát thành công; nhánh nút Phát không tính vào hạn mức. Cần `BLUETOOTH_CONNECT` (Android 12+); thiếu quyền → `Skip`, không crash.
5. **Quyền chỉ khi cần:** `<uses-permission BLUETOOTH_CONNECT>` khai báo; hộp xin quyền chỉ hiện khi người dùng **bật** công tắc tự phát; từ chối thì công tắc quay về TẮT kèm một dòng giải thích. Không xin lúc mở app.
6. **Thông báo có nút Phát:** `NotificationHelper.notifyCommuteReadyWithPlay(...)` (kênh Commute có sẵn, `FLAG_IMMUTABLE`); nút Phát gửi vào `CommuteMediaSessionService` (action `ACTION_PLAY` đã có). Chạm thông báo thì phát bản tin đã lưu, không soạn lại.
7. **Trạng thái tự phát** lưu DataStore (`CommuteLastAutoPlayEpochDay`, Long?); không đổi cơ sở dữ liệu.
8. **Công tắc tự phát** `CommuteAutoPlayOnHeadset` (Boolean?, mặc định null = TẮT).
9. **Chuỗi:** khoảng 14 khóa `commute_*` mới × 38 locale (EN và VI viết tay, 36 bản tiếng Anh), một script "tất cả hoặc không"; không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` xanh.

## Kiểm chứng
- JVM, TDD đỏ→xanh (đọc lý do đỏ từ XML sau khi `.exit` tồn tại), mutation có backup và `cmp`:
  - `CommuteSchedulePolicy` (qua nửa đêm, giờ nghe sớm hơn 30 phút so với nửa đêm, đã qua giờ, đổi giờ mùa hè, múi giờ khác).
  - `CommuteAutoPlayPolicy` (mọi nhánh `Skip`/`Offer`/`Play`, biên đúng 2 giờ, biên nửa đêm UTC, hạn mức mỗi ngày).
  - Consent: `allowGemini=false` thì **không có request mạng** (OkHttp giả đếm số gọi = 0) và không đọc key; `true` thì mới gọi; cả hai nơi gọi (Worker, ViewModel) truyền đúng giá trị.
  - Receiver: thiết bị không phải âm thanh bị bỏ qua; thiếu quyền thì `Skip`; `ForegroundServiceStartNotAllowedException` chuyển sang nút Phát; không nuốt hủy coroutine; chỉ đánh dấu "đã phát hôm nay" sau khi phát thành công.
  - `NotificationHelper` (nút Phát, `FLAG_IMMUTABLE`, một id cố định), DataStore (khóa riêng), UI (công tắc, ô chọn giờ, từ chối quyền thì tắt công tắc), nối `CommuteWorker`/`RApp` (test nguồn), chuỗi 38 locale.
  - Cả suite `--rerun-tasks`, lint, `assembleDevDebug`.
- androidTest (hộp consent, ô chọn giờ) bằng `adb -s 115333744A005844 shell am instrument` (không dùng `connected*`).
- Máy thật chỉ **Tecno KJ7**: cổng thiết bị chỉ đọc, sao lưu byte-exact Room/`-shm`/`-wal`/DataStore/`shared_prefs`. Ca: hộp consent và công tắc; đổi giờ và xem lịch WorkManager được đặt lại; chạy Worker một lần (nút soạn) với consent tắt và kiểm **không có request ra `generativelanguage.googleapis.com`**; thông báo có nút Phát và chạm thì phát bản đã lưu; từ chối quyền Bluetooth thì công tắc tắt; nếu có tai nghe Bluetooth thì thử cắm thật, nếu không thì thử giả lập bằng adb và ghi rõ. Quảng cáo toàn màn hình: chỉ Back, không bấm "Continue to app".
- **Không kiểm được:** tự phát thật khi cắm tai nghe nếu thiếu tai nghe; Gemini thật; lịch đúng từng phút khi tiết kiệm pin; Android Auto qua tín hiệu này.

## Giới hạn đã biết
- Tự phát chỉ là cố gắng; nhiều máy sẽ rơi về thông báo có nút Phát.
- Người dùng cũ chưa đồng ý sẽ nghe bản tin soạn trong máy (đơn giản hơn) cho tới khi bật consent.
- Lịch có thể trễ vài phút; đổi giờ chỉ có hiệu lực từ lần soạn kế tiếp.
- 36 locale dùng chuỗi tiếng Anh.
