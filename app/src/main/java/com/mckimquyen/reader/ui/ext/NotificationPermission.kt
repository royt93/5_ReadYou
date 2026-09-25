package com.mckimquyen.reader.ui.ext

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mckimquyen.reader.R

/** POST_NOTIFICATIONS is a runtime permission only from Android 13 (API 33). */
fun needsNotificationPermission(sdkInt: Int, isGranted: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !isGranted

fun Context.isNotificationPermissionGranted(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Returns a callback to run when the user turns feed notifications ON: asks for POST_NOTIFICATIONS
 * on Android 13+ if not granted yet, and tells the user when it is denied (notifications would be
 * dropped silently otherwise). The feed setting itself is saved regardless, so it takes effect as
 * soon as the permission is granted later from system settings.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) context.showToastLong(context.getString(R.string.notification_permission_denied))
    }
    return {
        if (needsNotificationPermission(Build.VERSION.SDK_INT, context.isNotificationPermissionGranted())) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
