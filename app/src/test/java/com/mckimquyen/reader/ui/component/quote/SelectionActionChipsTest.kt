package com.mckimquyen.reader.ui.component.quote

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SelectionActionChipsTest {

    @get:Rule
    val compose = createComposeRule()

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

    private fun toolbar(calls: MutableList<String>, withSelection: Boolean = true): QuoteTextToolbar {
        val t = QuoteTextToolbar(ShownToolbar(), onInvestigate = { calls += "investigate" }) { calls += "share" }
        t.showMenu(Rect(10f, 20f, 200f, 60f), if (withSelection) ({ calls += "copy" }) else null, null, null, null)
        return t
    }

    @Test
    fun withSelection_showsBothChips() {
        val t = toolbar(mutableListOf())
        compose.setContent { QuoteShareChip(t) }

        compose.onNodeWithTag(QUOTE_SHARE_CHIP_TAG).assertIsDisplayed()
        compose.onNodeWithTag(QUOTE_INVESTIGATE_CHIP_TAG).assertIsDisplayed()
    }

    @Test
    fun withoutSelection_showsNoChip() {
        val t = toolbar(mutableListOf(), withSelection = false)
        compose.setContent { QuoteShareChip(t) }

        compose.onAllNodesWithTag(QUOTE_SHARE_CHIP_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(QUOTE_INVESTIGATE_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun tappingInvestigate_copiesThenInvestigates_andNeverShares() {
        val calls = mutableListOf<String>()
        val t = toolbar(calls)
        compose.setContent { QuoteShareChip(t) }

        compose.onNodeWithTag(QUOTE_INVESTIGATE_CHIP_TAG).performClick()

        assertEquals(listOf("copy", "investigate"), calls)
    }

    @Test
    fun tappingShare_stillCopiesThenShares_andNeverInvestigates() {
        val calls = mutableListOf<String>()
        val t = toolbar(calls)
        compose.setContent { QuoteShareChip(t) }

        compose.onNodeWithTag(QUOTE_SHARE_CHIP_TAG).performClick()

        assertEquals(listOf("copy", "share"), calls)
    }
}
