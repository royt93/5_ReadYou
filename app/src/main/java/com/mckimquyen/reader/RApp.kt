package com.mckimquyen.reader

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import com.google.android.gms.ads.MobileAds
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.AppSv
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.android.CrashHandler
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.android.SplashActivity
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.net.NetworkDataSource
import com.mckimquyen.reader.infrastructure.rss.OPMLDataSource
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.sdkadbmob.AdMobManager
import com.mckimquyen.reader.sdkadbmob.AppLifecycleListener
import com.mckimquyen.reader.ui.ext.del
import com.mckimquyen.reader.ui.ext.getLatestApk
import com.mckimquyen.reader.ui.ext.isFdroid
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

//https://www.reddit.com/r/rss/comments/fylt16/is_there_a_website_where_you_can_download_opml/

//TODO finger print
//TODO why you see ad
//TODO import oplm vietnam

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
class RApp : Application(), Configuration.Provider {

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
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(this@RApp) {}
            AdMobManager.init(this@RApp) { success, gaidCurrent ->
                Log.d("roy93~", "AdMobManager init success $success, gaidCurrent $gaidCurrent")
            }
        }
        registerActivityLifecycleCallbacks(
            AppLifecycleListener(
                { isForeground, activity ->
                    if (isForeground) {
                        Log.d("roy93~", "App moved to Foreground")
                        if (activity.localClassName == SplashActivity::class.java.simpleName) {
                            //do nothing
                        } else {
                            AdMobManager.showAppOpenAd(activity)
                        }
                    } else {
                        Log.d("roy93~", "App moved to Background")
                    }
                }, { activity ->
                    Log.d("roy93~", "callbackActivityCreated ${activity.localClassName}")
                    if (activity.localClassName == SplashActivity::class.java.simpleName) {
                        //do nothing
                    } else {
                        AdMobManager.loadAppOpenAd(this, BuildConfig.ADMOB_APP_OPEN_ID)
                    }
                }
            )
        )
    }

    /**
     * Override the [Configuration.Builder] to provide the [HiltWorkerFactory].
     */
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
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
}
