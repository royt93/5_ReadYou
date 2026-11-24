# [REL-05] Việc của owner trước khi phát hành bản mới (Android + AdMob)

Cập nhật 2026-11-24. Phần này tôi không làm được thay: Play Console, AdMob Console. Không có giá trị bí mật trong file này (ID, hash, khoá nằm ở `myKeyStore`, repo private).

Quyết định của owner đã chốt:
- Giữ AppLovin trong APK (không exclude `applovin-sdk`), khai Data safety cho cả hai SDK.
- Giữ interstitial ở "Next Article" (xem `audit_gemini.md`, mục rủi ro hạn mức chung).

## 1. UMP (đồng ý quảng cáo): publish message thật

Máy chỉ thử được bằng message thử. Việc owner làm trên AdMob Console:
1. Privacy & messaging > European regulations: tạo và **publish** message cho đúng App ID (chọn đúng app Android).
2. Privacy & messaging > US states regulations: publish message và bật các bang cần.
3. Kiểm "Ad choices" trong app: Settings > Privacy > Ad choices (đã có mã và test, chưa chạm trên máy).
4. Sau khi publish, thử trên máy ở EEA bằng `umpDebugGeography` (chỉ bản debug) và xác nhận form hiện, chọn "Consent" rồi "Manage".

## 2. Data safety (Play Console), cần owner điền

Nguồn dữ liệu: `doc/task/REL-03_data-safety-audit.md`, mục 5 và mục 7. Tóm tắt phần quảng cáo, cho cả hai SDK vì AppLovin còn trong APK:
- Thiết bị hoặc ID khác (Advertising ID, App Set ID): thu thập, chia sẻ với bên thứ ba, mục đích Quảng cáo/Tiếp thị.
- Quảng cáo hoặc tương tác (impression, doanh thu): chia sẻ với Google và AppLovin, mục đích Quảng cáo.
- Chẩn đoán/Hiệu năng: SDK quảng cáo có thể gửi.
- Khai quyền `AD_ID` (đã có trong manifest) ở mục "Advertising ID" của App content.
- Không khẳng định "mã hoá khi truyền cho mọi dữ liệu" khi `usesCleartextTraffic` còn `true` (SEC-01 còn mở).
- Tôi chưa có bản chụp biểu mẫu hiện tại, và không bắt được gói tin thật của SDK. Owner đối chiếu từng mục với biểu mẫu thật.

## 3. AppLovin (đang giữ trong APK)

- Key SDK và 4 unit AppLovin trong `app.properties` là giá trị cũ, **chưa xác minh**. Vào AppLovin Dashboard kiểm khoá còn hợp lệ và 4 unit đúng app. Khi `IS_ENABLE_ADMOB=true` (hiện tại) AppLovin không chạy, nên sai ở đây không gây lỗi ngay, nhưng nếu ai bật `false` mà key sai thì mất ad.
- Nếu muốn bỏ ~3 MB: exclude `applovin-sdk` build được nhưng `AdManager` vẫn có mã gọi lớp AppLovin ở nhánh AppLovin; chưa chạy R8 release và máy thật cho phương án này. Owner đã chọn giữ.

## 0. Việc phải làm TRƯỚC khi upload AAB (từ audit độc lập 2026-11-24, kết luận GO có điều kiện, 7.5/10)

