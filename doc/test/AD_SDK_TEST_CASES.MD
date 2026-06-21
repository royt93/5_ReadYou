# Ad SDK Integration — Test Cases

> **Phiên bản:** 2026-06-21  
> **SDK:** `com.github.royt93:AdmobApplovinWrapper:1.1.5` (package `com.roy.sdkadbmob`)  
> **Provider chính:** AppLovin MAX (`IS_ENABLE_ADMOB=false`)  
> **Áp dụng cho:** `devDebug`, `devRelease`, `prodRelease`

---

## Phân loại test

| Ký hiệu | Loại | Tool |
|---|---|---|
| **[U]** | Unit test — JVM/Robolectric | `./gradlew testDevDebugUnitTest` |
| **[I]** | Instrumented test — emulator/device | `./gradlew connectedDevDebugAndroidTest` |
| **[M]** | Manual test — cần tay người + device thật | Checklist thủ công |

**Cờ môi trường:**
- `[ON]` = device có mạng bình thường
- `[OFF]` = Airplane Mode / tắt WiFi + Data
- `[SLOW]` = throttle mạng về 2G (Developer Options → Network throttle)

---

## Module 1 — Configuration & Build (`TC-CONFIG`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-CONFIG-001 | [U] | — | `AdKeys.VIP_SECRET` bằng `VipKeys.VIP_30D_KEY` | Assert `AdKeys.VIP_SECRET == VipKeys.VIP_30D_KEY` | PASS | — |
| TC-CONFIG-002 | [U] | — | `PRIVACY_POLICY_URL` bắt đầu bằng `https://` | Assert `AdKeys.PRIVACY_POLICY_URL.startsWith("https://")` | PASS | — |
| TC-CONFIG-003 | [M] | [ON] | Release build: `ADMOB_REWARDED_ID` còn dùng Google test ID | Mở `app/build.gradle` release block, kiểm tra field `ADMOB_REWARDED_ID` | Thấy comment `WARNING [H2]` và giá trị `ca-app-pub-3940256099942544/...` — **PHẢI thay real ID trước khi bật `IS_ENABLE_ADMOB=true`** | H2 |
| TC-CONFIG-004 | [M] | — | Không có plain VIP key trong file doc | `grep -r "9fA0q7eN" doc/` và `grep -r "eQ7@93L0" doc/` | 0 kết quả | H3 |
| TC-CONFIG-005 | [M] | — | Không có plain VIP key trong source `.kt` | `grep -r "9fA0q7eN" app/src/` và `grep -r "eQ7@93L0" app/src/` | 0 kết quả | H3 |
| TC-CONFIG-006 | [M] | — | `IS_ENABLE_ADMOB=false` trên cả debug + release | Kiểm tra `app/build.gradle` cả 2 block | Đều là `false` | — |
| TC-CONFIG-007 | [M] | — | `AdSafetyLimits.TEST` chỉ dùng cho debug | Đọc `RApp.setupAdmob()` — `safety = if (BuildConfig.DEBUG) AdSafetyLimits.TEST else AdSafetyLimits.CONTENT` | Đúng phân nhánh | — |

---

