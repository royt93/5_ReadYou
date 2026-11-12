package com.mckimquyen.reader.ui.page.home.read.deepdive

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.home.read.TopBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeepDiveMenuEntryTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var opened = 0

    private fun show() = compose.setContent {
        TopBar(
            navController = NavHostController(compose.activity),
            isShow = true,
            title = "Title",
            link = "https://example.test",
            onDeepDive = { opened++ },
        )
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.more)).performClick()
    }

    @Test fun overflowMenuListsDeepDive() {
        show(); openMenu()
        compose.onNodeWithText(compose.activity.getString(R.string.deep_dive_menu)).assertIsDisplayed()
    }

    @Test fun tappingItReportsOneOpenAndClosesTheMenu() {
        show(); openMenu()
        compose.onNodeWithText(compose.activity.getString(R.string.deep_dive_menu)).performClick()
        assertEquals(1, opened)
        compose.onAllNodesWithText(compose.activity.getString(R.string.deep_dive_menu)).assertCountEquals(0)
    }

    @Test fun nothingIsCalledUntilTheUserTaps() {
        show(); openMenu()
        assertEquals(0, opened)
    }
}
