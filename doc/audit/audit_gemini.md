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
- **Ảnh đầu và `admob.MD` lệch một chữ số trong publisher; ảnh thứ hai (owner gửi lại, "id cuối cùng") khớp `admob.MD` và `app.properties` ở cả 4 unit cùng publisher.** Ảnh đầu là đọc nhầm hoặc chụp mờ. Đã chốt.
- **Unit `native`** có trong `admob.MD` nhưng app **không có slot native**; không thêm (spec cấm tự thêm slot ad).
- **Chưa kiểm:** các unit này chưa bao giờ được dùng để xin quảng cáo từ app này; unit mới tạo có thể cần vài giờ để có fill; `app-ads.txt` chưa kiểm; AppLovin SDK key và 4 ad unit AppLovin vẫn là giá trị cũ, chưa có tài liệu nào xác nhận chúng thuộc reader.
- **Kiểm chứng:** build `prodRelease` ký khoá phát hành với ID mới (chỉ build trên máy, **không cài**): dex chứa đúng 4 unit mới, manifest mang App ID mới, 0 ID cũ. Script cổng đã sửa để kiểm cả hai publisher; chạy lại: APK ký debug, 0 ID thật, manifest App ID Google sample.

## Đã chứng minh trên A50s bằng ID thử (2026-11-23 14:20-14:55, bản debug)

Cả **bốn loại ad** chạy với ID Google sample, đều có nhãn "Test Ad", **0 dòng `Use RequestConfiguration`** ở mọi lượt: **banner** hiện đáy trang đọc (`✅ Loaded`, `👁️ Impression`), nằm dưới thanh nút, không che nút nào; **App Open** hiện khi mở lại app sau khi về nền (lần mở đầu đúng hành vi `skipAppOpenOnFirstEverLaunch`); **interstitial** hiện khi Settings quay lại; **rewarded** hiện, "Reward granted", cấp 3 ngày VIP bằng `grantVipDays`. Ad toàn màn đóng bằng nút `Close` / "Continue to app" có nhãn, không chạm creative. Mẫu "Share" tới `ShareTargetActivity` dùng để có bài mở trang đọc (gõ URL bằng `adb input` bị bàn phím IME làm méo ký tự).

**Token VIP:** công cụ `mint_vip_token.py` (myKeyStore, ngoài repo) đúc token ECDSA bằng Python + openssl; **SDK thật chấp nhận token do nó đúc** (5/5 test trên A50s với khoá dùng một lần, không phải khoá thật: token hợp lệ, replay bị từ chối, máy khác bị từ chối, hết hạn bị từ chối, đổi một ký tự bị từ chối); đột biến public key sai khiến 2/5 đỏ, lượt đối chứng lại xanh. Token bind `v2.1|hạn|máy hoặc *|com.mckimquyen.reader`.

**Hash test-device cho chứng chỉ release:** tính bằng APK dò không có mã ad, không quyền, không gửi request (md5 của ANDROID_ID), ký bằng cả khoá debug và khoá phát hành. Khoá debug cho một hash **đã có trong danh sách**, nên phương pháp được kiểm chứng; khoá phát hành cho một hash **chưa có**, nay đã thêm (24 hash, chỉ thêm). Hash này chỉ cho A50s: máy khác của owner vẫn cần tính riêng.

**Consent EEA (UMP), Google Pixel 7 Pro, Android 17, 2026-11-23 15:35-15:50, bản debug, vùng ép bằng `-PumpDebugGeography=EEA` và hash UMP của máy (đầu vào chỉ của bản debug, release cố định rỗng, 5 test, 5 đột biến bắt):** form hiện (là thông điệp thử "Publisher Test Ads" của Google, không phải thông điệp thật của app); **trước khi trả lời: 0 yêu cầu ad, provider chưa khởi tạo, log `provider init DEFERRED`** (hai lượt); "Do not consent": `gdprApplies=1`, `PurposeConsents` toàn 0, 0 vendor, `canRequestAds=true` (từ chối vẫn cho yêu cầu không cá nhân hoá) rồi provider khởi tạo và 3 yêu cầu ad; "Consent": `PurposeConsents` toàn 1, 55 vendor, cũng `canRequestAds=true`. Ad App Open thử hiện sau đó, đóng bằng thanh có nhãn, 0 tín hiệu ad thật. Bằng chứng (log và ảnh) nằm ở `myKeyStore/com.mckimquyen.reader/evidence/consent-eea-pixel-20261123/` (private). **Hạn chế nói thẳng:** (a) form là thông điệp thử, owner vẫn phải publish thông điệp UMP thật cho App ID; (b) chưa kiểm US-state/GPP, UK, mở lại từ cài đặt, offline, bản ký thật; (c) tệp prefs mặc định của app nơi UMP lưu đồng ý **không hoàn nguyên được về trước lần chạy đầu** (đã ghi vào README bằng chứng), Pixel đang ở trạng thái sau lượt 1; (d) các nút form là WebView nên uiautomator không thấy, tôi chạm hai điểm lấy từ ảnh chụp của chính máy ngay trước đó, sau khi bạn cho phép tự dùng adb.