## Module 2 — Splash & Consent UMP (`TC-SPLASH`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-SPLASH-001 | [M] | [ON] | Non-EEA: consent skip, navigate bình thường | Cài app trên device non-EEA (VN). Mở app. | Không có UMP dialog. Vào Main trong < 5s. | — |
| TC-SPLASH-002 | [M] | [ON] | EEA accept: dialog xuất hiện, navigate sau khi accept | Dùng VPN EEA. Xóa app data. Mở app. Accept consent. | Dialog UMP hiện → user accept → vào Main. | — |
| TC-SPLASH-003 | [M] | [OFF] | **Offline + first launch**: splash không hang, fallback sau ≤ 8s | Airplane Mode. Xóa app data. Mở app. Đợi. | Sau tối đa 8s → tự navigate vào Main. Log thấy `⏰ consent+splash timeout`. | **C1** |
| TC-SPLASH-004 | [M] | [OFF] | **Offline + có cached consent**: navigate bình thường, không cần timeout | Cài app, mở 1 lần (online). Close. Bật Airplane Mode. Mở lại. | Vào Main < 3s (UMP cache hit). Không trigger timeout. | C1 |
| TC-SPLASH-005 | [M] | [ON] | String "Lưu ý quảng cáo" đúng ngôn ngữ device | Đổi device locale → vi / zh / ja / fr / de. Mở app. | Splash text hiển thị đúng ngôn ngữ tương ứng. | mn2 |
| TC-SPLASH-006 | [M] | [SLOW] | Mạng chậm: timeout 8s đủ để consent resolve | Throttle 2G. Mở app. | Consent resolve trước 8s → navigate bình thường (không trigger fallback). | C1 |
| TC-SPLASH-007 | [M] | [ON] | Double-navigation guard: `navigationStarted` chặn gọi `goToMain()` 2 lần | Monitor logcat filter `goToMain`. Mở app lần thường. | Log `goToMain` chỉ xuất hiện đúng 1 lần. | — |
| TC-SPLASH-008 | [M] | [ON] | App Open ad chỉ show khi warmup xong, KHÔNG show trên màn splash | Quan sát splash → Main transition. | Không có ad overlay trên màn splash thuần. Ad (nếu có) chỉ show giữa splash và main. | Policy R2 |

---

## Module 3 — App Open Ad (`TC-APPOPEN`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-APPOPEN-001 | [M] | [ON] | App Open show khi cold start (release) | Cài release build. Mở app lần đầu. | App Open ad hiện sau khi consent xong, trước khi vào Main. | — |
| TC-APPOPEN-002 | [M] | [ON] | App Open show khi resume từ background | Vào Main → Home button → Mở lại app sau > `appOpenMinIntervalSec`. | App Open ad hiện khi foreground. | — |
| TC-APPOPEN-003 | [M] | [ON] | Frequency cap: App Open không show liên tiếp dưới 120s (release) | Resume app 2 lần liên tiếp trong < 120s. | Lần 2 KHÔNG có App Open ad. | — |
| TC-APPOPEN-004 | [M] | [OFF] | Offline: App Open không show, navigate bình thường | Airplane Mode. Mở app. | Không có ad. Vào Main bình thường (không crash). | — |
| TC-APPOPEN-005 | [M] | [ON] | Debug: `appOpenMinIntervalSec = 10s` (TEST limits) | Debug build. Resume app 2 lần cách nhau > 10s. | Lần 2 có App Open. | — |

---

## Module 4 — Banner Ad (`TC-BANNER`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-BANNER-001 | [M] | [ON] | Banner load và hiển thị ở bottom ReadingPage | Mở 1 bài đọc. Đợi toolbar visible. | Banner adaptive hiển thị dock bottom, không che nút Back/Star/Unread. | Policy R1 |
| TC-BANNER-002 | [M] | [ON] | Banner ẩn khi toolbar ẩn (scroll xuống) | Trong ReadingPage, scroll xuống đủ để toolbar auto-hide. | Banner cũng ẩn theo toolbar. | mn1 |
| TC-BANNER-003 | [M] | [ON] | Banner không che nội dung tương tác khi toolbar visible | Quan sát layout khi toolbar + banner đều show. | Banner dock hoàn toàn dưới BottomBar, không overlap nút. | Policy R1 |
| TC-BANNER-004 | [M] | [OFF] | Offline: shimmer placeholder hiển thị, không crash | Airplane Mode. Mở bài đọc. | Shimmer / empty space ở vị trí banner. Không có crash hoặc error toast. | — |
| TC-BANNER-005 | [M] | [ON] | Back-and-forth: banner không leak sau destroy ReadingPage | Mở ReadingPage → Back → Mở lại → Back. Lặp 5 lần. | Không có memory crash. `logcat` không có `AdView leak` warning. | — |
| TC-BANNER-006 | [M] | [ON] | Adaptive banner size: width khớp screen width | Rotate device (portrait ↔ landscape). | Banner re-measure theo screen width mới, không cắt bị. | — |

