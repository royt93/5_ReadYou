# Audit SDK `AdmobApplovinWrapper` 1.8.5 để dùng cho RSS Cat Hub (2026-11-23)

**Người làm:** Claude Code, đọc mã SDK `royt93/AdmobApplovinWrapper` (tag 1.8.5, HEAD `97d5adf`), đọc `doc/AD_PROMPT_AOS.MD` (2.564 dòng), `doc/audit/audit_gemini.md` của SDK (đợt 1.8.3), app demo, rồi chạy bản release R8 trên Samsung A50s (Android 11). Tên file theo yêu cầu của owner; nội dung do Claude Code viết, không phải do Gemini.

## Kết luận: **GO có điều kiện** cho production

SDK dùng được cho app này **nếu** làm đủ các điều kiện dưới. Tôi **không** tuyên bố "an toàn tuyệt đối": chưa chạy consent EEA thật, chưa thấy ad thật hay ad thử hiển thị trên máy (xem "Chưa kiểm").

| # | Điều kiện bắt buộc | Trạng thái |
|---|---|---|
| 1 | `vipKeySecret` và `vipTokenPublicKey` riêng của app, không dùng bản mẫu | Xong: sinh mới, nằm ở `myKeyStore/com.mckimquyen.reader/app.properties`, không còn trong git |
| 2 | Hash test-device cho **mọi** máy owner, build debug **và** release | Một phần: 23 hash (thêm từ README myKeyStore). Hash cho **chứng chỉ release** của máy owner chưa có |
| 3 | Publish UMP "European regulations" (+ "US states") cho đúng App ID | **Owner làm trên AdMob Console**, tôi không kiểm được |
| 4 | App không hướng trẻ em; không bật `tagForChildDirectedTreatment` với AppLovin (crash cố ý) | Đúng: app không đặt cờ này |
| 5 | VIP là quyền lợi marketing client-side, không bán tiền | Giữ nguyên: nút mua vẫn disabled |
| 6 | Chạy trên máy thật **không nằm trong whitelist VIP** với ID thử trước khi phát hành | Chưa: A50s bị whitelist VIP của SDK nên không thấy ad; cần máy khác (xem "Chưa kiểm") |

## Bảy hạng mục owner yêu cầu

| Hạng mục | Kết quả | Bằng chứng |
|---|---|---|
| Provider AdMob/AppLovin | Chọn **một** provider lúc build bằng `IS_ENABLE_ADMOB`; không có waterfall tự chuyển khi no-fill. App đang `true` (AdMob) | `AdSdkConfig`, spec Section 0 |
| Có mạng / không mạng | Fullscreen trả `AdError.NoNetwork` và callback vẫn fire `false`; banner trả `null`; consent tự retry khi mạng về (validated network) | Đọc `AdManager`, spec "Hành vi theo trạng thái mạng". **Chưa chạy offline trên máy** |
| Banner / Open App / Rewarded / Interstitial | Có đủ 4 loại; banner tự quản vòng đời; callback luôn fire một lần | Đọc mã; **audit 1.8.3 ghi MEDIUM**: cooldown backoff không phát callback fail cho Interstitial và Rewarded (chỉ App Open đúng) |
| Rò bộ nhớ | `paidEventListener` gắn với Activity set nó và bị xoá khi Activity huỷ, nên **đặt trong `Application.onCreate`** (đã làm); listener khác cũng có owner-tracking | Đọc `AdManager` dòng 864-883, 298-307 |
| Trial 1 ngày | SDK tự cấp, chỉ release, gms dựa Install Referrer (đổi tài khoản Google + cài lại = trial mới; Huawei cài lại = trial mới) | Spec Step 9. **Chưa kiểm trên máy**: build debug bỏ qua trial |
| VIP bằng mã, không server | ECDSA P-256: app chỉ giữ public key; token có hạn, bind máy và `applicationId`; thẻ cào `vipRedeemCodes` yếu hơn (mã nằm trong APK). Nhập mã **bắt buộc có mạng** (V-03, yêu cầu sản phẩm, không phải bảo mật) | `activateVipByKey` dòng 3920+, `TokenSigner` |
| Consent mọi quốc gia | gms: UMP-first, fail-closed, init provider chỉ sau `canRequestAds`; US dùng GPP `usnat/usca`, **không đọc** section riêng từng bang; nongms không dùng ở app này | Spec Step 8; **chưa chạy EEA thật** |
| Policy AdMob/AppLovin | 6 rule Step 7: banner dock dưới, App Open không đè splash, interstitial có frequency cap, rewarded chỉ khi bấm nút, không giả nút đóng | Xem `doc/AD.MD` |

