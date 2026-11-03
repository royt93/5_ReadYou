package com.mckimquyen.reader.ui.page.secondbrain.galaxy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.galaxy.GalaxyNeighbour
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import java.text.DateFormat
import java.util.Date

const val GALAXY_SHEET_TAG = "galaxy_sheet"
const val GALAXY_OPEN_TAG = "galaxy_open"
const val GALAXY_NEIGHBOURS_TAG = "galaxy_neighbours"
const val GALAXY_NEIGHBOUR_ROW_TAG_PREFIX = "galaxy_neighbour_"

private const val TITLE_MAX_LINES = 3
private const val NEIGHBOUR_TITLE_MAX_LINES = 2
private const val MIN_TOUCH_TARGET_DP = 48

/** A tall card must scroll instead of pushing the galaxy off a short, edge-to-edge screen. */
private const val MAX_CARD_HEIGHT_DP = 280

/**
 * Details of the tapped star: title, source, date and a button to reopen the article, followed by up to a few related
 * stars with the headline words they share. An inline panel rather than a modal sheet (Material3 1.0.1 has none), so
 * it never covers the galaxy the user is exploring. Tapping a related row selects that star; it never opens it.
 */
@Composable
fun GalaxyStarSheet(
    star: GalaxyStar,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    neighbours: List<GalaxyNeighbour> = emptyList(),
    onSelectNeighbour: (String) -> Unit = {},
) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag(GALAXY_SHEET_TAG),
    ) {
        Column(
            Modifier.heightIn(max = MAX_CARD_HEIGHT_DP.dp).verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
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

            if (neighbours.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag(GALAXY_NEIGHBOURS_TAG)) {
                    Text(stringResource(R.string.galaxy_related_title), style = MaterialTheme.typography.titleSmall)
                    neighbours.forEach { neighbour ->
                        Column(
                            Modifier.fillMaxWidth()
                                .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                                .clickable(role = Role.Button) { onSelectNeighbour(neighbour.star.id) }
                                .padding(vertical = 6.dp)
                                .testTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + neighbour.star.id),
                        ) {
                            Text(
                                neighbour.star.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = NEIGHBOUR_TITLE_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.galaxy_shared_terms, neighbour.sharedTerms.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
