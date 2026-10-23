# FIX-20 — Không ghi DataStore settings chứa secret vào log

- Ngày: 2026-10-23
- Trạng thái: đã duyệt, thực thi Inline Execution và kiểm chứng (2026-10-23); commit `379096e9` đã push.
- Không tự commit/push. Không in, lưu hoặc copy giá trị key vào chat/báo cáo.

## Vấn đề đã xác minh

`SettingsProvider` trong `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt:224-228` map `context.dataStore.data` rồi gọi `Log.i("RLog", "AppTheme: ${it}")`. `it` là toàn bộ `Preferences` của DataStore `settings`, nên logcat nhận cả các giá trị lưu trong store này. `ExtDataStore.kt` định nghĩa key AI do người dùng nhập: `customGeminiApiKey`, `customOpenAiApiKey`, `customDeepSeekApiKey`, `customGroqApiKey`.

Trong lúc smoke Pixel ngày 2026-10-23, một lần đọc logcat đã thấy dòng này in giá trị `customGeminiApiKey`. Giá trị đó không được ghi lại vào báo cáo hay spec.

Phạm vi kiểm tra read-only không thấy dump tương tự với Fever/Google Reader: credential của chúng thuộc `Account.securityKey` trong Room, còn WebDAV dùng `SharedPreferences` riêng (`webdav_backup_prefs`). Đây là kết luận từ tìm kiếm mã nguồn, không phải chứng minh mọi exception log không bao giờ chứa dữ liệu nhạy cảm. Release hiện có rule R8 `-assumenosideeffects` cho `android.util.Log` (`app/proguard-rules.pro:120`); debug build, dev variant và log đã tạo trước đó vẫn bị ảnh hưởng, nên không xem đây là biện pháp thay thế.

## Mục tiêu

Ngừng ghi nội dung `Preferences` vào logcat mà vẫn giữ nguyên luồng cập nhật settings của giao diện.

## Không thuộc phạm vi

- FIX-09/key nhúng APK, mã hoá hoặc đổi nơi lưu key người dùng, thu hồi/rotate key, xoá logcat đã có.
- Redact toàn bộ log hoặc audit mọi exception message trong ứng dụng.
- Schema Room, UI, dependency, ad/IAP/money.
- Không đổi hành vi `toSettings()`, `collectAsStateValue`, `CompositionLocalProvider`.

## Thiết kế

1. Xoá hẳn dòng `Log.i("RLog", "AppTheme: ${it}")` trong `SettingsProvider`. Lambda `map` chỉ còn gọi `it.toSettings()`.
2. Xoá `import android.util.Log` nếu không còn dùng trong file `Settings.kt`.
3. Không thay bằng log "an toàn", allowlist hoặc blacklist tên key. Một dump generic có thể lại lộ preference nhạy cảm được thêm sau này; nhu cầu debug theme chưa có bằng chứng.
4. Thêm regression test Robolectric/Compose tại `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/SettingsProviderLoggingTest.kt`:
   - Dùng application test tối giản, `ShadowLog.clear()`.
   - Ghi vào DataStore `settings` các key AI thật của dự án bằng sentinel fake riêng cho Gemini/OpenAI/DeepSeek/Groq, cộng một preference không nhạy cảm đã biết (ví dụ theme index) để chứng minh dữ liệu đã đi qua flow settings.
   - Render `SettingsProvider`, đợi idle, rồi đọc log bằng `ShadowLog.getLogs()`.
   - Assert không log nào có tag `RLog` chứa bất kỳ sentinel nào hoặc chuỗi `AppTheme:`; assert giá trị theme thực sự được cung cấp qua `LocalThemeIndex` để tránh test pass vì provider không chạy.
   - Cleanup DataStore/test state bằng API thực tế sẵn có; không để key giả lan sang test khác.
5. TDD: test RED trên mã hiện tại phải fail vì log chứa sentinel. Sau khi xoá dòng log, test GREEN. Nếu test chỉ fail vì thiếu DataStore setup thì sửa fixture, không tính là RED hợp lệ.

## Xử lý lỗi và an toàn dữ liệu

- Không thêm try/catch hoặc retry. Thay đổi chỉ loại bỏ một side effect log.
- Test không dùng key thật, không đọc DataStore của thiết bị, không chạy device.
- Báo cáo ghi nguồn dòng, loại key bị ảnh hưởng và kết quả test; không chứa giá trị key, kể cả một phần.
- Ghi rõ bản sửa không xoá logcat/buffer hiện có và không chứng minh key chưa bị người khác thu thập. Việc rotate/revoke key là quyết định thủ công riêng của chủ sở hữu.

## Kiểm chứng

- RED: `./gradlew testDevDebugUnitTest --tests '*SettingsProviderLoggingTest' --rerun` fail đúng vì sentinel/`AppTheme:` xuất hiện.
- GREEN: cùng lệnh pass.
- Chạy focused test settings/theme liên quan nếu có, rồi full `./gradlew testDevDebugUnitTest --rerun`; đếm XML mới và không tính `FROM-CACHE`.
- `assembleDevDebug` và `compileDevDebugAndroidTestKotlin`.
- Dùng `rg` kiểm tra không còn `Log.*Preferences/DataStore dump` trong mã app đã quét; không claim đã audit mọi log.
- Không chạy device hoặc đọc logcat thiết bị cho bản sửa này.

## Tiêu chí chấp nhận

1. `SettingsProvider` không còn ghi `Preferences` hoặc chuỗi `AppTheme:` vào log.
2. Regression test chứng minh sentinel AI key không lọt log trong khi settings vẫn đi qua provider.
3. Full unit suite, build và androidTest compile pass bằng bằng chứng mới.
4. Tài liệu báo cáo không có secret và nêu rõ phần ngoài phạm vi.
5. Không commit/push khi chưa được yêu cầu.
