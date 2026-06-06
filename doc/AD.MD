# Kế hoạch migrate Ad sang `AdmobApplovinWrapper` 1.1.5

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
| Rewarded | ❌ không có | ✅ nút "Watch ad → 3 ngày VIP" + fallback interstitial |
| VIP | chỉ gate AMOLED bằng interstitial | màn VIP đầy đủ: by-key, auto-trial 1 ngày (SDK built-in), progress/countdown |
| VIP secret | — | single `vipKeySecret` = 30d key (Base64, không plain trong `.kt`) |

## 4. Thông tin user đã cấp / còn mở

Đã cấp đủ:
- AppLovin Rewarded ID: `2d899bfeff96984a` (debug + release).
- AdMob: dùng Google **test** ID (`IS_ENABLE_ADMOB=false`, AdMob dormant).
- Privacy Policy URL: `https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f`.
- VIP key 30 ngày: `9fA0q7eN!27cLx04@21993Y2u0I7#Q0` (= `vipKeySecret`). VIP key 3 ngày: `eQ7@93L0f!2Y2707xN04021993u0I#2aK`.

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
