package com.mckimquyen.reader.ui.page.home.flow

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.MoreVert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.unit.dp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FlowActionsMenuWidgetTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun moreMenuShowsFourActionsAndOptionalMarkAll() {
        composeRule.setContent {
            FlowToolbarActionsHarness(showMarkAllAsRead = true)
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        listOf(R.string.mark_all_as_read, R.string.commute_cast_title, R.string.brain_rpg_title, R.string.watchdog_title)
            .forEach { composeRule.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        composeRule.onNodeWithContentDescription(context.getString(R.string.search)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.daily_digest_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reels_title)).assertIsDisplayed()
    }

    @Test
    fun moreMenuOmitsMarkAllWhenFilterDisallowsIt() {
        composeRule.setContent { FlowToolbarActionsHarness(showMarkAllAsRead = false) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.mark_all_as_read)).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.commute_cast_title)).assertIsDisplayed()
    }

    @Test
    fun selectingEachOverflowActionInvokesItsCallbackAndClosesMenu() {
        val selected = mutableListOf<Int>()
        composeRule.setContent {
            FlowToolbarActionsHarness(
                showMarkAllAsRead = true,
                onMarkAll = { selected += 0 },
                onCommuteCast = { selected += 1 },
                onBrainRpg = { selected += 2 },
                onWatchdog = { selected += 3 },
            )
        }
        val actions = listOf(
            R.string.mark_all_as_read,
            R.string.commute_cast_title,
            R.string.brain_rpg_title,
            R.string.watchdog_title,
        )
        actions.forEachIndexed { index, stringId ->
            val label = context.getString(stringId)
            composeRule.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
            composeRule.onNodeWithText(label).performClick()
            composeRule.waitForIdle()
            assertEquals(index, selected.last())
            composeRule.onAllNodesWithText(label).assertCountEquals(0)
        }
        assertEquals(actions.indices.toList(), selected)
    }

    @androidx.compose.runtime.Composable
    private fun FlowToolbarActionsHarness(
        showMarkAllAsRead: Boolean,
        onMarkAll: () -> Unit = {},
        onCommuteCast: () -> Unit = {},
        onBrainRpg: () -> Unit = {},
        onWatchdog: () -> Unit = {},
    ) {
        var expanded by remember { mutableStateOf(false) }
        Row(Modifier.width(320.dp)) {
            FeedbackIconButton(imageVector = Icons.Rounded.ArrowBack, contentDescription = context.getString(R.string.back)) {}
            FeedbackIconButton(imageVector = Icons.Rounded.Article, contentDescription = context.getString(R.string.daily_digest_title)) {}
            FeedbackIconButton(imageVector = Icons.Outlined.PlayCircle, contentDescription = context.getString(R.string.reels_title)) {}
            FeedbackIconButton(imageVector = Icons.Rounded.MoreVert, contentDescription = context.getString(R.string.more)) { expanded = true }
            FeedbackIconButton(imageVector = Icons.Rounded.Search, contentDescription = context.getString(R.string.search)) {}
        }
        FlowActionsMenu(
            expanded = expanded,
            showMarkAllAsRead = showMarkAllAsRead,
            onDismiss = { expanded = false },
            onMarkAllAsRead = onMarkAll,
            onCommuteCast = onCommuteCast,
            onBrainRpg = onBrainRpg,
            onWatchdog = onWatchdog,
        )
    }
}
