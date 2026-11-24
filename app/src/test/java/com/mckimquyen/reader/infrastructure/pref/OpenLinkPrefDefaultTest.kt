package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class OpenLinkPrefDefaultTest {
    @Test
    fun anUnsetKeyGivesTheDeclaredDefault() {
        assertSame(OpenLinkPref.default, OpenLinkPref.fromPreferences(preferencesOf()))
    }

    @Test
    fun everyStoredValueMapsToItsOwnOption() {
        val expected = listOf(
            OpenLinkPref.AutoPreferCustomTabs, OpenLinkPref.AutoPreferDefaultBrowser, OpenLinkPref.CustomTabs,
            OpenLinkPref.DefaultBrowser, OpenLinkPref.SpecificBrowser, OpenLinkPref.AlwaysAsk,
        )
        expected.forEachIndexed { i, option ->
            assertEquals("value $i", option, OpenLinkPref.values[i])
            assertEquals(i, option.value)
        }
    }

    @Test
    fun theBlockListDialogWritesTheEditedValueNotTheOldOne() {
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val page = File(src, "com/mckimquyen/reader/ui/page/setting/acc/AccountDetailsPage.kt").readText()
        val confirm = page.substringAfter("onConfirm = {").substringBefore("TextFieldDlg(")
        org.junit.Assert.assertTrue("onConfirm must write the edited list", "SyncBlockListPref.of(blockListValue)" in page)
        org.junit.Assert.assertTrue("the old value must not be written back", "SyncBlockListPref.put(it, viewModel, selectedAccount.syncBlockList)" !in confirm)
    }
}
