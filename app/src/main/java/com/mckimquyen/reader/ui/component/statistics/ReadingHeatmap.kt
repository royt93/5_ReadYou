package com.mckimquyen.reader.ui.component.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.statistics.HeatLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

const val READING_HEATMAP_TAG = "reading_heatmap"
const val HEATMAP_WEEKS = 53
const val HEATMAP_DAYS_PER_WEEK = 7

private const val WINDOW_DAYS = 365L
private val CELL_SIZE: Dp = 12.dp
private val CELL_GAP: Dp = 3.dp
private val CELL_CORNER: Dp = 2.dp
private const val ALPHA_LEVEL_1 = 0.25f
private const val ALPHA_LEVEL_2 = 0.5f
private const val ALPHA_LEVEL_3 = 0.75f
private const val ALPHA_LEVEL_4 = 1f

/** Một ô của heatmap: `week` là cột (0 = tuần cũ nhất), `row` 0 = Thứ Hai ... 6 = Chủ nhật. */
internal data class HeatmapCell(val date: LocalDate, val week: Int, val row: Int, val level: Int)

/** Bố cục thuần: các ô từ Thứ Hai trước/bằng (today - 364) tới today, không có ô sau hôm nay. */
internal fun buildHeatmapCells(heatmap: Map<LocalDate, Int>, today: LocalDate): List<HeatmapCell> {
    val windowStart = today.minusDays(WINDOW_DAYS - 1)
    val start = windowStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val cells = ArrayList<HeatmapCell>()
    var date = start
    while (!date.isAfter(today)) {
        cells += HeatmapCell(
            date = date,
            week = (ChronoUnit.DAYS.between(start, date) / HEATMAP_DAYS_PER_WEEK).toInt(),
            row = date.dayOfWeek.value - 1,
            level = HeatLevel.of(heatmap[date] ?: 0),
        )
        date = date.plusDays(1)
    }
    return cells
}

@Composable
private fun levelColor(level: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level) {
        HeatLevel.LEVEL_NONE -> scheme.surfaceVariant
        1 -> scheme.primary.copy(alpha = ALPHA_LEVEL_1)
        2 -> scheme.primary.copy(alpha = ALPHA_LEVEL_2)
        3 -> scheme.primary.copy(alpha = ALPHA_LEVEL_3)
        else -> scheme.primary.copy(alpha = ALPHA_LEVEL_4)
    }
}

@Composable
fun ReadingHeatmap(
    heatmap: Map<LocalDate, Int>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val cells = remember(heatmap, today) { buildHeatmapCells(heatmap, today) }
    val weeks = (cells.maxOfOrNull { it.week } ?: 0) + 1
    val activeDays = heatmap.count { it.value > 0 }
    val description = pluralStringResource(R.plurals.reading_stats_heatmap_desc, activeDays, activeDays)
    val scrollState = rememberScrollState()
    val colors = (HeatLevel.LEVEL_NONE..HeatLevel.LEVEL_MAX).map { levelColor(it) }

    // Mở ra ở mép phải để thấy các tuần mới nhất trước.
    LaunchedEffect(weeks) { scrollState.scrollTo(scrollState.maxValue) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .semantics { contentDescription = description }
                .testTag(READING_HEATMAP_TAG),
        ) {
            val width = (CELL_SIZE + CELL_GAP) * weeks
            val height = (CELL_SIZE + CELL_GAP) * HEATMAP_DAYS_PER_WEEK
            Canvas(modifier = Modifier.width(width).height(height)) {
                val cell = CELL_SIZE.toPx()
                val step = (CELL_SIZE + CELL_GAP).toPx()
                val corner = CornerRadius(CELL_CORNER.toPx())
                for (c in cells) {
                    drawRoundRect(
                        color = colors[c.level],
                        topLeft = Offset(c.week * step, c.row * step),
                        size = Size(cell, cell),
                        cornerRadius = corner,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            Text(
                text = stringResource(R.string.reading_stats_less),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(CELL_SIZE)
                        .then(Modifier.semantics { }),
                ) {
                    Canvas(modifier = Modifier.size(CELL_SIZE)) {
                        drawRoundRect(color = color, cornerRadius = CornerRadius(CELL_CORNER.toPx()))
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.reading_stats_more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
