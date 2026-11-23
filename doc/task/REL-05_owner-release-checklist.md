# [REL-05] Việc của owner trước khi phát hành bản mới (Android + AdMob)

Cập nhật 2026-11-23. Phần này tôi không làm được thay: Play Console, AdMob Console, quyết định khoá ký. Không có giá trị bí mật trong file này (ID, hash, khoá nằm ở `myKeyStore`, repo private).

Quyết định của owner đã chốt:
- Keystore hiện tại giữ nguyên, không đổi.
- Giữ AppLovin trong APK (không exclude `applovin-sdk`), khai Data safety cho cả hai SDK.
- Giữ interstitial ở "Next Article" (xem `audit_gemini.md`, mục rủi ro hạn mức chung).

## 1. Khoá ký đã lộ (rủi ro lớn nhất, chưa xử lý)

`keystores.jks` và `keystore.properties` từng nằm trong git của repo **public** (2024-06-01 và 2026-01-03). Untrack chỉ chặn lộ thêm, không thu hồi khoá. Owner giữ keystore hiện tại, nên:

1. Play Console > App > Setup > App signing: kiểm app đã bật **Play App Signing** chưa.
   - Đã bật: khoá lộ là *upload key*, kẻ có khoá chỉ ký được bản "upload", không ký được bản người dùng nhận. Vẫn nên reset upload key (Play Console > App signing > Request upload key reset).
   - Chưa bật: khoá lộ chính là khoá phát hành. Không thay được cho app đã đăng; ai có khoá có thể ký bản giả cho cùng package. Đây là rủi ro cần owner cân nhắc, tôi không có cách sửa từ phía mã.
2. Nếu reset upload key: tạo keystore mới, đặt vào `myKeyStore/com.mckimquyen.reader/` và cập nhật `KEYSTORE_FILE`, `KS_*` trong `app.properties`. Hash test-device phụ thuộc chứng chỉ ký, nên phải probe lại bằng APK không có ad SDK (công cụ có trong `~/exc-flaky-evidence/tecno-hash-probe-20261123`), chỉ thêm, không xoá.
3. Không xoá lịch sử git (filter-repo, force-push) nếu chưa chắc: tôi không tự làm.

## 2. UMP (đồng ý quảng cáo): publish message thật

Máy chỉ thử được bằng message thử. Việc owner làm trên AdMob Console:
1. Privacy & messaging > European regulations: tạo và **publish** message cho đúng App ID (chọn đúng app Android).
2. Privacy & messaging > US states regulations: publish message và bật các bang cần.
3. Kiểm "Ad choices" trong app: Settings > Privacy > Ad choices (đã có mã và test, chưa chạm trên máy).
4. Sau khi publish, thử trên máy ở EEA bằng `umpDebugGeography` (chỉ bản debug) và xác nhận form hiện, chọn "Consent" rồi "Manage".

## 3. Data safety (Play Console), cần owner điền

Nguồn dữ liệu: `doc/task/REL-03_data-safety-audit.md`, mục 5 và mục 7. Tóm tắt phần quảng cáo, cho cả hai SDK vì AppLovin còn trong APK:
- Thiết bị hoặc ID khác (Advertising ID, App Set ID): thu thập, chia sẻ với bên thứ ba, mục đích Quảng cáo/Tiếp thị.
- Quảng cáo hoặc tương tác (impression, doanh thu): chia sẻ với Google và AppLovin, mục đích Quảng cáo.
- Chẩn đoán/Hiệu năng: SDK quảng cáo có thể gửi.
- Khai quyền `AD_ID` (đã có trong manifest) ở mục "Advertising ID" của App content.
- Không khẳng định "mã hoá khi truyền cho mọi dữ liệu" khi `usesCleartextTraffic` còn `true` (SEC-01 còn mở).
- Tôi chưa có bản chụp biểu mẫu hiện tại, và không bắt được gói tin thật của SDK. Owner đối chiếu từng mục với biểu mẫu thật.

## 4. AppLovin (đang giữ trong APK)

- Key SDK và 4 unit AppLovin trong `app.properties` là giá trị cũ, **chưa xác minh**. Vào AppLovin Dashboard kiểm khoá còn hợp lệ và 4 unit đúng app. Khi `IS_ENABLE_ADMOB=true` (hiện tại) AppLovin không chạy, nên sai ở đây không gây lỗi ngay, nhưng nếu ai bật `false` mà key sai thì mất ad.
- Nếu muốn bỏ ~3 MB: exclude `applovin-sdk` build được nhưng `AdManager` vẫn có mã gọi lớp AppLovin ở nhánh AppLovin; chưa chạy R8 release và máy thật cho phương án này. Owner đã chọn giữ.

## 5. Kiểm lần cuối trước khi đưa lên store

1. Dựng bản **release ký thật** (cần `keystore.jks` trong `myKeyStore`), không cài lên máy test nếu chưa có hash cert đó. Máy owner: hash cert release đã có cho A50s, Pixel, Tecno KJ7.
2. Lần đầu cài bản ký thật: mở app và kiểm logcat `This request is sent from a test device.` ngay lần chạy đầu. Nếu thấy `Use RequestConfiguration` thì **dừng ngay**, không bấm vào ad.
3. Thử trial 1 ngày: chỉ chạy khi cài từ Play (Internal testing), cần Install Referrer; build debug và sideload bỏ qua.
4. Đưa lên **Internal testing** trước, rồi mới Production.
5. Bản dịch hai chuỗi VIP và hai chuỗi Ad choices là dịch máy, chưa người duyệt.

## 6. Giới hạn đã biết

- Chưa chạy ad bằng đơn vị thật (cố ý, để tránh invalid traffic).
- Chưa có máy API 26-28.
- Interstitial và rewarded dùng chung hạn mức: xem `audit_gemini.md`.
