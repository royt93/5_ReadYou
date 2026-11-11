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
class DeepDiveConsentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverAsked_isNull_notFalse() {
        assertEquals(null, context.deepDiveGeminiConsent)
    }

    @Test
    fun agreedAndDeclined_roundTrip(): Unit = runBlocking {
        context.setDeepDiveGeminiConsent(true)
        assertEquals(true, context.deepDiveGeminiConsent)
        context.setDeepDiveGeminiConsent(false)
        assertEquals(false, context.deepDiveGeminiConsent)
    }

    @Test
    fun deepDiveConsentIsIndependentOfQuizAndPodcastStudio(): Unit = runBlocking {
        context.setQuizGeminiConsent(true)
        context.setPodcastStudioGeminiConsent(true)
        assertEquals("quiz/podcast consent must not agree for Deep Dive", null, context.deepDiveGeminiConsent)

        context.setDeepDiveGeminiConsent(false)
        assertEquals(true, context.quizGeminiConsent)
        assertEquals(true, context.podcastStudioGeminiConsent)
    }

    @Test
    fun keyNameIsDistinctFromTheOtherGeminiConsentKeys() {
        val name = DataStoreKeys.DeepDiveGeminiConsent.key.name
        assertEquals("deep_dive_gemini_consent", name)
        assertNotEquals(DataStoreKeys.QuizGeminiConsent.key.name, name)
        assertNotEquals(DataStoreKeys.PodcastStudioGeminiConsent.key.name, name)
    }
}
