# Báo Cáo Phân Tích Code: Lỗi Tiềm Ẩn & Memory Leak

> **⚠️ CẬP NHẬT (review lại đối chiếu code hiện tại):**
> Toàn bộ hệ thống Ad đã được **migrate sang SDK ngoài** `com.roy.sdkadbmob.AdManager`
> (thư viện `com.github.royt93:AdmobWrapper`). File `sdkadbmob/AdMobManager.kt` **không
> còn tồn tại** trong project — chỉ còn `sdkadbmob/ComposeBannerAd.kt`. Do đó:
> - **Mục #1, #2, #3 ĐÃ LỖI THỜI** (mô tả file không còn tồn tại). Việc dọn dẹp lifecycle/
>   listener nay do SDK wrapper tự quản lý (xem `doc/AD.MD`).
> - **Mục #4 (ReviewManager) VẪN CÒN HIỆU LỰC** — `rateAppInApp()` vẫn gọi mỗi `onResume()`.
> - **Mục #5 (TTS) VẪN ĐÚNG** — `TtsManager.kt` còn nguyên, an toàn.

Sau khi kiểm tra toàn bộ mã nguồn của dự án (tập trung vào các patterns thường gây lỗi và leak memory trong Android), đây là các vấn đề và memory leak tiềm ẩn cần được xử lý:

## 1. Leak toàn bộ `Activity` sống mãi với Process (Nghiêm trọng) - **[🗑️ LỖI THỜI — file đã bị xóa khi migrate ad SDK]**
**File:** `com/mckimquyen/reader/sdkadbmob/AdMobManager.kt` *(không còn tồn tại)*
* **Mô tả:** Trong hàm `initSplashScreen(activity: Activity, onAdLoaded: () -> Unit)`, có sử dụng `CoroutineScope(Dispatchers.Default).launch` để lắng nghe `EventBus.eventFlow.collectLatest`. 
  `EventBus.eventFlow` là một `SharedFlow` không bao giờ kết thúc, do đó Coroutine này chạy vô hạn (không bị huỷ vì không dính tới Lifecycle nào). Khối lệnh (lambda) bên trong `collectLatest` có chứa biến `activity`, dẫn đến việc `Activity` (thường là Splash/Main) truyền vào lần đầu tiên sẽ **bị rò rỉ (leak) vĩnh viễn** trong toàn bộ thời gian sống của ứng dụng, không thể bị Garbage Collector dọn dẹp.
* **Cách khắc phục:** 
  1. Hạn chế truyền `Activity` vào `CoroutineScope` Global như vậy.
  2. Tại hàm `initSplashScreen`, chỉ lắng nghe Flow bằng `lifecycleScope` của chính `Activity` đó, ví dụ `activity.lifecycleScope.launch { ... }` (nếu `activity` là `ComponentActivity`).

## 2. Leak Listener trong Singleton `AdMobManager` (Vừa) - **[🗑️ LỖI THỜI — `interstitialListener` không còn trong code]**
**File:** `com/mckimquyen/reader/infrastructure/android/MainActivity.kt` *(không còn tham chiếu `AdMobManager.interstitialListener`)*
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

## 3. Leak nhỏ từ hệ thống `Handler.postDelayed` (Nhẹ) - **[🗑️ LỖI THỜI — file đã bị xóa khi migrate ad SDK]**
**File:** `com/mckimquyen/reader/sdkadbmob/AdMobManager.kt` *(không còn tồn tại)*
* **Mô tả:** Có rất nhiều chỗ dùng đoạn code:
  `Handler(Looper.getMainLooper()).postDelayed({ ... }, 500/1000)`
  Bên trong các block `{ ... }` này có thể truy cập `Activity` (ví dụ thông qua lambda callback). Project hiện tại không quản lý hay xóa (`removeCallbacks`) cho các `Handler` này khi màn hình bị thu hồi.
