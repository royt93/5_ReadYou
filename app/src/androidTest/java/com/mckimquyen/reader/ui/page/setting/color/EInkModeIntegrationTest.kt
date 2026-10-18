package com.mckimquyen.reader.ui.page.setting.color

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.DarkThemePref
import com.mckimquyen.reader.infrastructure.pref.EInkModePref
import com.mckimquyen.reader.infrastructure.pref.SettingsProvider
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real DataStore -> SettingsProvider -> AppTheme path. The test restores the two keys it touches so
 * the emulator's app state is unchanged afterwards.
 */
@RunWith(AndroidJUnit4::class)
class EInkModeIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var savedEInk: Boolean? = null
    private var savedDark: Int? = null

    @Before
    fun saveState() = runBlocking {
        val prefs = context.dataStore.data.first()
        savedEInk = prefs[DataStoreKeys.EInkMode.key]
        savedDark = prefs[DataStoreKeys.DarkTheme.key]
        context.dataStore.put(DataStoreKeys.EInkMode, false)
        context.dataStore.put(DataStoreKeys.DarkTheme, DarkThemePref.ON.value)
    }

    @After
    fun restoreState() {
        runBlocking {
            savedEInk?.let { context.dataStore.put(DataStoreKeys.EInkMode, it) }
                ?: context.dataStore.edit { it.remove(DataStoreKeys.EInkMode.key) }
            savedDark?.let { context.dataStore.put(DataStoreKeys.DarkTheme, it) }
                ?: context.dataStore.edit { it.remove(DataStoreKeys.DarkTheme.key) }
        }
    }

    @Test
    fun toggleDrivesThemeWhiteAndRestoresUserDarkThemeWithoutOverwritingIt() {
        var background by mutableStateOf(Color.Unspecified)
        var onBackground by mutableStateOf(Color.Unspecified)
        var themeSeesDark by mutableStateOf(false)
        composeRule.setContent {
            SettingsProvider {
                AppTheme(useDarkTheme = true) {
                    background = MaterialTheme.colorScheme.background
                    onBackground = MaterialTheme.colorScheme.onBackground
                    themeSeesDark = com.mckimquyen.reader.infrastructure.pref.LocalDarkTheme.current.value ==
                        DarkThemePref.ON.value
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { background != Color.Unspecified }
        val darkBackground = background
        assertNotEquals("Dark theme should not already be white", Color.White, darkBackground)

        runBlocking { context.dataStore.put(DataStoreKeys.EInkMode, EInkModePref.ON.value) }
        composeRule.waitUntil(TIMEOUT_MS) { background == Color.White }
        assertEquals(Color.Black, onBackground)
        assertTrue("E-Ink content must not see dark theme helpers", !themeSeesDark)
        assertEquals(
            "E-Ink must not overwrite the saved dark-theme preference",
            DarkThemePref.ON.value,
            runBlocking { context.dataStore.data.first()[DataStoreKeys.DarkTheme.key] },
        )

        runBlocking { context.dataStore.put(DataStoreKeys.EInkMode, EInkModePref.OFF.value) }
        composeRule.waitUntil(TIMEOUT_MS) { background == darkBackground }
        assertEquals(darkBackground, background)
    }

    @Test
    fun colorAndStyleTitleClearsStatusBarWithEInkOn() {
        runBlocking { context.dataStore.put(DataStoreKeys.EInkMode, EInkModePref.ON.value) }
        composeRule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false) }
        composeRule.setContent {
            SettingsProvider {
                AppTheme(useDarkTheme = false) {
                    ColorAndStylePage(navController = NavHostController(composeRule.activity))
                }
            }
        }
        val title = composeRule.activity.getString(R.string.color_and_style)
        // Wait until both the title is laid out and the real status-bar inset has been delivered.
        var topInset = 0
        composeRule.waitUntil(TIMEOUT_MS) {
            topInset = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
                ?.top ?: 0
            topInset > 0 && composeRule.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty()
        }
        val titleTop = composeRule.onAllNodes(hasText(title)).fetchSemanticsNodes().minOf { it.boundsInWindow.top }
        assertTrue("Title ($titleTop) must sit below status bar inset ($topInset)", titleTop >= topInset)
    }

    @Test
    fun customPaletteAccessorsAreNeutralOnRealSettingsPageAndRestoreWhenOff() {
        val colors = java.util.concurrent.atomic.AtomicReference<List<Color>>(emptyList())
        val customPalette = com.mckimquyen.reader.ui.theme.palette.TonalPalettes(20.0, 60.0)
        composeRule.setContent {
            SettingsProvider {
                AppTheme(useDarkTheme = true, wallpaperPalettes = listOf(customPalette)) {
                    val palette = com.mckimquyen.reader.ui.theme.palette.LocalTonalPalettes.current
                    val observed = listOf(
                        palette primary 40, palette secondary 50, palette tertiary 60,
                        palette neutral 90, palette neutralVariant 80, palette error 40,
                        customPalette primary 90,
                    )
                    androidx.compose.runtime.SideEffect { colors.set(observed) }
                    ColorAndStylePage(navController = NavHostController(composeRule.activity))
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { colors.get().isNotEmpty() }
        val original = colors.get()
        assertTrue(original.any { it.red != it.green || it.green != it.blue })
        runBlocking { context.dataStore.put(DataStoreKeys.EInkMode, true) }
        composeRule.waitUntil(TIMEOUT_MS) {
            colors.get().all { it.red == it.green && it.green == it.blue }
        }
        runBlocking { context.dataStore.put(DataStoreKeys.EInkMode, false) }
        composeRule.waitUntil(TIMEOUT_MS) { colors.get() == original }
    }

    @Test
    fun realSettingsRowPersistsToggleAndRestoresDarkPreference() {
        composeRule.setContent {
            SettingsProvider {
                AppTheme(useDarkTheme = true) {
                    ColorAndStylePage(navController = NavHostController(composeRule.activity))
                }
            }
        }
        val label = composeRule.activity.getString(R.string.eink_mode_title)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            runBlocking { context.dataStore.data.first()[DataStoreKeys.EInkMode.key] == true }
        }
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            runBlocking { context.dataStore.data.first()[DataStoreKeys.EInkMode.key] == false }
        }
        assertEquals(DarkThemePref.ON.value, runBlocking { context.dataStore.data.first()[DataStoreKeys.DarkTheme.key] })
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
