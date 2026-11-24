package com.mckimquyen.reader.ui.page.about

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * On the Pixel the Home page measured #FFFBFF and Settings #FDEDEB (both from the app's dynamic palette) while About measured #FFFBFE with a #FAFAFA web view:
 * AboutActivity called MaterialTheme without AppTheme, so it ignored the palette, dark mode and E-Ink setting every other page follows.
 */
class AboutActivityThemeTest {
    private val source: String = listOf(
        "src/main/java/com/mckimquyen/reader/ui/page/about/AboutActivity.kt",
        "app/src/main/java/com/mckimquyen/reader/ui/page/about/AboutActivity.kt",
    ).map(::File).first { it.exists() }.readText()

    private val content: String = source.substringAfter("setContent {").substringBefore("\n@Composable")

    @Test
    fun aboutContentIsWrappedInTheAppTheme() {
        assertTrue("AboutActivity must call AppTheme(useDarkTheme = ...)", Regex("""AppTheme\(\s*useDarkTheme""").containsMatchIn(content))
    }

    @Test
    fun aboutReadsTheSavedSettingsBeforeThemingSoDarkModeAndPaletteApply() {
        val settings = content.indexOf("SettingsProvider")
        val theme = content.indexOf("AppTheme(")
        assertTrue("SettingsProvider must exist and wrap AppTheme", settings in 0 until theme)
    }

    @Test
    fun aboutDarkChoiceComesFromTheSavedPreference() {
        assertTrue("dark mode must come from LocalDarkTheme, not a hard-coded value", "LocalDarkTheme.current.isDarkTheme()" in content)
    }
}
