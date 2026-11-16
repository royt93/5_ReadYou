package com.mckimquyen.reader.ui.component

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrDeviceFixtures
import com.mckimquyen.reader.ui.theme.AppTheme
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val ICON_TAG = "front02j_icon"
private const val NAME_TAG = "front02j_name"
private const val PNG_SIDE_PX = 64
private const val PNG_QUALITY = 100
private const val RED_MIN = 0.9f
private const val OTHER_MAX = 0.1f
private const val LOAD_TIMEOUT_MS = 10_000L
private const val LONG_NAME_CHARS = 60
private const val SAMPLE_ACTOR = "alice.bsky.social"
private val ROW_WIDTH = 240.dp

/**
 * What a source's name and icon look like on the real screen. Draws the app's real FeedIcon and a Text with the same style,
 * maxLines and overflow as FeedItem, in a plain ComponentActivity (no MainActivity, no ad, no Hilt, no screen coordinates).
 * The icon comes from a fake server on 127.0.0.1 through the app's real image loader.
 *
 * NOT covered: the real FeedItem or the source list, dark mode, e-ink mode, a large font, the https-only Bluesky icon rule
 * (the fake server is plain http; that rule has JVM tests only), a real network, an icon that fails to load.
 */
@RunWith(AndroidJUnit4::class)
class FeedNameIconDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val imageRequests = AtomicInteger()
    private val redPng: ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(PNG_SIDE_PX, PNG_SIDE_PX, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(AndroidColor.RED) }
            .compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
    }.toByteArray()
    private val images = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                imageRequests.incrementAndGet()
                return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(redPng))
            }
        }
        start()
    }
    private val shown = mutableStateOf(Shown("", null, ROW_WIDTH))

    private data class Shown(val name: String, val icon: String?, val width: Dp)

    @Before
    fun drawTheProbeRow() {
        compose.setContent {
            val current = shown.value
            AppTheme(useDarkTheme = false) { SourceRow(current) }
        }
    }

    @After
    fun stopImageServer() {
        images.shutdown()
    }

    /** Same shape as FeedItem lines 74-82: an icon, then a one-line name that ends with an ellipsis when it does not fit. */
    @Composable
    private fun SourceRow(source: Shown) {
        Row(modifier = Modifier.width(source.width)) {
            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.testTag(ICON_TAG)) { FeedIcon(source.name, source.icon) }
                Text(
                    modifier = Modifier.testTag(NAME_TAG).padding(start = 12.dp, end = 6.dp),
                    text = source.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    private fun show(name: String, icon: String? = null, width: Dp = ROW_WIDTH) {
        compose.runOnIdle { shown.value = Shown(name, icon, width) }
        compose.waitForIdle()
    }

    private fun iconCenter(): Color {
        val pixels = compose.onNodeWithTag(ICON_TAG).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun isPureRed(c: Color) = c.red >= RED_MIN && c.green <= OTHER_MAX && c.blue <= OTHER_MAX

    private fun nameOverflows(): Boolean {
        val action = compose.onNodeWithTag(NAME_TAG).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("the name must expose its text layout", action)
        val layouts = mutableListOf<TextLayoutResult>()
        action?.invoke(layouts)
        return layouts.single().hasVisualOverflow
    }

    @Test
    fun aBlueskyName_isShownExactlyAsTheAppBuildsIt() {
        val name = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("Alice   A"))
        show(name)
        compose.onNodeWithTag(NAME_TAG).assertTextEquals("Bluesky · Alice A")
        assertEquals("Bluesky · Alice A", name)
    }

    @Test
    fun aNostrName_isShownExactlyAsTheAppBuildsIt() {
        val npub = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
        show(NostrFeedMapper.feedName(npub, "Probe"))
        compose.onNodeWithTag(NAME_TAG).assertTextEquals("Nostr · Probe")
    }

    @Test
    fun aLongName_isCutToOneLine_andAShortNameIsNot() {
        val longName = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("W".repeat(LONG_NAME_CHARS)))
        assertEquals(BlueskyFeedMapper.FEED_NAME_PREFIX.length + BlueskyFeedMapper.MAX_NAME_CODE_POINTS, longName.length)
        show(longName)
        compose.onNodeWithTag(NAME_TAG).assertTextEquals(longName)
        assertTrue("a name that does not fit must be cut with an ellipsis", nameOverflows())
        show(BlueskyFeedMapper.feedName(SAMPLE_ACTOR, "Al"))
        assertFalse("a short name must not be cut", nameOverflows())
    }

    @Test
    fun anIconFromTheServer_isDownloadedAndDrawn() {
        show("Bluesky · Probe", icon = null)
        assertFalse("a source without an icon must not look like the server's red picture", isPureRed(iconCenter()))
        show("Bluesky · Probe", icon = images.url("/avatar-${UUID.randomUUID()}.png").toString())
        compose.waitUntil(LOAD_TIMEOUT_MS) { isPureRed(iconCenter()) }
        assertTrue("the server must have been asked for the picture", imageRequests.get() >= 1)
    }

    @Test
    fun aSourceWithoutAnIcon_showsItsFirstLetter_andAsksTheServerForNothing() {
        show("Bluesky · Probe")
        compose.onNode(hasText("B") and hasAnyAncestor(hasTestTag(ICON_TAG))).assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(0, imageRequests.get())
    }
}
