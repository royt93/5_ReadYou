package com.mckimquyen.reader.ui.page.home.read.bounty

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import java.text.DateFormat
import java.util.Date

const val BOUNTY_RADAR_TAG = "bounty_radar"
const val BOUNTY_RESULT_LIST_TAG = "bounty_result_list"
const val BOUNTY_ARTICLE_TAG_PREFIX = "bounty_article_"
const val BOUNTY_AI_BUTTON_TAG = "bounty_ai_button"
const val BOUNTY_CLOSE_TAG = "bounty_close"

private const val RADAR_SWEEP_MILLIS = 1800
private const val RADAR_RING_COUNT = 3
private const val RADAR_ROTATION_DEGREES = 360f
private const val RADAR_MAX_RESULT_HEIGHT_DP = 360
private const val RADAR_SIZE_DP = 160

/**
 * Nội dung sheet "Agent điều tra" (BOUNTY-01): radar khi đang quét kho, rồi danh sách bài liên quan trong kho Room.
 * Không biết gì về ViewModel/điều hướng: nhận state và callback để dễ test.
 */
@Composable
fun BountyInvestigationSheetContent(
    state: BountyInvestigationState,
    onOpenArticle: (String) -> Unit,
    onRequestAiContext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "🕵️ " + stringResource(R.string.bounty_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onClose, modifier = Modifier.testTag(BOUNTY_CLOSE_TAG)) {
                Text(stringResource(R.string.close))
            }
        }
        Spacer(Modifier.height(8.dp))

        when (state.phase) {
            BountyInvestigationPhase.SCANNING -> ScanningContent(state.query)
            BountyInvestigationPhase.ERROR -> Text(
                text = stringResource(R.string.bounty_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            BountyInvestigationPhase.RESULTS -> ResultsContent(state, onOpenArticle, onRequestAiContext)
        }
    }
}

@Composable
private fun ScanningContent(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RadarCanvas()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.bounty_scanning_status, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Radar tự vẽ bằng Canvas: vài vòng đồng tâm mờ dần và một tia quét xoay vòng. Không dùng thư viện animation ngoài. */
@Composable
private fun RadarCanvas() {
    val transition = rememberInfiniteTransition(label = "bounty_radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = RADAR_ROTATION_DEGREES,
        animationSpec = infiniteRepeatable(tween(RADAR_SWEEP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "bounty_radar_sweep",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RADAR_SWEEP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "bounty_radar_ripple",
    )
    val primary = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.bounty_radar_description)

    Canvas(
        modifier = Modifier
            .size(RADAR_SIZE_DP.dp)
            .semantics { contentDescription = description }
            .testTag(BOUNTY_RADAR_TAG),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        repeat(RADAR_RING_COUNT) { index ->
            val phase = (ripple + index.toFloat() / RADAR_RING_COUNT) % 1f
            drawCircle(
                color = primary.copy(alpha = (1f - phase) * 0.35f),
                radius = maxRadius * phase,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawCircle(color = primary.copy(alpha = 0.15f), radius = maxRadius, center = center, style = Stroke(width = 1.dp.toPx()))
        rotateSweep(sweep, center, maxRadius, primary)
    }
}

private fun DrawScope.rotateSweep(
    degrees: Float,
    center: Offset,
    radius: Float,
    color: Color,
) {
    rotate(degrees = degrees, pivot = center) {
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.55f)),
                center = center,
            ),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun ResultsContent(
    state: BountyInvestigationState,
    onOpenArticle: (String) -> Unit,
    onRequestAiContext: () -> Unit,
) {
    val result = state.result ?: return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = state.query,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.bounty_stats_summary, result.articles.size, result.sourceCount),
        style = MaterialTheme.typography.bodyMedium,
    )
    val earliest = result.earliestDate
    val latest = result.latestDate
    if (earliest != null && latest != null) {
        val context = LocalContext.current
        val format = DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0])
        Text(
            text = stringResource(R.string.bounty_time_range, format.format(earliest), format.format(latest)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))

    if (result.articles.isEmpty()) {
        Text(
            text = stringResource(R.string.bounty_empty_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = RADAR_MAX_RESULT_HEIGHT_DP.dp)
                .testTag(BOUNTY_RESULT_LIST_TAG),
        ) {
            items(result.articles, key = { it.article.id }) { item ->
                RelatedArticleRow(item, state.query, onOpenArticle)
            }
        }
    }

    if (state.canUseAiContext) {
        Spacer(Modifier.height(12.dp))
        AiContextSection(state.ai, onRequestAiContext)
    }
}

@Composable
private fun RelatedArticleRow(item: ArticleListItemWithFeed, query: String, onOpenArticle: (String) -> Unit) {
    val context = LocalContext.current
    val format = DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0])
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenArticle(item.article.id) }
            .padding(vertical = 8.dp)
            .testTag(BOUNTY_ARTICLE_TAG_PREFIX + item.article.id),
    ) {
        Text(
            text = highlight(item.article.title, query, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.feed.name} • ${format.format(Date(item.article.date.time))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiContextSection(ai: BountyAiState, onRequestAiContext: () -> Unit) {
    when (ai) {
        BountyAiState.Idle -> OutlinedButton(
            onClick = onRequestAiContext,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BOUNTY_AI_BUTTON_TAG),
        ) { Text("✨ " + stringResource(R.string.bounty_ai_context_action)) }

        BountyAiState.Loading -> Text(
            text = stringResource(R.string.bounty_ai_context_loading),
            style = MaterialTheme.typography.bodyMedium,
        )

        is BountyAiState.Ready -> Column {
            Text(
                text = stringResource(R.string.bounty_ai_context_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = ai.text, style = MaterialTheme.typography.bodyMedium)
        }

        is BountyAiState.Failed -> Column {
            Text(
                text = when (ai.reason) {
                    BountyAiFailure.NO_KEY -> stringResource(R.string.bounty_ai_context_no_key)
                    else -> stringResource(R.string.bounty_error)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ai.reason != BountyAiFailure.NO_KEY) {
                Button(onClick = onRequestAiContext, modifier = Modifier.testTag(BOUNTY_AI_BUTTON_TAG)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

/** Tô nổi mọi lần xuất hiện của [query] (không phân biệt hoa thường) trong [text]. */
internal fun highlight(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (cursor < text.length) {
        val hit = text.indexOf(query, cursor, ignoreCase = true)
        if (hit < 0) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, hit))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
            append(text.substring(hit, hit + query.length))
        }
        cursor = hit + query.length
    }
}
