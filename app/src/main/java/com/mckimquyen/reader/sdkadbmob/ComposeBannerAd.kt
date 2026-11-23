package com.mckimquyen.reader.sdkadbmob

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mckimquyen.reader.ui.ext.findActivity
import com.roy.sdkadbmob.AdBanner
import com.roy.sdkadbmob.AdBannerSize
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.ExperimentalAdApi

/**
 * Thin wrapper over the SDK [AdBanner]: adaptive full-width banner, lifecycle (pause/resume/destroy) is managed by the SDK.
 *
 * [AdBanner] casts `LocalContext` straight to an `Activity` and silently draws nothing when that fails, so the real
 * Activity (found through any ContextWrapper chain) is handed down explicitly. No Activity means no banner, never a crash.
 */
@OptIn(ExperimentalAdApi::class)
@Composable
fun ComposeBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val adSize: AdBannerSize = remember(activity) { AdManager.getAdaptiveBannerSize(activity) }
    CompositionLocalProvider(LocalContext provides activity) {
        AdBanner(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            adSize = adSize,
        )
    }
}
