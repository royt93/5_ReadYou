package com.mckimquyen.reader.infrastructure.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.window.SplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.R
import com.mckimquyen.reader.sdkadbmob.AdMobManager
import dagger.hilt.android.AndroidEntryPoint

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("roy93~", "onCreate")

        setContent {
            SplashScreen {
                goToMain()
            }
        }

        AdMobManager.loadAppOpenAd(
            context = this@SplashActivity,
            adUnitId = BuildConfig.ADMOB_APP_OPEN_ID,
            onAdLoaded = { result ->
                Log.d("roy93~", "onAdLoaded result $result")
                goToMain()
                AdMobManager.showAppOpenAd(this@SplashActivity)
            },
        )
    }

    private fun goToMain() {
        Log.d("roy93", "goToMain")
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finishAffinity()
    }
}

@Composable
fun SplashScreen(onReady: () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxSize()
//            .background(Color.white), // hoặc Color.White
        , contentAlignment = Alignment.Center
    ) {
//        Image(
//            painter = painterResource(id = R.drawable.ic_launcher_960),
//            contentDescription = "Splash",
//            modifier = Modifier.size(120.dp), // Tuỳ chỉnh kích thước
//            contentScale = ContentScale.Fit
//        )
    }
}

