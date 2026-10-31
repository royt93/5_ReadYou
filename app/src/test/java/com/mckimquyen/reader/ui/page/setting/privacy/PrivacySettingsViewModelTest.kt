package com.mckimquyen.reader.ui.page.setting.privacy

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import com.mckimquyen.reader.infrastructure.pref.HtmlAntiTrackerPref
import com.mckimquyen.reader.infrastructure.pref.UrlTrackingCleanPref
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.retractionRadarEnabled
import com.mckimquyen.reader.ui.ext.headlineSignalsEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class PrivacySettingsViewModelTest {
    private lateinit var context: Context
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun <T> awaitReal(block: suspend () -> T): T =
        withContext(Dispatchers.Default) { withTimeout(REAL_TIMEOUT_MS) { block() } }

    private companion object {
        const val REAL_TIMEOUT_MS = 10_000L
    }

    @Test
    fun settersPersistAndPublishPrivacyPreferences() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()

        viewModel.setDohProvider(DohProviderPref.CLOUDFLARE)
        viewModel.setUrlTrackingClean(false)
        viewModel.setHtmlAntiTracker(false)
        advanceUntilIdle()

        // DataStore writes on a real IO dispatcher, so await in real time (not runTest virtual time).
        assertEquals(DohProviderPref.CLOUDFLARE, awaitReal { viewModel.dohProvider.first { it == DohProviderPref.CLOUDFLARE } })
        assertFalse(awaitReal { viewModel.urlTrackingClean.first { !it } })
        assertFalse(awaitReal { viewModel.htmlAntiTracker.first { !it } })
    }

    @Test
    fun headlineSignals_defaultsOnAndPersistsBothDirections() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()
        assertTrue(viewModel.headlineSignals.value)
        assertTrue(context.headlineSignalsEnabled)

        viewModel.setHeadlineSignals(false)
        advanceUntilIdle()
        assertFalse(awaitReal { viewModel.headlineSignals.first { !it } })
        assertFalse(context.headlineSignalsEnabled)

        viewModel.setHeadlineSignals(true)
        advanceUntilIdle()
        assertTrue(awaitReal { viewModel.headlineSignals.first { it } })
        assertTrue(context.headlineSignalsEnabled)
    }

    @Test
    fun retractionRadar_defaultsOnAndPersistsWhenSwitchedOff() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()
        assertTrue(viewModel.retractionRadar.value)

        viewModel.setRetractionRadar(false)
        advanceUntilIdle()

        assertFalse(awaitReal { viewModel.retractionRadar.first { !it } })
        assertFalse(context.retractionRadarEnabled)
    }
}
