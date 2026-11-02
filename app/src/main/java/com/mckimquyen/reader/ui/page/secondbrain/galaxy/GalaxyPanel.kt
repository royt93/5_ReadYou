package com.mckimquyen.reader.ui.page.secondbrain.galaxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

const val GALAXY_EMPTY_TAG = "galaxy_empty"
const val GALAXY_TOGGLE_CONCEPTS_TAG = "galaxy_toggle_concepts"
const val GALAXY_TOGGLE_STARS_TAG = "galaxy_toggle_stars"

/** Everything the galaxy mode shows under the Second Brain title, for each [GalaxyUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyPanel(
    state: GalaxyUiState,
    onSelectStar: (String?) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onOpenArticle: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        GalaxyUiState.Loading -> CircularProgressIndicator(modifier)
        GalaxyUiState.Empty -> Text(
            stringResource(R.string.galaxy_empty),
            modifier = modifier.testTag(GALAXY_EMPTY_TAG),
        )
        GalaxyUiState.Error -> Column(modifier) {
            Text(stringResource(R.string.galaxy_error))
            Button(onClick = onRetry) { Text(stringResource(R.string.galaxy_retry)) }
        }
        is GalaxyUiState.Ready -> Column(modifier.fillMaxSize()) {
            if (state.graph.stars.isEmpty()) {
                Text(
                    stringResource(R.string.galaxy_empty),
                    modifier = Modifier.padding(vertical = 16.dp).testTag(GALAXY_EMPTY_TAG),
                )
            } else {
                GalaxyCanvas(
                    graph = state.graph,
                    selectedStarId = state.selectedStarId,
                    onSelectStar = onSelectStar,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            if (state.maxTime > state.minTime) {
                Text(stringResource(R.string.second_brain_range_label), style = MaterialTheme.typography.labelLarge)
                var low by remember(state.from) { mutableFloatStateOf(state.from.toFloat()) }
                var high by remember(state.to) { mutableFloatStateOf(state.to.toFloat()) }
                RangeSlider(
                    value = low..high,
                    onValueChange = { low = it.start; high = it.endInclusive },
                    onValueChangeFinished = { onRangeChange(low.toLong(), high.toLong()) },
                    valueRange = state.minTime.toFloat()..state.maxTime.toFloat(),
                )
            }
            state.graph.stars.firstOrNull { it.id == state.selectedStarId }?.let { star ->
                GalaxyStarSheet(star = star, onOpen = onOpenArticle)
            }
        }
    }
}
