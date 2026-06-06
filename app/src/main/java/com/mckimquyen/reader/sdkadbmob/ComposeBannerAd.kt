package com.mckimquyen.reader.sdkadbmob

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.roy.sdkadbmob.AdBanner
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.ExperimentalAdApi

/**
 * Wrapper mỏng delegate sang SDK [AdBanner] (SDK 1.1.3+).
 *
 * SDK tự inflate shimmer placeholder + auto pause/resume/destroy qua [androidx.compose.runtime.DisposableEffect]
 * (autoManageLifecycle=true) — app KHÔNG cần loadBanner/bannerPause/bannerResume/bannerDestroy thủ công.
 * Dùng adaptive banner full-width (rớt về 320×50 nếu chưa lấy được Activity).
 */
@OptIn(ExperimentalAdApi::class)
@Composable
fun ComposeBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val adSize = remember(activity) {
        activity?.let { AdManager.getAdaptiveBannerSize(it) } ?: com.google.android.gms.ads.AdSize.BANNER
    }
    AdBanner(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        adSize = adSize,
    )
}
