package com.mckimquyen.reader.ui.golden

import android.app.Application
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.ui.page.home.feed.subs.WebFeedPreviewContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

/**
 * Golden-image checks of the preview content the subscribe dialog shows for a feed that was just found. The stored PNGs
 * have a transparent background (only the content is drawn, with a plain Material theme, not the app's own theme), so they
 * lock the layout and text only, not the real colours of the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class NostrScreensGoldenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun article(i: Int, title: String) =
        Article("a$i", Date(i.toLong()), title, rawDescription = "", shortDescription = "", link = "https://example.test/$i", feedId = "f", accountId = 1)

    private fun render(siteTitle: String, articles: List<Article>, saving: Boolean = false): View {
        var host: View? = null
        composeRule.setContent {
            host = LocalView.current
            MaterialTheme { WebFeedPreviewContent(siteTitle, articles, onConfirm = {}, onCancel = {}, saving = saving) }
        }
        composeRule.waitForIdle()
        return requireNotNull(host)
    }

    private fun check(name: String, view: View) {
        val result = Golden.compare(name, Golden.capture(view))
        assertTrue(Golden.message(name, result), result is Golden.Result.Same || result is Golden.Result.Recorded)
    }

    @Test fun previewWithThreeNotes() = check(
        "preview_three_notes",
        render("Nostr · npub1mlc…7a0a", listOf(article(1, "Hello nostr, first note"), article(2, "Older note"), article(3, "A third note"))),
    )

    @Test fun previewWhileSaving_buttonsAreDisabled() = check(
        "preview_saving",
        render("Nostr · npub1mlc…7a0a", listOf(article(1, "Hello nostr, first note")), saving = true),
    )

    @Test fun previewWithLongTitles_staysInsideTheWidth() = check(
        "preview_long_titles",
        render(
            "Nostr · npub1mlc…7a0a",
            listOf(article(1, "word ".repeat(15) + "word…"), article(2, "中文 and an emoji 😀 mixed into one title that keeps going")),
        ),
    )

    // The helper itself must notice a change: record one screen, then compare a slightly different one against it.
    @Test fun theGoldenHelperNoticesAChangedScreen() {
        val title = androidx.compose.runtime.mutableStateOf("Hello nostr, first note")
        var host: View? = null
        composeRule.setContent {
            host = LocalView.current
            MaterialTheme { WebFeedPreviewContent("Site", listOf(article(1, title.value)), onConfirm = {}, onCancel = {}) }
        }
        composeRule.waitForIdle()
        val dir = java.io.File("build/golden-selfcheck").also { it.mkdirs() }
        val reference = Golden.capture(requireNotNull(host))
        val file = java.io.File(dir, "reference.png")
        file.outputStream().use { reference.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val same = android.graphics.BitmapFactory.decodeFile(file.path)
        assertTrue("an identical screen must match its own stored image", Golden.sameImage(same, Golden.capture(requireNotNull(host))))
        composeRule.runOnIdle { title.value = "Completely different words in this title now" }
        composeRule.waitForIdle()
        assertFalse("a changed screen must NOT match", Golden.sameImage(same, Golden.capture(requireNotNull(host))))
    }
}
