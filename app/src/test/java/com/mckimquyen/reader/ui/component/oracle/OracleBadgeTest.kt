package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OracleBadgeTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var clicks = 0

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun showsTheLabelAndIsClickable() {
        compose.setContent { OracleBadge(onClick = { clicks++ }) }
        compose.onNodeWithText(str(R.string.oracle_badge_label)).assertIsDisplayed()
        compose.onNodeWithTag("oracle_badge").assertHasClickAction()
    }

    @Test fun clickingCallsBack() {
        compose.setContent { OracleBadge(onClick = { clicks++ }) }
        compose.onNodeWithTag("oracle_badge").performClick()
        assertEquals(1, clicks)
    }

    @Test fun spokenDescriptionNamesTheLabelAndTheAction() {
        compose.setContent { OracleBadge(onClick = {}) }
        val expected = "${str(R.string.oracle_badge_label)}. ${str(R.string.oracle_badge_open)}"
        compose.onNodeWithContentDescription(expected).assertIsDisplayed()
    }
}
