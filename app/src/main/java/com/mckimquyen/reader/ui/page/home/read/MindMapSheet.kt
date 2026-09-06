package com.mckimquyen.reader.ui.page.home.read

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.MindMapNode
import kotlin.math.roundToInt

/**
 * Material You Interactive Concept Mind Map Bottom Sheet / Fullscreen Canvas.
 *
 * Designed with strict Material 3 guidelines and Edge-to-Edge inset protection:
 * - Fluid pan and zoom gesture viewport with scale bounds [0.55f .. 2.2f].
 * - Elegant cubic Bézier spline connections linking parents to children.
 * - Dynamic M3 tonal card nodes (PrimaryContainer for Root, SecondaryContainer for Pillars, SurfaceVariant for Sub-points).
 * - Tap node interaction displaying contextual detail card with outline export and copy actions.
 * - NavigationBarsPadding safe area with floating reset/recenter control.
 */
@Composable
fun MindMapSheetContent(
    state: MindMapState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onForceOffline: (() -> Unit)? = null,
    onCopyOutline: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val defaultCopyOutline: (String) -> Unit = { outlineText ->
        if (onCopyOutline != null) {
            onCopyOutline(outlineText)
        } else {
            clipboardManager.setText(AnnotatedString(outlineText))
            Toast.makeText(
                context,
                context.getString(R.string.mindmap_copied_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(580.dp)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.mindmap_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            // Source Badge (AI vs Offline Smart Graph)
            if (state is MindMapState.Success) {
                Surface(
                    shape = CircleShape,
                    color = if (state.mindMap.isOfflineFallback) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = if (state.mindMap.isOfflineFallback) {
                            stringResource(R.string.mindmap_offline_badge)
                        } else {
                            stringResource(R.string.mindmap_ai_badge)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.mindMap.isOfflineFallback) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = { defaultCopyOutline(state.mindMap.formatAsOutline()) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.mindmap_export_outline),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Body Content
        when (state) {
            MindMapState.Idle,
            MindMapState.Loading -> MindMapLoadingView()

            is MindMapState.Success -> {
                MindMapInteractiveCanvas(
                    mindMap = state.mindMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            is MindMapState.Error -> {
                MindMapErrorView(
                    message = if (state.arg != null) {
                        stringResource(state.messageRes, state.arg)
                    } else {
                        stringResource(state.messageRes)
                    },
                    onRetry = onRetry,
                    onUseOffline = onForceOffline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MindMapLoadingView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.mindmap_generating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MindMapErrorView(
    message: String,
    onRetry: () -> Unit,
    onUseOffline: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.summary_try_again))
            }

            if (onUseOffline != null) {
                OutlinedButton(
                    onClick = onUseOffline,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.mindmap_offline_badge))
                }
            }
        }
    }
}

/**
 * Coordinate positioning for nodes in the virtual plane.
 */
private data class NodePosition(
    val node: MindMapNode,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
)

@Composable
private fun MindMapInteractiveCanvas(
    mindMap: ArticleMindMap,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var selectedNode by remember { mutableStateOf<MindMapNode?>(null) }

    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val accentLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 2.2f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
    ) {
        val root = mindMap.getRootNode()
        val branches = mindMap.getBranches()

        // Calculate layout geometry on the virtual canvas
        val virtualWidth = 1000f
        val virtualHeight = 700f

        val positions = remember(mindMap) {
            val list = mutableListOf<NodePosition>()
            if (root != null) {
                // Root node position (top center)
                val rootX = virtualWidth / 2f
                val rootY = 90f
                val rootW = 260f
                val rootH = 75f
                list.add(NodePosition(root, rootX, rootY, rootW, rootH))

                val branchCount = branches.size.coerceAtLeast(1)
                val branchSpacing = (virtualWidth - 100f) / branchCount

                branches.forEachIndexed { branchIndex, branch ->
                    val branchX = 50f + (branchIndex + 0.5f) * branchSpacing
                    val branchY = 250f
                    val branchW = 200f
                    val branchH = 65f
                    list.add(NodePosition(branch, branchX, branchY, branchW, branchH))

                    // Sub-nodes underneath this branch
                    val children = mindMap.findChildren(branch.id)
                    children.forEachIndexed { childIndex, child ->
                        val childX = branchX + (childIndex - (children.size - 1) / 2f) * 110f
                        val childY = 410f + (childIndex % 2) * 80f
                        val childW = 160f
                        val childH = 60f
                        list.add(NodePosition(child, childX, childY, childW, childH))
                    }
                }
            }
            list
        }

        // Virtual Plane Container with Pan & Zoom transform
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffsetX
                    translationY = panOffsetY
                }
        ) {
            // 1. Connection Splines (Bézier Curves)
            Canvas(
                modifier = Modifier
                    .width(virtualWidth.dp)
                    .height(virtualHeight.dp)
            ) {
                positions.forEach { parentPos ->
                    val childrenPos = positions.filter { it.node.parentId == parentPos.node.id }
                    childrenPos.forEach { childPos ->
                        val isHighlighted = selectedNode?.id == parentPos.node.id || selectedNode?.id == childPos.node.id
                        val start = Offset(parentPos.centerX, parentPos.centerY + parentPos.height / 2f)
                        val end = Offset(childPos.centerX, childPos.centerY - childPos.height / 2f)

                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            val midY = (start.y + end.y) / 2f
                            cubicTo(
                                start.x, midY,
                                end.x, midY,
                                end.x, end.y
                            )
                        }

                        drawPath(
                            path = path,
                            color = if (isHighlighted) accentLineColor else lineColor,
                            style = Stroke(
                                width = if (isHighlighted) 3.5f else 2.2f,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }

            // 2. Interactive Node Cards
            positions.forEach { pos ->
                val node = pos.node
                val isSelected = selectedNode?.id == node.id

                val leftOffset = (pos.centerX - pos.width / 2f).roundToInt()
                val topOffset = (pos.centerY - pos.height / 2f).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftOffset, topOffset) }
                        .width(pos.width.dp)
                        .height(pos.height.dp)
                        .clickable { selectedNode = if (selectedNode?.id == node.id) null else node }
                ) {
                    MindMapNodeCard(
                        node = node,
                        isSelected = isSelected,
                    )
                }
            }
        }

        // 3. Floating Reset Zoom Button (Safe navigation inset)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = {
                    scale = 1.0f
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FitScreen,
                    contentDescription = stringResource(R.string.mindmap_reset_view),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. Detail Context Bottom Sheet overlay when a node is tapped
        AnimatedVisibility(
            visible = selectedNode != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedNode?.let { node ->
                NodeDetailCard(
                    node = node,
                    onDismiss = { selectedNode = null },
                )
            }
        }
    }
}

@Composable
private fun MindMapNodeCard(
    node: MindMapNode,
    isSelected: Boolean,
) {
    val containerColor = when (node.depth) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (node.depth) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (node.depth == 0) 4.dp else 1.dp),
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = if (isSelected) 6.dp else 0.dp,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            val iconVector = when (node.depth) {
                0 -> Icons.Rounded.AutoAwesome
                1 -> Icons.Rounded.AccountTree
                else -> Icons.Rounded.Lightbulb
            }

            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (node.tag != null && node.depth <= 1) {
                    Text(
                        text = node.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = node.label,
                    style = if (node.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                    fontWeight = if (node.depth <= 1) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NodeDetailCard(
    node: MindMapNode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = when (node.depth) {
                            0 -> stringResource(R.string.mindmap_root_badge)
                            1 -> "Pillar"
                            else -> "Detail"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = node.detail.ifBlank { node.label },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = {
                        val toCopy = "${node.label}: ${node.detail}"
                        clipboardManager.setText(AnnotatedString(toCopy))
                        Toast.makeText(
                            context,
                            context.getString(R.string.mindmap_copied_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mindmap_node_details))
                }
            }
        }
    }
}
