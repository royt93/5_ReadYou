# STRAT-01b (partial; STRAT-01 stays in todo/): soạn bản tin buổi sáng chỉ khi pin không thấp

**Date:** 2026-11-19
**Branch:** `dev`
**Device:** không chạm thiết bị nào (chỉ JVM).
**Scope:** production đổi **4 tệp**: `domain/sv/CommuteWorker.kt` (`setRequiresBatteryNotLow(true)`), `domain/commute/CommuteScheduleMigration.kt` (mới, hàm thuần), `ui/ext/ExtDataStore.kt` (một khóa Boolean `commute_battery_condition_applied` + hai hàm), `RApp.kt` (chọn chính sách theo cờ, ghi cờ sau khi xếp lịch). Không đổi UI, chuỗi, Room (v22), manifest, `app/build.gradle`, mã hóa; không dependency. Task gốc `doc/task/todo/STRAT-01_…md` **vẫn ở todo/**.
**Spec:** `docs/superpowers/specs/2026-11-19-strat-01b-battery-condition-design.md`. Không có plan riêng.

## Việc đã làm
Gherkin gốc STRAT-01 đặt điều kiện "đang cắm sạc/pin > 50%" cho bước **soạn** bản tin (Gemini + giọng tổng hợp, nặng pin). Nay lịch soạn hằng ngày có thêm **`setRequiresBatteryNotLow(true)`** cạnh điều kiện mạng. Máy đã cài đang có lịch cũ không có điều kiện, và `KEEP` không đổi ràng buộc, mà WorkManager `2.8.0-alpha02` **không có `UPDATE`**; nên khi khởi động lần đầu sau nâng cấp, app dùng `REPLACE` **đúng một lần** (hàm `CommuteScheduleMigration.policyFor(applied)`), ghi cờ **sau** khi xếp lịch, và từ đó dùng `KEEP` (để mỗi lần mở app không dời giờ chạy). Đổi giờ nghe vẫn dùng `REPLACE` nên cũng mang điều kiện mới.

## Kiểm chứng (đo thật, JVM)
- **TDD:** test đỏ vì lỗi biên dịch (chưa có `CommuteScheduleMigration`), rồi xanh. Hai test cũ khẳng định ngược ("không có ràng buộc pin") đã đổi ý nghĩa: `theNetworkConstraintIsKept_andTheBatteryNotLowConditionIsAdded_butNotACharger` và `theWorkerTakesTheBatteryNotLowConstraint_butNoChargerConstraint`; thêm 4 test mới (hai test chính sách, ràng buộc khi đổi giờ, nối `RApp` đúng thứ tự).
- **Đột biến JVM 6/6:** b1 bỏ ràng buộc, b2 đảo chính sách, b3 không ghi cờ, b4 ghi cờ **trước** khi xếp lịch (thực chất: bỏ dòng ghi cờ ở chỗ chuyển), b5 luôn `REPLACE`, b6 đòi cắm sạc. 5/6 khớp dự đoán; **b5 lệch một test**: đỏ thêm `startupPassesTheSavedListenTime_andKeepsTheSchedule` (test cũ cấm chữ `REPLACE` trong lệnh khởi động), nên dự đoán thiếu một test, không phải test yếu. Mã khôi phục giống hệt (`cmp`), `git diff` production rỗng.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.921 test, 0 lỗi, 0 bỏ qua** (3.917 + 4). **Một lần chạy đầu đỏ 1 test không liên quan** (`BrainRpgRepositoryTest.firstProgressRead_importsLegacyPrefsBeforeExposingRoomState`, dòng 65); chạy riêng lớp đó **3 lần (rerun) đều xanh** và chạy lại cả bộ **xanh**: chập chờn, ghi thẳng, chưa tìm gốc.

## Giới hạn (nói thẳng)
- **Không đạt ngưỡng 50% của Gherkin.** `setRequiresBatteryNotLow` dùng mức "pin thấp" của hệ thống Android (khoảng 15 đến 20%); WorkManager không có ngưỡng tùy ý. Không có tùy chọn "chỉ khi đang sạc". Đúng lựa chọn của bạn (đặt ở bước soạn, không đọc `BatteryManager`).
- **Người dùng pin thấp cả ngày không có bản tin hôm đó** (WorkManager hoãn rồi chạy khi pin hồi; nếu quá giờ nghe thì bản tin đến muộn hoặc bỏ lỡ).
- **`REPLACE` một lần dời lần chạy kế tiếp** về "delay tính từ lúc nâng cấp" (cùng cách tính lúc cài mới): bản tin của đúng ngày nâng cấp có thể soạn sớm hoặc muộn vài tiếng một lần.
- **Chưa kiểm trên máy:** WorkManager thật hoãn khi pin thấp, `REPLACE` trên máy đã có lịch cũ. Chỉ có test JVM (cấu trúc ràng buộc + chuỗi khởi động).
- Các yêu cầu Gherkin gốc STRAT-01 còn lại không làm: "tự chọn 10 bài theo thói quen đọc", phát hoàn toàn không chạm (Android chặn), tự phát khi nối Android Auto.