---

## Module 5 — Interstitial (`TC-INTER`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-INTER-001 | [M] | [ON] | Interstitial load ở `MainActivity.onCreate` | Logcat filter `loadInterstitial`. Mở app → vào Main. | Log `[MainActivity] 📥 Calling AdManager.loadInterstitial()`. | — |
| TC-INTER-002 | [M] | [ON] | Interstitial show khi back từ SettingsPage (system back) | Vào Settings. Nhấn nút Back hệ thống. | Interstitial ad hiển thị (nếu loaded). Sau khi dismiss → về Home. | — |
| TC-INTER-003 | [M] | [ON] | Interstitial show khi tap back icon trong SettingsPage | Vào Settings. Tap icon ← trên toolbar. | Interstitial ad hiển thị. Sau khi dismiss → về Home. | — |
| TC-INTER-004 | [M] | [OFF] | Offline: back từ Settings không crash, navigate bình thường | Airplane Mode. Vào Settings. Back. | Không có ad. Vào Home bình thường. Không crash. | M3 |
| TC-INTER-005 | [M] | [ON] | Frequency cap debug: interstitial show lại sau 10s | Debug build. Back Settings 2 lần cách nhau > 10s. | Lần 2 vẫn có interstitial (nếu reload). | — |
| TC-INTER-006 | [M] | [ON] | Frequency cap release: interstitial không show liên tiếp dưới 60s | Release build. Back Settings 2 lần cách nhau < 60s. | Lần 2 KHÔNG có interstitial. Navigate vẫn xảy ra. | — |
| TC-INTER-007 | [M] | [ON] | AMOLED unlock: interstitial show khi free user accept | DarkThemePage → toggle AMOLED → dialog → "Watch ad". | Interstitial show. Sau dismiss: AMOLED được unlock. | — |
| TC-INTER-008 | [M] | [OFF] | AMOLED: offline free user → toast "Ad not ready", AMOLED không unlock | Airplane Mode. Toggle AMOLED → dialog → "Watch ad". | Toast hiện. AMOLED vẫn locked. | M1 |

---

## Module 6 — Rewarded Ad (`TC-REWARDED`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-REWARDED-001 | [M] | [ON] | Rewarded load khi vào VipManagementPage | Logcat filter `loadRewarded`. Vào Settings → VIP. | SDK gọi load khi màn mở. | — |
| TC-REWARDED-002 | [M] | [ON] | Xem hết rewarded → grant 3 ngày VIP | Tap "Watch ad → 3 days VIP". Xem ad đến cuối. | VIP active. Hero card đổi gold. Countdown ~3 ngày. Haptic feedback. | — |
| TC-REWARDED-003 | [M] | [ON] | Đóng rewarded sớm → KHÔNG grant VIP, show interstitial | Tap "Watch ad". Đóng ad trước khi kết thúc. | Interstitial hiện (fallback revenue). Sau dismiss: dialog "need to watch full ad". VIP KHÔNG được grant. | M2 |
| TC-REWARDED-004 | [M] | [OFF] | Offline: tap "Watch ad" → không có rewarded, không có interstitial, dialog fail | Airplane Mode. Tap "Watch ad". | Không crash. Dialog thông báo lỗi hoặc "thử lại". VIP KHÔNG được grant. | M2, M3 |
| TC-REWARDED-005 | [M] | [ON] | Nút "Watch ad" disabled khi user đang VIP active | Kích hoạt VIP bằng key. Quay lại màn VIP. | Nút "Watch ad" bị disabled (không tap được). | — |
| TC-REWARDED-006 | [M] | [ON] | Rewarded callback thread-safe: grant xảy ra trên main thread | Xem hết rewarded. Quan sát logcat. | Không có `CalledFromWrongThreadException`. UI update mượt. | — |

