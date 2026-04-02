package com.mckimquyen.reader

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.android.gms.ads.MobileAds
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.AppSv
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.android.CrashHandler
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.net.NetworkDataSource
import com.mckimquyen.reader.infrastructure.pref.LanguagesPref
import com.mckimquyen.reader.infrastructure.rss.OPMLDataSource
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.applovin.sdk.AppLovinSdk
import com.mckimquyen.reader.ui.ext.del
import com.mckimquyen.reader.ui.ext.getLatestApk
import com.mckimquyen.reader.ui.ext.isFdroid
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

//https://www.reddit.com/r/rss/comments/fylt16/is_there_a_website_where_you_can_download_opml/

//TODO finger print
//TODO why you see ad

//done mckimquyen
//admob
//review in app bingo
//font scale
//120hz
//change icon launcher
//double to exit app
//leak canary
//proguard
//change pkg name manifest
//policy
//rate app, share app, more app
//build version
//keystore
//beta tester

@HiltAndroidApp
class RApp : Application(), WorkConfiguration.Provider, ImageLoaderFactory {

    override fun attachBaseContext(base: Context) {
        // Read locale from SharedPreferences (mirrored from DataStore by LanguagesPref.put()).
        // We CANNOT use DataStore here: the preferencesDataStore delegate calls applicationContext
        // which is null during attachBaseContext. SharedPreferences has no such restriction.
        val locale = try {
            val languagePref = base
                .getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
                .getInt("languages", 0)
            LanguagesPref.fromValue(languagePref).getLocale()
        } catch (e: Exception) {
            Log.e("RLog", "Error reading locale in RApp: $e", e)
            LocaleList.getDefault().get(0)
        }

        // Create configuration with locale
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))

        // Wrap context with new configuration
        val wrappedContext = base.createConfigurationContext(configuration)

        super.attachBaseContext(wrappedContext)
    }

    @Inject
    lateinit var androidDatabase: AndroidDatabase

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var networkDataSource: NetworkDataSource

    @Inject
    lateinit var opmlDataSource: OPMLDataSource

    @Inject
    lateinit var rssHelper: RssHelper

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var appSv: AppSv

    @Inject
    lateinit var androidStringsHelper: AndroidStringsHelper

    @Inject
    lateinit var accountService: AccountSv

    @Inject
    lateinit var localRssSv: LocalRssSv

    @Inject
    lateinit var opmlService: OpmlSv

    @Inject
    lateinit var rssSv: RssSv

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @IODispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var imageLoader: ImageLoader

    /**
     * When the application startup.
     *
     * 1. Set the uncaught exception handler
     * 2. Initialize the default account if there is none
     * 3. Synchronize once
     * 4. Check for new version
     */
    override fun onCreate() {
        super.onCreate()
        setupAdmob()
        CrashHandler(this)
        applicationScope.launch {
            accountInit()
            workerInit()
            checkUpdate()
        }
    }

    fun setupAdmob() {
        val provider = if (BuildConfig.IS_ENABLE_ADMOB) "AdMob" else "AppLovin MAX"
        Log.d("roy93~Ad", "[setupAdmob] 🚀 Starting ad setup, provider=$provider, isDebug=${BuildConfig.DEBUG}")

        val adConfig = AdSdkConfig(
            isEnableAdmob          = BuildConfig.IS_ENABLE_ADMOB,
            isDebug                = BuildConfig.DEBUG,
            admobBannerId          = BuildConfig.ADMOB_BANNER_ID,
            admobInterstitialId    = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId         = BuildConfig.ADMOB_APP_OPEN_ID,
            applovinBannerId       = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId      = BuildConfig.APPLOVIN_APP_OPEN_ID
        )

        Log.d("roy93~Ad", "[setupAdmob] 📦 AdSdkConfig built, calling setConfig()")
        AdManager.setConfig(adConfig)

        Log.d("roy93~Ad", "[setupAdmob] ⏱️ Calling earlyInit() — starting session clock")
        AdManager.earlyInit(this)

        if (BuildConfig.IS_ENABLE_ADMOB) {
            Log.d("roy93~Ad", "[setupAdmob] 📡 AdMob mode — calling MobileAds.initialize()")
            MobileAds.initialize(this) { status ->
                Log.d("roy93~Ad", "[setupAdmob] ✅ MobileAds.initialize() done, calling AdManager.init()")
                AdManager.init(this, adConfig) { success, gaid ->
                    Log.d("roy93~Ad", "[setupAdmob] AdManager.init() result: success=$success, gaid=$gaid")
                    if (success) {
                        Log.d("roy93~Ad", "[setupAdmob] 📲 Registering AppOpenAd lifecycle on MainThread")
                        Handler(Looper.getMainLooper()).post {
                            AdManager.registerAppOpenAdLifecycle(this@RApp)
                            Log.d("roy93~Ad", "[setupAdmob] ✅ registerAppOpenAdLifecycle() done")
                        }
                    } else {
                        Log.d("roy93~Ad", "[setupAdmob] ⚠️ AdManager.init() failed — AppOpen lifecycle NOT registered")
                    }
                }
            }
        } else {
            Log.d("roy93~Ad", "[setupAdmob] 📡 AppLovin MAX mode — building initConfig")
            val initConfig = com.applovin.sdk.AppLovinSdkInitializationConfiguration.builder(
                BuildConfig.APPLOVIN_SDK_KEY,
                this
            )
                .setMediationProvider(com.applovin.sdk.AppLovinMediationProvider.MAX)
                .build()

            Log.d("roy93~Ad", "[setupAdmob] 📡 Calling AppLovinSdk.initialize()")
            AppLovinSdk.getInstance(this).initialize(initConfig) {
                Log.d("roy93~Ad", "[setupAdmob] ✅ AppLovinSdk.initialize() done, calling AdManager.init()")
                AdManager.init(this, adConfig) { success, gaid ->
                    Log.d("roy93~Ad", "[setupAdmob] AdManager.init() result: success=$success, gaid=$gaid")
                    if (success) {
                        Log.d("roy93~Ad", "[setupAdmob] 📲 Registering AppOpenAd lifecycle on MainThread")
                        Handler(Looper.getMainLooper()).post {
                            AdManager.registerAppOpenAdLifecycle(this@RApp)
                            Log.d("roy93~Ad", "[setupAdmob] ✅ registerAppOpenAdLifecycle() done")
                        }
                    } else {
                        Log.d("roy93~Ad", "[setupAdmob] ⚠️ AdManager.init() failed — AppOpen lifecycle NOT registered")
                    }
                }
            }
        }
    }

    /**
     * Override the [WorkConfiguration.Builder] to provide the [HiltWorkerFactory].
     */
    override fun getWorkManagerConfiguration(): WorkConfiguration =
        WorkConfiguration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            // Only log verbose WorkManager internals in debug builds
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR
            )
            .build()

    private suspend fun accountInit() {
        withContext(ioDispatcher) {
            if (accountService.isNoAccount()) {
                accountService.addDefaultAccount()
            }
        }
    }

    private suspend fun workerInit() {
        rssSv.get().doSync(isOnStart = true)
    }

    private suspend fun checkUpdate() {
        if (isFdroid) return
        withContext(ioDispatcher) {
            applicationContext.getLatestApk().let {
                if (it.exists()) it.del()
            }
        }
        appSv.checkUpdate(showToast = false)
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}
