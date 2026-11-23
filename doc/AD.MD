# Tích hợp `AdmobApplovinWrapper` 1.8.5 (cập nhật 2026-11-23)

> Nâng từ 1.1.5 (2026-06-06) lên **1.8.5** theo `doc/AD_PROMPT_AOS.MD`. Provider chạy: **AdMob** (`IS_ENABLE_ADMOB=true`), AppLovin MAX là phụ. Mọi bí mật nằm ngoài repo, ở `myKeyStore/com.mckimquyen.reader/app.properties` (repo private `royt93/myKeyStore`). Audit SDK: `doc/audit/audit_gemini.md`.

## 0. Trạng thái

- JVM **4053 test, 0 lỗi**; lint **0 lỗi** (2.538 cảnh báo, +36 là `Untranslatable` của `ad_attribution_label`, cùng loại 1.900 cái có sẵn); `prodRelease` R8 build được.
- **Bản release R8 chạy được trên Samsung A50s (Android 11)** sau khi sửa keep rule `NetworkDataSource` (xem mục 6). **Ad chưa được kiểm hiển thị trên máy nào**: A50s là máy whitelist VIP của SDK nên mọi ad bị tắt.
- Chưa push `dev`. Chưa dựng bản ký khoá phát hành thật.

## 1. Touchpoint

| Slot | File | Trigger | API |
|---|---|---|---|
| Init | `RApp.setupAdmob()` | `Application.onCreate` | `setConfig`, `setTestDeviceIds`, `paidEventListener`, `errorReporter`, `initialize` |
| Consent + App Open splash | `SplashActivity` | `onCreate` | `requestConsentInfoUpdate` rồi `initSplashScreen` (không còn timer 8 giây của app) |
| App Open resume | SDK | `ProcessLifecycleOwner` | tự động; `SplashActivity` nằm trong `appOpenExcludedActivities` |
| Banner | `sdkadbmob/ComposeBannerAd` -> `ReadingPage` | cuối trang đọc | `AdBanner(adSize = AdBannerSize)` với Activity thật |
| Interstitial | `MainActivity` (load), `SettingsPage`, `DarkThemePage`, `AiSettingsPage` (show) | quay lại, mở khoá AMOLED | `loadInterstitial`, `showInterstitial` |
| Rewarded | `VipManagementPage`, `BrainRpgViewModel`, `StreakRescueViewModel`, `CommuteCastSheet` | người dùng bấm | `showRewarded`; VIP dùng `grantVipDays(3)` |
| VIP nhập mã | `VipManagementPage` + `VipRedeem` | bấm Kích hoạt | `activateVipByToken` (token ECDSA **và** thẻ cào `vipRedeemCodes`) |

## 2. Thay đổi so với 1.1.5

| Khía cạnh | 1.1.5 | 1.8.5 |
|---|---|---|
| VIP | `activateVipByKey(secret, ngày)` plaintext, secret Base64 trong git | ECDSA P-256: app chỉ giữ public key; thẻ cào `vipRedeemCodes`; đường plaintext của SDK **tắt mặc định**, app không dùng |
| Rewarded -> VIP | `activateVipByKey(secret, 3)` (sẽ **thất bại im lặng** ở 1.8.5) | `grantVipDays(ctx, 3)` |
| Bí mật | trong `build.gradle`, `keystore.properties`, `AdKeys` (git public) | `app.properties` ngoài repo; release **dừng build** nếu thiếu khoá |
| Test device | không có `setTestDeviceIds` | 23 hash từ myKeyStore, gọi cho debug **và** release |
| AdMob App ID | cố định (ID thật) cho cả debug | `manifestPlaceholders`: debug = Google sample, release = ID thật |
| Splash | timer 8 giây của app đua với watchdog của SDK | chỉ watchdog của SDK |
| Backup | VIP/trial ledger đi theo sao lưu | `loitp_admob.xml` bị loại khỏi `backup_rules` và `data_extraction_rules` |
| Reward | cộng ở callback `showRewarded` | `grantRewardOnEarn=true`: callback bắn lúc earn; vẫn một kênh nên không cộng đôi |
| Gson | 2.13.2 do SDK kéo ngầm | khai tường minh `gson:2.13.2` (SDK mới không kéo nữa) |

## 3. Thông tin owner phải cấp / làm

