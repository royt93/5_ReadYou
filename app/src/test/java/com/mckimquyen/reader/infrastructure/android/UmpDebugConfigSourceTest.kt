package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The UMP debug geography forces the consent form for a phone outside the EEA. It is a testing aid and must never be on in a release build:
 * with it on, real users would see a form for the wrong region. The SDK already ignores it unless `isDebug`, but the app must not rely on that
 * alone, so the gradle file and RApp are checked as text (a release build cannot be run in a JVM test).
 */
class UmpDebugConfigSourceTest {

    private fun text(vararg candidates: String) = candidates.map(::File).first { it.exists() }.readText()
    private val gradle get() = text("build.gradle", "app/build.gradle")
    private val rapp get() = text("src/main/java/com/mckimquyen/reader/RApp.kt", "app/src/main/java/com/mckimquyen/reader/RApp.kt")

    private fun block(source: String, name: String): String {
        val start = source.indexOf("        $name {", source.indexOf("buildTypes {"))
        assertTrue("$name block not found", start > 0)
        return source.substring(start, source.indexOf("\n        }", start))
    }

    @Test
    fun debugBuild_takesTheUmpDebugFieldsFromGradleProperties() {
        val debug = block(gradle, "debug")
        assertTrue(debug.contains("UMP_DEBUG_GEOGRAPHY"))
        assertTrue(debug.contains("UMP_TEST_DEVICE_HASHED_IDS"))
        assertTrue(debug.contains("providers.gradleProperty(\"umpDebugGeography\")"))
    }

    @Test
    fun releaseBuild_hardCodesTheUmpDebugFieldsToEmpty() {
        val release = block(gradle, "release")
        assertTrue(release.contains("\"UMP_DEBUG_GEOGRAPHY\", \"\\\"\\\"\""))
        assertTrue(release.contains("\"UMP_TEST_DEVICE_HASHED_IDS\", \"\\\"\\\"\""))
        assertFalse("release must not read a gradle property for the UMP debug fields", release.contains("gradleProperty"))
    }

    @Test
    fun rApp_passesTheFieldsThroughAndOnlyWhenDebug() {
        val text = rapp
        assertTrue(text.contains("umpDebugGeography"))
        assertTrue(text.contains("umpTestDeviceHashedIds"))
        val line = text.lines().first { it.contains("umpDebugGeography") }
        assertTrue("the geography must be gated on BuildConfig.DEBUG: $line", line.contains("BuildConfig.DEBUG"))
        val ids = text.lines().first { it.contains("umpTestDeviceHashedIds") }
        assertTrue("the hashed ids must be gated on BuildConfig.DEBUG: $ids", ids.contains("BuildConfig.DEBUG"))
    }

    @Test
    fun parse_dropsBlankEntriesAndKeepsOrder() {
        assertEquals(listOf("A1", "B2"), AdUmpDebug.parseHashedIds(" A1, ,B2,, "))
        assertEquals(emptyList<String>(), AdUmpDebug.parseHashedIds(""))
    }

    @Test
    fun geography_isNullForBlankAndKnownValuesAreKept() {
        assertEquals(null, AdUmpDebug.geographyOrNull(""))
        assertEquals(null, AdUmpDebug.geographyOrNull("   "))
        assertEquals("EEA", AdUmpDebug.geographyOrNull(" eea "))
        assertEquals("US_STATE", AdUmpDebug.geographyOrNull("US_STATE"))
        assertEquals("NOT_EEA", AdUmpDebug.geographyOrNull("not_eea"))
        assertEquals("an unknown value must not reach the SDK", null, AdUmpDebug.geographyOrNull("MARS"))
    }
}
