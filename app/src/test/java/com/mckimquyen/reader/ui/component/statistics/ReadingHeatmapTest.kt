package com.mckimquyen.reader.ui.component.statistics

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.mckimquyen.reader.domain.model.statistics.HeatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingHeatmapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18) // Chủ nhật

    @Test
    fun buildHeatmapCells_placesTodayInLastWeekAtSundayRow() {
        val cells = buildHeatmapCells(emptyMap(), today)

        val last = cells.last()
        assertEquals(today, last.date)
        assertEquals(6, last.row)
        assertEquals(cells.maxOf { it.week }, last.week)
    }

    @Test
    fun buildHeatmapCells_startsOnMondayOnOrBeforeWindowStart() {
        val cells = buildHeatmapCells(emptyMap(), today)

        val first = cells.first()
        assertEquals(DayOfWeek.MONDAY, first.date.dayOfWeek)
        assertEquals(0, first.week)
        assertEquals(0, first.row)
        assertTrue(!first.date.isAfter(today.minusDays(364)))
        assertTrue(first.date.isAfter(today.minusDays(364 + 7)))
    }

    @Test
    fun buildHeatmapCells_levelsFollowHeatLevelAndMissingDaysAreZero() {
        val heatmap = mapOf(today to 7, today.minusDays(1) to 2)

        val byDate = buildHeatmapCells(heatmap, today).associateBy { it.date }

        assertEquals(HeatLevel.of(7), byDate.getValue(today).level)
        assertEquals(HeatLevel.of(2), byDate.getValue(today.minusDays(1)).level)
        assertEquals(0, byDate.getValue(today.minusDays(2)).level)
    }

    @Test
    fun buildHeatmapCells_cellsAreContiguousAndNoneAfterToday() {
        val cells = buildHeatmapCells(emptyMap(), today)

        assertTrue(cells.none { it.date.isAfter(today) })
        cells.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
        assertTrue(cells.size <= HEATMAP_WEEKS * HEATMAP_DAYS_PER_WEEK)
    }

    @Test
    fun heatmap_exposesSingleContentDescriptionWithActiveDayCount() {
        composeTestRule.setContent {
            ReadingHeatmap(heatmap = mapOf(today to 1, today.minusDays(3) to 4), today = today)
        }

        val node = composeTestRule.onNodeWithTag(READING_HEATMAP_TAG).fetchSemanticsNode()
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertEquals("Reading heatmap: 2 active days in the last year", description)
    }
}
