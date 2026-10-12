package com.mckimquyen.reader.ui.component.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.mckimquyen.reader.domain.model.bilingual.CachingParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.ParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.TranslationResult
import com.mckimquyen.reader.infrastructure.pref.LocalReadingBilingual
import com.mckimquyen.reader.infrastructure.pref.LocalReadingBilingualTarget
import com.mckimquyen.reader.infrastructure.pref.ReadingBilingualPref
import com.mckimquyen.reader.infrastructure.pref.ReadingBilingualTargetPref
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Real [Reader] + a fake translator: no model download, no network, deterministic. */
class BilingualReadingWidgetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class Recording(val answer: suspend (String, String) -> TranslationResult) : ParagraphTranslator {
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        override suspend fun translate(text: String, target: String): TranslationResult {
            requests += text to target
            return answer(text, target)
        }
    }

    private fun upper() = Recording { text, _ -> TranslationResult.Translated("TR:" + text.uppercase()) }

    private fun show(
        translator: ParagraphTranslator,
        bilingual: ReadingBilingualPref = ReadingBilingualPref.ON,
        target: ReadingBilingualTargetPref = ReadingBilingualTargetPref("vi"),
        html: String = "<p>The actor portrays the host.</p>",
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalReadingBilingual provides bilingual,
                LocalReadingBilingualTarget provides target,
                LocalParagraphTranslator provides translator,
            ) {
                // The anchor keeps the compose hierarchy alive even when the Reader emits nothing.
                Column {
                    Text("anchor", modifier = Modifier.testTag("anchor"))
                    LazyColumn { Reader(link = "https://example.test/", content = html, onLinkClick = {}) }
                }
            }
        }
        composeRule.onNodeWithTag("anchor").assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun translations(): List<String> = composeRule.onAllNodes(
        SemanticsMatcher("translation") { it.config.getOrNull(SemanticsProperties.TestTag) == BILINGUAL_TRANSLATION_TAG },
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }

    @Test
    fun off_neverCallsTheTranslator_andShowsNoTranslation() {
        val fake = upper()
        show(fake, bilingual = ReadingBilingualPref.OFF)
        assertTrue(translations().isEmpty())
        assertTrue("OFF must not translate anything", fake.requests.isEmpty())
    }

    @Test
    fun on_showsTheTranslationUnderTheParagraph_andKeepsTheOriginal() {
        val fake = upper()
        show(fake)
        composeRule.waitUntil(5_000) { translations().isNotEmpty() }

        assertEquals(listOf("TR:THE ACTOR PORTRAYS THE HOST."), translations())
        assertEquals("vi", fake.requests.single().second)
        composeRule.onNode(
            SemanticsMatcher("original") {
                it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "The actor portrays the host." } == true
            },
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun explicitTarget_isPassedToTheTranslator() {
        val fake = upper()
        show(fake, target = ReadingBilingualTargetPref("de"))
        composeRule.waitUntil(5_000) { fake.requests.isNotEmpty() }
        assertEquals("de", fake.requests.single().second)
    }

    @Test
    fun sameLanguage_showsNoTranslation() {
        val fake = Recording { _, _ -> TranslationResult.SameLanguage }
        show(fake)
        composeRule.waitUntil(5_000) { fake.requests.isNotEmpty() }
        composeRule.waitForIdle()
        assertTrue(translations().isEmpty())
    }

    @Test
    fun failure_showsNothing_andDoesNotCrash() {
        val fake = Recording { _, _ -> TranslationResult.Failed }
        show(fake)
        composeRule.waitUntil(5_000) { fake.requests.isNotEmpty() }
        composeRule.waitForIdle()
        assertTrue(translations().isEmpty())
    }

    @Test
    fun aTranslatorThatThrows_behindTheCachingLayer_leavesTheOriginalReadable() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val throwing = ParagraphTranslator { _, _ ->
            calls.incrementAndGet()
            error("engine exploded")
        }
        show(CachingParagraphTranslator(throwing), html = "<p>Only the original is here.</p>")
        composeRule.waitUntil(5_000) { calls.get() > 0 }
        composeRule.waitForIdle()

        assertTrue(translations().isEmpty())
        composeRule.onNode(
            SemanticsMatcher("original") {
                it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.contains("Only the original") } == true
            },
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun shortAndNumericParagraphs_areNeverSentToTheTranslator() {
        val fake = upper()
        show(fake, html = "<p>42</p><p>OK</p><p>A real sentence worth translating.</p>")
        composeRule.waitUntil(5_000) { fake.requests.isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals(listOf("A real sentence worth translating."), fake.requests.map { it.first })
    }

    @Test
    fun toggling_off_removesTheTranslation_andOn_bringsItBack() {
        val fake = upper()
        var bilingual by mutableStateOf<ReadingBilingualPref>(ReadingBilingualPref.ON)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalReadingBilingual provides bilingual,
                LocalReadingBilingualTarget provides ReadingBilingualTargetPref("vi"),
                LocalParagraphTranslator provides fake,
            ) {
                Column {
                    Text("anchor", modifier = Modifier.testTag("anchor"))
                    LazyColumn {
                        Reader(link = "https://example.test/", content = "<p>Switch me on and off.</p>", onLinkClick = {})
                    }
                }
            }
        }
        composeRule.onNodeWithTag("anchor").assertIsDisplayed()
        composeRule.waitUntil(5_000) { translations().isNotEmpty() }

        bilingual = ReadingBilingualPref.OFF
        composeRule.waitUntil(5_000) { translations().isEmpty() }

        bilingual = ReadingBilingualPref.ON
        composeRule.waitUntil(5_000) { translations().isNotEmpty() }
    }

    @Test
    fun aSlowTranslation_appearsOnlyWhenItFinishes_withoutBlockingTheOriginal() {
        val gate = CompletableDeferred<Unit>()
        val fake = Recording { text, _ ->
            gate.await()
            TranslationResult.Translated("late:$text")
        }
        show(fake)
        composeRule.waitUntil(5_000) { fake.requests.isNotEmpty() }
        assertTrue("original is readable while the model is still working", translations().isEmpty())
        composeRule.onNode(
            SemanticsMatcher("original") {
                it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.contains("actor portrays") } == true
            },
            useUnmergedTree = true,
        ).assertIsDisplayed()

        gate.complete(Unit)
        composeRule.waitUntil(5_000) { translations().isNotEmpty() }
    }

    @Test
    fun changingTheTargetLanguage_dropsTheOldTranslation_andShowsTheNewOne() {
        val fake = Recording { text, target -> TranslationResult.Translated("$target:$text") }
        var target by mutableStateOf(ReadingBilingualTargetPref("fr"))
        composeRule.setContent {
            CompositionLocalProvider(
                LocalReadingBilingual provides ReadingBilingualPref.ON,
                LocalReadingBilingualTarget provides target,
                LocalParagraphTranslator provides fake,
            ) {
                Column {
                    Text("anchor", modifier = Modifier.testTag("anchor"))
                    LazyColumn {
                        Reader(link = "https://example.test/", content = "<p>Retarget this paragraph.</p>", onLinkClick = {})
                    }
                }
            }
        }
        composeRule.onNodeWithTag("anchor").assertIsDisplayed()
        composeRule.waitUntil(5_000) { translations() == listOf("fr:Retarget this paragraph.") }

        target = ReadingBilingualTargetPref("de")
        composeRule.waitUntil(5_000) { translations() == listOf("de:Retarget this paragraph.") }
        assertEquals(listOf("fr", "de"), fake.requests.map { it.second })
    }

    @Test
    fun aSlowRequestForTheOldTarget_neverOverwritesTheNewTarget() {
        val gateFr = CompletableDeferred<Unit>()
        val fake = Recording { text, target ->
            if (target == "fr") gateFr.await()
            TranslationResult.Translated("$target:$text")
        }
        var target by mutableStateOf(ReadingBilingualTargetPref("fr"))
        composeRule.setContent {
            CompositionLocalProvider(
                LocalReadingBilingual provides ReadingBilingualPref.ON,
                LocalReadingBilingualTarget provides target,
                LocalParagraphTranslator provides fake,
            ) {
                Column {
                    Text("anchor", modifier = Modifier.testTag("anchor"))
                    LazyColumn {
                        Reader(link = "https://example.test/", content = "<p>Race between two languages.</p>", onLinkClick = {})
                    }
                }
            }
        }
        composeRule.onNodeWithTag("anchor").assertIsDisplayed()
        composeRule.waitUntil(5_000) { fake.requests.isNotEmpty() }

        target = ReadingBilingualTargetPref("de")
        composeRule.waitUntil(5_000) { translations() == listOf("de:Race between two languages.") }
        gateFr.complete(Unit) // the stale French answer arrives late
        composeRule.waitForIdle()
        assertEquals(listOf("de:Race between two languages."), translations())
    }
}
