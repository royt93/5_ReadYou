package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.customGeminiApiKey
import com.mckimquyen.reader.ui.ext.dataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiSummaryPersistenceTest {

    private val sampleHighlights = ArticleHighlights(
        tldr = "Jetpack Compose transforms modern Android UI development.",
        keyTakeaways = listOf(
            "Declarative UI paradigm eliminates boilerplate.",
            "Recomposition optimization improves rendering speed.",
            "Material 3 dynamic color theming is supported out of the box."
        ),
        readingTimeSavedMin = 4,
        tags = listOf("Android", "Compose", "Kotlin"),
        isOfflineFallback = false,
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        unmockkStatic(android.util.Base64::class)
    }

    @Test
    fun serialize_and_deserialize_restoresAllFieldsAccurately() {
        val serialized = ArticleHighlightsExtractor.serialize(sampleHighlights)
        assertTrue("Serialized output must be non-empty JSON", serialized.isNotBlank())
        assertTrue(serialized.contains("Jetpack Compose transforms"))

        val restored = ArticleHighlightsExtractor.deserialize(serialized)
        assertNotNull(restored)
        assertEquals(sampleHighlights.tldr, restored?.tldr)
        assertEquals(sampleHighlights.keyTakeaways, restored?.keyTakeaways)
        assertEquals(sampleHighlights.readingTimeSavedMin, restored?.readingTimeSavedMin)
        assertEquals(sampleHighlights.tags, restored?.tags)
        assertFalse(restored?.isOfflineFallback ?: true)
    }

    @Test
    fun deserialize_nullOrBlank_returnsNull() {
        assertEquals(null, ArticleHighlightsExtractor.deserialize(null))
        assertEquals(null, ArticleHighlightsExtractor.deserialize(""))
        assertEquals(null, ArticleHighlightsExtractor.deserialize("   "))
    }

    @Test
    fun deserialize_legacyPlainText_wrapsInHighlightsGracefully() {
        val rawText = "This is a legacy summary created before structured highlights format."
        val restored = ArticleHighlightsExtractor.deserialize(rawText)
        assertNotNull(restored)
        assertEquals(rawText, restored?.tldr)
        assertTrue(restored?.keyTakeaways?.isEmpty() ?: false)
    }

    @Test
    fun resolveApiKeys_withCustomByokKey_prioritizesCustomKeyFirst() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockGateway = mockk<AiRequestGateway>(relaxed = true)
        val mockOkHttp = mockk<OkHttpClient>(relaxed = true)

        mockkStatic("com.mckimquyen.reader.ui.ext.ExtDataStoreKt")
        every { mockContext.customGeminiApiKey } returns "CUSTOM_USER_API_KEY_123"

        val service = GeminiSummaryService(mockContext, mockOkHttp, mockGateway)
        val resolvedKeys = service.resolveApiKeys()

        assertTrue("Resolved keys must not be empty", resolvedKeys.isNotEmpty())
        assertEquals("CUSTOM_USER_API_KEY_123", resolvedKeys.first())

        unmockkStatic("com.mckimquyen.reader.ui.ext.ExtDataStoreKt")
    }

    @Test
    fun resolveApiKeys_withoutCustomByokKey_usesDefaultSystemKeys() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockGateway = mockk<AiRequestGateway>(relaxed = true)
        val mockOkHttp = mockk<OkHttpClient>(relaxed = true)

        mockkStatic("com.mckimquyen.reader.ui.ext.ExtDataStoreKt")
        every { mockContext.customGeminiApiKey } returns ""

        val service = GeminiSummaryService(mockContext, mockOkHttp, mockGateway)
        val resolvedKeys = service.resolveApiKeys()

        assertTrue("Resolved keys must contain default keys", resolvedKeys.isNotEmpty())
        assertFalse(resolvedKeys.contains(""))

        unmockkStatic("com.mckimquyen.reader.ui.ext.ExtDataStoreKt")
    }
}
