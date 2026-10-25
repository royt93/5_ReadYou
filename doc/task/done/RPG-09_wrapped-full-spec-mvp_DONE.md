# [RPG-09] Brain Wrapped 9:16 — MVP Completion Report

- **Trạng thái:** MVP hoàn tất trên nhánh `dev`; chưa push.
- **Ngày:** 2026-10-25
- **Phạm vi hoàn tất:** Weekly PNG Story 1080×1920, XP event log Room v19, XP tuần + chủ đề theo event, streak hiện tại, Neon Canvas renderer và chia sẻ qua FileProvider/Android Sharesheet.
- **Ngoài phạm vi MVP:** Radar chart, QR tải app, banner Chủ Nhật, danh hiệu Top X%, XP backfill lịch sử.

## Thay đổi chính

1. `BrainRpgXpEventEntity` và Room v19: event chứa category, XP delta, reward type, timestamp. Migration 18→19 tạo bảng/index, không backfill dữ liệu cũ.
2. READ/QUIZ/DOUBLE/TRIAGE reward ghi event cùng transaction ledger/profile/category; duplicate reward và transaction rollback không tạo event giả.
3. Week range là Thứ Hai 00:00 đến Thứ Hai kế tiếp theo timezone địa phương; truy vấn nửa mở `[start, end)`. Chủ đề tuần tổng hợp XP event, tie-break theo `categoryId`; không dùng all-time category XP. Hiển thị streak hiện tại, không suy ra streak tuần.
4. `BrainWrappedRenderer` vẽ PNG 1080×1920 phong cách Neon celebratory. `BrainWrappedSharer` ghi cache riêng, tạo `image/png` FileProvider intent có URI read grant.
5. `BrainRpgViewModel` nạp summary và chạy render/file I/O trên dispatcher phù hợp. Share button disable khi tuần có 0 XP; lỗi hiển thị localized.
6. Bổ sung 13 strings đầy đủ trên 38 locale.

## Kiểm chứng

- Baseline trước code: `./gradlew testDevDebugUnitTest --rerun` pass 1283 tests.
- `BrainRpgWeekTest`: 3 tests pass; local Monday/Sunday/timezone boundaries.
- `BrainRpgWeeklySummaryTest`: 3 tests pass; half-open range, category tie, empty week, current streak.
- Room DAO/repository/triage event tests pass; event only on successful new reward, duplicate/failure no event.
- `BrainWrappedRendererTest`, `BrainWrappedSharerTest`, `BrainRpgViewModelWrappedTest`, `WeeklyWrappedCardTest` pass.
- `StringResourceParityTest`: 6/6 pass, 38 locale resource directories.
- Full `./gradlew testDevDebugUnitTest --rerun`: **1303 tests, 0 failures/errors**.
- `./gradlew assembleDevDebug`, `lintDevDebug`, `compileDevDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**; lint 0 errors.
- Selected Pixel instrumentation, invoked explicitly through `adb -s 2B051FDH3006MU shell am instrument` (not Gradle connected fan-out): **4/4 pass**. Covers Room migration, FileProvider URI PNG decode, 1080×1920 dimensions, and share button enabled/disabled.
- **Pixel 7 Pro smoke (serial `2B051FDH3006MU`):** app opened Brain RPG; initial zero-week XP state disabled share. Read + correct quiz created two new events (`General Knowledge`: READ +50, QUIZ +150), and UI enabled share. Sharesheet opened with PNG preview. Extracted PNG verified 1080×1920. Topic is shown as `Tri Thức Tổng Hợp`; current streak shown as 1 day. No full-screen interstitial appeared during this flow; test banner was visible elsewhere and not touched.
- Pixel Room snapshot after smoke: version 19, `integrity_check=ok`, profile total XP 800, 3 streak days at snapshot time, 2 XP events created after upgrade. No restore performed. Snapshot archive: `/tmp/rpg09-final-device-db.tar`.

## Giới hạn đã ghi nhận

- XP events chỉ được theo dõi từ migration trở đi; không có backfill giả cho XP lịch sử. Tuần trước/nâng cấp không có events có thể hiện weekly XP 0.
- Device broad Gradle task `connectedDevDebugAndroidTest` đã broadcast sang TECNO cùng Pixel; không dùng lại. Targeted instrumentation được chạy bằng explicit Pixel serial. Một lần thử migration fixture ban đầu thiếu schema và fail; fixture đã sửa, targeted Pixel instrumentation sau đó pass 4/4.
- Pixel smoke tạo XP/event mới; owner chọn giữ dữ liệu, không restore.

## Commits local trên `dev`

- `e1d4581b` local week boundaries
- `fbb38b75` Room v19 migration/entity
- `a1062981` atomic XP events
- `10e9d53a` weekly aggregate
- `fa691813` Canvas renderer + share helper
- `22a2a818` ViewModel/UI/localization
- `0f204bf6` instrumentation tests
- `e2bbfa68` fixture fixes
- `6b3d1fc0` migration fixture and long-topic layout polish

Các commit RPG-09 vẫn local; chờ owner chọn push. Không thêm radar/QR/banner/percentile vào MVP.
