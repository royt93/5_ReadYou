package com.mckimquyen.reader.ui.page.home.flow

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.pref.FlowArticleListImagePref
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListImage
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Nostr note with an image link in its text, through the REAL mapper and the REAL list row, with the image loader replaced by one that
 * only records which address it was asked for (no network, no disk). It proves the list asks for the cover image exactly when the user's
 * image switch is ON; it does not prove the picture is drawn, nor that a real server answers.
 */
@RunWith(AndroidJUnit4::class)
class NostrCoverImageDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = CopyOnWriteArrayList<Any?>()
    private var appLoader: ImageLoader? = null

    @Before
    fun useARecordingImageLoader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        appLoader = Coil.imageLoader(context)
        Coil.setImageLoader(ImageLoader.Builder(context).components { add(RecordingInterceptor(asked)) }.build())
    }

    @After
    fun giveTheAppImageLoaderBack() {
        appLoader?.let { Coil.setImageLoader(it) }
    }

    @Test
    fun switchOn_aNoteWithAnHttpsImageLink_asksForThatImage() {
        showRow("look $FIRST_IMAGE", FlowArticleListImagePref.ON)
        waitForAnImageRequest()
        compose.waitForIdle()
        assertEquals(listOf(FIRST_IMAGE), asked.distinct())
    }

    @Test
    fun switchOff_aNoteWithAnHttpsImageLink_asksForNothing() {
        showRow("look $FIRST_IMAGE", FlowArticleListImagePref.OFF)
        compose.waitForIdle()
        assertTrue("asked for ${asked.distinct()}", asked.isEmpty())
    }

    @Test
    fun switchOn_aNoteWithoutALink_asksForNothing() {
        showRow("only words, no link", FlowArticleListImagePref.ON)
        compose.waitForIdle()
        assertTrue("asked for ${asked.distinct()}", asked.isEmpty())
    }

    @Test
    fun switchOn_anHttpImageLink_asksForNothing() {
        showRow("look $HTTP_IMAGE", FlowArticleListImagePref.ON)
        compose.waitForIdle()
        assertTrue("asked for ${asked.distinct()}", asked.isEmpty())
    }

    @Test
    fun switchOn_onlyTheFirstOfTwoImageLinks_isAsked() {
        showRow("one $FIRST_IMAGE two $SECOND_IMAGE", FlowArticleListImagePref.ON)
        waitForAnImageRequest()
        compose.waitForIdle()
        assertEquals(listOf(FIRST_IMAGE), asked.distinct())
    }

    private fun waitForAnImageRequest() = compose.waitUntil(WAIT_MS) { asked.isNotEmpty() }

    private fun showRow(noteText: String, switch: FlowArticleListImagePref) {
        val event = requireNotNull(
            NostrEvent.parse(
                JSONObject().apply {
                    put("id", NOTE_ID); put("pubkey", AUTHOR); put("created_at", CREATED_AT_SECONDS); put("kind", 1)
                    put("tags", JSONArray()); put("content", noteText); put("sig", "0".repeat(SIG_HEX_LENGTH))
                }.toString(),
            ),
        )
        val article = NostrFeedMapper.toArticles(listOf(event), FEED_ID, ACCOUNT_ID, NOW_MILLIS).single()
        val feed = Feed(id = FEED_ID, name = "Nostr test", url = "nostr:test", groupId = "group", accountId = ACCOUNT_ID)
        compose.setContent {
            CompositionLocalProvider(LocalFlowArticleListImage provides switch) {
                ArticleItem(ArticleWithFeed(article = article, feed = feed))
            }
        }
    }

    private class RecordingInterceptor(private val asked: MutableList<Any?>) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            asked += chain.request.data
            return SuccessResult(drawable = ColorDrawable(Color.RED), request = chain.request, dataSource = DataSource.MEMORY_CACHE)
        }
    }

    private companion object {
        const val WAIT_MS = 15_000L
        const val FIRST_IMAGE = "https://img.example.test/one.jpg"
        const val SECOND_IMAGE = "https://img.example.test/two.png"
        const val HTTP_IMAGE = "http://img.example.test/plain.jpg"
        const val FEED_ID = "nostr-feed"
        const val ACCOUNT_ID = 1
        const val AUTHOR = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
        const val NOTE_ID = "0000000000000000000000000000000000000000000000000000000000000001"
        const val CREATED_AT_SECONDS = 1_700_000_000L
        const val NOW_MILLIS = 1_800_000_000_000L
        const val SIG_HEX_LENGTH = 128
    }
}
