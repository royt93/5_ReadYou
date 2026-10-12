package com.mckimquyen.reader.ui.component.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.mckimquyen.reader.infrastructure.pref.LocalReadingBionic
import com.mckimquyen.reader.infrastructure.pref.ReadingBionicPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Renders real article HTML through [Reader] and inspects the AnnotatedString that actually reaches the
 * screen, instead of just checking that something composed.
 */
class BionicReadingWidgetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(bionic: ReadingBionicPref, html: String, onLink: (String) -> Unit = {}) {
        composeRule.setContent {
            CompositionLocalProvider(LocalReadingBionic provides bionic) {
                // A fixed anchor keeps the compose hierarchy alive even when the Reader emits no items
                // (empty content), so "renders nothing" is observable instead of "No compose hierarchies".
                Column {
                    Text("anchor", modifier = Modifier.testTag("anchor"))
                    LazyColumn {
                        Reader(link = "https://example.test/", content = html, onLinkClick = onLink)
                    }
                }
            }
        }
        composeRule.onNodeWithTag("anchor").assertIsDisplayed()
        composeRule.waitForIdle()
    }

    /** Every text node's AnnotatedString, in tree order. */
    private fun renderedTexts(): List<AnnotatedString> = composeRule.onAllNodes(
        SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null },
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull() }
        .filter { it.text != "anchor" }

    private fun AnnotatedString.bold() =
        spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.substring(it.start, it.end) }

    @Test
    fun off_rendersPlainText_withNoBionicBold() {
        show(ReadingBionicPref.OFF, "<p>Reading makes everything simpler</p>")

        val text = renderedTexts().single { it.text.contains("Reading") }
        assertTrue("OFF must not add any bold", text.bold().isEmpty())
    }

    @Test
    fun on_boldsTheLeadingLettersOfEachWord() {
        show(ReadingBionicPref.ON, "<p>Reading makes everything simpler</p>")

        val text = renderedTexts().single { it.text.contains("Reading") }
        // Reading(7)->3, makes(5)->2, everything(10)->4, simpler(7)->3
        assertEquals(listOf("Rea", "ma", "ever", "sim"), text.bold())
    }

    @Test
    fun toggling_changesOnlyTheStyling_neverTheVisibleText() {
        val html = "<p>Same words, different weight.</p>"
        var bionic by mutableStateOf<ReadingBionicPref>(ReadingBionicPref.OFF)
        composeRule.setContent {
            CompositionLocalProvider(LocalReadingBionic provides bionic) {
                Column {
                    Text("anchor", modifier = Modifier.testTag("anchor"))
                    LazyColumn { Reader(link = "https://example.test/", content = html, onLinkClick = {}) }
                }
            }
        }
        composeRule.onNodeWithTag("anchor").assertIsDisplayed()
        composeRule.waitForIdle()
        val off = renderedTexts().single { it.text.contains("Same") }

        bionic = ReadingBionicPref.ON
        composeRule.waitForIdle()
        val on = renderedTexts().single { it.text.contains("Same") }

        assertTrue(off.bold().isEmpty())
        assertTrue(on.bold().isNotEmpty())
        assertEquals(off.text, on.text)
    }

    @Test
    fun on_keepsTheLinkAnnotationOnExactlyTheLinkText() {
        show(ReadingBionicPref.ON, "<p>See <a href=\"https://example.test/post\">the full story</a> here</p>")

        val text = renderedTexts().single { it.text.contains("full story") }
        val link = text.getStringAnnotations("URL", 0, text.length).single()
        assertEquals("https://example.test/post", link.item)
        assertEquals("the full story", text.text.substring(link.start, link.end))
    }

    @Test
    fun on_tappingTheLinkStillReportsItsUrl() {
        var clicked: String? = null
        show(ReadingBionicPref.ON, "<p>See <a href=\"https://example.test/post\">the full story</a> here</p>", onLink = { clicked = it })

        // Tap in the middle of the link word "story"; the click handler resolves the offset to the URL annotation.
        composeRule.onNode(
            SemanticsMatcher("link paragraph") { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.contains("full story") } == true },
            useUnmergedTree = true,
        ).performTouchInput { click(Offset(width * 0.45f, height / 2f)) }
        composeRule.waitForIdle()

        assertEquals("https://example.test/post", clicked)
    }

    @Test
    fun on_neverTouchesCodeBlocks() {
        show(ReadingBionicPref.ON, "<p>Intro text</p><pre><code>fun main() { println(1) }</code></pre>")

        val code = renderedTexts().single { it.text.contains("fun main") }
        assertTrue("code must stay exactly as written", code.bold().isEmpty())
        val prose = renderedTexts().single { it.text.contains("Intro") }
        assertTrue(prose.bold().isNotEmpty())
    }

    @Test
    fun on_handlesEmptyContentWithoutCrashing() {
        show(ReadingBionicPref.ON, "")
        assertTrue(renderedTexts().isEmpty())
    }

    @Test
    fun on_handlesWhitespaceOnlyContentWithoutCrashing() {
        show(ReadingBionicPref.ON, "<p>   </p>")
        assertTrue(renderedTexts().none { it.bold().isNotEmpty() })
    }
}