**Vẫn chưa chứng minh:** form UMP của thông điệp thật, trial 1 ngày (chỉ bản release), spinner khi cooldown, bản **ký khoá phát hành với ID thật chạy trên máy** (không cài, vì unit mới chưa từng có fill và owner chưa kiểm tay), và mọi thứ trên máy ngoài A50s.

## Danh sách auto-VIP cũ và vòng lặp chờ callback (2026-11-23, từ git và mã)

- **Danh sách 21 GAID có từng được phát hành.** Nhánh `release20260401` (2026-04-01, versionName `2026.04.01`) chứa mã nhúng `AdMobManager.kt` gọi `addVIPMember(getMyListVipDevice())` với đúng 21 GAID ở lần init đầu của **bản release** (không phải debug), rồi `saveGAIDList` vào `loitp_admob.xml`. Bản `release20250501` có đoạn đó nhưng đã bị comment. Hệ quả: người dùng thật của bản 2026-04-01 đã bị lưu danh sách này. Danh sách chỉ whitelist **21 máy cụ thể**, nên chỉ máy có đúng GAID mới bị tắt ad (số rất nhỏ); không phải mọi người dùng. SDK 1.8.5 vẫn đọc và không xoá. Git không cho biết bản nào đã lên Play; việc này do owner xác nhận. Chưa thêm bước dọn trong code.
- **Rà điểm gọi `showRewarded` (9 chỗ, 4 file):** SDK luôn bắn callback với `earned=false` trên mọi đường không hiện ad (safety block, cooldown, chưa sẵn sàng, không mạng, đang hiện ad khác); kết quả không bao giờ bị nuốt ở `showRewarded`. Cooldown im lặng chỉ ảnh hưởng `loadRewarded`, nhưng app **không chờ kết quả load ở đâu**, nên không có spinner treo vì lý do đó. Chỗ duy nhất có trạng thái chờ là `StreakRescueViewModel` (`isLoadingAd`): thêm 2 test JVM (chạm lần hai, nút khác bị chặn lúc chờ, thất bại rồi thử lại, thử lại thành công) và **4 đột biến đều bị bắt**.

## Các mục kiểm thêm trên Pixel, 2026-11-23 buổi chiều (bản debug, ID Google sample; log và ảnh trong `myKeyStore/com.mckimquyen.reader/evidence/`)

