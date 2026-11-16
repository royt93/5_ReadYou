package com.mckimquyen.reader.ui.page.home.feed

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrDeviceFixtures
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
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

private const val PNG_SIDE_PX = 64
private const val PNG_QUALITY = 100
private const val RED_MIN = 0.9f
private const val OTHER_MAX = 0.1f
private const val LOAD_TIMEOUT_MS = 10_000L
private const val LONG_NAME_CHARS = 60
private const val NARROW_NAME_CHARS = 2
private const val SAMPLE_ACTOR = "alice.bsky.social"
private const val SAMPLE_UNREAD = 7

/** Owns the view models of the probe and hands out the real [FeedOptionViewModel], built from the running app, without Hilt. */
private class TestOwner(app: RApp) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
    }
}

/**
 * The REAL FeedItem (not a look-alike) on the real screen, in a plain ComponentActivity: no MainActivity, no ad, no Hilt, no screen
 * coordinates. FeedItem's default argument calls hiltViewModel(); with a ViewModelStoreOwner that is not a NavBackStackEntry that
 * call falls back to the owner's own default factory, which builds the real FeedOptionViewModel from the app's RssSv (read-only here).
 *
 * NOT covered: the source list, a long press and the options drawer, dark mode, e-ink, a large font, the https-only Bluesky icon rule
 * (the fake server is plain http), a real network, an icon that fails to load.
 */
@RunWith(AndroidJUnit4::class)
class FeedItemDeviceTest {
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
    private lateinit var owner: TestOwner // set in @Before before anything reads it
    private var feed by mutableStateOf(sourceFeed("", null, 0))
    private var expanded by mutableStateOf(true)
    private var clicks by mutableStateOf(0)

    private fun sourceFeed(name: String, icon: String?, important: Int?) =
        Feed(id = "front02l_probe", name = name, icon = icon, url = "bsky:$SAMPLE_ACTOR", groupId = "front02l_group", accountId = 0)
            .also { it.important = important }

    @Before
    fun drawTheRealFeedItem() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RApp
        owner = TestOwner(app)
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                AppTheme(useDarkTheme = false) {
                    FeedItem(feed = feed, isEnded = { true }, isExpanded = { expanded }, onClick = { clicks += 1 })
                }
            }
        }
    }

    /** The probe's view model collects a Flow in its own scope: clear it so nothing outlives the test. */
    @After
    fun releaseEverything() {
        owner.viewModelStore.clear()
        images.shutdown()
    }

    private fun show(name: String, icon: String? = null, important: Int? = 0) {
        compose.runOnIdle { feed = sourceFeed(name, icon, important) }
        compose.waitForIdle()
    }

    private fun nameNode(name: String) = compose.onNode(hasText(name), useUnmergedTree = true)
    private fun iconNode(name: String) = compose.onNode(hasContentDescription(name), useUnmergedTree = true)

    private fun iconCenter(name: String): Color {
        val pixels = iconNode(name).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun isPureRed(c: Color) = c.red >= RED_MIN && c.green <= OTHER_MAX && c.blue <= OTHER_MAX

    private fun nameOverflows(name: String): Boolean {
        val action = nameNode(name).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("the name must expose its text layout", action)
        val layouts = mutableListOf<TextLayoutResult>()
        action?.invoke(layouts)
        return layouts.single().hasVisualOverflow
    }

    @Test
    fun aBlueskySource_showsItsNameOnTheRealRow() {
        val name = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("Alice   A"))
        assertEquals("Bluesky · Alice A", name)
        show(name)
        nameNode(name).assertIsDisplayed()
    }

    @Test
    fun aNostrSource_showsItsNameOnTheRealRow() {
        val npub = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
        val name = NostrFeedMapper.feedName(npub, "Probe")
        assertEquals("Nostr · Probe", name)
        show(name)
        nameNode(name).assertIsDisplayed()
    }

    @Test
    fun aLongName_isCutToOneLine_andAShortNameIsNot() {
        val longName = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("W".repeat(LONG_NAME_CHARS)))
        assertEquals(BlueskyFeedMapper.FEED_NAME_PREFIX.length + BlueskyFeedMapper.MAX_NAME_CODE_POINTS, longName.length)
        show(longName)
        assertTrue("a name that does not fit must be cut with an ellipsis", nameOverflows(longName))
        val shortName = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, "A".repeat(NARROW_NAME_CHARS))
        show(shortName)
        assertFalse("a short name must not be cut", nameOverflows(shortName))
    }

    @Test
    fun anIconFromTheServer_isDownloadedAndDrawn_andNoIconAsksTheServerForNothing() {
        val name = "Bluesky · Probe"
        show(name, icon = null)
        compose.onAllNodes(hasContentDescription(name), useUnmergedTree = true).assertCountEquals(0)
        compose.waitForIdle()
        assertEquals("a source without an icon must not ask the image server", 0, imageRequests.get())
        show(name, icon = images.url("/avatar-${UUID.randomUUID()}.png").toString())
        compose.waitUntil(LOAD_TIMEOUT_MS) { isPureRed(iconCenter(name)) }
        assertTrue("the server must have been asked for the picture", imageRequests.get() >= 1)
    }

    @Test
    fun aSourceWithoutAnIcon_showsItsFirstLetter() {
        show("Bluesky · Probe", icon = null)
        compose.onNode(hasText("B"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theUnreadBadge_showsTheCount_andDisappearsAtZeroOrNull() {
        val name = "Bluesky · Probe"
        show(name, important = SAMPLE_UNREAD)
        compose.onNode(hasText(SAMPLE_UNREAD.toString()), useUnmergedTree = true).assertIsDisplayed()
        show(name, important = 0)
        compose.onAllNodes(hasText(SAMPLE_UNREAD.toString()), useUnmergedTree = true).assertCountEquals(0)
        show(name, important = null)
        compose.onAllNodes(hasText(SAMPLE_UNREAD.toString()), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aCollapsedRowIsHidden_andATapOnAnOpenRowCallsOnClickOnce() {
        val name = "Nostr · Probe"
        show(name)
        nameNode(name).performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
        compose.runOnIdle { expanded = false }
        compose.waitUntil(LOAD_TIMEOUT_MS) { compose.onAllNodes(hasText(name), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
