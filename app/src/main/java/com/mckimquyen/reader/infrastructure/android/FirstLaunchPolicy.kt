package com.mckimquyen.reader.infrastructure.android

import android.content.Context
import kotlin.math.abs

/**
 * Decides whether the Welcome and Terms of Service screen is shown.
 *
 * `isFirstLaunch` used to default to false and nothing ever wrote true, so a new user never saw the Terms. A missing key cannot simply mean "new user": every install
 * made before this change also has no key and must not be asked again. The package times tell them apart: on a fresh install the first-install and last-update times are
 * the same instant, after an update they are not.
 */
object FirstLaunchPolicy {
    /** A fresh install records both times within the same moment; an update cannot follow an install this quickly. */
    internal const val FRESH_INSTALL_TOLERANCE_MS = 5_000L

    /** [stored] is the saved answer: null before the user has ever agreed or declined, true or false afterwards. */
    fun isFirstLaunch(stored: Boolean?, firstInstallTimeMs: Long, lastUpdateTimeMs: Long): Boolean {
        if (stored != null) return stored
        return abs(lastUpdateTimeMs - firstInstallTimeMs) <= FRESH_INSTALL_TOLERANCE_MS
    }

    fun isFirstLaunch(context: Context, stored: Boolean?): Boolean {
        if (stored != null) return stored
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            isFirstLaunch(null, info.firstInstallTime, info.lastUpdateTime)
        }.getOrDefault(false) // if the package times cannot be read, do not block anyone behind the Terms screen
    }
}
