package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val SECOND_BRAIN_TITLE_TAG = "second_brain_title"
const val SECOND_BRAIN_GRAPH_TAG = "second_brain_graph"
const val SECOND_BRAIN_RESET_TAG = "second_brain_reset"
const val SECOND_BRAIN_LIST_TAG = "second_brain_list"
const val SECOND_BRAIN_HINT_TAG = "second_brain_hint"

private const val NODE_MIN_RADIUS = 0.012f
private const val NODE_MAX_RADIUS = 0.032f
private const val HIT_RADIUS_DP = 24
private const val GRAPH_WEIGHT = 0.55f
private const val LIST_WEIGHT = 0.45f
private const val LABEL_TEXT_SP = 11
private const val DIMMED_ALPHA = 0.25f
private const val ZOOM_PERCENT = 100
private const val MAX_RADIUS_GROWTH = 2f
private const val LABEL_PADDING_PX = 6f

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
            is SecondBrainUiState.Ready -> Box(Modifier.weight(1f)) {
                ReadyContent(state, onSelectNode, onRangeChange, onOpenArticle)
            }
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
    val hint = stringResource(R.string.second_brain_select_hint)
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Column(Modifier.fillMaxSize()) {
        if (graph.nodes.isEmpty()) {
            Text(stringResource(R.string.second_brain_empty_window), modifier = Modifier.padding(vertical = 16.dp))
        } else {
            BrainGraphCanvas(
                graph = graph,
                selectedNodeId = state.selectedNodeId,
                onSelectNode = onSelectNode,
                description = description,
                modifier = Modifier.fillMaxWidth().weight(GRAPH_WEIGHT),
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

        Box(Modifier.fillMaxWidth().weight(LIST_WEIGHT).testTag(SECOND_BRAIN_LIST_TAG)) {
            val selected = graph.nodes.firstOrNull { it.id == state.selectedNodeId }
            if (selected == null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(SECOND_BRAIN_HINT_TAG))
            } else {
                val articles = selected.articleIds.mapNotNull { graph.articleInfo[it] }.sortedByDescending { it.date }
                Column {
                    Text(selected.label, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.second_brain_node_articles, selected.weight))
                    Text(stringResource(R.string.second_brain_related), style = MaterialTheme.typography.labelLarge)
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
        }
    }
}

@Composable
private fun BrainGraphCanvas(
    graph: BrainGraph,
    selectedNodeId: String?,
    onSelectNode: (String?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val positions = remember(graph) { BrainGraphLayout.compute(graph) }
    val maxWeight = remember(graph) { graph.nodes.maxOfOrNull { it.weight } ?: 1 }
    val neighbours = remember(graph, selectedNodeId) { GraphHighlight.neighbours(selectedNodeId, graph.edges) }
    var viewport by remember(graph) { mutableStateOf(GraphViewport()) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = LABEL_TEXT_SP.sp) }
    val zoomLabel = stringResource(R.string.second_brain_zoom_level, (viewport.scale * ZOOM_PERCENT).roundToInt())
    val resetLabel = stringResource(R.string.second_brain_reset_view)
    val zoomHint = stringResource(R.string.second_brain_zoom_hint)
    val hitRadiusPx = with(LocalDensity.current) { HIT_RADIUS_DP.dp.toPx() }
    val hasSelection = selectedNodeId != null
    val weightById = remember(graph) { graph.nodes.associate { it.id to it.weight } }

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { viewport = viewport.withSize(it.width.toFloat(), it.height.toFloat()) }
                .semantics {
                    contentDescription = description
                    stateDescription = zoomLabel
                }
                .testTag(SECOND_BRAIN_GRAPH_TAG)
                .pointerInput(positions) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport = viewport.zoomBy(zoom, centroid.x, centroid.y).panBy(pan.x, pan.y)
                    }
                }
                .pointerInput(positions) {
                    detectTapGestures(
                        onDoubleTap = { viewport = viewport.reset() },
                        onTap = { tap ->
                            val unit = viewport.toUnit(tap.x, tap.y)
                            val radiusUnit = hitRadiusPx / (minOf(size.width, size.height) * viewport.scale)
                            onSelectNode(BrainGraphLayout.hitTest(positions, unit.x, unit.y, radiusUnit))
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                graph.edges.forEach { edge ->
                    val a = positions[edge.from] ?: return@forEach
                    val b = positions[edge.to] ?: return@forEach
                    val active = GraphHighlight.isActiveEdge(selectedNodeId, edge)
                    val alpha = if (!hasSelection || active) 0.6f else DIMMED_ALPHA * 0.5f
                    drawLine(
                        color = (if (active) highlight else outline).copy(alpha = alpha),
                        start = viewport.toScreen(a),
                        end = viewport.toScreen(b),
                        strokeWidth = 1.5f + edge.weight.coerceAtMost(5) + if (active) 2f else 0f,
                    )
                }
                val growth = sqrt(viewport.scale).coerceAtMost(MAX_RADIUS_GROWTH)
                fun radiusOf(weight: Int): Float {
                    val fraction = weight.toFloat() / maxWeight
                    return (NODE_MIN_RADIUS + (NODE_MAX_RADIUS - NODE_MIN_RADIUS) * fraction) * size.minDimension * growth
                }
                graph.nodes.forEach { node ->
                    val p = positions[node.id] ?: return@forEach
                    val radius = radiusOf(node.weight)
                    val center = viewport.toScreen(p)
                    val bright = !hasSelection || node.id == selectedNodeId || node.id in neighbours
                    val color = if (node.id == selectedNodeId) highlight else primary
                    drawCircle(color = color.copy(alpha = if (bright) 1f else DIMMED_ALPHA), radius = radius, center = center)
                    if (node.id == selectedNodeId) {
                        drawCircle(color = highlight, radius = radius + 6f, center = center, style = Stroke(width = 3f))
                    }
                }
                val screen = graph.nodes.mapNotNull { n -> positions[n.id]?.let { n.id to viewport.toScreen(it) } }.toMap()
                GraphLabels.select(
                    nodes = graph.nodes,
                    screenPositions = screen,
                    selectedId = selectedNodeId,
                    neighbourIds = neighbours,
                    scale = viewport.scale,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    measure = { text -> textMeasurer.measure(text, labelStyle).size.let { it.width.toFloat() to it.height.toFloat() } },
                    gapFor = { id -> radiusOf(weightById[id] ?: 0) + LABEL_PADDING_PX },
                ).forEach { box ->
                    drawText(textMeasurer, box.text, Offset(box.left, box.top), style = labelStyle)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(zoomHint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { viewport = viewport.reset() },
                enabled = !viewport.isIdentity,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SECOND_BRAIN_RESET_TAG),
            ) { Text(resetLabel) }
        }
    }
}
