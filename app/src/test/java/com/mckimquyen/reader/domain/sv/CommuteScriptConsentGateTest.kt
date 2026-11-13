package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteScriptConsentGateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun article(i: Int) = Article(
        id = "a$i", date = Date(), title = "Title $i", rawDescription = "Raw $i",
        shortDescription = "Short $i", link = "https://example.invalid/$i", feedId = "f", accountId = 1,
    )

    private val articles = (1..3).map(::article)

    @Test fun withoutConsent_noRequestLeavesTheDevice() {
        val http = mockk<OkHttpClient>()
        val service = CommuteScriptService(context, http)
        val episode = runBlocking { service.generateScript(articles, allowGemini = false) }
        verify(exactly = 0) { http.newCall(any()) }
        assertTrue("the on-device script must still be a real episode", episode.dialogues.isNotEmpty())
    }

    @Test fun theDefault_isNoConsent() {
        val http = mockk<OkHttpClient>()
        val service = CommuteScriptService(context, http)
        runBlocking { service.generateScript(articles) }
        verify(exactly = 0) { http.newCall(any()) }
    }

    @Test fun withoutConsent_theEpisodeIsTheOnDeviceOne_withTheArticleIds() {
        val service = CommuteScriptService(context, mockk<OkHttpClient>())
        val episode = runBlocking { service.generateScript(articles, allowGemini = false, languageTag = "en-US") }
        assertEquals(listOf("a1", "a2", "a3"), episode.articleIds)
        assertEquals("CommuteCast Morning Briefing", episode.title)
    }

    @Test fun withConsent_theNetworkIsTried_andAFailureStillFallsBackToTheOnDeviceScript() {
        val http = mockk<OkHttpClient>()
        io.mockk.every { http.newCall(any()) } throws java.io.IOException("offline")
        val service = CommuteScriptService(context, http)
        val episode = runBlocking { service.generateScript(articles, allowGemini = true, languageTag = "en-US") }
        assertTrue("with consent the request is attempted", runCatching { verify(atLeast = 1) { http.newCall(any()) } }.isSuccess)
        assertEquals("CommuteCast Morning Briefing", episode.title)
    }
}
