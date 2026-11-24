package com.mckimquyen.reader.infrastructure.android

import androidx.activity.result.ActivityResultLauncher
import com.roy.sdkadbmob.AdManager

/**
 * Keeps the app open ad from covering a system screen the user was just sent to (permission prompt, file picker, settings).
 *
 * Those screens take the app to the background, and the SDK shows an app open ad when the app comes back to the front, which would land on top of the
 * dialog the user is still answering. [AdManager.suppressAppOpenTemporarily] turns that automatic ad off; the SDK clears it by itself after five
 * minutes, but it is cleared here as soon as the result arrives, or at once if the launch fails.
 *
 * A launch that never returns a result (the user leaves for good) is covered by that five minute limit.
 */
class AppOpenGuard(private val setSuppressed: (Boolean) -> Unit = AdManager::suppressAppOpenTemporarily) {

    /** Wraps a launch: suppress, run it, and undo the suppression right away if it throws. */
    fun <I> wrap(launch: (I) -> Unit): (I) -> Unit = { input ->
        setSuppressed(true)
        try {
            launch(input)
        } catch (e: Exception) {
            setSuppressed(false)
            throw e
        }
    }

    /**
     * For a `startActivity` that takes the user to another app (share chooser, browser, Custom Tab). That call returns no result, so the suppression is
     * released by [onResult], which MainActivity calls from onResume when the app is back in front; the SDK also clears it by itself after five minutes.
     * If the other app cannot be started the suppression is undone at once and the error goes on.
     */
    fun leaveForOtherApp(start: () -> Unit) {
        setSuppressed(true)
        try {
            start()
        } catch (e: Exception) {
            setSuppressed(false)
            throw e
        }
    }

    /** Call from the result callback of the launcher. */
    fun onResult() = setSuppressed(false)

    companion object {
        /** The guard used by the screens. */
        val shared: AppOpenGuard by lazy { AppOpenGuard() } // lazy: a plain JVM test must be able to load the class without the ad SDK

        /** `launcher.launch(input)` with the app open ad suppressed until the result. */
        /** `leaveForOtherApp` on the shared guard, for call sites that have no result callback. */
        fun leaveForOtherApp(start: () -> Unit) = shared.leaveForOtherApp(start)

        fun <I> ActivityResultLauncher<I>.launchGuarded(input: I, guard: AppOpenGuard = shared) = guard.wrap<I> { launch(it) }(input)
    }
}
