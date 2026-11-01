package com.mckimquyen.reader.ui.component.quote

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SelectionActionChipsDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class ShownToolbar : TextToolbar {
        override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) { status = TextToolbarStatus.Shown }
        override fun hide() { status = TextToolbarStatus.Hidden }
    }

    private fun toolbar(calls: MutableList<String>): QuoteTextToolbar {
        val t = QuoteTextToolbar(ShownToolbar(), onInvestigate = { calls += "investigate" }) { calls += "share" }
        t.showMenu(Rect(10f, 20f, 200f, 60f), { calls += "copy" }, null, null, null)
        return t
    }

    @Test
    fun showsBothShareAndInvestigateChips() {
        val t = toolbar(mutableListOf())
        compose.setContent { QuoteShareChip(t) }

        compose.onNodeWithTag(QUOTE_SHARE_CHIP_TAG).assertIsDisplayed()
        compose.onNodeWithTag(QUOTE_INVESTIGATE_CHIP_TAG).assertIsDisplayed()
    }

    @Test
    fun clickingInvestigate_invokesCopyThenInvestigate() {
        val calls = mutableListOf<String>()
        val t = toolbar(calls)
        compose.setContent { QuoteShareChip(t) }

        compose.onNodeWithTag(QUOTE_INVESTIGATE_CHIP_TAG).performClick()

        assertEquals(listOf("copy", "investigate"), calls)
    }

    @Test
    fun clickingShare_invokesCopyThenShare() {
        val calls = mutableListOf<String>()
        val t = toolbar(calls)
        compose.setContent { QuoteShareChip(t) }

        compose.onNodeWithTag(QUOTE_SHARE_CHIP_TAG).performClick()

        assertEquals(listOf("copy", "share"), calls)
    }
}
