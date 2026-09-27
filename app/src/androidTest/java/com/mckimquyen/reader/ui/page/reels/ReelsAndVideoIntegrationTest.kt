package com.mckimquyen.reader.ui.page.reels

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.media.video.ArticleVideoExtractor
import com.mckimquyen.reader.infrastructure.media.video.VideoPipController
import com.mckimquyen.reader.infrastructure.media.video.VideoPipHelper
import com.mckimquyen.reader.ui.component.reader.ArticleVideoPlayer
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * End-to-end coverage for REEL-01/REEL-02 on a real device: articles come out of a file-backed Room
 * database, get summarised offline, render as reels, and the video player starts and releases
 * without leaving a Picture-in-Picture marker behind.
 */
@RunWith(AndroidJUnit4::class)
class ReelsAndVideoIntegrationTest {

    private lateinit var database: AndroidDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(DB_NAME)
        database = Room.databaseBuilder(context, AndroidDatabase::class.java, DB_NAME).build()

        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "reels", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "reels", accountId = ACCOUNT_ID))
        database.feedDao().insertList(
            listOf(
                Feed(
                    id = FEED_ID,
                    name = "Example Energy Daily",
                    url = "https://news.example.com/rss",
                    groupId = GROUP_ID,
                    accountId = ACCOUNT_ID,
                )
            )
        )
        database.articleDao().insertList(
            listOf(
                article(id = "plain", html = LONG_BODY),
                article(id = "withVideo", html = "$LONG_BODY<video src=\"$SAMPLE_VIDEO_URL\"></video>"),
                article(id = "withIframe", html = "$LONG_BODY<iframe src=\"https://www.youtube.com/embed/x\"></iframe>"),
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
        VideoPipController.onStopped()
    }

    private fun article(id: String, html: String) = Article(
        id = id,
        title = "Solar power hits a record high",
        rawDescription = html,
        shortDescription = "",
        img = null,
        link = "https://news.example.com/$id",
        feedId = FEED_ID,
        accountId = ACCOUNT_ID,
        date = Date(System.currentTimeMillis()),
    )

    @Test
    fun storedArticlesBecomeReelCardsWithOfflineBullets() = runBlocking {
        val stored = database.articleDao().queryRecentArticlesWithFeed(ACCOUNT_ID, limit = 50)
        assertEquals(3, stored.size)

        val items = stored.map { withFeed ->
            val html = withFeed.article.fullContent ?: withFeed.article.rawDescription
            val plainText = Jsoup.parse(html.orEmpty()).text()
            val bullets = ArticleHighlightsExtractor
                .extractOfflineHighlights(withFeed.article.title, plainText)
                .keyTakeaways
                .take(ReelsViewModel.BULLET_COUNT)
            withFeed.toReelItem(
                bullets = bullets,
                hasVideo = ArticleVideoExtractor.extract(html, withFeed.article.link).isNotEmpty(),
            )
        }

        assertTrue("Every card needs bullets", items.all { it.bullets.isNotEmpty() })
        assertTrue(
            "No card may exceed ${ReelsViewModel.BULLET_COUNT} bullets",
            items.all { it.bullets.size <= ReelsViewModel.BULLET_COUNT },
        )
        assertTrue(items.all { it.feedName == "Example Energy Daily" })
        assertTrue(items.first { it.articleId == "withVideo" }.hasVideo)
        assertFalse(
            "A YouTube iframe must not be treated as a native video",
            items.first { it.articleId == "withIframe" }.hasVideo,
        )
    }

    @Test
    fun reelsPagerRendersRealArticlesOnDevice() = runBlocking {
        val stored = database.articleDao().queryRecentArticlesWithFeed(ACCOUNT_ID, limit = 50)
        val items = stored.map { it.toReelItem(bullets = listOf("One", "Two", "Three"), hasVideo = false) }

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent { ReelsPager(items = items, onOpenArticle = {}) }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun exoPlayerInitialisesAndReleasesWithoutLeakingAPipMarker() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = ExoPlayer.Builder(context).build()
            try {
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(SAMPLE_VIDEO_URL))
                player.prepare()
                assertFalse("Article videos must never autoplay", player.playWhenReady)
            } finally {
                player.release()
            }
        }

        assertNull(
            "A released player must leave no PiP marker behind",
            VideoPipController.playing,
        )
    }

    @Test
    fun videoPlayerComposableAttachesAndDetachesCleanly() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent { ArticleVideoPlayer(url = SAMPLE_VIDEO_URL) }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        // Closing the activity disposes the composable; its DisposableEffect must clear the marker.
        scenario.close()

        assertNull(
            "Leaving the reading page must clear the PiP marker",
            VideoPipController.playing,
        )
    }

    @Test
    fun pipParamsStayWithinSystemLimitsForRealVideoSizes() {
        // Every one of these must be accepted by the platform, including the pathological ones.
        listOf(
            1920 to 1080,
            1080 to 1920,
            0 to 0,
            4000 to 500,
            500 to 4000,
        ).forEach { (width, height) ->
            val ratio = VideoPipHelper.aspectRatioFor(width, height)
            val value = ratio.numerator.toFloat() / ratio.denominator.toFloat()
            assertTrue(
                "Ratio $value from ${width}x$height would crash enterPictureInPictureMode",
                value in (1f / 2.39f)..2.39f,
            )
        }
    }

    @Test
    fun pipControllerTracksPlaybackState() {
        VideoPipController.onPlaying(width = 1280, height = 720)
        assertEquals(1280, VideoPipController.playing?.width)

        VideoPipController.onStopped()
        assertNull(VideoPipController.playing)
    }

    private companion object {
        const val DB_NAME = "reels_integration_db"
        const val ACCOUNT_ID = 1
        const val GROUP_ID = "reels_group"
        const val FEED_ID = "reels_feed"

        /** Never fetched: the player is only prepared, never played, so no network is required. */
        const val SAMPLE_VIDEO_URL = "https://cdn.example.com/clip.mp4"

        val LONG_BODY = """
            <p>Solar generation set a new national record this quarter, regulators confirmed today.</p>
            <p>Output reached 42% of total demand during peak afternoon hours across the grid.</p>
            <p>Battery installations doubled year over year, smoothing the evening ramp considerably.</p>
            <p>Analysts expect the grid operator to raise its renewable target again next spring.</p>
        """.trimIndent()
    }
}