* **Mức độ:** Vì thời gian trễ chỉ từ `500ms` đến `1s`, leak thời gian rất ngắn nên chỉ gây lãng phí bộ nhớ tạm thời, ít có nguy cơ Crash OOM.
* **Cách khắc phục:** Tối ưu hóa bằng cách thay vì dùng `Handler(...)`, ta có thể chuyển sang dùng Coroutine Delay trong `lifecycleScope` của màn hình, hoặc giữ instance của các `Runnable` để `removeCallbacks` khi không còn cần đến.

## 4. `ReviewManager` check logic (Lỗi chức năng tiềm ẩn) - **[🔴 CÒN HIỆU LỰC — cần xử lý]**
**File:** `com/mckimquyen/reader/infrastructure/android/MainActivity.kt` (extension `rateAppInApp`, gọi tại `onResume()`)
* **Mô tả:** Logic kiểm tra `$daysSinceLastReview` được lưu giá trị dạng mili-giây nhưng chia tay hơi thủ công. Nên lưu ý nếu người dùng thay đổi Date Time hệ thống. Đồng thời `ReviewManager` của thư viện Google không nên bị gọi mỗi `onResume()` cho dù có check time hay flag, khuyến nghị nên gọi ở thời điểm thích hợp hơn (điều kiện sau khi thực hiện xong X actions nào đó) vì Google Rate Limit sẽ tự động chặn việc nháy hộp pop-up rate liên tục dù dev có cưỡng ép đẩy lệnh.

## 5. Tính năng Nghe Báo (Text-to-Speech) - **[✅ AN TOÀN - KHÔNG LEAK]**
**File:** 
- `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt`
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`

* **Mô tả kiểm tra & thiết kế:**
  Tính năng Nghe Báo đã được triển khai với kiến trúc đảm bảo Memory Leak Free tuyệt đối:
  1. **Singleton & Application Context:** `TtsManager` được khởi tạo như một `@Singleton` và chỉ nhận vào `@ApplicationContext` (thay vì Activity). Kể cả khi Native Engine `TextToSpeech` bên dưới C++ Android có ôm cái context này mãi thì cũng là context của toàn ứng dụng (sống chết theo app), hoàn toàn **không gây leak Activity/View**.
  2. **An toàn Coroutine Lifecycle:** `ReadingViewModel` lắng nghe tín hiệu (`TtsState`) thông qua `viewModelScope.launch`. Khi người dùng thoát (Back) trang báo, `ViewModel` bị Clear -> `viewModelScope` tự huỷ bộ listener.
  3. **Dọn dẹp triệt để:** Ngay thời điểm `ViewModel` bị gọi hàm huỷ `onCleared()`, tôi có gắn block gọi `ttsManager.stop()` qua AudioEngine, đập rớt ngay hành vi đọc lách nhách dưới nền, tối ưu hoá hoàn toàn RAM lẫn CPU Pin của máy.
* **Kết luận:** Feature hoàn toàn vô trùng và không thể Leak RAM.

## Tổng Kết
Ứng dụng nhìn chung sử dụng Jetpack Compose (Modern UI) + GetX / Android Jetpack ViewModel. Các `ViewModel` đều tuân thủ tốt, không bị truyền cứng `Context` vào mà dùng Injection (Hilt). 

**Trạng thái xử lý lỗi (đã cập nhật theo code hiện tại):**
- Mục #1, #2, #3 **đã trở nên vô hiệu** vì toàn bộ `AdMobManager.kt` cũ đã bị xóa và thay
  bằng SDK ngoài `com.roy.sdkadbmob.AdManager`. Cơ chế dọn dẹp listener/lifecycle nay nằm
  trong SDK wrapper, không còn là trách nhiệm của project (xem `doc/AD.MD`).
- Mục #4 (`ReviewManager` gọi mỗi `onResume()`, tính ngày bằng system clock) **vẫn còn**
  trong `MainActivity.kt` → là điểm cần refactor tiếp theo.
- Mục #5 (TTS) vẫn an toàn, không leak.