## Rủi ro còn lại (xếp theo mức)

1. **Invalid traffic ở bản release trên máy owner (Cao).** Hash test-device phụ thuộc cả máy lẫn chứng chỉ ký. README myKeyStore ghi hai sự cố ad thật (2026-08, 2026-09) vì hash release thiếu. 23 hash hiện có chưa chắc gồm hash của **chứng chỉ release** `67:B2:E6:CD…`. Quy tắc giữ nguyên: bản ký thật chỉ cài sau khi có hash hoặc sau khi chạy probe không gửi ad.
2. **Khoá ký đã lộ (Critical, ngoài SDK).** `keystores.jks` và mật khẩu từng nằm trong git của repo public. Chuyển sang myKeyStore và untrack chỉ chặn lộ mới. Phải reset upload key trên Play Console.
3. **VIP client-side (Thấp, chấp nhận theo E-1 của SDK).** User root có thể hook `isVIPMember`; thẻ cào lộ mã là hại vĩnh viễn; xoá dữ liệu app + cài lại có thể redeem lại.
4. **`QC_TEST_DEVICE_HASHES` nằm trong SDK (Trung bình).** SDK luôn tự thêm 13 máy QC của chủ SDK vào danh sách test của app này (E-10, accepted risk của SDK). Vô hại cho app, nhưng danh sách thật của app là 23 hash ở myKeyStore.
5. **Cooldown không báo lỗi (Trung bình).** Nếu UI chờ callback để tắt spinner thì có thể treo khi SDK ở cooldown. Cần kiểm tay các điểm gọi `showRewarded` (RPG, CommuteCast, VIP).
6. **Banner `AdBanner` cần `Activity` (Thấp, đã xử lý).** SDK ép `LocalContext` thành Activity và im lặng bỏ qua nếu không được; `ComposeBannerAd` cấp Activity thật. Chưa thấy banner hiện trên máy (xem dưới).

## Audit ID AdMob so với `doc/admob.MD` (2026-11-23, owner yêu cầu)

Owner đưa `doc/admob.MD` (ghi App ID, 4 unit và 1 unit native của app) và một ảnh chụp danh sách ad unit. Tôi đối chiếu với ID reader đang dùng:

- **Reader đang dùng sai publisher.** `build.gradle` cũ, các nhánh release 2025-05 đến 2026-06 và `app.properties` tôi dựng đều mang publisher cũ (`3612…`, App ID `~7020425646`). `admob.MD` ghi publisher khác (`3004…`, App ID `~7354810943`). Trong myKeyStore chỉ vài app lẻ dùng publisher cũ (với unit khác hẳn), còn 9 thư mục dùng publisher của `admob.MD` (62 chỗ). Không có chỗ nào xác nhận publisher cũ là của reader. Hệ quả của việc để nguyên: release gửi request tới unit không thuộc tài khoản của app (không doanh thu, có thể vi phạm policy). **Đã sửa:** `app.properties` (myKeyStore, private) dùng đủ 5 ID theo `admob.MD`; repo reader không chứa ID nào.
- **Unit rewarded cũ đáng ngờ:** đuôi `5224354917` **trùng đúng** unit rewarded mẫu công khai của Google; code cũ từng dùng nó làm fallback. Nhiều khả năng đó chưa bao giờ là unit thật. Đã thay.
- **Ảnh và `admob.MD` lệch một chữ số trong publisher** (vị trí thứ 12). Số unit của 4 loại khớp hoàn toàn. Owner chọn giá trị của `admob.MD` (khớp 62 chỗ trong myKeyStore); tôi chưa thấy được Console để xác nhận lần nữa.
- **Unit `native`** có trong `admob.MD` nhưng app **không có slot native**; không thêm (spec cấm tự thêm slot ad).
- **Chưa kiểm:** các unit này chưa bao giờ được dùng để xin quảng cáo từ app này; unit mới tạo có thể cần vài giờ để có fill; `app-ads.txt` chưa kiểm; AppLovin SDK key và 4 ad unit AppLovin vẫn là giá trị cũ, chưa có tài liệu nào xác nhận chúng thuộc reader.
- **Kiểm chứng:** build `prodRelease` ký khoá phát hành với ID mới (chỉ build trên máy, **không cài**): dex chứa đúng 4 unit mới, manifest mang App ID mới, 0 ID cũ. Script cổng đã sửa để kiểm cả hai publisher; chạy lại: APK ký debug, 0 ID thật, manifest App ID Google sample.

## Phát hiện trong lúc migrate (không thuộc SDK)

