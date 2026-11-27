package com.mckimquyen.reader.ui.page.home.flow

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On a Tecno the header above the semantic results was 454 dp tall. The title took the whole row, so the "Save as collection" button was squeezed to no width at all and
 * its label broke one word per line. That pushed the keyword results below the screen, and a search looked empty until the user scrolled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SemanticResultsHeaderTest {
    // The one-line label "Save as collection" measured 58 dp at density 1.0 once the title no longer takes the whole row; the broken layout gave the button 0 dp.
    private companion object { val MIN_LABEL_WIDTH = 50.dp }

    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun bounds(res: Int) = rule.onNodeWithText(context.getString(res)).getBoundsInRoot()

    @Test fun theSaveButtonKeepsRoomForItsLabelAndTheHeaderStaysOneLine() {
        rule.setContent { Box(Modifier.width(360.dp)) { SemanticResultsHeader(onSave = {}) } }
        rule.waitForIdle()
        val button = bounds(R.string.smart_collection_save_action)
        val title = bounds(R.string.semantic_search_results_title)
        assertTrue("the save button is only ${button.width} wide, its label cannot fit", button.width >= MIN_LABEL_WIDTH)
        assertTrue("the header is ${button.height} / ${title.height} tall, the label is wrapped", button.height < 60.dp && title.height < 60.dp)
    }

    @Test fun aLongTitleDoesNotPushTheButtonOut() {
        rule.setContent { Box(Modifier.width(280.dp)) { SemanticResultsHeader(onSave = {}) } }
        rule.waitForIdle()
        assertTrue(bounds(R.string.smart_collection_save_action).width >= MIN_LABEL_WIDTH)
    }
}
