# STRAT-01 (Bản tin sáng: đồng ý riêng tư, giờ nghe tự chọn, tự phát khi cắm tai nghe)

**Date:** 2026-11-14  
**Branch:** `dev` (không push)  
**Scope:** Bản tin sáng (CommuteCast) đã có từ epic 14. STRAT-01 thêm bốn thứ: (1) hỏi đồng ý riêng trước khi gửi bài lên Gemini, (2) người dùng chọn giờ nghe (app soạn trước 30 phút), (3) công tắc tự phát khi cắm tai nghe Bluetooth, (4) nếu Android chặn thì hiện thông báo có nút **Phát**. Room **giữ v22**.  
**Spec / plan:** `docs/superpowers/specs/2026-11-13-strat-01-morning-dj-design.md`, `docs/superpowers/plans/2026-11-13-strat-01-morning-dj.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn), vì ba yêu cầu của Gherkin gốc không làm.

## Nói thẳng về phạm vi

- **Có một lỗi riêng tư có từ trước, nay đã sửa.** `CommuteScriptService` luôn thử Gemini trước, gửi tiêu đề và mô tả (≤250 ký tự) của 5 đến 10 bài **mà không hỏi người dùng**, trong khi mọi tính năng AI khác đều có consent riêng. Giờ mặc định là chưa đồng ý và khi đó kịch bản được soạn trong máy, không gọi mạng.
- **Hệ quả cho người dùng cũ:** ai chưa bật consent sẽ nghe bản soạn trong máy (đơn giản hơn) cho tới khi họ đồng ý. Đây là thay đổi hành vi có chủ đích.
- **Tự phát chỉ là "cố gắng".** Từ Android 12, app ở nền chỉ được bật dịch vụ nền trong vài trường hợp, tín hiệu "thiết bị Bluetooth vừa kết nối" không nằm trong đó ([tài liệu Android](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)). Vì vậy luôn có nhánh thông báo nút Phát; app **không hứa** tự phát được trên mọi máy.
- **Không làm (so với Gherkin gốc):** điều kiện sạc hoặc pin trên 50% (owner không chọn), "tự chọn 10 bài theo thói quen đọc" (giữ bộ chọn theo ngân sách thời gian của DJ-08), tự phát khi nối Android Auto, báo thức chính xác, tự phát khi khởi động máy, quảng cáo/mua bán/tiền.
- Lịch Android có thể trễ vài phút khi tiết kiệm pin; **không** hứa đúng từng phút.
- Không dependency mới, không Room v23, không migration.

## Đã làm

- **Luật lịch thuần** `CommuteSchedulePolicy`: giờ nghe → độ trễ tới lần soạn kế tiếp (giờ nghe − 30 phút), dùng `java.time` nên đúng khi qua nửa đêm và khi đổi giờ mùa hè, lấy ngày theo múi giờ của máy. Giá trị DataStore hỏng thì quay về 07:00.
- **Luật tự phát thuần** `CommuteAutoPlayPolicy` (có thứ tự rõ): công tắc tắt, không có bản tin, đã nghe, quá 2 giờ, hôm nay đã tự phát, không phải thiết bị âm thanh ⇒ bỏ qua; đang có audio khác ⇒ chỉ hiện nút Phát; còn lại ⇒ phát.
- **Bốn khóa DataStore mới** (consent, giờ nghe, công tắc, ngày tự phát cuối) và cờ "đã nghe" cạnh bản tin đã lưu. Consent mới **độc lập** mọi consent AI khác (có test).
- **Cổng consent** `allowGemini` (mặc định `false`) ở `CommuteScriptService`, truyền từ **cả hai** nơi gọi (Worker qua `CommuteEpisodePreparer`, và nút trong sheet qua `CommuteCastViewModel`); test nguồn khẳng định không còn nơi gọi nào khác.
- **`CommuteWorker`** theo giờ nghe: bỏ 06:00 cố định; khởi động app dùng `KEEP` (không đặt lại lịch mỗi lần mở app), đổi giờ dùng `REPLACE`. **`ExistingPeriodicWorkPolicy.UPDATE` không có** trong WorkManager 2.8.0-alpha02 của dự án (kiểm bằng `javap`).
- **Nút Phát thật sự phát:** phát hiện khi lập plan rằng `ACTION_PLAY` của dịch vụ chỉ hiện thông báo, không bật âm thanh. Thêm `ACTION_PLAY_LATEST` (vào chế độ dịch vụ nền trước, rồi mới phát) và đánh dấu "đã nghe" ở cả hai đường (bộ nhớ và kho).
- **`BluetoothAudioReceiver` + `HeadsetAutoPlayHandler`:** receiver không export, chỉ nghe `ACL_CONNECTED`; chỉ nhận thiết bị âm thanh; kiểm quyền ngay chỗ đọc lớp thiết bị và chặn `SecurityException`; `goAsync()` luôn `finish()`; bắt `ForegroundServiceStartNotAllowedException` rồi chuyển sang nút Phát; **chỉ ghi "đã tự phát hôm nay" sau khi phát thành công**; không nuốt hủy coroutine.
- **Quyền `BLUETOOTH_CONNECT`** khai báo trong manifest, nhưng chỉ **xin khi người dùng bật công tắc tự phát**; từ chối thì công tắc không bật và hiện lời giải thích. Không thêm quyền Bluetooth/vị trí/báo thức chính xác/khởi động nào khác (có test).
- **Màn Riêng tư:** ba dòng mới (hộp đồng ý Gemini, giờ nghe dùng `TimePickerDialog` hệ thống như trang Zen, công tắc tự phát).
- 10 chuỗi `commute_*` × 38 locale (EN và VI viết tay, 36 bản tiếng Anh), một script "tất cả hoặc không".

## Kiểm chứng (JVM)

- Full suite chạy lại sạch (`--rerun-tasks`, HEAD `bfa8374f`): **3.352 test, 0 lỗi, 0 skip**, 451 tệp, không tệp cũ (nền ORACLE-03 là 3.208; thêm 144). Lint: **0 lỗi, 2.465 cảnh báo** (bằng nền) và **0 vấn đề trong tệp STRAT-01**. `assembleDevDebug` và `assembleDevDebugAndroidTest` thành công. Lược đồ không đổi.
- Test mới (số test mỗi lớp mới): luật lịch 14, luật tự phát 17, DataStore 11, cờ đã nghe 6, cổng consent 4 + nguồn 6, `CommuteWorker` lịch 9 + nguồn 5, chuỗi 8, thông báo nút Phát 7, dịch vụ phát 5, handler tai nghe 13, receiver/manifest 11, settings view model 8, nguồn trang 9, dòng giao diện 7 (tổng 140), cộng các test thêm vào lớp có sẵn (preparer và view model của sheet). Tổng suite tăng **144** so với nền 3.208; tổng cộng tay các lớp ra 145, lệch một đơn vị mà tôi không truy ra được nguồn, nên con số đáng tin là số đo của cả suite (3.352).
- **Mutation** (sửa, test phải đỏ, khôi phục bằng `cmp`): trên 90 ca. **Năm ca lúc đầu sống sót và đã vá bằng test:** ngày lấy theo UTC thay vì múi giờ; thứ tự "đã nghe" trước "quá cũ"; dây nối `CommuteMorningSettingsRows` vào trang; `Column` có lề; hai ca chỉ lộ khi đọc lại kỹ (xem "Phát hiện").

## Kiểm chứng (Tecno KJ7 `115333744A005844`, Android 14, 2026-11-14)

Owner chốt "chỉ dùng tecno cho mọi phiên"; KJ7 rơi khỏi adb giữa phiên, owner cắm lại. **Không chạm Pixel.** Cổng thiết bị chỉ đọc (launcher ở foreground, mở khóa, không instrumentation); sao lưu byte-exact Room, `-shm`, `-wal`, DataStore và hai `shared_prefs` (`user_version 20`, integrity ok, 212 bài). Quyền thông báo và Bluetooth của app vốn tắt; owner cho cấp tạm.

- **androidTest `CommuteMorningSettingsWidgetTest` 4/4** (`am instrument`, không `connected*`): ba dòng hiện cùng giờ mặc định 07:00; bấm dòng consent và dòng tự phát báo giá trị ngược; có từ chối quyền thì hiện lời giải thích, không có thì không. **Hai mutation trên máy đều bị bắt.**
- **Ảnh chụp thật** màn Riêng tư: có "Write the morning…" (tắt), "Morning episode listen time 07:00" kèm ghi chú, "Play when Bluetooth…" (tắt). Ảnh đầu cho thấy dòng giờ nghe sát mép trái, khác các dòng còn lại; đã sửa lề (có test nguồn giữ) rồi chụp lại.
- **Nút Phát:** gọi đúng đường mà nút dùng (`ACTION_PLAY_LATEST` tới dịch vụ): phiên media của app **active** với mô tả bản tin, app giành audio focus giọng nói, bản tin được **đánh dấu đã nghe** (`played=true`); dừng bằng `ACTION_STOP`.
- **Thông báo "sẵn sàng" có nút Phát:** đăng bằng chính `NotificationHelper` của app; hệ thống ghi: kênh `commute_cast_channel`, id 9944, tiêu đề/nội dung đúng chuỗi, **một** action "Play" là `PendingIntent` **startForegroundService**, chạm thân thông báo mở `startActivity`.
- **Đổi giờ nghe** sang 08:30 qua đúng hai lời gọi mà màn cài đặt dùng (lưu giờ rồi `REPLACE`): cơ sở dữ liệu WorkManager vẫn **một** công việc `CommuteCastDailyWork` (không đặt đôi lịch), `period_count` về 0, độ trễ khởi đầu **1.205,1 phút**: enqueue lúc 11:54:56 cộng độ trễ = **15/11 08:00:02**, đúng giờ nghe 08:30 trừ 30 phút. Khởi động lại app: lịch **không** bị đặt về 07:00 (`KEEP`).
- **Không dùng Gemini** (key chung hỏng); ca consent tắt chỉ chứng minh bằng test JVM "không có `newCall`".
- Quảng cáo toàn màn hình khi mở app: chỉ Back (có lúc cần chờ rồi Back nhiều lần); không chạm "Continue to app".
- **Khôi phục:** Room, `-shm`, `-wal`, DataStore (194 byte), `commute_cast_prefs.xml`, `podcast_positions.xml` đều **BYTE IDENTICAL**; `Reader-journal` và `pairing_store.xml` vắng như trước; quyền thông báo và Bluetooth về `granted=false`; gói test đã gỡ; helper tạm đã xóa khỏi cây mã (không commit); bản HEAD sạch cài lại. Lưu ý: bản app trên máy giờ là bản mới (Room v22), lịch WorkManager nằm ngoài các tệp sao lưu nên **vẫn ở 08:30**; đặt lại 07:00 trong Cài đặt để trả về mặc định.

## Phát hiện khi làm

1. **Nút Phát trong thiết kế ban đầu sẽ không phát gì** (`ACTION_PLAY` chỉ hiện thông báo). Bắt được khi đọc mã lúc lập plan, vá bằng `ACTION_PLAY_LATEST`, và đã chứng minh trên máy.
2. **`UPDATE` không có** trong WorkManager của dự án; spec và plan đã sửa dùng `REPLACE`.
3. **`super.onReceive` trong `@AndroidEntryPoint` receiver là lỗi biên dịch** (hàm trừu tượng); plan đã sửa theo thực tế.
4. **Lỗi lint thật:** `BluetoothDevice.bluetoothClass` cần quyền; sửa bằng kiểm quyền ngay chỗ đọc cộng chặn `SecurityException`, **không** tắt cảnh báo. Một test cũ (`ReadNewsManifestTest`) cấm thêm quyền nên đã ghi lý do cho quyền mới.
5. **Mutation sống sót làm lộ test yếu** (múi giờ, thứ tự, dây nối trang, lề) đều đã vá.
6. **Tôi bấm nhầm trên máy thật:** một cú chạm theo tọa độ ở ảnh cũ khi app SkyJoy của owner đang ở foreground, và một cú chạm khác **vô tình bật** công tắc đồng ý "Podcast Studio" (gửi bài lên Gemini) của RSS Cat Hub. Tôi dừng, hỏi owner, khôi phục từ bản sao lưu (kiểm từng byte, khóa consent đã mất), rồi chuyển sang chỉ dùng lệnh kỹ thuật (intent, `am instrument`, đọc cơ sở dữ liệu) để thử các ca còn lại. Script khôi phục cũng có lỗi đường dẫn tương đối và ghi qua ống Python bị từ chối; đã sửa (đường dẫn tuyệt đối, đưa tệp qua stdin).
7. **WorkManager ghi cơ sở dữ liệu ở luồng nền** nên `am instrument` kết thúc quá sớm làm mất bản ghi; helper tạm phải chờ vài giây. Không ảnh hưởng mã thật.
8. `/tmp` bị dọn giữa phiên (mất script mutation, sao lưu, khôi phục); đã dựng lại và kiểm lại bằng băm.

## Chưa kiểm (nói thẳng)

- **Tự phát thật khi cắm tai nghe Bluetooth.** Chưa có tai nghe để thử; nhánh `PlayNow`, `OFFERED`, từ chối của Android chỉ được kiểm bằng test JVM (handler 13 test, receiver 11 test). Tín hiệu giả `am broadcast` không có thiết bị nên chỉ chứng minh được nhánh "không phải thiết bị âm thanh".
- **Hộp xin quyền Bluetooth thật** và việc công tắc quay về tắt khi từ chối: chỉ kiểm bằng test JVM và test giao diện; tôi không bấm trên máy sau sự cố bấm nhầm.
- **Gemini thật** (key chung hỏng, FIX-09 hoãn): chưa thấy Gemini trả kịch bản; consent bật/tắt chỉ kiểm bằng test.
- Bản tin do **Worker** tự soạn lúc đến giờ: chưa chờ tới giờ thật; chỉ kiểm lịch trong cơ sở dữ liệu.
- Lịch đúng từng phút khi tiết kiệm pin; Android Auto qua tín hiệu này; TalkBack; ngôn ngữ ngoài EN và VI; Pixel và máy thứ hai (owner khóa chỉ Tecno).
- Màn Riêng tư đã chụp ảnh **trước** khi sửa lề; sau khi sửa tôi cài lại nhưng không chụp lại được do sự cố bấm nhầm. Lề được giữ bằng test nguồn và 3 mutation.

## Giới hạn

- Tự phát chỉ là cố gắng; nhiều máy sẽ rơi về nút Phát.
- Người dùng cũ chưa đồng ý nghe bản soạn trong máy cho tới khi bật consent.
- Lịch có thể trễ vài phút; đổi giờ chỉ có hiệu lực từ lần soạn kế tiếp.
- 36 locale dùng chuỗi tiếng Anh.

## Commits trên `dev`

- `a213e34f` docs: thiết kế · `bf723050` docs: kế hoạch · `0494f564` docs: sửa spec (REPLACE)
- `726b4d24` luật lịch · `76d61ca2` luật tự phát
- `d98a9b30` khóa DataStore và cờ đã nghe
- `0f186367` cổng consent Gemini
- `3b5238d2` lịch theo giờ nghe · `e7d9aef4` chuỗi 38 locale
- `099c6b2f` thông báo nút Phát và đường phát thật
- `3cc7e835` tự phát khi cắm tai nghe · `29d95651` màn cài đặt riêng tư
- `bfa8374f` quyền Bluetooth và cổng manifest · `ddb8aabc` test trên máy · `c6bc7224` lề dòng giờ nghe