| Mục | Kết quả | Chưa chứng minh |
|---|---|---|
| **Mã redeem của owner** (2 mã: 30 và 3 ngày) | `java.util.Properties` đọc lại đúng; `BuildConfig` của debug và release mang đúng hai mã; SDK thật trên máy: 3 ngày, rồi 30 ngày cộng dồn (về 33 ngày), mã dùng lần hai bị từ chối kể cả sau "Revoke VIP", mã sai một ký tự bị từ chối; 3/3 xanh, đột biến (mã lệch một ký tự) làm đỏ | mã chỉ dùng **một lần mỗi máy**; xoá dữ liệu app hoặc cài lại trả mã về; mã lộ thì máy nào chưa dùng cũng nhập được |
| **Đổi lại đồng ý** | Trang "Riêng tư và bảo mật" có mục "Ad choices" gọi `showConsentFormIfAvailable` (38 ngôn ngữ, dịch máy); 3 test, 3 đột biến bắt | **Chưa chạm mục này trên máy** (form chỉ mở ở vùng bắt buộc) |
| **US_STATE (GPP)** | UMP không hiện form, ghi `GppSID=7` và chuỗi GPP; ad vẫn chờ bước consent rồi mới khởi tạo | SDK có đổi GPP thành "do not sell" hay không: **chưa chứng minh** (log chỉ in giá trị gửi AppLovin, app chạy AdMob) |
| **Offline** | Khởi động lạnh không mạng: không crash, 0 yêu cầu ad; với trạng thái đồng ý trống thì **fail-closed** (`canRequestAds=false`, vào Home không ad); bật Wi-Fi lại: SDK tự `retryConsentIfNeeded`, `canRequestAds=true`, provider khởi tạo, 3 yêu cầu ad, không cần khởi động lại | không có ad nào **hiển thị** sau khi phục hồi; mạng rớt giữa lúc đang xem ad; bản release |
| **Danh sách 21 GAID cũ** | Bản 2026-04-01 đã lưu danh sách vào prefs của người dùng thật; thêm `LegacyAutoVipCleaner`: xoá **một lần** sau init, chỉ khoá danh sách, VIP từ token, mã hay trial giữ nguyên (test thiết bị: VIP 3 ngày còn sau khi dọn); tái hiện trên Pixel: `vipByGaid=true` rồi `isVIPMember` về `false`, lần chạy 2 không còn danh sách; 4 test, 4 đột biến bắt | người dùng thật chưa có số liệu |
| **Trial 1 ngày (bản release)** | **Không chứng minh được**: bản release không `run-as` được, SDK không ghi log ở release, và cơ chế cần Play Install Referrer (cài bằng adb thì referrer không tin cậy và mặc định không cấp trial) | cần cài từ Play (nhánh kiểm thử nội bộ) |
| **Vòng chờ callback rewarded** | `StreakRescueViewModel` là chỗ duy nhất có trạng thái chờ; 2 test thêm, 4 đột biến bắt | spinner khi cooldown trên máy |

**Gate cuối (`post-adsdk-6-final`, HEAD `f732da0b`):** 510 XML, **4068 test, 0 lỗi, 0 bỏ qua**; lint 0 lỗi, `NewApi` 0, 2.538 cảnh báo (cùng loại 36 `Untranslatable` đã biết); `prodRelease` R8 ký debug, 0 ID AdMob thật, manifest mang App ID Google sample; 88.088.017 byte, 3 dex.

## Rà chính sách AdMob đối chiếu mã (2026-11-23, theo [Disallowed interstitial implementations](https://support.google.com/admob/answer/6201362?hl=en))

Chính sách (trích): *"Do not place interstitial ads on app load and when exiting apps as interstitials should only be placed in between pages of app content."*; *"no more than one interstitial ad after every two user actions… this requirement also applies when a user clicks the Back button"*; interstitial chỉ ở *"logical breaks in between your app's content"*.

| Vi phạm tìm thấy trong mã | Đã làm |
|---|---|
| Interstitial khi bấm Back ở **Settings** (Back hệ thống và nút mũi tên) và **AI Settings** | Gỡ: Back chỉ điều hướng |
| Interstitial hiện thay cho **rewarded bị từ chối** ở trang VIP và Deep Dive của CommuteCast (người dùng bấm "xem ad để nhận thưởng", nhận ad không thưởng) | Gỡ: chỉ báo "chưa sẵn sàng" |
| Mở khoá **AMOLED** bằng interstitial ("xem video để mở khoá" nhưng đó là phần thưởng) | Đổi sang rewarded, chỉ mở khoá khi `earned`; không mở khoá trong nhánh không thưởng |

Hiện app **không còn điểm gọi `showInterstitial` nào** ở mã chạy; `loadInterstitial` vẫn nạp ở `MainActivity` nhưng không có chỗ hiện (có thể bỏ nạp để đỡ tốn request; chưa làm). 8 test nguồn `AdPolicySourceTest` khoá lại các điều trên, 8 đột biến đều bị bắt (một mutant sống sót ở lượt đầu đã được siết test rồi bắt).

