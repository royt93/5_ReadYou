# RPG-03 — Hộp thoại cứu chuỗi đọc (Streak Rescue) — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Phần lõi của RPG-03 đã có từ trước: XP theo chuyên mục **hiển thị** giảm theo `floor(base x 0.9^số tuần)` (RPG-07), thông báo suy thoái hằng ngày, hồi sinh một chuyên mục bằng rewarded ad, nút Streak Shield thủ công, và 6 hành vi streak đã có test (RPG-06). Task này chỉ thêm phần còn thiếu theo Gherkin gốc: **hộp thoại "mất chuỗi" khi mở app**.
- **Có:** khi mở app mà chuỗi đã lưu >= 3 ngày và đã đứt (nghỉ >= 2 ngày), hiện hộp thoại tối đa 1 lần mỗi ngày UTC với ba lựa chọn: `Xem video để giữ chuỗi` (rewarded ad, bật Streak Shield), `Bắt đầu lại từ đầu` (đặt chuỗi về 0 và lưu), `Để sau` (không đổi dữ liệu; chạm ra ngoài hay Back cũng là "để sau").
- **Không có:** định dạng quảng cáo mới, mua hàng, hoàn tác "bắt đầu lại", đổi công thức decay hay `nextStreak`, đổi schema Room (DB vẫn v20).
- Owner chọn giữ nút rewarded ad dù nhóm ad/IAP đang hoãn đến ~2026-11-18; chỉ tái dùng `AdManager.showRewarded`, không thêm cơ chế ad nào.

## Cấu trúc mã
- `domain/model/rpg/StreakRescuePolicy.kt`: chính sách thuần Kotlin (`MIN_STREAK_TO_OFFER = 3`, khoảng nghỉ >= 2 ngày, chưa bật shield, chưa hỏi hôm nay, chưa đọc lần nào thì không hỏi, đồng hồ lùi thì không hỏi).
- `BrainRpgRepository`: `resetBrokenStreak()` và `rescueBrokenStreak()` chạy dưới `mutationMutex` và **kiểm tra lại điều kiện** (kể cả ngưỡng chuỗi tối thiểu) vì callback của ad là bất đồng bộ, có thể đến trễ hoặc hai lần; chỉ trả `true` khi dữ liệu thật sự đổi.
- `ui/page/rpg/StreakRescuePromptDayStore.kt` (+ `infrastructure/di/StreakRescueModule.kt`): nhớ ngày đã hỏi trong DataStore (`DataStoreKeys.StreakRescueLastPromptDay`), có bản giả trong test.
- `ui/page/rpg/StreakRescueViewModel.kt`: đồng hồ và trình phát ad được inject (constructor phụ cho Hilt, vì Hilt không cung cấp được tham số kiểu hàm); trạng thái chỉ giữ cờ `adFailed`, chuỗi được localize ở UI nên ViewModel không giữ `Context`.
- `ui/component/rpg/StreakRescueDialog.kt` và `HomeEntry.kt`: `AlertDialog` Material 3 1.0.1 chỉ có hai khe nút nên cả ba nút nằm trong một cột ở `confirmButton`; khi ad đang tải thì khóa cả ba nút.
- 6 chuỗi `streak_rescue_*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh dự phòng).

## Phát hiện khi làm
1. **Lỗi thật ở test ViewModel:** `StreakRescueViewModel` không bao giờ chuyển sang `Prompt` dù policy trả offer. Nguyên nhân thật (xác minh bằng một probe tạm đã xóa): Room trong test dùng executor nền nên `advanceUntilIdle()` không đợi được; đặt `setQueryExecutor { it.run() }` và `setTransactionExecutor { it.run() }` (mẫu sẵn có của `BrainRpgViewModelTest`) thì cả 7 test xanh. Đây là lỗi của test, không phải của ViewModel.
2. **Ngưỡng chuỗi tối thiểu chưa được kiểm tra lại trong repository** (chỉ policy kiểm): một callback ad đến trễ có thể bật shield cho chuỗi mà policy sẽ không bao giờ đề nghị. Test đỏ trước, sửa sau, thêm 2 test biên (dưới và đúng bằng ngưỡng).
3. Đọc DB trên máy chỉ từ file chính sẽ bỏ sót thay đổi còn nằm trong WAL: lần đầu tôi đọc thấy chuỗi vẫn 5 sau "Bắt đầu lại", đọc đủ `Reader` + `Reader-wal` mới thấy 0. Ghi chú để các lần smoke sau luôn đọc cả ba file.

## Kiểm chứng
- **Unit:** 1.702 test, 0 lỗi, 0 skip (nền trước task 1.672, thêm 30: `StreakRescuePolicyTest` 9, `BrainRpgRepositoryStreakRescueTest` 9, `StreakRescueViewModelTest` 7, `StreakRescueDialogTest` 5). 31 test cũ của `BrainRpgRepositoryTest` vẫn xanh. `StringResourceParityTest` 6/6.
- **Lint:** 0 lỗi, 2.459 cảnh báo (nền 2.454); tra báo cáo XML không có cảnh báo nào nằm trong file hay chuỗi của task. **Build:** `assembleDevDebug` (đã kiểm đồ thị Hilt) và `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** 3/3 (`StreakRescueDeviceTest`).
- **Smoke thật trên Pixel (seed chuỗi 5, đọc lần cuối 3 ngày trước):** hộp thoại hiện đúng "lỡ 3 ngày", "Chuỗi 5 ngày" với cả ba nút. **Để sau:** hộp thoại đóng, Room vẫn `5|20755|0`. **Bắt đầu lại từ đầu:** hộp thoại đóng, Room (file chính + WAL) thành `0|20755|0`. **Xem video:** nhấn nút thì SDK ad báo `AdMobRewarded: NOT READY`, hộp thoại giữ nguyên và hiện "Không thể tải video. Vui lòng thử lại.", Room không đổi (`5|20755|0`), thử lại lần hai cho kết quả như vậy.

