package com.mckimquyen.reader.ui.ext

import org.junit.Assert.*
import org.junit.Test

class DataStoreKeysTest {

    @Test
    fun `test DataStore key names are consistent`() {
        assertEquals("InitialPage key name should be correct", "initialPage", DataStoreKeys.InitialPage.key.name)
        assertEquals("InitialFilter key name should be correct", "initialFilter", DataStoreKeys.InitialFilter.key.name)
        assertEquals("Languages key name should be correct", "languages", DataStoreKeys.Languages.key.name)
        assertEquals("CurrentAccountId key name should be correct", "currentAccountId", DataStoreKeys.CurrentAccountId.key.name)
    }

    @Test
    fun `test DataStore keys are not null`() {
        assertNotNull("InitialPage key should not be null", DataStoreKeys.InitialPage.key)
        assertNotNull("InitialFilter key should not be null", DataStoreKeys.InitialFilter.key)
        assertNotNull("Languages key should not be null", DataStoreKeys.Languages.key)
        assertNotNull("CurrentAccountId key should not be null", DataStoreKeys.CurrentAccountId.key)
    }

    @Test
    fun `test DataStore keys are unique`() {
        val keys = listOf(
            DataStoreKeys.InitialPage.key.name,
            DataStoreKeys.InitialFilter.key.name,
            DataStoreKeys.Languages.key.name,
            DataStoreKeys.CurrentAccountId.key.name,
            DataStoreKeys.ThemeIndex.key.name,
            DataStoreKeys.DarkTheme.key.name
        )

        val uniqueKeys = keys.toSet()
        assertEquals("All DataStore keys should be unique", keys.size, uniqueKeys.size)
    }

    @Test
    fun `test preference key names are not empty`() {
        val keyNames = listOf(
            DataStoreKeys.InitialPage.key.name,
            DataStoreKeys.InitialFilter.key.name,
            DataStoreKeys.Languages.key.name,
            DataStoreKeys.CurrentAccountId.key.name
        )

        keyNames.forEach { keyName ->
            assertFalse("Key name should not be empty: $keyName", keyName.isEmpty())
            assertTrue("Key name should not be blank: $keyName", keyName.isNotBlank())
        }
    }

    @Test
    fun `test boolean preference keys`() {
        assertNotNull("IsFirstLaunch key should not be null", DataStoreKeys.IsFirstLaunch.key)
        assertNotNull("AmoledDarkTheme key should not be null", DataStoreKeys.AmoledDarkTheme.key)

        assertEquals("IsFirstLaunch key name should be correct", "isFirstLaunch", DataStoreKeys.IsFirstLaunch.key.name)
        assertEquals("AmoledDarkTheme key name should be correct", "amoledDarkTheme", DataStoreKeys.AmoledDarkTheme.key.name)
    }

    @Test
    fun `test string preference keys`() {
        assertNotNull("SkipVersionNumber key should not be null", DataStoreKeys.SkipVersionNumber.key)
        assertNotNull("NewVersionNumber key should not be null", DataStoreKeys.NewVersionNumber.key)

        assertEquals("SkipVersionNumber key name should be correct", "skipVersionNumber", DataStoreKeys.SkipVersionNumber.key.name)
        assertEquals("NewVersionNumber key name should be correct", "newVersionNumber", DataStoreKeys.NewVersionNumber.key.name)
    }

    @Test
    fun `test DataStoreKeys inheritance structure`() {
        assertTrue("InitialPage should extend DataStoreKeys", DataStoreKeys.InitialPage is DataStoreKeys<*>)
        assertTrue("InitialFilter should extend DataStoreKeys", DataStoreKeys.InitialFilter is DataStoreKeys<*>)
        assertTrue("Languages should extend DataStoreKeys", DataStoreKeys.Languages is DataStoreKeys<*>)
        assertTrue("CurrentAccountId should extend DataStoreKeys", DataStoreKeys.CurrentAccountId is DataStoreKeys<*>)
    }
}