1. **Reset upload key trên Play Console.** `keystores.jks` và mật khẩu nằm trong git history của repo public `royt93/5_ReadYou` (2024-06-01, 2026-01-03). Untrack không thu hồi.
2. Publish UMP "European regulations" (+ "US states") cho đúng AdMob App ID; xác nhận app-ads.txt.
3. Hash test-device cho **chứng chỉ release** của máy bạn (chưa có); danh sách hiện tại: 23 hash.
4. Xác nhận 4 AdMob ad unit release và 4 AppLovin ad unit là của đúng app.
5. Data safety: thêm `AD_ID`, `ACCESS_ADSERVICES_*`, Install Referrer, UMP (xem `doc/task/REL-03_data-safety-audit.md` mục 7).
6. Duyệt bản dịch máy của hai chuỗi VIP mới (`vip_failed_busy`, `vip_failed_no_network`) ở 37 ngôn ngữ.
7. Mã thẻ cào 30 ngày và 3 ngày mới nằm ở `VIP_REDEEM_CODES` trong `app.properties`; mã cũ (Base64 trong git) **không còn dùng được**.

## 4. Policy check (Step 7)

| # | Rule | Kết quả |
|---|---|---|
| 1 | Banner không che nội dung tương tác | Đạt theo mã: dock dưới `ReadingPage`, có `navigationBarsPadding`. **Chưa thấy trên máy** |
| 2 | App Open không hiện trên splash thuần | Đạt theo mã: `appOpenExcludedActivities = [SplashActivity]` + test nguồn |
| 3 | Interstitial theo frequency cap | `AdSafetyLimits.TEST` ở debug, `.CONTENT` ở release |
| 4 | Rewarded chỉ khi người dùng bấm | Đạt theo mã |
| 5 | Không tự click, không nút đóng giả | Đạt: banner do SDK vẽ |
| 6 | Không thưởng cho interstitial thay rewarded | Đạt: nhánh `earned=false` chỉ cho interstitial, **không cấp VIP** |

## 5. Rủi ro đã biết

- A50s không kiểm được ad: GAID máy nằm trong `keyListGAID` (21 GAID) mà bản app cũ đã lưu vào `loitp_admob.xml`; SDK 1.8.5 vẫn đọc và không xoá. Mọi máy từng chạy bản cũ của owner có thể bị tương tự.
- Cooldown backoff của SDK không báo lỗi cho Interstitial/Rewarded (audit 1.8.3 MEDIUM); chưa kiểm UI có treo spinner không.
- Nhập mã VIP bắt buộc có mạng (yêu cầu sản phẩm của SDK, V-03). App báo "cần mạng" thay vì "mã sai".
- Thẻ cào lộ mã là hại vĩnh viễn; xoá dữ liệu app + cài lại có thể redeem lại.

## 6. Lỗi tìm thấy khi chạy bản release R8 (không thuộc SDK)

`RApp.onCreate` crash `ClassCastException` ở Dagger `SwitchingProvider.get` mỗi lần mở bản release, **cả bản baseline trước migrate**. Gốc: kiểm tra cập nhật đã tắt nên không còn ai gọi method của `NetworkDataSource`; luật Retrofit `-if interface * { @retrofit2.http.* <methods>; }` hết khớp; R8 biên dịch `Retrofit.create()` thành `throw ClassCastException()`. Sửa: `-keep interface ...NetworkDataSource { *; }` + test `ProguardRulesTest`. Cách bắt được: chạy bản release thật trên máy; JVM test không bắt được R8.

## 7. Công cụ đúc token VIP

`generateVipToken` chỉ chạy khi `isDebug=true` và cần Context Android đã init SDK, nên cần một **app debug riêng** (không phải RSS Cat Hub) giữ private key. Private key: `VIP_TOKEN_PRIVATE_KEY` trong `myKeyStore/com.mckimquyen.reader/app.properties`, không bao giờ vào repo app. Token bind `applicationId` `com.mckimquyen.reader` và bind máy (`bindToCurrentDevice`), nên phải đúc đúng package và (nếu bind máy) trên đúng máy nhận. **Chưa dựng.**

---

# Lịch sử: kế hoạch cũ cho 1.1.5

## Kế hoạch migrate 1.1.5 (2026-06-06, đã thay thế)

> Cập nhật: 2026-06-06. Migrate từ `com.github.royt93:AdmobWrapper:1.1.1` → `com.github.royt93:AdmobApplovinWrapper:1.1.5`
> (lib đổi tên, package vẫn `com.roy.sdkadbmob`). Provider chính **AppLovin MAX**, AdMob là phụ
> (`IS_ENABLE_ADMOB=false`, dùng Google test ID). Bám sample Compose của SDK (`ComposeDemoActivity` + `MyApplication`).

## 0. Trạng thái