## Giới hạn đã biết
1. **Nhánh ad thành công chưa được thấy trên máy thật:** rewarded ad của AdMob thử nghiệm không sẵn sàng (`NOT READY`) trong cả hai lần thử, nên chỉ kiểm được nhánh thất bại trên máy. Nhánh thành công (bật shield, hộp thoại đóng) được kiểm bằng test ViewModel với trình phát ad giả và test repository, không phải bằng ad thật.
2. Ngày tính theo UTC (quyết định cũ của RPG-06, giữ nguyên); Streak Shield bắc cầu mọi khoảng nghỉ chứ không chỉ 1 ngày; "Bắt đầu lại" không hoàn tác được.
3. Hộp thoại chỉ hiện khi mở app (một lần mỗi ngày UTC); không hiện lại khi quay lại app từ nền trong cùng ngày.
4. Ad toàn màn hình (`AdActivity`) xuất hiện mỗi lần mở app trong lúc smoke; đã tự đóng bằng nút "Tiếp tục đến ứng dụng", không chạm nội dung quảng cáo.

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu cả Room (`Reader`, `Reader-shm`, `Reader-wal`) và DataStore (không rỗng, `integrity_check` ok trên bản sao, 214 bài). Sau smoke khôi phục cả hai bằng `run-as sh -c 'cat > ...'`; `cmp` xác nhận cả ba file Room và DataStore giống hệt bản sao lưu, `integrity_check` ok, 214 bài, gói test đã gỡ.

## Commits trên nhánh `dev`
- `24dd9671` docs: thiết kế RPG-03
- `ed0ff2dc`, `9449c9e0`, `539427e5` docs: kế hoạch RPG-03
- `b2ee401f` feat(rpg-03): add pure streak rescue prompt policy
- `c7d3aaa1` feat(rpg-03): add resetBrokenStreak and rescueBrokenStreak to repository
- `a839e3e2` feat(rpg-03): add streak rescue prompt day key and strings across 38 locales
- `1ad43304` fix(rpg-03): revalidate the offer minimum streak inside rescue and reset
- `b82623dc` feat(rpg-03): add StreakRescueViewModel with injectable prompt store and rewarded ad launcher
- `251c1c86` feat(rpg-03): add StreakRescueDialog and show it from HomeEntry on startup
- commit hoàn tất (test instrumentation và tài liệu) nằm ngay sau các commit trên
