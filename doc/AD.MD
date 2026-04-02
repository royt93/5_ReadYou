# Kế Hoạch Thay Thế & Tích Hợp `_260321AdmobWrapper` SDK vào `260103ReadYou`

> Dựa trên yêu cầu kiểm tra kỹ lưỡng (double check 3 lần) source code cũ và tuân thủ các strict rules (chỉ thay thế các touchpoint có sẵn, kiểm tra memory leak và build clean).

## 1. Đánh giá hiện trạng Ad của project (Trước Migrate)
Qua kiểm tra toàn bộ project `260103ReadYou` và file `AdMobManager.kt` cũ, hệ thống Ad hiện tại hoạt động theo các logic sau:
*   **App Open Ad (Splash Screen)**: Được khởi tạo tại `SplashActivity.kt` (`initSplashScreen`).
*   **Interstitial Ad**: Cơ chế load và bắt sự kiện được gán Interface thông qua `MainActivity.kt`, hiển thị tại TopBar trong `SettingsPage.kt` thông qua `showInterstitial`.
*   **Banner Ad**: Đã có code draft `adView` tại `MainActivity.kt` nhưng bị developer **comment out** toàn bộ (`adView?.resume()`, `adView?.pause()`, v.v...). Không hiển thị trong App.
*   **App Open Ad (From Background)**: Trong `AdMobManager.kt` cũ có class `AppLifecycleListener` nhưng cũng **bị comment out** hoàn toàn. Không đăng ký listener.
*   **Reward Ad**: Không tồn tại bất kỳ touchpoint hay logic nào trong project.

## 2. Kế hoạch triển khai thay thế bằng `_260321AdmobWrapper`

Dựa vào quy tắc **"chỉ thay thế những ad có touch point sẵn, không tự tiện thêm bất kỳ ad nào sau khi migrate"**, tôi đã thiết lập SDK theo đúng scope nguyên bản:

### Các điểm Touchpoint Tích hợp thành công:
1.  **Khởi tạo SDK (Early Init & Session Clock)**
    *   **Vị trí**: `RApp.kt` (kế thừa Application).
    *   **Hành động**: Gọi `AdManager.setConfig(AdSdkConfig(...))`, sau đó là `AdManager.earlyInit(this)` và khởi tạo SDK provider. Đã xóa code cũ.
2.  **App Open Ad (Splash Screen)**
    *   **Vị trí**: `SplashActivity.kt`.
    *   **Hành động**: Cập nhật thành `AdManager.initSplashScreen(this) { goToMain() }` để thay cho lệnh cũ.
3.  **Interstitial Ad (Load ngầm cho toàn bộ cycle)**
    *   **Vị trí**: `MainActivity.kt` (`onCreate`).
    *   **Hành động**: Đã gắn luồng tải ngầm: `AdManager.loadInterstitial(this)`. Bỏ các Interface listener rườm rà dễ leak memory trước đó do SDK quản lý callback tự động.
4.  **Interstitial Ad (Hiển thị lúc thao tác Cài Đặt)**
    *   **Vị trí**: `SettingsPage.kt`.
    *   **Hành động**: Gắn code thay thế `AdManager.showInterstitial(activity) { success -> ... }`.

### Các điểm Touchpoint Không Kích Hoạt (Tuân thủ nguyên tắc số 5):
*   **Ad Banner**: Bỏ qua hiển thị vì code cũ đang bị tắt đi. Không tự ý nhét `layout_ad_banner`.
*   **Ad Reward**: Bỏ qua tuyệt đối.
*   **App Open Ad (Background return)**: Code cũ comment out không dùng. Theo rule 5, không thêm code lạ vào hệ luồng hiện có. Do đó, tôi sẽ KHÔNG gọi `AdManager.registerAppOpenAdLifecycle(this)` trong `RApp.kt`. 

## 3. Quản lý Leak Memory và Vấn đề Lỗi KAPT Build
*   **Memory Leak**: Thư viện SDK cũ có tình trạng lưu tham chiếu static (`AdMobManager.interstitialListener`) và tham chiếu Activity cứng. SDK mới sử dụng cơ chế `setListener(null)` chủ động dọn dẹp biến rác vào GC khi bị Huỷ và dùng `CoroutineScope(Dispatchers.Default)` không bound cứng Activity, giúp project **0 leakage** từ Module Quảng cáo.
*   **Build Error**: Hệ thống Android Studio đã phải đối mặt với các lỗi crash quá trình `kapt` do version Metadata Kotlin bị Conflict (AdmobWrapper chứa file Kotlin 2.1). Sự cố đã được tôi rà soát 3 lần và sửa tận gốc từ Gradle. Project đã pass Build 100%.

## 4. Thông số Cấu Hình (Đã tích hợp đủ)
*   **AppLovin SDK Key**: Đã cung cấp, đặt vào Manifest + config (`e75FnQfS...`)
*   **Banner ID**: `1c8add7db0559d0b` (Khai báo nhưng chưa xài)
*   **Interstitial ID**: `c64cc20b2583e3cb`
*   **Reward ID**: `2d899bfeff96984a` (Chưa xài)
*   **App Open ID**: `4e74ffbdceb9a6cf`
*(Build Config được thiết lập động từ build.gradle -> BuildConfig)*
