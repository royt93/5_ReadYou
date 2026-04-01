package com.mckimquyen.reader.infrastructure.android

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.core.view.WindowCompat
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.sdkadbmob.AdMobManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    // Guard against goToMain() being called more than once (e.g. race between
    // SplashScreen's onReady and AdMobManager's onAdLoaded callbacks).
    private var navigationStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("roy93~", "onCreate")
//        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SplashScreen()
        }

        AdMobManager.initSplashScreen(activity = this, onAdLoaded = {
            goToMain()
        })
    }

    private fun goToMain() {
        // Prevent double-navigation if both callbacks fire
        if (navigationStarted) {
            Log.d("roy93", "goToMain already started, skipping")
            return
        }
        navigationStarted = true
        Log.d("roy93", "goToMain")
        val intent = Intent(this, MainActivity::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Use new ActivityOptions API for Android 14+
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        } else {
            // Use legacy overridePendingTransition for older versions
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Trì hoãn finish để đợi animation hoàn tất
        lifecycleScope.launch {
            delay(300) // delay khoảng 300ms (hoặc đúng thời gian của animation)
            finish() // Finish sau animation
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // App logo
        Image(
            painter = painterResource(id = com.mckimquyen.reader.R.drawable.ic_launcher_round),
            contentDescription = "App Logo",
            modifier = Modifier.size(180.dp)
        )

        // App name
        Text(
            text = "RSS Hub",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.offset(y = 120.dp)
        )

        // Loading text
        Text(
            text = "Loading...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.offset(y = 160.dp)
        )

        // Simple loading indicator
        CircularProgressIndicator(
            modifier = Modifier.offset(y = 200.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )

        // Bottom notice
        Text(
            text = "Please note: this action may show ads",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 60.dp)
        )
    }
}

