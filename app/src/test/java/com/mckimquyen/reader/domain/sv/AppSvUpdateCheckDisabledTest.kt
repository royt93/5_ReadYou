package com.mckimquyen.reader.domain.sv

import android.content.Context
import com.mckimquyen.reader.infrastructure.net.NetworkDataSource
import com.mckimquyen.reader.ui.ext.UPDATE_CHECK_ENABLED
import com.mckimquyen.reader.ui.page.setting.tip.UpdateViewModel
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The update check pointed at the upstream project (`Ashinch/ReadYou`, latest 0.16.2), so it could never report a newer version of this app,
 * and its Update button opened that project's page. The Play build updates through the store, so the check is switched off: no request is
 * made when the app opens or when the version is tapped, and the "checking" toast is not shown for a check that will not happen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSvUpdateCheckDisabledTest {
    @Test
    fun theSwitchIsOff() = assertFalse(UPDATE_CHECK_ENABLED)

    @Test
    fun checkUpdate_makesNoRequestAndReportsNothing() = runTest {
        val network = mockk<NetworkDataSource>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appSv = AppSv(mockk<Context>(relaxed = true), network, dispatcher, dispatcher)

        val result = appSv.checkUpdate(showToast = false)

        assertNull("a disabled check must not claim there is or is not an update", result)
        coVerify(exactly = 0) { network.getReleaseLatest(any()) }
    }

    @Test
    fun theViewModelDoesNotStartACheck_soNoCheckingToastIsShown() {
        // viewModelScope runs on Dispatchers.Main; point it at an unconfined test dispatcher so a started check would run before the assert.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val appSv = mockk<AppSv>(relaxed = true)
            val viewModel = UpdateViewModel(appSv)
            var preProcessorCalls = 0
            var postProcessorCalls = 0

            viewModel.checkUpdate(preProcessor = { preProcessorCalls++ }, postProcessor = { postProcessorCalls++ })

            assertEquals("the 'checking' toast runs in the pre-processor", 0, preProcessorCalls)
            assertEquals(0, postProcessorCalls)
            coVerify(exactly = 0) { appSv.checkUpdate(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
