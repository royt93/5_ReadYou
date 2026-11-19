# STRAT-01b — Soạn bản tin buổi sáng chỉ khi pin không thấp (thiết kế ngắn, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** STRAT-01 (bản thu hẹp), `CommuteWorker.enqueueDailyWork`

> Trạng thái: owner đã chốt (2026-11-19): điều kiện đặt ở **bước soạn** (không phải bước tự phát); dùng **`setRequiresBatteryNotLow(true)`**; máy đã cài nhận qua **REPLACE đúng một lần** khi nâng cấp (cờ di cư trong DataStore), sau đó vẫn KEEP. Chưa có plan, chưa có mã. Chỉ JVM, không thiết bị.

## Mục tiêu
Gherkin gốc STRAT-01: WorkManager soạn bản tin lúc 06:30 "với điều kiện máy đang cắm sạc/pin > 50%". Bản thu hẹp bỏ điều kiện này; việc soạn (gọi Gemini + tạo giọng) tốn pin. Nay soạn chỉ khi pin **không thấp**.

## Nói thẳng về ngưỡng
`setRequiresBatteryNotLow(true)` dùng mức "pin thấp" của **hệ thống Android** (khoảng 15 đến 20%), **không phải 50%**. WorkManager không có ngưỡng tùy ý; muốn đúng 50% phải tự đọc `BatteryManager` trong worker rồi hoãn (`retry`), điều owner **không chọn** (đổi giờ soạn khó đoán, có thể mất bản tin). Báo cáo ghi rõ: **không đạt ngưỡng 50% của Gherkin**, chỉ đạt "không soạn khi pin thấp". Không có tùy chọn "chỉ khi đang sạc".

## Sự thật đã đọc (2026-11-19)
- `CommuteWorker.enqueueDailyWork` (`PeriodicWorkRequest` 24 giờ, delay đến 30 phút trước giờ nghe) chỉ có `setRequiredNetworkType(CONNECTED)`. Gọi từ đúng một chỗ lúc khởi động (`RApp.workerInit`, `KEEP`) và một chỗ khi người dùng đổi giờ nghe (`PrivacySettingsViewModel`, `REPLACE`).
- WorkManager `2.8.0-alpha02` **không có** `ExistingPeriodicWorkPolicy.UPDATE` (chỉ `KEEP`, `REPLACE`), và `KEEP` không đổi ràng buộc của lịch đã có. Máy đã cài vì vậy sẽ **không** nhận điều kiện mới nếu chỉ sửa `enqueueDailyWork`.
- Hai test hiện có **khẳng định điều ngược lại** và phải đổi: `CommuteWorkerScheduleTest.theNetworkConstraintIsKept_andNoChargingOrBatteryConstraintIsAdded` (`!requiresBatteryNotLow`) và `CommuteWorkerScheduleSourceTest.theWorkerStillTakesNoChargingOrBatteryConstraint` (không chứa `setRequiresBatteryNotLow`).
- Tùy chọn Commute lưu trong DataStore (`CommuteListenMinuteOfDay`, `CommuteAutoPlayOnHeadset`...), đọc/ghi qua hàm mở rộng `Context.commute…`.

## Thiết kế
- `enqueueDailyWork`: thêm `.setRequiresBatteryNotLow(true)` vào `Constraints`. Không đổi network, tên work, chu kỳ, delay.
- **Cờ di cư** `DataStoreKeys.CommuteBatteryConstraintApplied` (Boolean, DataStore, **một khóa mới**, chỉ-thêm): chưa có = chưa áp điều kiện. Hàm thuần `CommuteScheduleMigration.policyFor(applied: Boolean): ExistingPeriodicWorkPolicy` = `REPLACE` khi chưa áp, `KEEP` khi đã áp.
- `RApp.workerInit`: chọn chính sách bằng hàm trên, gọi `enqueueDailyWork` với chính sách đó, rồi **sau khi gọi thành công** ghi cờ `true` (ghi cờ **sau** khi xếp lịch để một lần thất bại không đánh dấu nhầm là đã áp). `PrivacySettingsViewModel` (đổi giờ) vốn dùng `REPLACE` nên cũng áp điều kiện; không cần đổi, nhưng test kiểm.
- Không đổi UI, chuỗi, Room (v22), manifest, `app/build.gradle`, mã hóa; không dependency.

## Phạm vi trung thực
- **Chứng minh (JVM):** ràng buộc có `requiresBatteryNotLow` cùng network; `policyFor` đúng; khởi động gọi chính sách theo cờ và ghi cờ sau; gọi lần hai dùng KEEP; đổi giờ vẫn REPLACE; mọi test cũ về lịch còn đúng (giờ, chu kỳ, tên).
- **Không chứng minh:** WorkManager thật trên máy (hoãn khi pin thấp), `REPLACE` trên máy đã có lịch, thời điểm Android chạy. Không đụng thiết bị.
- **Rủi ro:** `REPLACE` một lần **dời lần chạy kế tiếp** về "delay tính từ lúc nâng cấp" (cùng cách tính với lúc cài mới); bản tin của đúng ngày nâng cấp có thể soạn sớm hoặc muộn vài tiếng một lần. Người dùng pin thấp cả ngày **không có bản tin hôm đó** (nói thẳng).

## Kiểm chứng
- TDD: test đỏ trước; đột biến JVM (bỏ `setRequiresBatteryNotLow`; `policyFor` đảo; không ghi cờ; ghi cờ **trước** khi xếp lịch; bỏ kiểm cờ nên REPLACE mọi lần khởi động) với bảng dự đoán suy luận từng test.
- JVM full `--rerun-tasks` (số trước 3.917). Diff production: `CommuteWorker.kt`, `RApp.kt`, `ExtDataStore.kt` (một khóa + hai hàm), một tệp mới `CommuteScheduleMigration.kt`.
