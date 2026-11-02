package com.mckimquyen.reader.ui.page.secondbrain.galaxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import java.text.DateFormat
import java.util.Date

const val GALAXY_SHEET_TAG = "galaxy_sheet"
const val GALAXY_OPEN_TAG = "galaxy_open"

private const val TITLE_MAX_LINES = 3
private const val MIN_TOUCH_TARGET_DP = 48

/**
 * Details of the tapped star: title, source, date and a button to reopen the article. An inline panel rather than a
 * modal sheet (Material3 1.0.1 has none), so it never covers the galaxy the user is exploring.
 */
@Composable
fun GalaxyStarSheet(star: GalaxyStar, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag(GALAXY_SHEET_TAG),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                star.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${star.feedName} · ${dateFormat.format(Date(star.date))}",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { onOpen(star.id) },
                modifier = Modifier.padding(top = 8.dp).heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(GALAXY_OPEN_TAG),
            ) { Text(stringResource(R.string.galaxy_star_open)) }
        }
    }
}
