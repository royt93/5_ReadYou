# [ZEN-05] Lịch Zen Daily Edition sai giờ đã chọn

- **Type:** Bug
- **Priority:** `P1 (High)`
- **Estimation:** `3 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`domain/zen/ZenDailyEditionManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/zen/ZenDailyEditionManager.kt), [`domain/sv/DailyEditionWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/DailyEditionWorker.kt#L30-L48)

## Vấn đề thực tế
`ZenDailyEditionManager` khai báo `_morningTime`/`_eveningTime` (`MutableStateFlow<String>`, mặc định `"07:00"`/`"20:00"`) đọc từ `SharedPreferences` (`KEY_MORNING_TIME`, `KEY_EVENING_TIME`) lúc `init`, nhưng **không hề có hàm `setMorningTime()`/`setEveningTime()`** nào ghi giá trị mới vào `prefs` hay cập nhật `StateFlow` — 2 khóa pref này thực chất chưa từng được ghi bởi bất kỳ nơi nào trong codebase (không có nơi gọi `putString(KEY_MORNING_TIME, ...)`). Người dùng không có cách nào thực sự thay đổi giờ phát hành.

Nghiêm trọng hơn: `DailyEditionWorker.enqueueDailyWork()` (dòng 32-44) hoàn toàn không đọc `morningTime`/`eveningTime` — nó tạo `PeriodicWorkRequestBuilder<DailyEditionWorker>(12, TimeUnit.HOURS)` chạy lặp lại **mỗi 12 giờ kể từ thời điểm `enqueueUniquePeriodicWork` được gọi lần đầu** (tức thời điểm user bật tính năng), không neo theo bất kỳ mốc giờ trong ngày nào. Ví dụ user bật tính năng lúc 14:32 sẽ nhận thông báo lúc ~14:32 và ~02:32 mỗi ngày, không phải 07:00/20:00 như Acceptance Criteria gốc của ZEN-03 yêu cầu — và hoàn toàn không phản ứng khi user "đổi" giờ (vì không có cách đổi).

Dù vậy task ZEN-03 (Scheduled Daily Edition) đã được tuyên bố DONE trong `doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md`. Test hiện có (`ZenSpeedReadingIntegrationTest.kt`) không verify lịch chạy đúng giờ, chỉ verify preference được lưu/đọc đúng.

## User Story
> Là người coi trọng sự tập trung trong giờ làm việc,
> Tôi muốn app thực sự gửi bản tin tổng hợp đúng vào khung giờ tôi đã chọn (ví dụ 07:00 và 20:00), không phải một chu kỳ 12 giờ tùy tiện,
> Để tôi kiểm soát được chính xác thời điểm bị làm phiền, đúng như tôi đã cấu hình.

## Acceptance Criteria (Gherkin)
- **Given** `ZenSettingsPage` cho phép người dùng chọn giờ buổi sáng và buổi tối (time picker)
- **When** người dùng chọn giờ mới (ví dụ 06:30 và 21:00) và lưu
- **Then** `ZenDailyEditionManager` phải có hàm cập nhật thực sự ghi giá trị mới vào `SharedPreferences` VÀ cập nhật `StateFlow` tương ứng
- **And** `DailyEditionWorker`/lịch WorkManager phải được re-schedule để lần chạy tiếp theo rơi đúng vào 06:30 hoặc 21:00 (theo mốc gần nhất), không phải cộng dồn cứng 12 giờ kể từ lần enqueue trước
- **And** cơ chế lên lịch phải tính đúng khoảng delay ban đầu (initial delay) tới mốc giờ mục tiêu tiếp theo (dùng `Calendar`/`OneTimeWorkRequest` nối tiếp, hoặc cơ chế tương đương đảm bảo neo đúng giờ trong ngày — không dùng `PeriodicWorkRequestBuilder` cố định chu kỳ độc lập với giờ trong ngày)
- **And** có unit test verify: với một `morningTime`/`eveningTime` cho trước và một "thời điểm hiện tại" giả lập, hàm tính initial delay trả về đúng số mili-giây tới đúng mốc giờ tiếp theo (bao gồm case đã qua giờ trong ngày → phải nhảy sang hôm sau)
- **And** test hiện có không được để lọt qua trường hợp lịch sai giờ (bổ sung thêm test verify lịch chạy đúng giờ, không chỉ test preference lưu đúng).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-05] "Lịch Zen Daily Edition sai giờ đã chọn" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-05_daily-edition-schedule-time-fix.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Cụ thể: xác nhận `ZenDailyEditionManager` chưa có setter cho `morningTime`/`eveningTime`, và `DailyEditionWorker.enqueueDailyWork()` chưa đọc các giá trị này khi tính lịch.
2. Implement fix:
   - Thêm `setMorningTime(time: String)` / `setEveningTime(time: String)` vào `ZenDailyEditionManager`, ghi vào `SharedPreferences` và cập nhật `StateFlow`, đồng thời re-enqueue worker khi giờ thay đổi (nếu tính năng đang bật).
   - Sửa `DailyEditionWorker.enqueueDailyWork()` (hoặc thiết kế lại cơ chế lên lịch, ví dụ dùng 2 `OneTimeWorkRequest` tự re-schedule nối tiếp mỗi lần chạy xong, tính initial delay tới đúng mốc giờ sáng/tối tiếp theo) để bám đúng giờ user cấu hình thay vì chu kỳ 12h cố định từ lúc bật.
   - Đảm bảo UI trong `ZenSettingsPage.kt` gọi đúng setter mới khi user đổi giờ (kiểm tra xem UI đã có time picker cho tính năng này chưa; nếu chưa có, cần bổ sung UI picker localize đủ 6 ngôn ngữ).
3. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
4. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
5. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
6. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## ✅ Completion Report (2026-09-26)

**Điểm audit: 9.5/10** — Acceptance Criteria thỏa mãn 100%:
- ✅ `ZenDailyEditionManager.setMorningTime()`/`setEveningTime()` ghi `SharedPreferences` + cập nhật `StateFlow`, validate `HH:mm` (reject `25:99`, `7:00`, rỗng), re-schedule khi đang bật.
- ✅ `DailyEditionWorker.scheduleNext()` tính initial delay tới mốc giờ gần nhất (qua `ZenDailyEditionManager.millisUntilNextOccurrence`), dùng `OneTimeWorkRequest` + tự re-schedule khi chạy xong; bỏ chu kỳ cứng 12h.
- ✅ `doWork()` re-schedule dựa trên giờ đã lưu trong prefs (đọc lại mỗi lần chạy, bền vững với reboot/cache clear).
- ✅ UI time picker trong `ZenSettingsPage` (nội tại `EditionTimeRow` + `TimePickerDialog` hệ thống), nội địa hóa 6 ngôn ngữ (`zen_morning_edition_time`, `zen_evening_edition_time`).
- ✅ Magic number trích thành hằng (`MAX_UNREAD_ARTICLES`, `MORNING_START/END_HOUR`, `WORK_NAME`, `TIME_PARTS`, `DEFAULT_PICKER_HOUR`) theo R5.
- −0.5 vì không có test cho re-schedule chain bên trong `doWork()` (cần HiltTest infrastructure chưa có).

**Tests thêm:**
- Unit (`ZenDailyEditionManagerTest`, 10 test mới): `isValidTime` 12 case, `millisUntilNextOccurrence` (trước/sau giờ, rollover hôm sau, đúng mốc, invalid), setter persist + reject + late-load safety.
- Widget (`ZenSettingsPageTest`, 5 test mới trên Pixel 7 Pro): render `EditionTimeRow`, không tự callback lúc render, `formatTime`/`parseTime` round-trip + fallback.
- Integration (`ZenSpeedReadingIntegrationTest`: `zenDailyEditionManager_setTimesPersistAndSchedule`): enable + set 06:30/21:00 → prefs đúng + delay hợp lệ.
- Full: `assembleDevDebug` + `testDevDebugUnitTest` + `connectedDevDebugAndroidTest` (ZenSpeedReading ×8, ZenSettingsPage ×5) pass trên Pixel 7 Pro (`2B051FDH3006MU`).

**Smoke test (Pixel 7 Pro `2B051FDH3006MU`, 2026-09-26 22:07):**
- App khởi chạy sạch (`SplashActivity` resumed, process alive, không FATAL).
- Logcat `D DailyEditionWorker: Scheduled next daily edition in 31927s` (enable, 22:07→07:00 sáng mai ✓), `30127s` sau khi set 06:30 (→06:30 sáng mai ✓), `30127s` sau set 21:00 (mốc gần nhất vẫn 06:30 ✓).

**Commit:** `11a25f9f`