---

## Module 7 — VIP Key Management (`TC-VIP`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-VIP-001 | [U] | — | Key 30 ngày decode từ Base64 hợp lệ | `VipKeys.VIP_30D_KEY.isNotBlank()` | `true` | — |
| TC-VIP-002 | [U] | — | Key 3 ngày decode từ Base64 hợp lệ | `VipKeys.VIP_3D_KEY.isNotBlank()` | `true` | — |
| TC-VIP-003 | [U] | — | Hai key phải khác nhau | `VipKeys.VIP_30D_KEY != VipKeys.VIP_3D_KEY` | `true` | — |
| TC-VIP-004 | [U] | — | `lookupDays` trim khoảng trắng | `lookupDays("  ${VIP_30D_KEY}  ")` | `30` | — |
| TC-VIP-005 | [U] | — | `lookupDays` case-sensitive | `lookupDays(VIP_30D_KEY.uppercase())` | `null` | — |
| TC-VIP-006 | [I] | [ON] | Nhập đúng key 30 ngày → VIP active, dialog success | UI test: nhập key → Activate | `isVipByKeyActive() = true`, remaining ~30d | — |
| TC-VIP-007 | [I] | — | Nhập key sai → dialog failed, VIP không active | UI test: nhập "WRONG-KEY" → Activate | Dialog "Failed". `isVipByKeyActive() = false` | — |
| TC-VIP-008 | [M] | [ON] | Progress bar tăng dần theo thời gian (elapsed semantic) | Kích hoạt VIP. Quan sát progress bar trong 30s. | Bar bắt đầu gần 0%, tăng dần hướng 100%. KHÔNG bắt đầu đầy. | — |
| TC-VIP-009 | [M] | [ON] | Countdown cập nhật mỗi giây | Kích hoạt VIP. Quan sát countdown text. | `Xd HHh MMm SSs` giảm 1s mỗi giây. | — |
| TC-VIP-010 | [M] | [ON] | Revoke VIP → về Free state | Kích hoạt VIP. Tap "Revoke VIP" → Confirm. | Hero card về xám "Free user". Countdown ẩn. Progress bar ẩn. | — |
| TC-VIP-011 | [M] | [ON] | VIP hết hạn khi màn đang mở → UI tự lật về Free | Kích hoạt VIP key 3 ngày. Giả lập hết hạn (đổi system clock). | Sau khi đồng hồ vượt expiry, màn tự đổi về Free. | — |
| TC-VIP-012 | [M] | [OFF] | Offline: kích hoạt VIP bằng key (không cần network) | Airplane Mode. Nhập key đúng. Activate. | VIP active bình thường — VIP-by-key chỉ check local SharedPreferences, không cần network. | — |
| TC-VIP-013 | [M] | [ON] | Privacy Policy footer → mở browser đúng URL | Tap "Privacy Policy" link ở bottom màn VIP. | Browser mở `https://loitp.notion.site/Term-Privacy-Policy...`. Không crash nếu không có browser (dùng `runCatching`). | — |

---

## Module 8 — VIP First-Install Grace (`TC-GRACE`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-GRACE-001 | [M] | [ON] | First install release: 1 ngày VIP auto-grant | Cài release APK lần đầu. Mở app. Vào VIP screen. | Hero card gold. Label "🎁 Quà tặng cài đặt mới". Expiry ≈ now + 24h. | — |
| TC-GRACE-002 | [M] | [ON] | Debug build: grace KHÔNG grant (không nhiễu QC) | Cài debug APK lần đầu. Mở app. Vào VIP screen. | Hero card xám "Free user". Không có grace VIP. | — |
| TC-GRACE-003 | [M] | [ON] | Mở lại app lần 2+: grace không re-grant | Cài release. Mở lần 1 (grace granted). Force-kill. Mở lần 2. | VIP còn hiệu lực từ lần 1. Logcat KHÔNG có `grant VIP` lần 2. | — |
| TC-GRACE-004 | [M] | [ON] | Sau khi user tự nhập key → label đổi từ grace sang "VIP redeemed" | Mở lần đầu (grace active). Nhập key 30 ngày. | Label đổi từ "🎁 Quà tặng" sang "VIP 30 ngày". `userRedeemedAtLeastOnce()=true`. | — |

