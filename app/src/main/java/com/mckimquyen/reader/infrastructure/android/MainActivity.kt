package com.mckimquyen.reader.infrastructure.android

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import coil.ImageLoader
import coil.compose.LocalImageLoader
import com.google.android.gms.ads.AdView
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.infrastructure.pref.AccountSettingsProvider
import com.mckimquyen.reader.infrastructure.pref.LanguagesPref
import com.mckimquyen.reader.infrastructure.pref.SettingsProvider
import com.mckimquyen.reader.sdkadbmob.AdMobManager
import com.mckimquyen.reader.ui.ext.languages
import com.mckimquyen.reader.ui.page.common.HomeEntry
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject

/**
 * The Single-Activity Architecture.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), AdMobManager.InterstitialAdListener {
    private var adView: AdView? = null

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var accountDao: AccountDao

    override fun attachBaseContext(newBase: Context) {
        val override = Configuration(newBase.resources.configuration)
        override.fontScale = 1.0f
        applyOverrideConfiguration(override)
        super.attachBaseContext(newBase)
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableAdaptiveRefreshRate()
        }
        rateAppInApp(BuildConfig.DEBUG)
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        adView?.destroy()
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.addFlags(FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS)
        }
//        Log.i("RLog", "onCreate: ${ProfileInstallerInitializer().create(this)}")

        // Set the language
        LanguagesPref.fromValue(languages).let {
            if (it == LanguagesPref.UseDeviceLanguages) return@let
            it.setLocale(this)
        }

        setContent {
            CompositionLocalProvider(
                LocalImageLoader provides imageLoader,
            ) {
                AccountSettingsProvider(accountDao) {
                    SettingsProvider {
                        HomeEntry()
                    }
                }
            }
        }

        AdMobManager.setCurrentActivity(this)
        AdMobManager.interstitialListener = this

//        adView = AdMobManager.loadBanner(this, BuildConfig.ADMOB_BANNER_ID, binding.bannerContainer)
        AdMobManager.loadInterstitial(this, BuildConfig.ADMOB_INTERSTITIAL_ID)
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

    override fun onAdLoaded() {
        Log.d("roy93~", "onAdLoaded")
    }

    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
        Log.d("roy93~", "onAdFailedToLoad $error")
    }

    override fun onAdShowed() {
        Log.d("roy93~", "onAdShowed")
    }

    override fun onAdDismissed() {
        Log.d("roy93~", "onAdDismissed")
    }

    override fun onAdClicked() {
        Log.d("roy93~", "onAdClicked")
    }

    override fun onAdFailedToShow(error: com.google.android.gms.ads.AdError) {
        Log.d("roy93~", "onAdFailedToShow $error")
    }

    override fun onAdNotAvailable() {
        Log.d("roy93~", "onAdNotAvailable")
    }
}

//rateAppInApp(BuildConfig.DEBUG)
fun Activity.rateAppInApp(forceRateInApp: Boolean = false) {
    //import gradle app
//    implementation("com.google.android.play:review:2.0.2")
//    implementation("com.google.android.play:review-ktx:2.0.2")

    val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    val lastReviewTime = sharedPreferences.getLong("last_review_time", 0L)
//    Log.d("roy93~", "requestReview lastReviewTime $lastReviewTime")
    val currentTime = Calendar.getInstance().timeInMillis
    val daysSinceLastReview = (currentTime - lastReviewTime) / (1000 * 60 * 60 * 24)
//    Log.d("roy93~", "requestReview forceRateInApp $forceRateInApp")
//    Log.d("roy93~", "requestReview daysSinceLastReview $daysSinceLastReview")
    if (daysSinceLastReview >= 7 || forceRateInApp) {
//    if (daysSinceLastReview >= 7) {
        val reviewManager = ReviewManagerFactory.create(this)
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                reviewManager.launchReviewFlow(this, reviewInfo)
                sharedPreferences.edit().putLong("last_review_time", currentTime).apply()
//                Log.d("roy93~", "requestReview result ${task.result}")
//                Log.d("roy93~", "requestReview isSuccessful ${task.isSuccessful}")
//                Log.d("roy93~", "requestReview isCanceled ${task.isCanceled}")
//                Log.d("roy93~", "requestReview isComplete ${task.isComplete}")
//                Log.d("roy93~", "requestReview exception ${task.exception}")
            } else {
//                Log.d("roy93~", "requestReview exception ${task.exception}")
            }
        }
    }

}