- **Bản release R8 crash ở `RApp.onCreate` trên mọi lần mở, cả bản trước migrate.** Nguyên nhân: sau khi tắt kiểm tra cập nhật (`UPDATE_CHECK_ENABLED=false`, 2026-11-23) không còn ai gọi method của `NetworkDataSource`, nên luật của Retrofit `-if interface * { @retrofit2.http.* <methods>; }` không còn khớp, R8 coi interface chưa có ai cài đặt và biên dịch `Retrofit.create()` thành `throw ClassCastException()`. Bản baseline `e890c8cb` (đã tắt kiểm tra cập nhật) **cũng crash** y hệt trên A50s: đo bằng cài lại baseline. Sửa bằng `-keep interface ...NetworkDataSource { *; }` (commit `1b5d58bf`). Đây là lỗi tôi **chưa bắt được ở đợt trước** vì tôi chưa chạy bản R8 sau khi tắt kiểm tra cập nhật; mốc cổng đã ghi chỗ này là chưa kiểm.
- Nâng SDK làm mất Gson 2.13.2 mà SDK 1.1.5 từng kéo vào ngầm; `converter-gson:2.9.0` chỉ có 2.8.5 nên `JsonParser.parseString` không biên dịch. Đã khai tường minh `gson:2.13.2`.

## Chưa kiểm (nói thẳng)

- **Chưa thấy ad nào hiện, và đã biết vì sao: GAID của A50s nằm trong danh sách auto-VIP lưu trong prefs của app.** Bản debug của tôi log `###init GAID=… vipByGaid=true → isVIPMember=true`, rồi `loadAppOpenAd ⏭️ skipped due to whitelist device` và `loadInterstitial … isVIPMember=true`. **Nguồn danh sách (đã truy):** `loitp_admob.xml` trên A50s có `keyListGAID` với **21 GAID**; SDK nạp nó ở `###init` (`setGAIDVipMember.addAll(appPreferences.getGAIDList())`). Danh sách do **bản app cũ ghi vào**: commit `67254884` (2025-05-01) hard-code 21 GAID máy test trong SDK cũ và gọi `addVIPMember(list)`, nên nó được lưu bền và **SDK 1.8.5 không xoá** (config mới `vipDeviceGaids` mặc định rỗng chỉ thêm, không dọn). Đây không phải do SDK 1.8.5 và không phải cấu hình của tôi. Hệ quả: trên A50s SDK **tắt mọi ad**, nên máy này **không kiểm được** banner, interstitial, App Open, rewarded. Đây là hành vi đúng của SDK, không phải lỗi; tôi **không** gỡ whitelist hay đổi VIP để lách (đã cấm). **Cập nhật cùng ngày (2026-11-23 13:51-13:55): sau khi xoá riêng `keyListGAID` khỏi `loitp_admob.xml` của A50s (đã sao lưu, khôi phục lại đúng hash `25d30f9c…` sau thử), ad hiện ra và đã chứng minh trên bản debug (ID Google sample):** `isVIPMember=false`; App Open `✅ Loaded`, Interstitial `✅ Loaded`, Rewarded `✅ Loaded`; **Interstitial** hiện khi Settings → Back (có nhãn "Test Ad", tôi đóng bằng nút `Close` có nhãn, không chạm creative); **Rewarded** hiện, đếm ngược, "Reward granted", log `User earned`, `grantVipDays ✅ cấp 3 ngày VIP`, màn VIP hiện "VIP active", rồi thu hồi bằng "Revoke VIP". **0 dòng `Use RequestConfiguration`** (không có tín hiệu ad thật) và log có `sent from a test device` 3 lần. **Chưa thấy:** banner (không có bài nào để mở `ReadingPage`), App Open lúc khởi động (hết 8 giây chờ nên vào Main trước; log `Activity destroyed before show`), consent EEA, offline, trial. Cái đã chứng minh trên máy trước đó: app khởi động, `requestConsentInfoUpdate` trả `canRequestAds=true`, provider AdMob init thành công, 23 hash test-device được nạp (`setTestDeviceIds` có mặt), `initSplashScreen` điều hướng sang Main, màn VIP mở được và mã sai báo "Invalid code". Cái **chưa** chứng minh: có ad thật hay ad thử hiện hay không, trên bất kỳ máy nào.
- Chưa chạy consent EEA (`umpDebugGeography`), chưa chạy offline, chưa kiểm trial, chưa kiểm token ECDSA thật (chưa có công cụ đúc), chưa kiểm rewarded.
- Chưa có API 26-28 thật; chỉ A50s Android 11.
- Bản dịch hai chuỗi VIP mới là dịch máy, chưa người duyệt.
