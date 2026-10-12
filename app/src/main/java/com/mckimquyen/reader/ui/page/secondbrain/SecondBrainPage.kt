package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import java.text.DateFormat
import java.util.Date

const val SECOND_BRAIN_TITLE_TAG = "second_brain_title"
private const val NODE_MIN_RADIUS = 0.012f
private const val NODE_MAX_RADIUS = 0.032f
private const val HIT_RADIUS = 0.045f

@Composable
fun SecondBrainPage(
    state: SecondBrainUiState,
    onSelectNode: (String?) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onOpenArticle: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The app is edge-to-edge: without safeDrawingPadding the title sits under the status bar and the
    // last row under the gesture bar.
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(
            stringResource(R.string.second_brain_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(SECOND_BRAIN_TITLE_TAG),
        )
        Spacer(Modifier.height(12.dp))
        when (state) {
            SecondBrainUiState.Loading -> CircularProgressIndicator()
            SecondBrainUiState.Empty -> Text(stringResource(R.string.second_brain_empty))
            SecondBrainUiState.Error -> {
                Text(stringResource(R.string.second_brain_error))
                Button(onClick = onRetry) { Text(stringResource(R.string.second_brain_retry)) }
            }
            is SecondBrainUiState.Ready -> ReadyContent(state, onSelectNode, onRangeChange, onOpenArticle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    state: SecondBrainUiState.Ready,
    onSelectNode: (String?) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val graph = state.graph
    val description = stringResource(R.string.second_brain_graph_desc, graph.nodes.size, graph.edges.size)
    if (graph.nodes.isEmpty()) {
        Text(stringResource(R.string.second_brain_empty_window), modifier = Modifier.padding(vertical = 16.dp))
    } else {
        BrainGraphCanvas(
            graph = graph,
            selectedNodeId = state.selectedNodeId,
            onSelectNode = onSelectNode,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description },
        )
    }

    if (graph.maxTime > graph.minTime) {
        Text(stringResource(R.string.second_brain_range_label), style = MaterialTheme.typography.labelLarge)
        var low by remember(state.from) { mutableFloatStateOf(state.from.toFloat()) }
        var high by remember(state.to) { mutableFloatStateOf(state.to.toFloat()) }
        RangeSlider(
            value = low..high,
            onValueChange = { low = it.start; high = it.endInclusive },
            onValueChangeFinished = { onRangeChange(low.toLong(), high.toLong()) },
            valueRange = graph.minTime.toFloat()..graph.maxTime.toFloat(),
        )
    }

    val selected = graph.nodes.firstOrNull { it.id == state.selectedNodeId }
    if (selected != null) {
        Text(selected.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.second_brain_node_articles, selected.weight))
        Text(stringResource(R.string.second_brain_related), style = MaterialTheme.typography.labelLarge)
        val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
        val articles = selected.articleIds.mapNotNull { graph.articleInfo[it] }.sortedByDescending { it.date }
        LazyColumn {
            items(articles, key = { it.id }) { article ->
                Column(Modifier.fillMaxWidth().clickable { onOpenArticle(article.id) }.padding(vertical = 6.dp)) {
                    Text(article.title, color = MaterialTheme.colorScheme.primary)
                    Text(dateFormat.format(Date(article.date)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BrainGraphCanvas(
    graph: BrainGraph,
    selectedNodeId: String?,
    onSelectNode: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positions = remember(graph) { BrainGraphLayout.compute(graph) }
    val maxWeight = remember(graph) { graph.nodes.maxOfOrNull { it.weight } ?: 1 }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(positions) {
                detectTapGestures { tap ->
                    val hit = BrainGraphLayout.hitTest(positions, tap.x / size.width, tap.y / size.height, HIT_RADIUS)
                    onSelectNode(hit)
                }
            },
        ) {
            graph.edges.forEach { edge ->
                val a = positions[edge.from] ?: return@forEach
                val b = positions[edge.to] ?: return@forEach
                drawLine(
                    color = outline.copy(alpha = 0.45f),
                    start = Offset(a.x * size.width, a.y * size.height),
                    end = Offset(b.x * size.width, b.y * size.height),
                    strokeWidth = 1.5f + edge.weight.coerceAtMost(5),
                )
            }
            graph.nodes.forEach { node ->
                val p = positions[node.id] ?: return@forEach
                val fraction = node.weight.toFloat() / maxWeight
                val radius = (NODE_MIN_RADIUS + (NODE_MAX_RADIUS - NODE_MIN_RADIUS) * fraction) * size.minDimension
                val center = Offset(p.x * size.width, p.y * size.height)
                drawCircle(color = if (node.id == selectedNodeId) highlight else primary, radius = radius, center = center)
                if (node.id == selectedNodeId) {
                    drawCircle(color = highlight, radius = radius + 6f, center = center, style = Stroke(width = 3f))
                }
            }
        }
    }
}