- **devDebug**: ✅ BUILD SUCCESSFUL
- **prodRelease** (signed + R8 minify + shrinkResources): ✅ BUILD SUCCESSFUL
- SDK version runtime: `SdkVersion.VERSION_NAME = 1.1.5`

## 1. Touchpoint table

| Slot | File hiện tại | Trigger | API mới | API cũ thay thế |
|---|---|---|---|---|
| Init SDK | `RApp.kt` `setupAdmob()` | `Application.onCreate` | `AdManager.setConfig` + `AdManager.initialize(this){…}` (one-shot 4 bước) | `earlyInit` + `MobileAds.initialize`/`AppLovinSdk.initialize` + `init` + `registerAppOpenAdLifecycle` viết tay |
| App Open (splash) | `SplashActivity.kt` | `onCreate` → warmup, navigate khi xong | `requestConsentInfoUpdate` → `initSplashScreen` | `initSplashScreen` (chưa có consent) |
| App Open (resume) | lib tự quản | `ProcessLifecycleOwner` (trong `initialize`) | tự động | thủ công `registerAppOpenAdLifecycle` |
| Banner | `sdkadbmob/ComposeBannerAd.kt` → `ReadingPage.kt:204` | bottom bar bài đọc | `@Composable AdBanner` (adaptive, auto lifecycle) | `loadBanner(...TextView...)` + `bannerPause/Resume/Destroy` thủ công |
| Interstitial (load) | `MainActivity.kt:135` | `onCreate` | `AdManager.loadInterstitial` (giữ nguyên) | — |
| Interstitial (show) | `SettingsPage.kt:80,102`, `DarkThemePage.kt:77` | back / unlock AMOLED | `AdManager.showInterstitial` (giữ nguyên) | — |
| Rewarded | `VipManagementPage.kt` | nút "Xem quảng cáo → 3 ngày VIP" | `loadRewarded` + `showRewarded`, fallback `showInterstitial` | (chưa có) |
| Consent (UMP) | `SplashActivity.kt` | `onCreate` | `AdManager.requestConsentInfoUpdate` | (chưa có) |
| VIP by key | `VipManagementPage.kt` | nhập code / revoke | `activateVipByKey` / `isVipByKeyActive` / `getVipByKeyExpiry` / `clearVipByKey` | (chưa có) |

## 2. Step-by-step migrate

1. **build.gradle** — bump lib `AdmobWrapper:1.1.1` → `AdmobApplovinWrapper:1.1.5`. Thêm `buildConfigField`
   `APPLOVIN_REWARDED_ID` (`2d899bfeff96984a`), `ADMOB_REWARDED_ID` (Google test `…/5224354917`),
   `PRIVACY_POLICY_URL` cho cả debug + release. Không đụng banner/interstitial/appOpen ID sẵn có.
2. **RApp.kt** — `setupAdmob()` rút về `setConfig` + `initialize(this){…}`. Bổ sung `admobRewardedId`,
   `applovinRewardedId`, `applovinSdkKey`, `vipKeySecret = AdKeys.VIP_SECRET`,
   `safety = if (DEBUG) AdSafetyLimits.TEST else AdSafetyLimits.CONTENT`. Xoá block `MobileAds`/`AppLovinSdk`/`earlyInit`/`init`/`registerAppOpenAdLifecycle` thủ công + import dư (`MobileAds`, `AppLovinSdk`, `Handler`, `Looper`).
3. **SplashActivity.kt** — bọc `requestConsentInfoUpdate(this){ canRequestAds -> initSplashScreen{ goToMain() } }`.
4. **ComposeBannerAd.kt** — viết lại thành wrapper mỏng delegate sang SDK `AdBanner` (adaptive width). `ReadingPage` giữ nguyên call `ComposeBannerAd()`.
5. **VIP screen (Compose)** — thêm package `ui/page/setting/vip/`: `AdKeys.kt` (VIP secret Base64 + privacy URL), `VipKeys.kt` (whitelist code→ngày Base64), `VipPrefs.kt` (grantedAt + userRedeemed), `VipManagementPage.kt` (13 thành phần UI + 5 animation). Route `RouteName.VIP` + `HomeEntry` + entry item trong `SettingsPage`.
6. **i18n** — VIP strings cho cả 6 locale: en, vi, zh-rCN, ja, fr-rFR, de-rDE.

## 3. So sánh trước / sau

