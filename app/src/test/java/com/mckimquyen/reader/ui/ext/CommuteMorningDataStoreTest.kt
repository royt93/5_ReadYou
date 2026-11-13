package com.mckimquyen.reader.ui.ext

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteMorningDataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test fun consent_neverAsked_isNull_notFalse() = assertEquals(null, context.commuteScriptGeminiConsent)

    @Test fun consent_roundTrips(): Unit = runBlocking {
        context.setCommuteScriptGeminiConsent(true)
        assertEquals(true, context.commuteScriptGeminiConsent)
        context.setCommuteScriptGeminiConsent(false)
        assertEquals(false, context.commuteScriptGeminiConsent)
    }

    @Test fun listenMinute_default_is0700() = assertEquals(420, context.commuteListenMinuteOfDay)

    @Test fun listenMinute_roundTrips(): Unit = runBlocking {
        context.setCommuteListenMinuteOfDay(8 * 60 + 15)
        assertEquals(495, context.commuteListenMinuteOfDay)
    }

    @Test fun listenMinute_aCorruptStoredValue_fallsBackToTheDefault(): Unit = runBlocking {
        context.dataStore.edit { it[DataStoreKeys.CommuteListenMinuteOfDay.key] = 99_999 }
        assertEquals(420, context.commuteListenMinuteOfDay)
    }

    @Test fun autoPlaySwitch_defaultsToOff() = assertEquals(false, context.commuteAutoPlayOnHeadset)

    @Test fun autoPlaySwitch_roundTrips(): Unit = runBlocking {
        context.setCommuteAutoPlayOnHeadset(true)
        assertEquals(true, context.commuteAutoPlayOnHeadset)
    }

    @Test fun lastAutoPlayDay_neverWritten_isNull() = assertEquals(null, context.commuteLastAutoPlayEpochDay)

    @Test fun lastAutoPlayDay_roundTrips(): Unit = runBlocking {
        context.setCommuteLastAutoPlayEpochDay(20_000L)
        assertEquals(20_000L, context.commuteLastAutoPlayEpochDay)
    }

    @Test fun keyNames_areHardCoded_andAllDistinct() {
        val names = listOf(
            DataStoreKeys.CommuteScriptGeminiConsent.key.name,
            DataStoreKeys.CommuteListenMinuteOfDay.key.name,
            DataStoreKeys.CommuteAutoPlayOnHeadset.key.name,
            DataStoreKeys.CommuteLastAutoPlayEpochDay.key.name,
        )
        assertEquals(
            listOf("commute_script_gemini_consent", "commute_listen_minute_of_day", "commute_auto_play_on_headset", "commute_last_auto_play_epoch_day"),
            names,
        )
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun theNewConsent_isIndependentOfEveryOtherAiConsent(): Unit = runBlocking {
        context.setOracleClaimsGeminiConsent(true)
        context.setQuizGeminiConsent(true)
        context.setDeepDiveGeminiConsent(true)
        context.setPodcastStudioGeminiConsent(true)
        assertEquals("agreeing to other AI features must not agree to this one", null, context.commuteScriptGeminiConsent)
        assertNotEquals(DataStoreKeys.OracleClaimsGeminiConsent.key.name, DataStoreKeys.CommuteScriptGeminiConsent.key.name)
    }
}