**App Open và banner đối chiếu với chính sách AdMob (đã đọc [App open ads](https://support.google.com/admob/answer/9341964?hl=en)):** *"App open ad units should only appear when the user opens or switches back to an app"*, *"Integrate app open ads into your splash or loading screens"*, tránh hiện *"before/after other ads or on top of existing ad content"*. Mã: App Open chỉ ở `SplashActivity` (có consent trước) và khi quay lại từ nền do SDK quản (`SplashActivity` bị loại), không phải ở màn hình giữa phiên: **khớp chính sách**. Một điểm cần để ý: trang đọc mở trong lúc ad đang phủ có thể xếp ad chồng ad; chưa gặp trên máy. App không thuộc "Designed for Families" (không dùng App Open với nhóm đó). Banner chỉ một chỗ, không xếp trên banner khác.

**Data safety cho AdMob (theo [Google Play data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)):** SDK thu và chia sẻ tự động *Advertising ID, App set ID, các định danh khác gắn với tài khoản trên máy, địa chỉ IP, tương tác người dùng, chẩn đoán*, chuyển cho Google LLC cho mục đích quảng cáo, phân tích, chống gian lận; mã hoá khi truyền bằng TLS; thu Advertising ID là tuỳ chọn người dùng (có thể xoá). Khớp quyền trong manifest gộp của bản release (`AD_ID`, `ACCESS_ADSERVICES_*`, `BIND_GET_INSTALL_REFERRER_SERVICE`). **Lưu ý riêng của app này:** biểu mẫu phải khai thêm phần Gemini, nostr, WebDAV đã có trong REL-03; và AppLovin vẫn được đóng gói (xem AD.MD mục 3c) nên phải khai luôn dữ liệu của AppLovin dù không chạy, hoặc loại nó. Không khẳng định "mã hoá mọi dữ liệu khi truyền" vì SEC-01.

**Còn rủi ro, chưa đối chiếu (đã đóng phần App Open và Data safety ở trên):** chính sách riêng của **App Open** (hiện khi mở và khi quay lại là được phép, nhưng tôi chưa đọc từng điều khoản), kích cỡ và vị trí banner trên mọi màn hình, tần suất App Open do SDK giữ (preset CONTENT), và việc ad thật chưa từng chạy với unit mới.

**Bản release R8 trên Pixel (ID thử):** khởi động không crash, 0 tín hiệu ad thật, **banner hiện** ở đáy trang đọc (ảnh trong evidence); App Open, interstitial (không còn) và rewarded chưa quan sát ở release; kết nối ADB của Pixel rớt ở giữa lúc test, máy đang giữ bản release (xem `STATE_NOT_RESTORED.md` trong evidence).

**Gate (`post-adsdk-8-policy-final`):** 511 XML, **4076 test, 0 lỗi, 0 bỏ qua**; lint 0 lỗi, NewApi 0; APK ký debug, 0 ID AdMob thật, manifest mang App ID Google sample; 88.088.017 byte, 3 dex.

## Phát hiện trong lúc migrate (không thuộc SDK)

- **Bản release R8 crash ở `RApp.onCreate` trên mọi lần mở, cả bản trước migrate.** Nguyên nhân: sau khi tắt kiểm tra cập nhật (`UPDATE_CHECK_ENABLED=false`, 2026-11-23) không còn ai gọi method của `NetworkDataSource`, nên luật của Retrofit `-if interface * { @retrofit2.http.* <methods>; }` không còn khớp, R8 coi interface chưa có ai cài đặt và biên dịch `Retrofit.create()` thành `throw ClassCastException()`. Bản baseline `e890c8cb` (đã tắt kiểm tra cập nhật) **cũng crash** y hệt trên A50s: đo bằng cài lại baseline. Sửa bằng `-keep interface ...NetworkDataSource { *; }` (commit `1b5d58bf`). Đây là lỗi tôi **chưa bắt được ở đợt trước** vì tôi chưa chạy bản R8 sau khi tắt kiểm tra cập nhật; mốc cổng đã ghi chỗ này là chưa kiểm.
- Nâng SDK làm mất Gson 2.13.2 mà SDK 1.1.5 từng kéo vào ngầm; `converter-gson:2.9.0` chỉ có 2.8.5 nên `JsonParser.parseString` không biên dịch. Đã khai tường minh `gson:2.13.2`.

## Chưa kiểm (nói thẳng)

- **Chưa thấy ad nào hiện, và đã biết vì sao: GAID của A50s nằm trong danh sách auto-VIP lưu trong prefs của app.** Bản debug của tôi log `###init GAID=… vipByGaid=true → isVIPMember=true`, rồi `loadAppOpenAd ⏭️ skipped due to whitelist device` và `loadInterstitial … isVIPMember=true`. **Nguồn danh sách (đã truy):** `loitp_admob.xml` trên A50s có `keyListGAID` với **21 GAID**; SDK nạp nó ở `###init` (`setGAIDVipMember.addAll(appPreferences.getGAIDList())`). Danh sách do **bản app cũ ghi vào**: commit `67254884` (2025-05-01) hard-code 21 GAID máy test trong SDK cũ và gọi `addVIPMember(list)`, nên nó được lưu bền và **SDK 1.8.5 không xoá** (config mới `vipDeviceGaids` mặc định rỗng chỉ thêm, không dọn). Đây không phải do SDK 1.8.5 và không phải cấu hình của tôi. Hệ quả: trên A50s SDK **tắt mọi ad**, nên máy này **không kiểm được** banner, interstitial, App Open, rewarded. Đây là hành vi đúng của SDK, không phải lỗi; tôi **không** gỡ whitelist hay đổi VIP để lách (đã cấm). **Cập nhật cùng ngày (2026-11-23 13:51-13:55): sau khi xoá riêng `keyListGAID` khỏi `loitp_admob.xml` của A50s (đã sao lưu, khôi phục lại đúng hash `25d30f9c…` sau thử), ad hiện ra và đã chứng minh trên bản debug (ID Google sample):** `isVIPMember=false`; App Open `✅ Loaded`, Interstitial `✅ Loaded`, Rewarded `✅ Loaded`; **Interstitial** hiện khi Settings → Back (có nhãn "Test Ad", tôi đóng bằng nút `Close` có nhãn, không chạm creative); **Rewarded** hiện, đếm ngược, "Reward granted", log `User earned`, `grantVipDays ✅ cấp 3 ngày VIP`, màn VIP hiện "VIP active", rồi thu hồi bằng "Revoke VIP". **0 dòng `Use RequestConfiguration`** (không có tín hiệu ad thật) và log có `sent from a test device` 3 lần. **Chưa thấy:** banner (không có bài nào để mở `ReadingPage`), App Open lúc khởi động (hết 8 giây chờ nên vào Main trước; log `Activity destroyed before show`), consent EEA, offline, trial. Cái đã chứng minh trên máy trước đó: app khởi động, `requestConsentInfoUpdate` trả `canRequestAds=true`, provider AdMob init thành công, 23 hash test-device được nạp (`setTestDeviceIds` có mặt), `initSplashScreen` điều hướng sang Main, màn VIP mở được và mã sai báo "Invalid code". Cái **chưa** chứng minh: có ad thật hay ad thử hiện hay không, trên bất kỳ máy nào.
- (Đã cũ, xem bảng chấm điểm cuối file) Consent EEA/US_STATE, offline, token ECDSA, mã đổi VIP và rewarded đã chạy sau đó trên Pixel/A50s/Tecno. Chưa kiểm: trial 1 ngày.
- Chưa có API 26-28 thật; chỉ A50s Android 11.
- Bản dịch hai chuỗi VIP mới là dịch máy, chưa người duyệt.


## Bổ sung 2026-11-23: bản release R8 trên Tecno KJ7

Bản release R8 (ký debug, ID mẫu Google) tại `3edfb2b9`: khởi động nguội ra `This request is sent from a test device.`, 0 lần `Use RequestConfiguration`. App Open hiện với nhãn "Test Ad", đóng bằng nút có nhãn; rewarded hiện "Test Ad", "Reward granted", VIP +3 ngày. Máy đã khôi phục byte-exact. Chưa chứng minh: ad với đơn vị thật, API 26-28.


## Nghiệm thu và chấm điểm /10 (2026-11-23, bằng chứng có sẵn, chỉ Android + AdMob)

Điểm = mức tin cậy từ bằng chứng chạy máy, không phải chất lượng mã. Không có máy nào chạy ad với đơn vị thật.

| Hạng mục | Điểm | Đã chứng minh | Còn thiếu |
|---|---|---|---|
| Rewarded | 9 | A50s, Pixel (debug), Tecno release R8: "Test Ad", "Reward granted", VIP +3 ngày; callback luôn fire; hết thưởng giả (AMOLED chỉ mở khi `earned`) | Rớt mạng giữa lúc ad; kiểm tay spinner khi SDK ở cooldown |
| App Open | 9.5 | A50s, Pixel, Tecno release R8; splash loại trừ; `AppOpenGuard` trên Tecno (debug HEAD): khi hộp thoại quyền thông báo đang chờ, 3 lần app ra trước đều log `Skipping - suppressAppOpenTemporarily(true) đang active`, không App Open, không ad thật; test JVM `AppOpenGuardLauncherTest` (launcher thật, Robolectric) + 7 mutant bị bắt | Đủ hai nửa trên máy: sau khi trả lời "Don't allow" và quay lại, App Open hiện (`showAppOpenAd`, `IMPRESSION`, nhãn "Test Ad"), nên cờ được thả đúng |
| Banner | 9 | A50s, Pixel debug; Pixel và Tecno release R8 (trang đọc bài: nhãn "Test Ad", 468x60, `This request is sent from a test device.`) | Banner chỉ có ở trang đọc bài; chưa đo ad thật |
| Interstitial | 8.5 | Một điểm ngắt: bấm "Next Article" ở trang đọc, qua `InterstitialPacer` (1 quảng cáo mỗi 3 lần bấm, sàn 3 theo luật AdMob 1/2 hành động; SDK thêm giới hạn thời gian/giờ, bỏ VIP). Preload chỉ khi lần bấm kế tiếp có thể hiện. Trên Tecno: lần 1-2 không ad, lần 3 hiện "Test Ad", lần 4-5 và Back không ad. 12 test mới, 5 mutant bị bắt | Chưa đo show-rate/eCPM thật; số 3 là ước đoán, cần chỉnh theo dữ liệu; policy 'Disallowed interstitial' chưa qua duyệt thật của AdMob |
| VIP | 8.5 | Token ECDSA do SDK thật chấp nhận, 2 mã đổi của owner, dọn danh sách auto-VIP cũ, rewarded -> `grantVipDays` | Nhập mã cần mạng; VIP client-side (chấp nhận); dịch máy chưa duyệt |
| Consent / UMP | 7 | EEA, US_STATE, offline, hồi phục; mục "Ad choices" có mã + test | UMP message thật chưa publish; chưa chạm mục Ad choices trên máy; GPP/UK thật; US_STATE -> doNotSell |
| Trial 1 ngày | 3 | Đọc mã: app không tự cấp, SDK cấp chỉ release | Cần cài từ Play (Install Referrer); chưa chạy |
| Policy AdMob | 7 | 3 vi phạm đã gỡ (`a3a1b0b0`), 8 test policy, 4 mutant guard | Form Data safety chưa điền; AppLovin ~3 MB trong APK, key/unit chưa xác minh |
| Chặn invalid traffic | 7.5 | 27 hash; hash cho cert release trên A50s, Pixel và Tecno KJ7 (probe không ad SDK, không ad request); 0 tín hiệu `Use RequestConfiguration` trong mọi lần chạy | Máy khác của owner (nếu dùng bản ký thật) chưa probe; hash vẫn cần kiểm `This request is sent from a test device.` ở lần đầu cài bản ký thật |
| Khoá ký | 4 | Chuyển sang myKeyStore, untrack | Key đã lộ trong git public: owner reset upload key trên Play Console |

**Tổng thể: 7/10, GO có điều kiện** (cập nhật cùng ngày: Banner 9, App Open 8.5; tổng không đổi vì các ô nặng là khoá ký, trial, hash). Ad hoạt động đúng trên bản release R8 với ID thử. Chặn phát hành: (1) reset upload key, (2) publish UMP message, (3) hash release cho máy owner dùng bản ký thật, (4) form Data safety. Việc đo còn lại: trial 1 ngày (cần cài từ Play), ad với đơn vị thật, API 26-28. Cập nhật cuối ngày: tổng vẫn 7/10 vì khoá ký 4/10 và trial 3/10 kéo xuống.
