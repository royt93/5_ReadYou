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
class OracleClaimsConsentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverAsked_isNull_notFalse() {
        assertEquals(null, context.oracleClaimsGeminiConsent)
    }

    @Test
    fun agreedAndDeclined_roundTrip(): Unit = runBlocking {
        context.setOracleClaimsGeminiConsent(true)
        assertEquals(true, context.oracleClaimsGeminiConsent)
        context.setOracleClaimsGeminiConsent(false)
        assertEquals(false, context.oracleClaimsGeminiConsent)
    }

    @Test
    fun isIndependentOfTheOtherGeminiConsents(): Unit = runBlocking {
        context.setQuizGeminiConsent(true)
        context.setPodcastStudioGeminiConsent(true)
        context.setDeepDiveGeminiConsent(true)
        assertEquals("other consents must not agree for Oracle", null, context.oracleClaimsGeminiConsent)

        context.setOracleClaimsGeminiConsent(false)
        assertEquals(true, context.quizGeminiConsent)
        assertEquals(true, context.podcastStudioGeminiConsent)
        assertEquals(true, context.deepDiveGeminiConsent)
    }

    @Test
    fun keyNameIsHardCodedAndDistinct() {
        val name = DataStoreKeys.OracleClaimsGeminiConsent.key.name
        assertEquals("oracle_claims_gemini_consent", name)
        assertNotEquals(DataStoreKeys.QuizGeminiConsent.key.name, name)
        assertNotEquals(DataStoreKeys.PodcastStudioGeminiConsent.key.name, name)
        assertNotEquals(DataStoreKeys.DeepDiveGeminiConsent.key.name, name)
    }
}
