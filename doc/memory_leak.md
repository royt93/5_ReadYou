# Báo Cáo Phân Tích Code: Lỗi Tiềm Ẩn & Memory Leak

Sau khi kiểm tra toàn bộ mã nguồn của dự án (tập trung vào các patterns thường gây lỗi và leak memory trong Android), đây là các vấn đề và memory leak tiềm ẩn cần được xử lý:

## 1. Leak toàn bộ `Activity` sống mãi với Process (Nghiêm trọng) - **[✅ ĐÃ FIX]**
**File:** `com/mckimquyen/reader/sdkadbmob/AdMobManager.kt`
* **Mô tả:** Trong hàm `initSplashScreen(activity: Activity, onAdLoaded: () -> Unit)`, có sử dụng `CoroutineScope(Dispatchers.Default).launch` để lắng nghe `EventBus.eventFlow.collectLatest`. 
  `EventBus.eventFlow` là một `SharedFlow` không bao giờ kết thúc, do đó Coroutine này chạy vô hạn (không bị huỷ vì không dính tới Lifecycle nào). Khối lệnh (lambda) bên trong `collectLatest` có chứa biến `activity`, dẫn đến việc `Activity` (thường là Splash/Main) truyền vào lần đầu tiên sẽ **bị rò rỉ (leak) vĩnh viễn** trong toàn bộ thời gian sống của ứng dụng, không thể bị Garbage Collector dọn dẹp.
* **Cách khắc phục:** 
  1. Hạn chế truyền `Activity` vào `CoroutineScope` Global như vậy.
  2. Tại hàm `initSplashScreen`, chỉ lắng nghe Flow bằng `lifecycleScope` của chính `Activity` đó, ví dụ `activity.lifecycleScope.launch { ... }` (nếu `activity` là `ComponentActivity`).

## 2. Leak Listener trong Singleton `AdMobManager` (Vừa) - **[✅ ĐÃ FIX]**
**File:** `com/mckimquyen/reader/infrastructure/android/MainActivity.kt`
* **Mô tả:** Ở `onCreate` của `MainActivity`, ta gọi:
  ```kotlin
  AdMobManager.interstitialListener = this@MainActivity
  ```
  Nhưng ở `onDestroy()` lại **không** clear gán `AdMobManager.interstitialListener = null`. Do `AdMobManager` là một Singleton `object`, nó sẽ giữ tham chiếu mạnh (strong reference) tới phiên bản `MainActivity` đã bị destroy (nếu có sự kiện xoay màn hình hoặc hệ thống dọn dẹp memory ngầm).
* **Cách khắc phục:** Tại `MainActivity.kt` ghi đè `onDestroy()` bổ sung:
  ```kotlin
  override fun onDestroy() {
      if (AdMobManager.interstitialListener == this) {
          AdMobManager.interstitialListener = null
      }
      super.onDestroy()
  }
  ```

## 3. Leak nhỏ từ hệ thống `Handler.postDelayed` (Nhẹ) - **[⚠️ CHẤP NHẬN ĐƯỢC]**
**File:** `com/mckimquyen/reader/sdkadbmob/AdMobManager.kt`
* **Mô tả:** Có rất nhiều chỗ dùng đoạn code:
  `Handler(Looper.getMainLooper()).postDelayed({ ... }, 500/1000)`
  Bên trong các block `{ ... }` này có thể truy cập `Activity` (ví dụ thông qua lambda callback). Project hiện tại không quản lý hay xóa (`removeCallbacks`) cho các `Handler` này khi màn hình bị thu hồi.
* **Mức độ:** Vì thời gian trễ chỉ từ `500ms` đến `1s`, leak thời gian rất ngắn nên chỉ gây lãng phí bộ nhớ tạm thời, ít có nguy cơ Crash OOM.
* **Cách khắc phục:** Tối ưu hóa bằng cách thay vì dùng `Handler(...)`, ta có thể chuyển sang dùng Coroutine Delay trong `lifecycleScope` của màn hình, hoặc giữ instance của các `Runnable` để `removeCallbacks` khi không còn cần đến.

## 4. `ReviewManager` check logic (Lỗi chức năng tiềm ẩn)
**File:** `com/mckimquyen/reader/infrastructure/android/MainActivity.kt` (extension `rateAppInApp`)
* **Mô tả:** Logic kiểm tra `$daysSinceLastReview` được lưu giá trị dạng mili-giây nhưng chia tay hơi thủ công. Nên lưu ý nếu người dùng thay đổi Date Time hệ thống. Đồng thời `ReviewManager` của thư viện Google không nên bị gọi mỗi `onResume()` cho dù có check time hay flag, khuyến nghị nên gọi ở thời điểm thích hợp hơn (điều kiện sau khi thực hiện xong X actions nào đó) vì Google Rate Limit sẽ tự động chặn việc nháy hộp pop-up rate liên tục dù dev có cưỡng ép đẩy lệnh.

## Tổng Kết
Ứng dụng nhìn chung sử dụng Jetpack Compose (Modern UI) + GetX / Android Jetpack ViewModel. Các `ViewModel` đều tuân thủ tốt, không bị truyền cứng `Context` vào mà dùng Injection (Hilt). 

**Trạng thái xử lý lỗi:** 
- Đã khắc phục việc **chứa Activity trong SharedFlow collector** trong `AdMobManager` bằng cách đổi sang `(activity as? ComponentActivity)?.lifecycleScope?.launch` và dùng `first()` kết hợp với `MutableSharedFlow(replay = 1)`.
- Đã bổ sung dọn dẹp biến `interstitialListener = null` tại dòng xoá bỏ (`onDestroy`) của `MainActivity`.
- Các leak `Handler.postDelayed` ở mức độ nhẹ vì thời gian tối đa delay chỉ 1s, nó sẽ tự giải phóng sau đó mà không làm OOM hệ thống.
Từ thời điểm này, toàn bộ điểm nháy Memory Leak lớn nhất của dự án đã được xử lý xong.
