package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirstLaunchPolicyTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aFreshInstallWithNoAnswerShowsTheTerms() {
        assertTrue(FirstLaunchPolicy.isFirstLaunch(null, 1_000_000L, 1_000_000L))
        assertTrue(FirstLaunchPolicy.isFirstLaunch(null, 1_000_000L, 1_000_000L + 800))
    }

    @Test
    fun anInstallThatWasUpdatedDoesNotAskExistingUsersAgain() {
        assertFalse(FirstLaunchPolicy.isFirstLaunch(null, 1_000_000L, 1_000_000L + 30 * day))
    }

    @Test
    fun aSavedAnswerAlwaysWins() {
        assertFalse("agreed already", FirstLaunchPolicy.isFirstLaunch(false, 5L, 5L))
        assertTrue("saved as still to ask", FirstLaunchPolicy.isFirstLaunch(true, 5L, 5L + 30 * day))
    }

    @Test
    fun theToleranceIsShortSoARealUpdateIsNeverMistakenForAnInstall() {
        assertTrue(FirstLaunchPolicy.FRESH_INSTALL_TOLERANCE_MS in 1_000L..60_000L)
        assertFalse(FirstLaunchPolicy.isFirstLaunch(null, 0L, FirstLaunchPolicy.FRESH_INSTALL_TOLERANCE_MS + 1))
    }

    @Test
    fun theAppUsesThePolicyForItsStartDestination() {
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val ext = File(src, "com/mckimquyen/reader/ui/ext/ExtDataStore.kt").readText()
        assertTrue("isFirstLaunch must go through FirstLaunchPolicy", "FirstLaunchPolicy.isFirstLaunch(this" in ext)
        assertFalse("the old constant-false default must be gone", "get(DataStoreKeys.IsFirstLaunch) ?: false" in ext)
    }
}
