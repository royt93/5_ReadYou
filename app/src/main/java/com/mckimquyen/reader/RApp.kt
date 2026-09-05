package com.mckimquyen.reader

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.AppSv
import com.mckimquyen.reader.domain.sv.CommuteWorker
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
import com.roy.sdkadbmob.AdSafetyLimits
import com.roy.sdkadbmob.SdkVersion
import com.mckimquyen.reader.ui.page.setting.vip.AdKeys
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
        val languagePref = try {
            base.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
                .getInt("languages", 0)
        } catch (e: Exception) {
            0
        }

        if (languagePref == 0) {
            super.attachBaseContext(base)
            return
        }

        val locale = LanguagesPref.fromValue(languagePref).getLocale()
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        }

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
        Log.d("roy93~Ad", "[setupAdmob] 🚀 ${SdkVersion.SDK_NAME} v${SdkVersion.VERSION_NAME}, provider=$provider, isDebug=${BuildConfig.DEBUG}")

        val adConfig = AdSdkConfig(
            isEnableAdmob          = BuildConfig.IS_ENABLE_ADMOB,
            isDebug                = BuildConfig.DEBUG,
            admobBannerId          = BuildConfig.ADMOB_BANNER_ID,
            admobInterstitialId    = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId         = BuildConfig.ADMOB_APP_OPEN_ID,
            admobRewardedId        = BuildConfig.ADMOB_REWARDED_ID,
            applovinBannerId       = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId      = BuildConfig.APPLOVIN_APP_OPEN_ID,
            applovinRewardedId     = BuildConfig.APPLOVIN_REWARDED_ID,
            applovinSdkKey         = BuildConfig.APPLOVIN_SDK_KEY,
            vipKeySecret           = AdKeys.VIP_SECRET,
            // Debug: nới throttle để QC test ad nhanh. Release: balanced preset cho app reader.
            safety                 = if (BuildConfig.DEBUG) AdSafetyLimits.TEST else AdSafetyLimits.CONTENT,
        )

        Log.d("roy93~Ad", "[setupAdmob] 📦 AdSdkConfig built — setConfig() + initialize() one-shot")
        AdManager.setConfig(adConfig)
        // initialize() tự chạy đủ 4 bước: earlyInit → provider SDK init → AdManager.init →
        // registerAppOpenAdLifecycle (main thread). Không cần wire MobileAds/AppLovinSdk thủ công.
        AdManager.initialize(this) { success, gaid ->
            Log.d("roy93~Ad", "[setupAdmob] AdManager.initialize() result: success=$success, gaid=$gaid")
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
        CommuteWorker.enqueueDailyWork(workManager)
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