---

## Module 9 — AMOLED + VIP Bypass (`TC-AMOLED`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-AMOLED-001 | [M] | [ON] | Free user: toggle AMOLED → dialog confirm xem ad | Settings → Color & Style → Dark Theme → toggle AMOLED. | Dialog "Unlock AMOLED theme" hiện. Chưa unlock. | — |
| TC-AMOLED-002 | [M] | [ON] | Free user: xem interstitial thành công → AMOLED unlocked | Dialog → "Watch ad" → xem hết. | AMOLED được bật. Toggle AMOLED tiếp theo là direct (không hỏi ad nữa). | — |
| TC-AMOLED-003 | [M] | [ON] | **VIP user: toggle AMOLED → unlock ngay, không hỏi ad** | Kích hoạt VIP. Vào Dark Theme. Toggle AMOLED. | Không có dialog. AMOLED bật ngay. `isVipByKeyActive()` được check. | **M1** |
| TC-AMOLED-004 | [M] | [OFF] | **Offline VIP user: AMOLED unlock bình thường** | Airplane Mode. VIP active. Toggle AMOLED. | Unlock ngay, không cần network. | M1 |
| TC-AMOLED-005 | [M] | [OFF] | **Offline free user: toast "Ad not ready", AMOLED không unlock** | Airplane Mode. Free user. Dialog → "Watch ad". | Toast "Ad not available". AMOLED vẫn locked. | — |
| TC-AMOLED-006 | [M] | [ON] | `GlobalScope` đã xóa: không crash khi config change giữa chừng | Tap "Watch ad" trong AMOLED dialog. Ngay lập tức rotate device. | Không crash `CalledFromWrongThreadException` hay `IllegalStateException`. AMOLED vẫn unlock nếu ad success. | **H1** |

---

## Module 10 — VipPrefs (`TC-PREFS`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-PREFS-001 | [U] | — | Default `grantedAt = 0L` | `VipPrefs.getGrantedAtMs()` trên prefs mới | `0L` | — |
| TC-PREFS-002 | [U] | — | Save/Get `grantedAt` chính xác | Save `123_456_789L` → Get | `123_456_789L` | — |
| TC-PREFS-003 | [U] | — | Clear `grantedAt` về 0 | Save 999 → Clear → Get | `0L` | — |
| TC-PREFS-004 | [U] | — | `userRedeemed` default false | Prefs mới → `userRedeemedAtLeastOnce()` | `false` | — |
| TC-PREFS-005 | [U] | — | `markUserRedeemed` bền vững qua instance mới | Mark → new instance → Get | `true` | — |
| TC-PREFS-006 | [U] | — | `clearGrantedAt` không xóa `userRedeemed` | Mark redeem → save 555 → clear → check redeemed | Redeemed vẫn `true`, grantedAt = `0L` | — |
| TC-PREFS-007 | [M] | [ON] | Không dùng `applicationContext` sai: prefs persist qua recreate | Kích hoạt VIP. Rotate device (recreate Activity). | `grantedAt` và `userRedeemed` vẫn đúng sau recreate. | — |

---

## Module 11 — VipMath (`TC-MATH`)

