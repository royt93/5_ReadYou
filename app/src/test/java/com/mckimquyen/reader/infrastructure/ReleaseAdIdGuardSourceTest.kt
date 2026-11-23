package com.mckimquyen.reader.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The release build must stop when an ad id is one of Google's sample ids (zero revenue, and Log.e is stripped by R8 so nothing would warn at run time).
 * The behavior itself was proven with three dry runs of Gradle (sample ids stop, the explicit flag lets a device test through, real ids pass);
 * this test only keeps the wiring from being removed.
 */
class ReleaseAdIdGuardSourceTest {
    private val gradle: String = listOf("build.gradle", "app/build.gradle").map(::File).first { it.exists() }.readText()
    private val release: String = gradle.substringAfter("        release {\n            // Release has no fallback").substringBefore("signingConfig signingConfigs.release")

    @Test
    fun theCheckStopsTheBuildWithoutTheExplicitFlag() {
        assertTrue(gradle.contains("GOOGLE_TEST_PUBLISHER = \"3940256099942544\""))
        assertTrue(gradle.contains("releaseRequested && !allowTestAdIds && v.contains(GOOGLE_TEST_PUBLISHER)"))
        assertTrue(gradle.contains("throw new GradleException(\"Release build: '\${key}' is a Google sample ad id"))
        assertTrue("the flag defaults to false", gradle.contains("getOrElse(\"false\") == \"true\""))
    }

    @Test
    fun everyReleaseAdIdGoesThroughTheCheck() {
        for (key in listOf("ADMOB_APP_ID", "ADMOB_BANNER_ID", "ADMOB_INTERSTITIAL_ID", "ADMOB_APP_OPEN_ID", "ADMOB_REWARDED_ID")) {
            assertTrue("$key must be read with releaseAdId in the release block", release.contains("releaseAdId(\"$key\")"))
            assertFalse("$key must not bypass the check", release.contains("privateProp(\"$key\")"))
        }
    }

    @Test
    fun theDebugBlockIsNotChecked_itServesTheSampleIds() {
        val debug = gradle.substringAfter("        debug {").substringBefore("        release {")
        assertFalse(debug.contains("releaseAdId("))
        assertEquals(4, Regex("""GOOGLE_TEST_(BANNER|INTERSTITIAL|APP_OPEN|REWARDED)\)""").findAll(debug).count())
    }
}
