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
class QuizConsentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverAsked_isNull_notFalse() {
        assertEquals(null, context.quizGeminiConsent)
    }

    @Test
    fun agreedAndDeclined_roundTrip(): Unit = runBlocking {
        context.setQuizGeminiConsent(true)
        assertEquals(true, context.quizGeminiConsent)

        context.setQuizGeminiConsent(false)
        assertEquals(false, context.quizGeminiConsent)
    }

    @Test
    fun quizConsentIsIndependentOfThePodcastStudioConsent(): Unit = runBlocking {
        context.setPodcastStudioGeminiConsent(true)
        assertEquals("agreeing for Podcast Studio must not agree for the quiz", null, context.quizGeminiConsent)

        context.setQuizGeminiConsent(false)
        assertEquals("declining the quiz must not touch Podcast Studio", true, context.podcastStudioGeminiConsent)
    }

    @Test
    fun keyNameIsDistinctFromThePodcastStudioKey() {
        assertNotEquals(DataStoreKeys.PodcastStudioGeminiConsent.key.name, DataStoreKeys.QuizGeminiConsent.key.name)
        assertEquals("quiz_gemini_consent", DataStoreKeys.QuizGeminiConsent.key.name)
    }
}
