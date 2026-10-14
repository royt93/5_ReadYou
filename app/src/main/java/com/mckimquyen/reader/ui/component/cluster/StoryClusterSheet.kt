package com.mckimquyen.reader.ui.component.cluster

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.cluster.SourceDnaAnalyzer
import com.mckimquyen.reader.domain.model.cluster.SourceDnaNode
import com.mckimquyen.reader.domain.model.cluster.SourceDnaRelation
import com.mckimquyen.reader.domain.model.cluster.SourceDnaResult
import com.mckimquyen.reader.ui.component.FeedIcon
import com.mckimquyen.reader.ui.ext.DialogEdgeToEdge
import java.text.DateFormat
import java.util.Date

/**
 * Dialog hiển thị toàn cảnh đa chiều (Multi-Perspective View)
 * của một sự kiện được AI gom cụm từ nhiều nguồn báo khác nhau.
 */
@Composable
fun StoryClusterSheet(
    cluster: StoryCluster?,
    onDismissRequest: () -> Unit,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onMarkAllRead: (StoryCluster) -> Unit = {},
    calculateSimilarity: (ArticleWithFeed, ArticleWithFeed) -> Float = { _, _ -> 0f },
) {
    if (cluster == null) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        )
    ) {
        DialogEdgeToEdge()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Scrim chạm ngoài để đóng
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismissRequest() }
            )

            // Sheet Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* absorb click */ },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                StoryClusterSheetContent(
                    cluster = cluster,
                    onArticleClick = onArticleClick,
                    onMarkAllRead = onMarkAllRead,
                    onClose = onDismissRequest,
                    calculateSimilarity = calculateSimilarity,
                )
            }
        }
    }
}

@Composable
fun StoryClusterSheetContent(
    cluster: StoryCluster,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onMarkAllRead: (StoryCluster) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    calculateSimilarity: (ArticleWithFeed, ArticleWithFeed) -> Float = { _, _ -> 0f },
) {
    var sourceDnaResult by remember(cluster.id) { mutableStateOf<SourceDnaResult?>(null) }
    var sourceDnaFailed by remember(cluster.id) { mutableStateOf(false) }
    val canOpenSourceDna = cluster.articles.map { it.feed.id }.distinct().size >= SOURCE_DNA_MIN_SOURCES
    Column(
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(36.dp, 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ) {}
        }

        // Top Bar: Icon + Tiêu đề Sheet + Nút đóng
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.story_cluster_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (cluster.isMultiSource) {
                                stringResource(R.string.story_cluster_badge, cluster.sourceCount)
                            } else {
                                stringResource(R.string.story_cluster_badge_single, cluster.articleCount)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (cluster.similarityScore > 0f) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.story_cluster_similarity_badge,
                                        (cluster.similarityScore * 100).toInt()
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tiêu đề sự kiện + Keyword chips
        Text(
            text = cluster.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (cluster.keywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cluster.keywords.forEach { kw ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "#$kw",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(8.dp))

        val dna = sourceDnaResult
        if (dna == null) {
            // Danh sách góc nhìn từ các nguồn báo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.story_cluster_all_perspectives, cluster.articles.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                )
                if (canOpenSourceDna) {
                    TextButton(
                        onClick = {
                            sourceDnaFailed = false
                            sourceDnaResult = runCatching {
                                SourceDnaAnalyzer.analyze(cluster.articles, calculateSimilarity)
                            }.onFailure { sourceDnaFailed = true }.getOrNull()
                        },
                    ) {
                        Text(text = stringResource(R.string.source_dna_action))
                    }
                }
            }
            if (sourceDnaFailed) {
                Text(
                    text = stringResource(R.string.source_dna_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(cluster.articles, key = { it.article.id }) { articleWithFeed ->
                    val isLead = articleWithFeed.article.id == cluster.leadArticle.article.id
                    PerspectiveArticleCard(
                        articleWithFeed = articleWithFeed,
                        isLead = isLead,
                        onClick = {
                            onClose()
                            onArticleClick(articleWithFeed)
                        },
                    )
                }
            }
        } else {
            SourceDnaGraph(
                result = dna,
                onBack = { sourceDnaResult = null },
                onArticleClick = {
                    onClose()
                    onArticleClick(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 380.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { onMarkAllRead(cluster) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.story_cluster_mark_all_read),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Button(
                onClick = {
                    onClose()
                    onArticleClick(cluster.leadArticle)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.story_cluster_read_main),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun PerspectiveArticleCard(
    articleWithFeed: ArticleWithFeed,
    isLead: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isLead) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (isLead) 1.5.dp else 1.dp,
            color = if (isLead) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // Source row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    FeedIcon(
                        feedName = articleWithFeed.feed.name,
                        iconUrl = articleWithFeed.feed.icon,
                        size = 16.dp,
                    )
                    Text(
                        text = articleWithFeed.feed.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isLead) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = stringResource(R.string.story_cluster_lead_badge),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Article title
            Text(
                text = articleWithFeed.article.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Short Description
            if (articleWithFeed.article.shortDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = articleWithFeed.article.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val SOURCE_DNA_MIN_SOURCES = 3
private const val SOURCE_DNA_PERCENT_SCALE = 100
private val SOURCE_DNA_BRANCH_INDENT = 14.dp

@Composable
private fun SourceDnaGraph(
    result: SourceDnaResult,
    onBack: () -> Unit,
    onArticleClick: (ArticleWithFeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownSource = stringResource(R.string.source_dna_unknown_source)
    val articleById = result.nodes.associateBy { it.article.article.id }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        item {
            Column {
                TextButton(onClick = onBack) { Text(text = stringResource(R.string.source_dna_back)) }
                Text(
                    text = stringResource(R.string.source_dna_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.source_dna_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.isEarliestAmbiguous || !result.hasAnySimilarityEdge) {
                    Text(
                        text = stringResource(R.string.source_dna_uncertain),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (result.wasTruncated) {
                    Text(
                        text = stringResource(R.string.source_dna_showing_earliest_limit, result.analyzedArticleCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(result.nodes, key = { it.article.article.id }) { node ->
            SourceDnaNodeCard(
                node = node,
                parent = node.parentArticleId?.let(articleById::get),
                unknownSource = unknownSource,
                onClick = { onArticleClick(node.article) },
            )
        }
    }
}

@Composable
private fun SourceDnaNodeCard(
    node: SourceDnaNode,
    parent: SourceDnaNode?,
    unknownSource: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val sourceLabel = node.sourceLabel ?: unknownSource
    val feedReportedTime = remember(node.article.article.date, context.resources.configuration.locales) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(node.article.article.date.time))
    }
    val parentLabel = parent?.sourceLabel ?: unknownSource
    val label = when (node.relation) {
        SourceDnaRelation.EARLIEST_IN_CLUSTER -> stringResource(R.string.source_dna_earliest_posted)
        SourceDnaRelation.HIGH_SIMILARITY -> stringResource(R.string.source_dna_high_similarity_to, parentLabel)
        SourceDnaRelation.NO_CLEAR_RELATIONSHIP -> stringResource(R.string.source_dna_no_clear_relationship)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (node.parentArticleId == null) 0.dp else SOURCE_DNA_BRANCH_INDENT)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.source_dna_feed_reported_time, feedReportedTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = node.article.article.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            node.similarityToParent?.let { similarity ->
                Text(
                    text = stringResource(R.string.source_dna_similarity, (similarity * SOURCE_DNA_PERCENT_SCALE).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
