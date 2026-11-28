package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * With an in-app language chosen, the three activities used to force fontScale = 1.0, so a user who raised the system font size for eyesight got small text.
 * The locale wrapper must keep the system font scale (the config is copied from the base context) while still applying the chosen locale.
 */
class FontScaleNotPinnedSourceTest {
    private val root: File = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        .resolve("com/mckimquyen/reader")

    private val activities = mapOf(
        "MainActivity" to "infrastructure/android/MainActivity.kt",
        "SplashActivity" to "infrastructure/android/SplashActivity.kt",
        "AboutActivity" to "ui/page/about/AboutActivity.kt",
    )

    private fun wrapper(path: String): String =
        root.resolve(path).readText().substringAfter("override fun attachBaseContext").substringBefore("override fun onCreate")

    @Test fun noActivityPinsTheFontScale() {
        activities.forEach { (name, path) ->
            assertTrue("$name must not touch fontScale", "fontScale" !in root.resolve(path).readText())
        }
    }

    @Test fun theLocaleWrapperStillCopiesTheBaseConfigAndSetsTheLocale() {
        activities.forEach { (name, path) ->
            val w = wrapper(path)
            assertTrue("$name copies the base configuration", "Configuration(newBase.resources.configuration)" in w)
            assertTrue("$name sets the chosen locale", "configuration.setLocale(locale)" in w)
            assertTrue("$name keeps the device-language early return", "languagePref == 0" in w)
        }
        assertEquals(3, activities.size)
    }
}