1. **Play Console > App content > Foreground service permissions:** khai loại **Media playback** (đọc to bài, podcast, CommuteCast). Bản này thêm `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `BLUETOOTH_CONNECT`, `ACCESS_LOCAL_NETWORK` và 3 service `foregroundServiceType=mediaPlayback`. Thiếu khai báo có thể chặn review. Owner đã nhận khai trước khi upload.
2. **Data safety:** thêm Advertising ID, văn bản bài báo gửi tới Gemini (chỉ khi người dùng tự nhập key), máy chủ WebDAV do người dùng cấu hình (có mật khẩu), thiết bị Bluetooth, mạng LAN.
3. **Nâng cấp DB v6 lên v22:** đã có `RoomUpgradeFromV6ToV22Test` (Room mở DB v6 bằng migration production và xác thực schema 22). Chưa nâng cấp được từ một APK bản store thật; nếu có máy dùng bản store, cài đè bản mới và đếm lại feed và bài.
4. **Build đúng task:** `bundleProdRelease` (flavor `prod`). Flavor `dev` cùng `applicationId`. Nếu thiếu `KEYSTORE_FILE` thì Gradle bỏ qua cấu hình ký và ra AAB không ký, nên kiểm chữ ký sau khi build (`keytool -printcert -jarfile app-prod-release.aab`, prefix `67:B2:E6:CD`).
5. **Cờ `-PallowTestAdIds=true` KHÔNG được dùng** khi build để upload.

## Điều đã biết, chưa sửa ở bản này (ghi để sửa bản sau)

- `androidx.activity.ComponentActivity` khai `exported="true"` không có bộ lọc intent trong manifest chính (thêm 2026-09-06 cho bộ test Android 17); nên chuyển sang manifest debug.
- File mapping R8 trong AAB là 264 MiB (`-dontobfuscate` cộng keep rộng cho compose, okhttp3, retrofit2). Google không nêu giới hạn riêng cho mapping Java (800 MB là của debug symbols native); nếu Play từ chối, thu gọn keep rule.
- Mã đổi VIP và `VIP_INTEGRITY_SECRET` nằm sẵn trong dex (VIP đã được owner hoãn).
- `allowBackup="true"`: Room và DataStore `settings` (có `customGeminiApiKey`) vào cloud backup.
- ĐÃ SỬA 2026-11-24: App Open bị chặn khi quay lại từ chooser chia sẻ và Custom Tabs (`AppOpenGuard.leaveForOtherApp`), `ComponentActivity` bỏ khỏi manifest chính, `AboutActivity` exported=false, mapping R8 264 → 127 MiB (bỏ `-keep class androidx.compose.**`).
- Test `CommuteCastViewModelTest` đã fail 1 lần trong full JVM, 5 lần chạy sau đều xanh (lỗi lẻ, chưa tìm nguyên nhân).

## 4. Kiểm lần cuối trước khi đưa lên store

1. Dựng bản **release ký thật** (cần `keystore.jks` trong `myKeyStore`), không cài lên máy test nếu chưa có hash cert đó. Máy owner: hash cert release đã có cho A50s, Pixel, Tecno KJ7.
2. Lần đầu cài bản ký thật: mở app và kiểm logcat `This request is sent from a test device.` ngay lần chạy đầu. Nếu thấy `Use RequestConfiguration` thì **dừng ngay**, không bấm vào ad.
3. Thử trial 1 ngày: chỉ chạy khi cài từ Play (Internal testing), cần Install Referrer; build debug và sideload bỏ qua.
4. Đưa lên **Internal testing** trước, rồi mới Production.
5. Bản dịch hai chuỗi VIP và hai chuỗi Ad choices là dịch máy, chưa người duyệt.

## 5. Giới hạn đã biết

- Ad bằng đơn vị thật: đã chạy rewarded trên Pixel (máy đã đăng ký hash) với build ký thật, nhãn "Quảng cáo thử nghiệm", 0 tín hiệu `Use RequestConfiguration` (2026-11-24, HEAD 2d915178). Chưa thử banner, interstitial, App Open với đơn vị thật.
- Nâng cấp dữ liệu thật (Pixel, 2026-11-24): cài bản R8 đè lên bản 20261122, giữ 2164 bài, 9 feed, Room v22. Chooser chia sẻ và Custom Tabs không bật App Open (log `suppressAppOpenTemporarily`, đối chứng Home thì hiện). Bản R8 mới không crash ở home, Settings, CommuteCast, danh sách, trang đọc, menu, chooser. Chưa mở hết mọi màn (Podcast Studio, AI, widget, tìm kiếm, WebDAV).
- Chưa có máy API 26-28.
- Interstitial và rewarded dùng chung hạn mức: xem `audit_gemini.md`.
