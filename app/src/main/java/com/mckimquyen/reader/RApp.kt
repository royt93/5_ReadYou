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
import com.mckimquyen.reader.domain.sv.BrainRpgDecayWorker
import com.mckimquyen.reader.domain.sv.FeedAutopilotWorker
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
import com.roy.sdkadbmob.ErrorReporter
import com.roy.sdkadbmob.PaidEventListener
import com.roy.sdkadbmob.SdkVersion
import com.mckimquyen.reader.ui.page.setting.vip.AdKeys
import com.mckimquyen.reader.infrastructure.android.SplashActivity
import com.mckimquyen.reader.infrastructure.android.AdUmpDebug
import com.mckimquyen.reader.infrastructure.android.LegacyAutoVipCleaner
import com.mckimquyen.reader.domain.commute.CommuteScheduleMigration
import com.mckimquyen.reader.ui.ext.commuteBatteryConditionApplied
import com.mckimquyen.reader.ui.ext.commuteListenMinuteOfDay
import com.mckimquyen.reader.ui.ext.setCommuteBatteryConditionApplied
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

        if (!com.mckimquyen.reader.infrastructure.android.AdValidationHelper.validateReleaseAdConfig(
                isDebug = BuildConfig.DEBUG,
                isEnableAdmob = BuildConfig.IS_ENABLE_ADMOB,
                admobRewardedId = BuildConfig.ADMOB_REWARDED_ID,
            )
        ) {
            Log.e("roy93~Ad", "⚠️ Production release build must not use Google test ID for AdMob rewarded ad!")
        }

        val adConfig = AdSdkConfig(
            isEnableAdmob          = BuildConfig.IS_ENABLE_ADMOB,
            isDebug                = BuildConfig.DEBUG,
            admobBannerId          = BuildConfig.ADMOB_BANNER_ID,
            admobInterstitialId    = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId         = BuildConfig.ADMOB_APP_OPEN_ID,
            admobRewardedId        = BuildConfig.ADMOB_REWARDED_ID,
            // AppLovin is deferred by the owner (2026-11-24): the app ships with AdMob only (IS_ENABLE_ADMOB=true) and these ids are not exercised.
            // Do not touch this branch without the owner asking for it.
            applovinBannerId       = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId      = BuildConfig.APPLOVIN_APP_OPEN_ID,
            applovinRewardedId     = BuildConfig.APPLOVIN_REWARDED_ID,
            applovinSdkKey         = BuildConfig.APPLOVIN_SDK_KEY,
            applovinPrivacyPolicyUrl = AdKeys.PRIVACY_POLICY_URL,
            // Integrity secret for the SDK's own preferences + the public key that verifies ECDSA VIP tokens + the redeem codes.
            // All three come from the private app.properties; the SDK refuses a release build that still uses its sample key.
            vipKeySecret           = AdKeys.VIP_SECRET,
            vipTokenPublicKey      = AdKeys.VIP_TOKEN_PUBLIC_KEY,
            vipRedeemCodes         = AdKeys.VIP_REDEEM_CODES,
            // Pay the reward the moment the user has earned it (Google's recommendation); the callback still fires exactly once,
            // and every rewarded call site in this app reads that single callback, so nothing is paid twice.
            grantRewardOnEarn      = true,
            // Consent test only: force the UMP form for a phone outside the EEA. Empty (null) unless a debug build was given the gradle properties.
            umpDebugGeography      = if (BuildConfig.DEBUG) AdUmpDebug.geographyOrNull(BuildConfig.UMP_DEBUG_GEOGRAPHY) else null,
            umpTestDeviceHashedIds = if (BuildConfig.DEBUG) AdUmpDebug.parseHashedIds(BuildConfig.UMP_TEST_DEVICE_HASHED_IDS) else emptyList(),
            // The splash already runs consent and the SDK's own splash ad; an App Open on top of it would stack two ads.
            appOpenExcludedActivities = listOf(SplashActivity::class.java),
            // Debug: nới throttle để QC test ad nhanh. Release: balanced preset cho app reader.
            safety                 = if (BuildConfig.DEBUG) AdSafetyLimits.TEST else AdSafetyLimits.CONTENT,
        )

        Log.d("roy93~Ad", "[setupAdmob] 📦 AdSdkConfig built — setConfig() + initialize() one-shot")
        AdManager.setConfig(adConfig)
        // Owner test devices. Debug AND release: a release-signed build has a different hash from a debug-signed one on the same
        // phone, and an unknown device gets real ads (invalid traffic). The SDK adds its own QC list; this list is only ever added to.
        AdManager.setTestDeviceIds(*AdKeys.TEST_DEVICE_IDS.toTypedArray())
        // Must be set here and not from an Activity: the SDK drops a listener when the Activity that set it is destroyed.
        AdManager.paidEventListener = PaidEventListener { adType, valueMicros, currency, precision, adSource ->
            if (BuildConfig.DEBUG) Log.d("roy93~Ad", "[paid] $adType $valueMicros $currency $precision $adSource")
        }
        AdManager.errorReporter = ErrorReporter { throwable, context ->
            Log.w("roy93~Ad", "[sdk-error] $context", throwable)
        }
        // initialize() tự chạy đủ 4 bước: earlyInit → provider SDK init → AdManager.init →
        // registerAppOpenAdLifecycle (main thread). Không cần wire MobileAds/AppLovinSdk thủ công.
        AdManager.initialize(this) { success, gaid ->
            Log.d("roy93~Ad", "[setupAdmob] AdManager.initialize() result: success=$success, gaid=$gaid")
            // The SDK has loaded its stored phone list by now; drop the old auto-VIP list once (see LegacyAutoVipCleaner).
            if (success) LegacyAutoVipCleaner.forApp(this).runOnce()
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
        // STRAT-01b: an older install still has a schedule without the battery condition; replace it once, then keep it (see CommuteScheduleMigration).
        CommuteWorker.enqueueDailyWork(
            workManager,
            listenMinuteOfDay = applicationContext.commuteListenMinuteOfDay,
            policy = CommuteScheduleMigration.policyFor(applicationContext.commuteBatteryConditionApplied),
        )
        applicationContext.setCommuteBatteryConditionApplied(true)
        BrainRpgDecayWorker.enqueueDailyWork(workManager)
        FeedAutopilotWorker.enqueueWeeklyWork(workManager)
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
