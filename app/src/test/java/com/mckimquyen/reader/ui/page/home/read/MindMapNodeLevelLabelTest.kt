package com.mckimquyen.reader.ui.page.home.read

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mckimquyen.reader.domain.model.article.MindMapNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MindMapNodeLevelLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(depth: Int) = composeRule.setContent {
        NodeDetailCard(node = MindMapNode(id = "n", label = "Label", depth = depth), onDismiss = {})
    }

    @Test
    @Config(qualifiers = "vi")
    fun pillarLevel_isLocalizedInVietnamese() {
        show(depth = 1)
        composeRule.onNodeWithText("Trụ cột").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "vi")
    fun detailLevel_isLocalizedInVietnamese() {
        show(depth = 2)
        composeRule.onNodeWithText("Chi tiết").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ja")
    fun pillarLevel_isLocalizedInJapanese() {
        show(depth = 1)
        composeRule.onNodeWithText("柱").assertIsDisplayed()
    }

    @Test
    fun levels_fallBackToEnglishByDefault() {
        show(depth = 2)
        composeRule.onNodeWithText("Detail").assertIsDisplayed()
    }
}
