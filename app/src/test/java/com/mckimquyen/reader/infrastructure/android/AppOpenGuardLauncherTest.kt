package com.mckimquyen.reader.infrastructure.android

import android.app.Application
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.mckimquyen.reader.infrastructure.android.AppOpenGuard.Companion.launchGuarded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The guard on a real ActivityResultLauncher: the app open ad is suppressed when the system screen opens and
 * released when its result comes back, with the same wiring the screens use (launchGuarded + onResult in the callback).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppOpenGuardLauncherTest {

    private val calls = mutableListOf<Boolean>()
    private val guard = AppOpenGuard { calls += it }

    private fun registry(onLaunch: (ActivityResultRegistry, Int) -> Unit) = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            onLaunch(this, requestCode)
        }
    }

    private fun launcherOn(reg: ActivityResultRegistry, results: MutableList<Boolean>) =
        reg.register("permission", ActivityResultContracts.RequestPermission()) { granted ->
            guard.onResult()
            results += granted
        }

    @Test
    fun suppressedWhileTheSystemScreenIsOpen_releasedWhenItsResultArrives() {
        val results = mutableListOf<Boolean>()
        var suppressedAtLaunch: Boolean? = null
        val reg = registry { r, code -> suppressedAtLaunch = calls.lastOrNull(); r.dispatchResult(code, true) }
        launcherOn(reg, results).launchGuarded("android.permission.POST_NOTIFICATIONS", guard)
        assertEquals("the ad was already suppressed when the screen opened", true, suppressedAtLaunch)
        assertEquals(listOf(true), results)
        assertEquals("suppress, then release", listOf(true, false), calls)
    }

    @Test
    fun aDeniedResultReleasesTheSuppressionToo() {
        val results = mutableListOf<Boolean>()
        val reg = registry { r, code -> r.dispatchResult(code, false) }
        launcherOn(reg, results).launchGuarded("android.permission.BLUETOOTH_CONNECT", guard)
        assertEquals(listOf(false), results)
        assertEquals(false, calls.last())
    }

    @Test
    fun stillSuppressedWhileNoResultHasArrived() {
        val reg = registry { _, _ -> /* the user is still in the system screen */ }
        launcherOn(reg, mutableListOf()).launchGuarded("android.permission.POST_NOTIFICATIONS", guard)
        assertEquals(listOf(true), calls)
    }

    @Test
    fun aLaunchThatThrowsReleasesTheSuppressionAndRethrows() {
        val reg = registry { _, _ -> throw IllegalStateException("no activity to handle this") }
        var thrown = false
        try {
            launcherOn(reg, mutableListOf()).launchGuarded("android.permission.POST_NOTIFICATIONS", guard)
        } catch (_: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(listOf(true, false), calls)
    }
}
