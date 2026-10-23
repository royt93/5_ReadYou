# [FIX-20] Chặn secret lọt logcat từ SettingsProvider — Báo cáo hoàn thành

- **Trạng thái:** Đã thực thi Inline Execution và kiểm chứng trên nhánh `dev`; commit `379096e9` đã được đẩy lên `origin/dev`.
- **Ngày hoàn thành:** 2026-10-23
- **Audit:** 9/10. Xoá dòng dump toàn bộ DataStore `Preferences` trong `SettingsProvider` (`Settings.kt:226`) và import `android.util.Log` thừa; ngăn các AI API key người dùng nhập (`customGeminiApiKey`, `customOpenAiApiKey`, `customDeepSeekApiKey`, `customGroqApiKey`) xuất hiện trong logcat. Regression test Compose/DataStore chứng minh sentinel AI key không lọt log trong khi settings theme vẫn được cập nhật bình thường.
- **Không thuộc phạm vi:** Không xoá logcat lịch sử trên thiết bị, không đổi nơi lưu hay mã hoá key, không thu hồi/rotate key, không mở lại FIX-09 (vẫn deferred).

## Bằng chứng phát hiện

Trong phiên smoke test podcast ngày 2026-10-23 trên Pixel 7 Pro, một lần đọc logcat chẩn đoán đã thấy dòng `Log.i("RLog", "AppTheme: ${it}")` in toàn bộ `Preferences`, trong đó có trường `customGeminiApiKey`. Giá trị key không được lưu hay ghi lại vào báo cáo này.

Kiểm tra mã nguồn cho thấy:
- `SettingsProvider` (`app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt:224-228`) đăng ký quan sát `context.dataStore.data` và dump toàn bộ đối tượng `Preferences` mỗi khi có thay đổi cấu hình.
- Các credential tài khoản khác (Fever, Google Reader) nằm ở Room `Account.securityKey`, còn WebDAV dùng `SharedPreferences` riêng (`webdav_backup_prefs`), không đi qua DataStore này.
- Bản build release có rule ProGuard `-assumenosideeffects` gỡ `android.util.Log`, nhưng các bản debug/dev và logcat phát sinh trong quá trình dùng/test trước đây vẫn chịu rủi ro.

## Thay đổi thực hiện

1. `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt`:
   - Xoá `import android.util.Log`.
   - Xoá dòng `Log.i("RLog", "AppTheme: ${it}")` trong lambda map của `SettingsProvider`. Lambda map chỉ còn gọi `it.toSettings()`.
2. `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/SettingsProviderLoggingTest.kt`:
   - Viết mới test Compose + Robolectric (`createComposeRule`).
   - Ghi 4 sentinel giả đại diện cho 4 AI key (`CustomGeminiApiKey`, `CustomOpenAiApiKey`, `CustomDeepSeekApiKey`, `CustomGroqApiKey`) và `ThemeIndex` thử nghiệm vào DataStore `settings`.
   - Render `SettingsProvider`, dùng `waitUntil` chờ theme index cập nhật tới Compose để bảo đảm flow đã emission.
   - Assert `LocalThemeIndex` nhận đúng giá trị mới; assert không log nào tag `RLog` chứa tiền tố `AppTheme:` hay bất kỳ sentinel nào trong 4 key giả.
   - Dọn sạch các key thử nghiệm trong `tearDown` để tránh lan sang test khác.

## Kết quả kiểm chứng

- **TDD RED:** Chạy `./gradlew testDevDebugUnitTest --tests '*SettingsProviderLoggingTest' --rerun` trên mã trước khi sửa: **FAIL** với `AssertionError: Log không được chứa tiền tố AppTheme: dump`, xác nhận test bắt đúng dòng log rò rỉ.
- **TDD GREEN:** Chạy lại sau khi xoá dòng log: **BUILD SUCCESSFUL**, 1/1 test pass.
- **Full unit suite:** Chạy `./gradlew testDevDebugUnitTest --rerun`: **BUILD SUCCESSFUL in 54s**, **1251 unit tests pass** (210 classes cũ + 1 class mới, 0 failures, 0 errors, 0 skipped), không dùng `JAVA_TOOL_OPTIONS`. Đếm trực tiếp từ các file XML mới sinh.
- **Build APK & androidTest compile:** `./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin`: **BUILD SUCCESSFUL in 6s**.
- **Quét mã:** `git grep -n 'AppTheme:.*it'` không trả về kết quả nào trong mã nguồn app.
- Không chạy thiết bị và không đọc lại logcat cho bản sửa này.

## Giới hạn và khuyến nghị bảo mật

- Bản sửa này ngăn việc ghi mới key vào logcat kể từ thời điểm áp dụng. Nó **không** xoá các dòng log đã nằm trong bộ nhớ đệm logcat của thiết bị trước đó và không thể thu hồi key đã phát sinh trong lịch sử.
- Việc đổi (rotate) hoặc thu hồi (revoke) API key trong bảng điều khiển nhà cung cấp (Google Cloud Console, OpenAI, DeepSeek, Groq) là hành động bảo mật độc lập của chủ sở hữu key, không tự động thực hiện trong phiên này.
- FIX-09 (key mặc định nhúng trong APK) tiếp tục giữ trạng thái **deferred** theo chỉ đạo ngày 2026-10-13 của chủ sở hữu.
