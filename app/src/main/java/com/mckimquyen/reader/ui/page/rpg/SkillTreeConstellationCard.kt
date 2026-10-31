package com.mckimquyen.reader.ui.page.rpg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.SkillTreeLayout
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.sv.QuizGeneratorService

const val SKILL_TREE_CONSTELLATION_TAG = "brain_rpg_constellation"
const val SKILL_TREE_CANVAS_TAG = "brain_rpg_constellation_canvas"
const val SKILL_TREE_NODE_TAG_PREFIX = "skill_node_"
const val SKILL_TREE_DETAIL_DIALOG_TAG = "skill_tree_detail_dialog"

private data class CategoryDescriptor(
    val id: String,
    val icon: ImageVector,
    val labelRes: Int,
)

private val CATEGORY_DESCRIPTORS = listOf(
    CategoryDescriptor(QuizGeneratorService.CATEGORY_TECH, Icons.Outlined.Memory, R.string.brain_rpg_category_tech),
    CategoryDescriptor(QuizGeneratorService.CATEGORY_SCIENCE, Icons.Outlined.Science, R.string.brain_rpg_category_science),
    CategoryDescriptor(QuizGeneratorService.CATEGORY_BUSINESS, Icons.Outlined.TrendingUp, R.string.brain_rpg_category_business),
    CategoryDescriptor(QuizGeneratorService.CATEGORY_HEALTH, Icons.Outlined.FitnessCenter, R.string.brain_rpg_category_health),
    CategoryDescriptor(QuizGeneratorService.CATEGORY_PHILOSOPHY, Icons.Outlined.MenuBook, R.string.brain_rpg_category_philosophy),
    CategoryDescriptor(QuizGeneratorService.CATEGORY_GENERAL, Icons.Outlined.Lightbulb, R.string.brain_rpg_category_general),
)

@Composable
internal fun SkillTreeConstellationCard(
    progress: UserProgress,
    recoveringCategoryId: String? = null,
    onRecover: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val maxEffectiveXp = remember(progress.categoryXp) {
        (progress.categoryXp.values.maxOrNull() ?: 100L).coerceAtLeast(100L)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SKILL_TREE_CONSTELLATION_TAG),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨ " + stringResource(R.string.brain_rpg_skill_tree_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${progress.totalXp} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                val nodePosMap = remember(widthPx, heightPx) {
                    SkillTreeLayout.nodes.associate {
                        it.categoryId to Offset(it.x * widthPx, it.y * heightPx)
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(SKILL_TREE_CANVAS_TAG)
                ) {
                    SkillTreeLayout.edges.forEach { edge ->
                        val start = nodePosMap[edge.fromCategoryId]
                        val end = nodePosMap[edge.toCategoryId]
                        if (start != null && end != null) {
                            drawLine(
                                color = lineColor,
                                start = start,
                                end = end,
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }

                CATEGORY_DESCRIPTORS.forEach { descriptor ->
                    val spec = SkillTreeLayout.nodes.firstOrNull { it.categoryId == descriptor.id } ?: return@forEach
                    val catEffectiveXp = progress.categoryXp[descriptor.id] ?: 0L
                    val catBaseXp = progress.categoryBaseXp[descriptor.id] ?: catEffectiveXp
                    val visual = SkillTreeLayout.visual(catEffectiveXp, catBaseXp, maxEffectiveXp)
                    val label = stringResource(descriptor.labelRes)

                    ConstellationNode(
                        spec = spec,
                        descriptor = descriptor,
                        label = label,
                        effectiveXp = catEffectiveXp,
                        baseXp = catBaseXp,
                        visual = visual,
                        areaWidthDp = maxWidth,
                        areaHeightDp = maxHeight,
                        onClick = { selectedCategoryId = descriptor.id }
                    )
                }
            }
        }
    }

    selectedCategoryId?.let { catId ->
        val descriptor = CATEGORY_DESCRIPTORS.firstOrNull { it.id == catId }
        if (descriptor != null) {
            val catEffectiveXp = progress.categoryXp[catId] ?: 0L
            val catBaseXp = progress.categoryBaseXp[catId] ?: catEffectiveXp
            val visual = SkillTreeLayout.visual(catEffectiveXp, catBaseXp, maxEffectiveXp)

            CategoryDetailDialog(
                descriptor = descriptor,
                effectiveXp = catEffectiveXp,
                baseXp = catBaseXp,
                visual = visual,
                recoveringCategoryId = recoveringCategoryId,
                onRecover = onRecover,
                onDismiss = { selectedCategoryId = null }
            )
        }
    }
}

@Composable
private fun ConstellationNode(
    spec: com.mckimquyen.reader.domain.model.rpg.SkillTreeNodeSpec,
    descriptor: CategoryDescriptor,
    label: String,
    effectiveXp: Long,
    baseXp: Long,
    visual: com.mckimquyen.reader.domain.model.rpg.SkillTreeNodeVisual,
    areaWidthDp: androidx.compose.ui.unit.Dp,
    areaHeightDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val centerXDp = areaWidthDp * spec.x
    val centerYDp = areaHeightDp * spec.y
    val nodeRadiusDp = (visual.diameterDp / 2).dp
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = if (visual.isDecayed) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f + visual.emphasis * 0.45f)
    }

    val contentDesc = buildString {
        append(label)
        append(", $effectiveXp XP")
        if (visual.isDecayed) {
            append(", $baseXp XP, ${visual.retainedPercent}%")
        }
    }

    Box(
        modifier = Modifier
            .offset(x = centerXDp - 44.dp, y = centerYDp - nodeRadiusDp)
            .width(88.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = contentDesc
            }
            .clickable(onClick = onClick)
            .testTag(SKILL_TREE_NODE_TAG_PREFIX + descriptor.id),
        contentAlignment = Alignment.TopStart
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(visual.diameterDp.dp)
                    .clip(CircleShape)
                    .alpha(if (visual.isDecayed) 0.65f else 1.0f)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = descriptor.icon,
                    contentDescription = null,
                    tint = if (visual.isDecayed) MaterialTheme.colorScheme.onSurfaceVariant else primaryColor,
                    modifier = Modifier.size((visual.diameterDp * 0.45f).dp)
                )

                if (visual.isDecayed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = if (visual.emphasis > 0.4f) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "$effectiveXp XP",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )

            if (visual.isDecayed) {
                Text(
                    text = stringResource(R.string.brain_rpg_xp_retained, baseXp, visual.retainedPercent),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CategoryDetailDialog(
    descriptor: CategoryDescriptor,
    effectiveXp: Long,
    baseXp: Long,
    visual: com.mckimquyen.reader.domain.model.rpg.SkillTreeNodeVisual,
    recoveringCategoryId: String?,
    onRecover: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = descriptor.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = stringResource(descriptor.labelRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$effectiveXp XP",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (visual.isDecayed) {
                        Text(
                            text = stringResource(R.string.brain_rpg_xp_retained, baseXp, visual.retainedPercent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = visual.emphasis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (visual.isDecayed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.brain_rpg_skill_tree_decay_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (visual.isDecayed && onRecover != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    val isPending = recoveringCategoryId == descriptor.id
                    Button(
                        onClick = { onRecover(descriptor.id) },
                        enabled = recoveringCategoryId == null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isPending) {
                                stringResource(R.string.brain_rpg_recover_pending)
                            } else {
                                stringResource(R.string.brain_rpg_recover_action)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        modifier = Modifier.testTag(SKILL_TREE_DETAIL_DIALOG_TAG)
    )
}
