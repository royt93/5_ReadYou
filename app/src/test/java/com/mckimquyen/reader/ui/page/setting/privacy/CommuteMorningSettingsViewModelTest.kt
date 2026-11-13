package com.mckimquyen.reader.ui.page.setting.privacy

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.commuteAutoPlayOnHeadset
import com.mckimquyen.reader.ui.ext.commuteListenMinuteOfDay
import com.mckimquyen.reader.ui.ext.commuteScriptGeminiConsent
import com.mckimquyen.reader.ui.ext.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteMorningSettingsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vm: PrivacySettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { context.dataStore.edit { it.clear() } }
        vm = PrivacySettingsViewModel(context)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun <T> eventually(read: () -> T, expected: T) = runBlocking {
        withTimeout(5_000) { while (read() != expected) delay(20) }
        assertEquals(expected, read())
    }

    @Test fun defaults_consentOff_0700_autoPlayOff() {
        assertEquals(false, vm.commuteScriptGeminiConsent.value)
        assertEquals(420, vm.commuteListenMinuteOfDay.value)
        assertEquals(false, vm.commuteAutoPlayOnHeadset.value)
    }

    @Test fun agreeing_writesTheConsent_andShowsItOn() {
        vm.setCommuteScriptGeminiConsent(true)
        eventually({ context.commuteScriptGeminiConsent }, true)
        eventually({ vm.commuteScriptGeminiConsent.value }, true)
    }

    @Test fun declining_afterAgreeing_writesFalse() {
        vm.setCommuteScriptGeminiConsent(true)
        eventually({ vm.commuteScriptGeminiConsent.value }, true)
        vm.setCommuteScriptGeminiConsent(false)
        eventually({ vm.commuteScriptGeminiConsent.value }, false)
        eventually({ context.commuteScriptGeminiConsent }, false)
    }

    @Test fun theNewConsent_doesNotTurnOnAnyOtherAiConsent() {
        vm.setCommuteScriptGeminiConsent(true)
        eventually({ vm.commuteScriptGeminiConsent.value }, true)
        assertEquals(false, vm.oracleClaimsGeminiConsent.value)
        assertEquals(false, vm.quizGeminiConsent.value)
        assertEquals(false, vm.deepDiveGeminiConsent.value)
        assertEquals(false, vm.podcastStudioGeminiConsent.value)
    }

    @Test fun changingTheListenTime_isSaved() {
        vm.setCommuteListenMinuteOfDay(8 * 60 + 15)
        eventually({ context.commuteListenMinuteOfDay }, 495)
        eventually({ vm.commuteListenMinuteOfDay.value }, 495)
    }

    @Test fun anOutOfRangeListenTime_isNotStored() {
        vm.setCommuteListenMinuteOfDay(99_999)
        eventually({ vm.commuteListenMinuteOfDay.value }, 420)
        assertEquals(420, context.commuteListenMinuteOfDay)
    }

    @Test fun turningAutoPlayOn_isSaved_andOffIsSaved() {
        vm.setCommuteAutoPlayOnHeadset(true)
        eventually({ context.commuteAutoPlayOnHeadset }, true)
        vm.setCommuteAutoPlayOnHeadset(false)
        eventually({ context.commuteAutoPlayOnHeadset }, false)
    }

    @Test fun savingTheListenTime_leavesTheOtherTwoSettingsAlone() {
        vm.setCommuteScriptGeminiConsent(true)
        vm.setCommuteAutoPlayOnHeadset(true)
        eventually({ vm.commuteAutoPlayOnHeadset.value }, true)
        vm.setCommuteListenMinuteOfDay(600)
        eventually({ vm.commuteListenMinuteOfDay.value }, 600)
        assertEquals(true, vm.commuteScriptGeminiConsent.value)
        assertEquals(true, vm.commuteAutoPlayOnHeadset.value)
    }
}
