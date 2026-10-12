package com.mckimquyen.reader.integration

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mckimquyen.reader.domain.model.bilingual.BilingualLanguages
import com.mckimquyen.reader.domain.model.bilingual.CachingParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.ParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.TranslationResult
import com.mckimquyen.reader.infrastructure.pref.ReadingBilingualPref
import com.mckimquyen.reader.infrastructure.pref.ReadingBilingualTargetPref
import com.mckimquyen.reader.infrastructure.pref.SettingsProvider
import com.mckimquyen.reader.ui.component.reader.BILINGUAL_TRANSLATION_TAG
import com.mckimquyen.reader.ui.component.reader.LocalParagraphTranslator
import com.mckimquyen.reader.ui.component.reader.Reader
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real DataStore + real [SettingsProvider] + real [Reader] + the real caching layer. Only the ML Kit engine
 * is faked, so the switch, the chosen language and the rendered translation are verified end to end.
 */
class BilingualReadingPreferenceIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    private val engine = ParagraphTranslator { text, target ->
        requests += text to target
        TranslationResult.Translated("[$target] $text")
    }

    private fun store(on: Boolean, target: String = BilingualLanguages.AUTO) = runBlocking {
        composeRule.activity.dataStore.put(DataStoreKeys.ReadingBilingual, on)
        composeRule.activity.dataStore.put(DataStoreKeys.ReadingBilingualTarget, target)
    }

    @Before
    fun reset() = store(ReadingBilingualPref.default.value, ReadingBilingualTargetPref.default.value)

    @After
    fun cleanUp() = store(ReadingBilingualPref.default.value, ReadingBilingualTargetPref.default.value)

    private fun showReader() {
        composeRule.setContent {
            SettingsProvider {
                CompositionLocalProvider(LocalParagraphTranslator provides CachingParagraphTranslator(engine)) {
                    LazyColumn {
                        Reader(link = "https://example.test/", content = "<p>Persistence matters for settings</p>", onLinkClick = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun translations(): List<String> = composeRule.onAllNodes(
        SemanticsMatcher("translation") { it.config.getOrNull(SemanticsProperties.TestTag) == BILINGUAL_TRANSLATION_TAG },
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }

    @Test
    fun freshInstall_translatesNothing() {
        showReader()
        composeRule.waitForIdle()
        assertTrue(translations().isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun storedOn_withAnExplicitLanguage_translatesIntoIt() {
        store(true, "fr")
        showReader()
        composeRule.waitUntil(5_000) { translations().isNotEmpty() }
        assertEquals(listOf("[fr] Persistence matters for settings"), translations())
    }

    @Test
    fun storedOn_withAuto_followsTheAppLocale_orStaysSilentIfUnsupported() {
        store(true, BilingualLanguages.AUTO)
        showReader()
        val expected = BilingualLanguages.fromLocale(composeRule.activity.resources.configuration.locales[0])
        if (expected == null) {
            composeRule.waitForIdle()
            assertTrue("an unsupported app locale must not guess a language", requests.isEmpty())
        } else {
            composeRule.waitUntil(5_000) { requests.isNotEmpty() }
            assertEquals(expected, requests.single().second)
        }
    }

    @Test
    fun changingTheSettingWhileReading_updatesTheLiveText() {
        store(false, "de")
        showReader()
        assertTrue(translations().isEmpty())

        store(true, "de")
        composeRule.waitUntil(5_000) { translations().isNotEmpty() }
        assertEquals(listOf("[de] Persistence matters for settings"), translations())

        store(false, "de")
        composeRule.waitUntil(5_000) { translations().isEmpty() }
    }

    @Test
    fun switchingTheTargetLanguage_translatesAgainAndDoesNotReuseTheOldLanguage() {
        store(true, "fr")
        showReader()
        composeRule.waitUntil(5_000) { translations().isNotEmpty() }

        store(true, "de")
        composeRule.waitUntil(5_000) { translations() == listOf("[de] Persistence matters for settings") }
        assertEquals(listOf("fr", "de"), requests.map { it.second })
    }
}
