package com.mckimquyen.reader.integration

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.mckimquyen.reader.infrastructure.pref.ReadingBionicPref
import com.mckimquyen.reader.infrastructure.pref.SettingsProvider
import com.mckimquyen.reader.ui.component.reader.Reader
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The full path, with the real DataStore: the setting is persisted, [SettingsProvider] reads it back, and
 * the article reader reflects it. Nothing is faked between the switch and the rendered text.
 */
class BionicReadingPreferenceIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val html = "<p>Persistence matters for settings</p>"

    private fun setPref(on: Boolean) = runBlocking {
        composeRule.activity.dataStore.put(DataStoreKeys.ReadingBionic, on)
    }

    @Before
    fun resetToDefault() = runBlocking {
        composeRule.activity.dataStore.put(DataStoreKeys.ReadingBionic, ReadingBionicPref.default.value)
    }

    @After
    fun cleanUp() = runBlocking {
        composeRule.activity.dataStore.put(DataStoreKeys.ReadingBionic, ReadingBionicPref.default.value)
    }

    private fun showReader() {
        composeRule.setContent {
            SettingsProvider {
                LazyColumn { Reader(link = "https://example.test/", content = html, onLinkClick = {}) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bolded(): List<String> {
        val text = composeRule.onAllNodes(
            SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull() }
            .single { it.text.contains("Persistence") }
        return text.boldSegments()
    }

    private fun AnnotatedString.boldSegments() =
        spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.substring(it.start, it.end) }

    @Test
    fun freshInstall_readerIsPlain() {
        showReader()
        assertTrue(bolded().isEmpty())
    }

    @Test
    fun storedOn_isPickedUpByTheReader() {
        setPref(true)
        showReader()
        // Persistence(11)->5, matters(7)->3, for(3)->2, settings(8)->4
        composeRule.waitUntil(5_000) { bolded().isNotEmpty() }
        assertEquals(listOf("Persi", "mat", "fo", "sett"), bolded())
    }

    @Test
    fun changingTheSettingWhileReading_updatesTheLiveText() {
        showReader()
        assertTrue(bolded().isEmpty())

        setPref(true)
        composeRule.waitUntil(5_000) { bolded().isNotEmpty() }
        assertEquals(4, bolded().size)

        setPref(false)
        composeRule.waitUntil(5_000) { bolded().isEmpty() }
    }

    @Test
    fun storedChoice_isStillThereAfterTheProcessReadsDataStoreAgain() {
        setPref(true)
        val readBack = runBlocking {
            composeRule.activity.dataStore.data.first().let { ReadingBionicPref.fromPreferences(it) }
        }
        assertEquals(ReadingBionicPref.ON, readBack)

        showReader()
        composeRule.waitUntil(5_000) { bolded().isNotEmpty() }
        assertEquals(4, bolded().size)
    }
}
