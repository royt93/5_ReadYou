package com.mckimquyen.reader.infrastructure.android

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.infrastructure.pref.AccountSettingsProvider
import com.mckimquyen.reader.infrastructure.pref.LanguagesPref
import com.mckimquyen.reader.infrastructure.pref.SettingsProvider
import com.roy.sdkadbmob.AdManager
import com.mckimquyen.reader.ui.page.common.HomeEntry
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * The Single-Activity Architecture.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
//    private var adView: AdView? = null

    @Inject
    lateinit var accountDao: AccountDao

    override fun attachBaseContext(newBase: Context) {
        // Read locale from SharedPreferences (mirrored by LanguagesPref.put()).
        // Same pattern as RApp.attachBaseContext — cannot use DataStore here.
        val locale = try {
            val languagePref = newBase
                .getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
                .getInt("languages", 0)
            LanguagesPref.fromValue(languagePref).getLocale()
        } catch (e: Exception) {
            Log.e("RLog", "Error reading locale preference: $e", e)
            LocaleList.getDefault().get(0)
        }

        // Create configuration with locale and font scale
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        configuration.fontScale = 1.0f

        // Wrap context with new configuration
        val wrappedContext = newBase.createConfigurationContext(configuration)

        super.attachBaseContext(wrappedContext)
    }

    override fun onResume() {
        super.onResume()
//        adView?.resume()
        rateAppInApp(BuildConfig.DEBUG)
    }

    override fun onPause() {
//        adView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
//        adView?.destroy()
        super.onDestroy()
    }

    private fun enableAdaptiveRefreshRate() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display // Sử dụng API mới
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay // Fallback cho API thấp hơn
        }

        if (display != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val supportedModes = display.supportedModes
                val highestRefreshRateMode = supportedModes.maxByOrNull { it.refreshRate }
                if (highestRefreshRateMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = highestRefreshRateMode.modeId
                    }
                    println("Adaptive refresh rate applied: ${highestRefreshRateMode.refreshRate} Hz")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        WindowCompat.setDecorFitsSystemWindows(window, false)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            window.addFlags(FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS)
//        }
//        Log.i("RLog", "onCreate: ${ProfileInstallerInitializer().create(this)}")

        // Initialize UI first to avoid ANR
        setContent {
            AccountSettingsProvider(accountDao) {
                SettingsProvider {
                    HomeEntry(activity = this@MainActivity)
                }
            }
        }

        // Apply adaptive refresh rate once at startup (no need to re-apply every onResume)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableAdaptiveRefreshRate()
        }

        // Language is now applied in attachBaseContext, no need to set it here

        // Initialize AdMob on main thread (required by AdMob)
        lifecycleScope.launch(Dispatchers.Main) {
            Log.d("roy93~Ad", "[MainActivity] 📥 Calling AdManager.loadInterstitial()")
            AdManager.loadInterstitial(this@MainActivity)
        }
    }

//    private var doubleBackToExitPressedOnce = false
//
//    @Deprecated("Deprecated in Java")
//    override fun onBackPressed() {
//        if (doubleBackToExitPressedOnce) {
//            super.onBackPressed()
//            return
//        }
//
//        this.doubleBackToExitPressedOnce = true
//        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show()
//        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
//    }



}

// Chỉ thử mời review TỐI ĐA 1 lần cho mỗi tiến trình (process). Cờ này reset khi app bị kill,
// nhờ đó dù onResume() được gọi nhiều lần (quay lại từ background, đổi cấu hình...) cũng không
// spam requestReviewFlow. Google vẫn rate-limit ở phía server, đây là lớp chặn phía client.
private var hasAttemptedReviewThisSession = false

// Số lần mở app tối thiểu trước khi mời review lần đầu — tránh hỏi ngay ở launch đầu tiên,
// chọn thời điểm tự nhiên hơn (người dùng đã quay lại app vài lần).
private const val MIN_OPENS_BEFORE_REVIEW = 3
private const val MIN_DAYS_BETWEEN_REVIEWS = 7L
private const val MILLIS_PER_DAY = 1000L * 60 * 60 * 24

//rateAppInApp(BuildConfig.DEBUG)
fun Activity.rateAppInApp(forceRateInApp: Boolean = false) {
    //import gradle app
//    implementation("com.google.android.play:review:2.0.2")
//    implementation("com.google.android.play:review-ktx:2.0.2")

    // Đã thử trong phiên này rồi thì bỏ qua (kể cả khi force, để không bật lại popup mỗi onResume).
    if (hasAttemptedReviewThisSession) return
    hasAttemptedReviewThisSession = true

    val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    val lastReviewTime = sharedPreferences.getLong("last_review_time", 0L)
    val currentTime = Calendar.getInstance().timeInMillis

    // Chống đổi giờ hệ thống: nếu đồng hồ bị lùi về quá khứ (currentTime < lastReviewTime),
    // coi như mốc cũ không hợp lệ và reset về hiện tại để không vô tình mở popup ngay.
    if (lastReviewTime > currentTime) {
        sharedPreferences.edit().putLong("last_review_time", currentTime).apply()
    }
    val safeLastReviewTime = sharedPreferences.getLong("last_review_time", currentTime)

    // Đếm số lần mở app (tăng 1 lần cho mỗi phiên).
    val openCount = sharedPreferences.getInt("app_open_count", 0) + 1
    sharedPreferences.edit().putInt("app_open_count", openCount).apply()

    val daysSinceLastReview = (currentTime - safeLastReviewTime) / MILLIS_PER_DAY
    val isFirstReview = safeLastReviewTime == 0L

    val shouldRequest = forceRateInApp ||
        (openCount >= MIN_OPENS_BEFORE_REVIEW &&
            (isFirstReview || daysSinceLastReview >= MIN_DAYS_BETWEEN_REVIEWS))

    if (!shouldRequest) return

    val reviewManager = ReviewManagerFactory.create(this)
    val request = reviewManager.requestReviewFlow()
    request.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val reviewInfo: ReviewInfo = task.result
            reviewManager.launchReviewFlow(this, reviewInfo)
            // Chỉ cập nhật mốc khi thực sự khởi chạy được luồng review.
            sharedPreferences.edit().putLong("last_review_time", currentTime).apply()
        }
    }
}