| ID | Loại | Môi trường | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|---|
| TC-MATH-001 | [U] | — | Progress = 0 khi vừa kích hoạt | `computeElapsedProgress(t, t+day, t)` | `0` | — |
| TC-MATH-002 | [U] | — | Progress = 50 ở giữa chu kỳ | `computeElapsedProgress(t, t+day, t+day/2)` | `50` | — |
| TC-MATH-003 | [U] | — | Progress = 100 khi hết hạn | `computeElapsedProgress(t, t+day, t+day)` | `100` | — |
| TC-MATH-004 | [U] | — | Progress = 100 khi total ≤ 0 (clock skew / expired) | `computeElapsedProgress(5000, 1000, 3000)` | `100` | — |
| TC-MATH-005 | [U] | — | Progress coerce về 0 khi now < granted | `computeElapsedProgress(10000, 10000+day, 5000)` | `0` | — |
| TC-MATH-006 | [U] | — | Progress coerce về 100 khi now > expiry | `computeElapsedProgress(t, t+day, t+2*day)` | `100` | — |
| TC-MATH-007 | [U] | — | `formatCountdown(0L)` | — | `"0d 00h 00m 00s"` | — |
| TC-MATH-008 | [U] | — | `formatCountdown` padding 2 chữ số | `5h 3m 7s` | `"0d 05h 03m 07s"` | — |
| TC-MATH-009 | [U] | — | `formatCountdown` nhiều ngày | `12d 3h 24m 15s` | `"12d 03h 24m 15s"` | — |
| TC-MATH-010 | [U] | — | `formatCountdown` giá trị âm coi như 0 | `formatCountdown(-5000L)` | `"0d 00h 00m 00s"` | — |

---

## Module 12 — Offline tổng hợp (`TC-OFFLINE`)

> Tất cả test dưới đây thực hiện với **Airplane Mode** bật từ đầu.

| ID | Loại | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|
| TC-OFFLINE-001 | [M] | First launch offline: splash không hang | Xóa app data. Airplane Mode. Mở app. Đợi. | Vào Main sau ≤ 8s. | **C1** |
| TC-OFFLINE-002 | [M] | Re-launch offline (có cache): splash nhanh | Launch 1 lần online (cache consent). Airplane Mode. Mở lại. | Vào Main < 3s. | C1 |
| TC-OFFLINE-003 | [M] | VIP key activate offline | Airplane Mode. Nhập key hợp lệ. Activate. | VIP active. Không cần network (local check). | TC-VIP-012 |
| TC-OFFLINE-004 | [M] | VIP AMOLED bypass offline | Airplane Mode. VIP active. Toggle AMOLED. | Unlock ngay. | TC-AMOLED-004 |
| TC-OFFLINE-005 | [M] | Interstitial load fail → back Settings vẫn navigate | Airplane Mode. Settings → Back. | Không crash. Về Home. Không có ad (expected). | TC-INTER-004 |
| TC-OFFLINE-006 | [M] | Banner offline: shimmer/placeholder thay ad | Airplane Mode. Mở bài đọc. Toolbar visible. | Banner vị trí trống hoặc shimmer. Không crash. | TC-BANNER-004 |
| TC-OFFLINE-007 | [M] | Rewarded offline → dialog fail, không grant VIP | Airplane Mode. Tap "Watch ad". | Không crash. VIP không được grant. | TC-REWARDED-004 |
| TC-OFFLINE-008 | [M] | Online → Offline → Online: interstitial tự recover | Mở app (online, interstitial load). Airplane Mode 2p. Online lại. Back Settings. | Interstitial có thể show nếu SDK retry thành công sau khi có mạng. | M3 |

---

## Module 13 — Policy AdMob (`TC-POLICY`)

