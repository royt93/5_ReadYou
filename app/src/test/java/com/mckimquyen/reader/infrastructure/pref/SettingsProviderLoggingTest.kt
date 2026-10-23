package com.mckimquyen.reader.infrastructure.pref

import android.app.Application
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsProviderLoggingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sentinels = listOf(
        "FAKE_GEMINI_KEY_SENTINEL_ALPHA",
        "FAKE_OPENAI_KEY_SENTINEL_BETA",
        "FAKE_DEEPSEEK_KEY_SENTINEL_GAMMA",
        "FAKE_GROQ_KEY_SENTINEL_DELTA",
    )

    @Before
    fun setUp() {
        ShadowLog.clear()
        context.dataStore.putBlocking(DataStoreKeys.CustomGeminiApiKey, sentinels[0])
        context.dataStore.putBlocking(DataStoreKeys.CustomOpenAiApiKey, sentinels[1])
        context.dataStore.putBlocking(DataStoreKeys.CustomDeepSeekApiKey, sentinels[2])
        context.dataStore.putBlocking(DataStoreKeys.CustomGroqApiKey, sentinels[3])
        context.dataStore.putBlocking(DataStoreKeys.ThemeIndex, TEST_THEME_INDEX)
    }

    @After
    fun tearDown() {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs.remove(DataStoreKeys.CustomGeminiApiKey.key)
                prefs.remove(DataStoreKeys.CustomOpenAiApiKey.key)
                prefs.remove(DataStoreKeys.CustomDeepSeekApiKey.key)
                prefs.remove(DataStoreKeys.CustomGroqApiKey.key)
                prefs.remove(DataStoreKeys.ThemeIndex.key)
            }
        }
        ShadowLog.clear()
    }

    @Test
    fun settingsProvider_providesUpdatedThemeWithoutLoggingSecretsOrPreferencesDump() {
        var observedThemeIndex = -1

        composeRule.setContent {
            SettingsProvider {
                observedThemeIndex = LocalThemeIndex.current
                Text(text = "theme=$observedThemeIndex")
            }
        }

        // SettingsProvider thu thập flow trên Dispatchers.Default, giá trị ban đầu là mặc định
        // (ThemeIndexPref.default). Chờ tới khi giá trị DataStore thật đi qua provider; lúc đó
        // lambda map (nơi từng có Log.i) chắc chắn đã chạy cho chính emission này.
        composeRule.waitUntil(timeoutMillis = SETTINGS_FLOW_TIMEOUT_MS) {
            observedThemeIndex == TEST_THEME_INDEX
        }
        assertEquals(TEST_THEME_INDEX, observedThemeIndex)

        val rlogs = ShadowLog.getLogs()
            .filter { it.tag == "RLog" }
            .map { it.msg }

        for (logMsg in rlogs) {
            assertFalse("Log không được chứa tiền tố AppTheme: dump", logMsg.contains("AppTheme:"))
            for (sentinel in sentinels) {
                assertFalse("Log không được chứa secret sentinel", logMsg.contains(sentinel))
            }
        }
    }

    private companion object {
        const val TEST_THEME_INDEX = 7
        const val SETTINGS_FLOW_TIMEOUT_MS = 5000L
    }
}
