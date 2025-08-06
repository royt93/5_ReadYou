package com.mckimquyen.reader.infrastructure.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.sdkadbmob.AdMobManager
import dagger.hilt.android.AndroidEntryPoint

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("roy93~", "onCreate")
//        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SplashScreen {
                goToMain()
            }
        }

        AdMobManager.initSplashScreen(activity = this, onAdLoaded = {
            goToMain()
        })
    }

    private fun goToMain() {
        Log.d("roy93", "goToMain")
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
//        overridePendingTransition(0, 0)
//        finishAffinity()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Trì hoãn finish để đợi animation hoàn tất
        window.decorView.postDelayed({
            finish() // Finish sau animation
        }, 300) // delay khoảng 300ms (hoặc đúng thời gian của animation)
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
        androidx.compose.material3.Text(
            text = "Please note: this action may show ads",
            color = androidx.compose.ui.graphics.Color.Black,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        )
    }
}

