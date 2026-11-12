package com.mckimquyen.reader.ui.ext

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleRemindedBetsDataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverWritten_isNull() = assertEquals(null, context.oracleRemindedBets)

    @Test
    fun writtenValue_roundTrips(): Unit = runBlocking {
        context.setOracleRemindedBets("a\nb")
        assertEquals("a\nb", context.oracleRemindedBets)
    }

    @Test
    fun keyNameIsHardCoded_andDistinctFromTheConsentKeys() {
        val name = DataStoreKeys.OracleRemindedBets.key.name
        assertEquals("oracle_reminded_bets", name)
        assertNotEquals(DataStoreKeys.OracleClaimsGeminiConsent.key.name, name)
    }

    @Test
    fun doesNotTouchTheOracleConsent(): Unit = runBlocking {
        context.setOracleClaimsGeminiConsent(true)
        context.setOracleRemindedBets("x")
        assertEquals(true, context.oracleClaimsGeminiConsent)
    }
}
