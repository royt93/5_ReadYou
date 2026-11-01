package com.mckimquyen.reader.ui.ext

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression for the device bug found in ECHO-01 smoke: the shared `Headline signals` switch was read once at
 * startup, so flipping it in Settings had no effect until the process restarted. The Home entry now observes
 * this flow instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadlineSignalsFlowTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newStore() = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(tmp.newFolder(), "settings.preferences_pb") },
    )

    private val key = booleanPreferencesKey("headline_signals_enabled")

    @Test
    fun emptyStore_defaultsToEnabled() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(true, newStore().headlineSignalsFlow().first())
    }

    @Test
    fun explicitOff_isReportedAsDisabled() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.edit { it[key] = false }

        assertEquals(false, store.headlineSignalsFlow().first())
    }

    @Test
    fun flippingTheSwitchWhileCollecting_emitsEachChange_withoutRestart() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { store.headlineSignalsFlow().take(3).toList(seen) }

        store.edit { it[key] = false }
        store.edit { it[key] = true }
        job.join()

        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun repeatedSameValue_isNotReEmitted() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { store.headlineSignalsFlow().take(2).toList(seen) }

        store.edit { it[booleanPreferencesKey("unrelated_pref")] = true }
        store.edit { it[key] = false }
        job.join()

        assertEquals(listOf(true, false), seen)
    }
}
