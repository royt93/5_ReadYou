package com.mckimquyen.reader.sdkadbmob

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.roy.sdkadbmob.AdManager
import android.widget.FrameLayout

@Composable
fun ComposeBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val container = remember { android.widget.FrameLayout(context) }
    val bannerView: android.view.View? = remember {
        try {
            AdManager.loadBanner(context, container, android.widget.TextView(context), com.google.android.gms.ads.AdSize.BANNER)
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                bannerView?.let { AdManager.bannerPause(it) }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                bannerView?.let { AdManager.bannerResume(it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bannerView?.let { AdManager.bannerDestroy(it) }
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        factory = {
            container // Return the container which holds the loaded ad!
        }
    )
}