| ID | Loại | Mô tả | Bước | Kỳ vọng | Rule |
|---|---|---|---|---|---|
| TC-POLICY-001 | [M] | Banner không che nút tương tác | Mở ReadingPage. Toolbar visible. Kiểm tra layout. | Banner hoàn toàn dưới BottomBar. Không overlap Back/Star/Unread/NextArticle. | R1 |
| TC-POLICY-002 | [M] | App Open KHÔNG show trên màn splash thuần | Quan sát splash → main transition. | Ad (nếu có) chỉ overlay giữa transition, không show khi splash đang loading. | R2 |
| TC-POLICY-003 | [M] | Interstitial respect frequency cap (release: 60s) | Release. Back Settings lần 1 → lần 2 (< 60s sau). | Lần 2 không có interstitial. | R3 |
| TC-POLICY-004 | [M] | Rewarded chỉ khi user tap nút | Quan sát: rewarded không tự show khi vào VipManagementPage. | Không có rewarded auto-show. Chỉ show khi tap "Watch ad". | R4 |
| TC-POLICY-005 | [M] | Không có fake close button | Inspect layout banner (screenshot). | Không có custom overlay giả mạo nút close trên banner. | R5 |

---

## Module 14 — Security (`TC-SEC`)

| ID | Loại | Mô tả | Bước | Kỳ vọng | Issue ref |
|---|---|---|---|---|---|
| TC-SEC-001 | [M] | Plain VIP key không có trong repo source | `grep -r "9fA0q7eN" .` từ repo root | 0 match (chỉ có Base64 trong `.kt`, `[REDACTED]` trong doc) | H3 |
| TC-SEC-002 | [M] | APK decompile: key ở dạng Base64, không plain text | Decompile release APK bằng jadx. Search class `VipKeys`. | Thấy `VIP_30D_B64 = "OWZBMHE3..."` (Base64). Không thấy plain key. | H3 |
| TC-SEC-003 | [M] | VIP key không thể đoán từ UI | Không có hint plain key trên màn VIP (placeholder chỉ là "VD: LOITP_30"). | Không lộ pattern key qua UI. | — |
| TC-SEC-004 | [M] | Replay: key đã dùng không thể kích hoạt thêm lần nữa (lib tự handle) | Kích hoạt key 30 ngày. Revoke. Nhập lại cùng key. | Hành vi tùy lib — nếu lib cho phép re-use, ghi nhận. Nếu không, dialog failed. | — |

---

## Checklist chạy test trước release

```
[ ] ./gradlew testDevDebugUnitTest   → TẤT CẢ [U] xanh
[ ] ./gradlew connectedDevDebugAndroidTest → TẤT CẢ [I] xanh (cần emulator)
[ ] TC-OFFLINE-001: first launch offline không hang splash
[ ] TC-OFFLINE-003: VIP key activate offline
[ ] TC-AMOLED-003: VIP user bypass AMOLED ad gate
[ ] TC-AMOLED-006: config change giữa AMOLED ad (H1 regression)
[ ] TC-CONFIG-003: ADMOB_REWARDED_ID comment WARNING còn đó (nhắc trước khi flip IS_ENABLE_ADMOB)
[ ] TC-CONFIG-004 + TC-CONFIG-005: grep plain VIP key = 0 result
[ ] TC-POLICY-001..005: 5 AdMob policy rules
[ ] TC-SPLASH-008: App Open không show trên splash thuần
```

---

## Ghi chú kỹ thuật

### Cách giả lập offline có kiểm soát
```bash
# Tắt tất cả interface trên emulator (từ host)
adb shell svc wifi disable
adb shell svc data disable

# Bật lại
adb shell svc wifi enable
adb shell svc data enable
```

### Cách throttle mạng về 2G trên emulator
Extended controls → Cellular → Network type → GPRS (2G)

### Cách verify splash timeout log
```bash
adb logcat -s "roy93~Ad" | grep -E "timeout|consent|initSplash|goToMain"
```

### Cách decompile APK để check TC-SEC-002
```bash
# Cài jadx-gui hoặc dùng jadx CLI
jadx --output-dir out/ app-prodRelease.apk
grep -r "VIP_30D_B64\|9fA0q7eN" out/
```
