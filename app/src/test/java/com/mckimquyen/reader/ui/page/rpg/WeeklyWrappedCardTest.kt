package com.mckimquyen.reader.ui.page.rpg

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.model.rpg.WeeklyBrainWrapped
import com.mckimquyen.reader.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeeklyWrappedCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shareButton_disabledWhenWeeklyXpIsZero() {
        composeRule.setContent {
            AppTheme(useDarkTheme = false) {
                WeeklyWrappedCard(
                    progress = UserProgress(),
                    weeklyState = WeeklyWrappedUiState(
                        isLoading = false,
                        summary = WeeklyBrainWrapped(1L, 2L, 0L, 3, null),
                    ),
                    onShare = {},
                )
            }
        }

        composeRule.onNodeWithTag(WRAPPED_SHARE_BUTTON_TAG).assert(isNotEnabled())
    }

    @Test
    fun shareButton_enabledWhenWeeklyXpIsPositiveAndNoOperationRunning() {
        composeRule.setContent {
            AppTheme(useDarkTheme = false) {
                WeeklyWrappedCard(
                    progress = UserProgress(),
                    weeklyState = WeeklyWrappedUiState(
                        isLoading = false,
                        summary = WeeklyBrainWrapped(1L, 2L, 150L, 3, "Science & Nature"),
                    ),
                    onShare = {},
                )
            }
        }

        composeRule.onNodeWithTag(WRAPPED_SHARE_BUTTON_TAG).assertIsEnabled()
    }

    @Test
    fun shareButton_disabledWhileSharing() {
        composeRule.setContent {
            AppTheme(useDarkTheme = false) {
                WeeklyWrappedCard(
                    progress = UserProgress(),
                    weeklyState = WeeklyWrappedUiState(
                        isLoading = false,
                        summary = WeeklyBrainWrapped(1L, 2L, 150L, 3, "Science & Nature"),
                        isSharing = true,
                    ),
                    onShare = {},
                )
            }
        }

        composeRule.onNodeWithTag(WRAPPED_SHARE_BUTTON_TAG).assert(isNotEnabled())
        composeRule.onNodeWithTag(WRAPPED_PROGRESS_TAG).assertExists()
    }
}
