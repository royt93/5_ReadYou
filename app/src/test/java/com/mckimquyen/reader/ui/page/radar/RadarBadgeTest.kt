package com.mckimquyen.reader.ui.page.radar

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.ui.component.radar.RadarBadge
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RadarBadgeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun editedBadge_showsLabelAndOpensDiff() {
        var clicks = 0
        rule.setContent { RadarBadge(flag = RadarFlag.EDITED, onClick = { clicks++ }) }

        rule.onNodeWithTag("radar_badge_EDITED").assertIsDisplayed()
        rule.onNodeWithTag("radar_badge_EDITED").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun removedBadge_isDistinctFromEdited() {
        rule.setContent { RadarBadge(flag = RadarFlag.REMOVED, onClick = {}) }

        rule.onNodeWithTag("radar_badge_REMOVED").assertIsDisplayed()
        rule.onNodeWithTag("radar_badge_EDITED").assertDoesNotExist()
    }
}
