package com.mckimquyen.reader.ui.page.home.read.podcast

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.podcast.ExoPodcastEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

/** Isolated runner (no ads/Hilt): real Room v18, real edge-to-edge window and a real ExoPlayer on a local WAV. */
@RunWith(AndroidJUnit4::class)
class PodcastInstrumentationTest {

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    @Test
    fun room_v18_roundTripsAudioColumns() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java).build()
        try {
            db.accountDao().insert(Account(id = 1, name = "T", type = AccountType.Local))
            db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
            db.feedDao().insert(Feed(id = "f", name = "F", url = "https://x/f", groupId = "g", accountId = 1))
            db.articleDao().insert(
                Article(
                    id = "a", date = Date(), title = "Ep", rawDescription = "", shortDescription = "", link = "https://x/e",
                    feedId = "f", accountId = 1, audioUrl = "https://x/e.mp3", audioMime = "audio/mpeg", audioDurationSec = 1913,
                )
            )
            val article = db.articleDao().queryById("a")?.article
            assertEquals("https://x/e.mp3", article?.audioUrl)
            assertEquals("audio/mpeg", article?.audioMime)
            assertEquals(1913, article?.audioDurationSec)
        } finally {
            db.close()
        }
    }

    @Test
    fun bar_clearsSystemBarsOnEdgeToEdgeWindow() {
        val state = PodcastPlaybackState(articleId = "a", status = PodcastStatus.PAUSED, durationSec = 600)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.setContentView(
                    ComposeView(activity).apply {
                        setContent {
                            Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
                                PodcastPlayerBar(state, {}, {}, {}, {}, {}, {})
                            }
                        }
                    }
                )
            }
            val deadline = System.currentTimeMillis() + 10_000
            var bar: Bounds? = null
            var toggle: Bounds? = null
            var window: Bounds? = null
            var insets: Bounds? = null
            while (bar == null && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    val i = ViewCompat.getRootWindowInsets(decor)
                        ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    val compose = findComposeView(decor)
                    if (i != null && compose != null && i.top > 0) {
                        val found = boundsByTag(compose)
                        if (found[PODCAST_BAR_TAG] != null && found[PODCAST_TOGGLE_TAG] != null) {
                            bar = found[PODCAST_BAR_TAG]
                            toggle = found[PODCAST_TOGGLE_TAG]
                            window = Bounds(0, 0, decor.width, decor.height)
                            insets = Bounds(i.left, i.top, i.right, i.bottom)
                        }
                    }
                }
                if (bar == null) Thread.sleep(50)
            }
            val b = bar ?: error("Never saw the podcast bar with real insets within 10s")
            val w = window!!
            val ins = insets!!
            assertTrue("bar must stay above the navigation bar: $b vs $w/$ins", b.bottom <= w.bottom - ins.bottom)
            assertTrue("bar must stay below the status bar", b.top >= ins.top)
            assertTrue("bar inside horizontal insets", b.left >= ins.left && b.right <= w.right - ins.right)
            val t = toggle!!
            assertTrue("toggle inside the bar", t.top >= b.top && t.bottom <= b.bottom)
        }
    }

    @Test
    fun exoEngine_playsLocalWav_advancesThenPauses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "podcast_test.wav").apply { writeBytes(silentWav(WAV_SECONDS)) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var engine: ExoPodcastEngine? = null
        var error = false
        instrumentation.runOnMainSync {
            engine = ExoPodcastEngine(context).apply { onError = { error = true } }
            engine!!.prepare(file.toUri().toString(), 0)
            engine!!.play()
        }
        try {
            waitUntil("duration known") { onMain { engine!!.durationSec() } >= WAV_SECONDS - 1 }
            waitUntil("position advances") { onMain { engine!!.positionSec() } >= 1 }
            instrumentation.runOnMainSync { engine!!.pause() }
            val pausedAt = onMain { engine!!.positionSec() }
            Thread.sleep(PAUSE_OBSERVE_MS)
            assertEquals("position must not move while paused", pausedAt, onMain { engine!!.positionSec() })
            assertTrue("engine must not report an error", !error)
        } finally {
            instrumentation.runOnMainSync { engine?.release() }
            file.delete()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MS)
        }
        error("Timed out waiting for: $what")
    }

    private fun silentWav(seconds: Int): ByteArray {
        val dataSize = SAMPLE_RATE * BYTES_PER_SAMPLE * seconds
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(WAV_HEADER_SIZE - 8 + dataSize); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * BYTES_PER_SAMPLE); putShort(BYTES_PER_SAMPLE.toShort()); putShort(16)
            put("data".toByteArray()); putInt(dataSize)
        }
        return ByteArrayOutputStream().apply { write(header.array()); write(ByteArray(dataSize)) }.toByteArray()
    }

    private fun findComposeView(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun boundsByTag(compose: ViewRootForTest): Map<String, Bounds> {
        val out = HashMap<String, Bounds>()
        val stack = ArrayDeque<SemanticsNode>().apply { add(compose.semanticsOwner.unmergedRootSemanticsNode) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.config.getOrNull(SemanticsProperties.TestTag)?.let {
                val b = node.boundsInWindow
                out[it] = Bounds(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            stack.addAll(node.children)
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 8_000
        const val BYTES_PER_SAMPLE = 2
        const val WAV_HEADER_SIZE = 44
        const val WAV_SECONDS = 20
        const val WAIT_MS = 10_000L
        const val POLL_MS = 100L
        const val PAUSE_OBSERVE_MS = 1_500L
    }
}
