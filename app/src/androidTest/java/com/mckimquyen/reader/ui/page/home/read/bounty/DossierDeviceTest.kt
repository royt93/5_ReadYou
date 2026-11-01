package com.mckimquyen.reader.ui.page.home.read.bounty

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.bounty.Dossier
import com.mckimquyen.reader.domain.model.bounty.DossierArticleRef
import com.mckimquyen.reader.domain.model.bounty.DossierMarkdownBuilder
import com.mckimquyen.reader.domain.model.bounty.QuotedSentence
import com.mckimquyen.reader.domain.model.bounty.RecurringPhrase
import com.mckimquyen.reader.domain.model.bounty.SourceFraming
import com.mckimquyen.reader.domain.model.bounty.TimelineEntry
import com.mckimquyen.reader.infrastructure.bounty.DossierSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Date

@RunWith(AndroidJUnit4::class)
class DossierDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val date = Date(1_700_000_000_000L)

    private fun ref(id: String, title: String, feed: String) = DossierArticleRef(id, title, feed, date)

    private fun dossier() = Dossier(
        query = "DeepSeek",
        summary = listOf(QuotedSentence("DeepSeek launches R1.", "BBC", date, "a1")),
        timeline = listOf(TimelineEntry(LocalDate.parse("2023-11-14"), listOf(ref("a1", "Timeline story", "BBC")))),
        phrases = listOf(RecurringPhrase("Sam Altman", 3)),
        framings = listOf(
            SourceFraming("BBC", listOf(ref("a1", "BBC take", "BBC"))),
            SourceFraming("Wired", listOf(ref("a2", "Wired take", "Wired"))),
        ),
        sourceCount = 2,
        generatedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun switchesAllFourTabs_onRealRenderer_andFiresCallbacks() {
        val opened = mutableListOf<String>()
        var shared = 0
        compose.setContent {
            DossierSheetContent(
                dossier = dossier(),
                onOpenArticle = { opened += it },
                onShare = { shared++ },
                onClose = {},
            )
        }

        compose.onNodeWithTag(DOSSIER_SECTION_TAG_PREFIX + "summary").assertIsDisplayed()
        listOf("timeline", "names", "sources").forEach { key ->
            compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + key).performClick()
            compose.onNodeWithTag(DOSSIER_SECTION_TAG_PREFIX + key).assertIsDisplayed()
        }
        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "timeline").performClick()
        compose.onNodeWithTag(DOSSIER_ARTICLE_TAG_PREFIX + "a1").performClick()
        compose.onNodeWithTag(DOSSIER_SHARE_TAG).performClick()

        assertEquals(listOf("a1"), opened)
        assertEquals(1, shared)
    }

    @Test
    fun sharer_writesRealFileAndResolvesFileProviderUri() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sharer = DossierSharer(context, Dispatchers.IO)
        val markdown = DossierMarkdownBuilder.build(dossier())

        val file = sharer.write(markdown, "DeepSeek")
        val chooser = sharer.buildShareIntent(file, "Share dossier")

        try {
            assertEquals(markdown, file.readText(Charsets.UTF_8))
            assertTrue(markdown.contains("## Summary") && markdown.contains("## Sources"))
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
            assertEquals("${context.packageName}.fileprovider", uri.authority)
            assertEquals(DossierSharer.MIME_MARKDOWN, send.type)
        } finally {
            file.delete()
        }
    }
}