| Khía cạnh | Trước (1.1.1) | Sau (1.1.5) |
|---|---|---|
| Lib coordinate | `AdmobWrapper:1.1.1` | `AdmobApplovinWrapper:1.1.5` |
| Init | `earlyInit` + provider init + `init` + `registerAppOpenAdLifecycle` (≈60 dòng) | `setConfig` + `initialize()` one-shot (≈8 dòng) |
| App Open resume | thủ công | lib tự hook `ProcessLifecycleOwner` |
| Consent (UMP) | ❌ không có | ✅ `requestConsentInfoUpdate` ở splash |
| Banner | API cũ + manual pause/resume/destroy (leak risk) | `AdBanner` composable auto-lifecycle qua `DisposableEffect` |
| Memory leak risk | giữ View/listener thủ công | Compose auto-cancel; SDK auto-clear listener |
| Frequency cap | mặc định | `AdSafetyLimits.TEST` (debug) / `.CONTENT` (release) |
| Rewarded | ❌ không có | ✅ nút "Watch ad → 3 ngày VIP"; fallback interstitial khi earned=false nhưng **không grant** (intentional: user đóng ad sớm không được thưởng) |
| VIP | chỉ gate AMOLED bằng interstitial | màn VIP đầy đủ: by-key, auto-trial 1 ngày (SDK built-in), progress/countdown |
| VIP secret | — | single `vipKeySecret` = 30d key (Base64, không plain trong `.kt`) |

## 4. Thông tin user đã cấp / còn mở

Đã cấp đủ:
- AppLovin Rewarded ID: `2d899bfeff96984a` (debug + release).
- AdMob: dùng Google **test** ID (`IS_ENABLE_ADMOB=false`, AdMob dormant).
- Privacy Policy URL: `https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f`.
- VIP key 30 ngày: `[REDACTED — xem VipKeys.VIP_30D_B64]` (= `vipKeySecret`). VIP key 3 ngày: `[REDACTED — xem VipKeys.VIP_3D_B64]`.

Còn mở (không chặn):
- Banner adaptive đặt ở bottom `ReadingPage` (dock dưới, không che nút) — OK theo policy.
- Toolbar VIP badge: app là Compose single-activity, entry VIP đặt trong Settings thay vì badge XML toolbar (đã có `Icons.WorkspacePremium`). Nếu cần badge trên top bar Home sẽ bổ sung sau.
- IAP (mua VIP 30/90/365/lifetime + Restore): nút `disabled`, chờ tích hợp Billing.

## 5. Consent (UMP) — kịch bản

- Non-EEA: skip dialog, ad load bình thường.
- EEA accept: dialog xong → ad load.
- EEA deny: dialog xong → lib không load personalized ad (lib tự xử lý). App vẫn navigate vào Main.
- Lỗi consent: lib fallback `canRequestAds=true`.

## 6. VIP auto-trial 1 ngày (SDK built-in 1.1.4+)

- SDK tự grant 1 ngày VIP cho first-install (release) qua `InstallReferrerHelper` + provisional arm. App **không** tự implement flag.
- VIP screen nhận diện grace entry (`userRedeemedAtLeastOnce()==false`) → hiển thị label `🎁 Quà tặng cài đặt mới`.

## 7. Policy check

| # | Rule | Kết quả |
|---|---|---|
| 1 | Banner không che nội dung tương tác | ✅ dock bottom `ReadingPage`, có `navigationBarsPadding` |
| 2 | App Open không show trên splash thuần | ✅ chỉ warmup ở splash, show khi resume từ background |
| 3 | Interstitial tôn trọng frequency cap | ✅ `AdSafetyLimits` (TEST debug / CONTENT release) |
| 4 | Rewarded chỉ trigger khi user bấm nút | ✅ nút trong VIP screen |
| 5 | Không tự click ad / fake close | ✅ banner composable do SDK render |

---

# Phụ lục — kế hoạch cũ (`_260321AdmobWrapper`, bản 1.1.1)

> Giữ lại để tham chiếu lịch sử. Đã thay thế bởi plan 1.1.5 ở trên.

## 1. Đánh giá hiện trạng (cũ)
- App Open (Splash): `SplashActivity.kt` `initSplashScreen`.
- Interstitial: load ở `MainActivity.kt`, show ở `SettingsPage.kt`.
- Banner: code draft ở `MainActivity.kt` bị comment out.
- App Open (background): `AppLifecycleListener` comment out.
- Reward: không có touchpoint.

## 2. Thông số cũ
- AppLovin SDK Key: `e75FnQfS…`
- Banner ID: `1c8add7db0559d0b`
- Interstitial ID: `c64cc20b2583e3cb`
- Reward ID: `2d899bfeff96984a`
- App Open ID: `4e74ffbdceb9a6cf`
