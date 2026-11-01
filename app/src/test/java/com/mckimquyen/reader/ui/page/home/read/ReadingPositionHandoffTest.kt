package com.mckimquyen.reader.ui.page.home.read

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mckimquyen.reader.domain.readposition.ReadingPositionMapper
import com.mckimquyen.reader.ui.component.reader.BodyItemRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingPositionHandoffTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sharedBodyRange_isPopulated_andTargetsOnlyBodyItems() {
        val range = BodyItemRange()
        compose.setContent {
            Content(
                content = (1..12).joinToString("") { "<p>Paragraph $it</p>" },
                feedName = "Feed", title = "Title", author = null, link = "https://e.com/a",
                publishedDate = java.util.Date(0L), listState = rememberLazyListState(),
                isLoading = false, articleId = "a1", bodyRange = range,
            )
        }
        compose.waitForIdle()

        assertTrue(range.start > 0)
        // Consecutive paragraphs share one list item, so a text-only body is a single item.
        assertEquals(1, range.count)
        val target = ReadingPositionMapper.scrollTargetFromBodyFraction(0.5f, range.start, range.count)
        assertEquals(range.start, target.listIndex)
        assertEquals(0.5f, target.withinItem, 0.0001f)
    }
}
