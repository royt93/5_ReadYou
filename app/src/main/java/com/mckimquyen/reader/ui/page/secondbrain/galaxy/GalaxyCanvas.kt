package com.mckimquyen.reader.ui.page.secondbrain.galaxy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.galaxy.GalaxyCamera
import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraph
import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraphBuilder
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLayout
import com.mckimquyen.reader.domain.model.galaxy.GalaxyPoint
import com.mckimquyen.reader.domain.model.galaxy.GalaxyProjection
import kotlin.math.sqrt

const val GALAXY_CANVAS_TAG = "galaxy_canvas"
const val GALAXY_RESET_TAG = "galaxy_reset"

private const val HIT_RADIUS_DP = 24
private const val MIN_TOUCH_TARGET_DP = 48
private const val STAR_RADIUS_FRACTION = 0.012f
private const val SELECTED_RING_EXTRA_PX = 6f
private const val SELECTED_RING_WIDTH_PX = 3f
private const val LINE_WIDTH_PX = 1.5f
private const val ACTIVE_LINE_EXTRA_PX = 1.5f
private const val LINE_ALPHA = 0.35f
private const val DIMMED_LINE_ALPHA = 0.12f
private const val FADE_SCALE_FLOOR = 0.6f
private const val FADE_SCALE_SPAN = 0.8f
private const val MIN_DEPTH_ALPHA = 0.25f

/** More read articles make the backdrop glow brighter, capped so a large archive never washes out the stars. */
private const val GLOW_MIN_ALPHA = 0.06f
private const val GLOW_MAX_ALPHA = 0.30f
private const val GLOW_RADIUS_FRACTION = 0.55f

/**
 * Interactive galaxy. One finger rotates, two fingers zoom, a tap picks the nearest star, a double tap resets.
 * Perspective-projected on a 2D Compose Canvas (no OpenGL, no sensors).
 */
@Composable
fun GalaxyCanvas(
    graph: GalaxyGraph,
    selectedStarId: String?,
    onSelectStar: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positions = remember(graph) { GalaxyLayout.compute(graph.stars) }
    // Not keyed by graph: moving the time slider must not throw the user's viewing angle away.
    var camera by remember { mutableStateOf(GalaxyCamera.DEFAULT) }
    val currentOnSelect by rememberUpdatedState(onSelectStar)
    val hitRadiusPx = with(LocalDensity.current) { HIT_RADIUS_DP.dp.toPx() }
    val description = stringResource(R.string.galaxy_canvas_desc, graph.stars.size, graph.lines.size)
    val stats = stringResource(R.string.galaxy_stats, graph.stars.size, graph.lines.size)
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .semantics { contentDescription = description }
                .testTag(GALAXY_CANVAS_TAG)
                .pointerInput(positions) {
                    detectTransformGestures { _, pan, zoom, _ -> camera = camera.rotateBy(pan.x, pan.y).zoomBy(zoom) }
                }
                .pointerInput(positions) {
                    detectTapGestures(
                        onDoubleTap = { camera = GalaxyCamera.DEFAULT },
                        onTap = { tap ->
                            currentOnSelect(
                                camera.hitTest(
                                    graph.stars, positions, size.width.toFloat(), size.height.toFloat(),
                                    tap.x, tap.y, hitRadiusPx,
                                ),
                            )
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGalaxy(graph, positions, camera, selectedStarId, primary, outline, highlight)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stats, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.galaxy_hint), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = { camera = GalaxyCamera.DEFAULT },
                enabled = !camera.isDefault,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(GALAXY_RESET_TAG),
            ) { Text(stringResource(R.string.galaxy_reset_view)) }
        }
    }
}

private fun DrawScope.drawGalaxy(
    graph: GalaxyGraph,
    positions: Map<String, GalaxyPoint>,
    camera: GalaxyCamera,
    selectedId: String?,
    primary: Color,
    outline: Color,
    highlight: Color,
) {
    val projected = HashMap<String, GalaxyProjection>(positions.size * 2)
    positions.forEach { (id, point) -> camera.project(point, size.width, size.height)?.let { projected[id] = it } }

    val fill = (graph.stars.size.toFloat() / GalaxyGraphBuilder.MAX_STARS).coerceAtMost(1f)
    val glowAlpha = GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * fill
    val glowRadius = size.minDimension * GLOW_RADIUS_FRACTION
    drawCircle(
        brush = Brush.radialGradient(listOf(primary.copy(alpha = glowAlpha), Color.Transparent), center, glowRadius),
        radius = glowRadius,
        center = center,
    )

    val hasSelection = selectedId != null
    graph.lines.forEach { line ->
        val a = projected[line.a] ?: return@forEach
        val b = projected[line.b] ?: return@forEach
        val active = line.a == selectedId || line.b == selectedId
        val alpha = if (!hasSelection || active) LINE_ALPHA else DIMMED_LINE_ALPHA
        drawLine(
            color = (if (active) highlight else outline).copy(alpha = alpha),
            start = Offset(a.x, a.y),
            end = Offset(b.x, b.y),
            strokeWidth = LINE_WIDTH_PX + if (active) ACTIVE_LINE_EXTRA_PX else 0f,
        )
    }

    val zoomGrowth = sqrt(camera.zoom)
    camera.depthOrder(positions).forEach { id ->
        val p = projected[id] ?: return@forEach
        val radius = size.minDimension * STAR_RADIUS_FRACTION * p.scale * zoomGrowth
        val alpha = ((p.scale - FADE_SCALE_FLOOR) / FADE_SCALE_SPAN).coerceIn(MIN_DEPTH_ALPHA, 1f)
        val selected = id == selectedId
        drawCircle(
            color = (if (selected) highlight else primary).copy(alpha = if (selected) 1f else alpha),
            radius = radius,
            center = Offset(p.x, p.y),
        )
        if (selected) {
            drawCircle(
                color = highlight,
                radius = radius + SELECTED_RING_EXTRA_PX,
                center = Offset(p.x, p.y),
                style = Stroke(width = SELECTED_RING_WIDTH_PX),
            )
        }
    }
}
