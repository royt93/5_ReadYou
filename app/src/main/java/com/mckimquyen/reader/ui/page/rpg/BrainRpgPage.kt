package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedRenderModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.ext.findActivity

@Composable
fun BrainRpgPage(
    navController: NavController,
    activity: Activity,
    viewModel: BrainRpgViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val progress by viewModel.userProgress.collectAsState()

    LaunchedEffect(viewModel) { viewModel.refresh() }
    var recoveringCategoryId by remember { mutableStateOf<String?>(null) }

    BackHandler {
        navController.popBackStack()
    }

    BaseScaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            ) {
                navController.popBackStack()
            }
        },
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    DisplayText(
                        text = stringResource(R.string.brain_rpg_title),
                        desc = stringResource(R.string.brain_rpg_subtitle)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 1. Level & Title Card
                item {
                    LevelProgressCard(progress = progress)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. Streak & Shield Card
                item {
                    StreakShieldCard(
                        progress = progress,
                        onActivateShield = {
                            viewModel.activateStreakShield(activity) { success ->
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.brain_rpg_streak_shield_success), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.vip_rewarded_not_earned), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 3. Knowledge Tree (Categories)
                item {
                    KnowledgeSkillTreeCard(
                        progress = progress,
                        recoveringCategoryId = recoveringCategoryId,
                        onRecover = { categoryId ->
                            if (recoveringCategoryId == null) {
                                recoveringCategoryId = categoryId
                                viewModel.recoverCategory(activity, categoryId) { recovered ->
                                    recoveringCategoryId = null
                                    val message = if (recovered) R.string.brain_rpg_recover_success else R.string.brain_rpg_recover_unavailable
                                    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 4. Quiz Performance
                item {
                    QuizStatsCard(progress = progress)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 5. Weekly Brain Wrapped
                item {
                    val wrappedState by viewModel.weeklyWrappedState.collectAsState()
                    WeeklyWrappedCard(
                        progress = progress,
                        weeklyState = wrappedState,
                        onRetry = viewModel::refreshWeeklyWrapped,
                        onShare = {
                            val summary = wrappedState.summary ?: return@WeeklyWrappedCard
                            val zone = ZoneId.systemDefault()
                            val weekLabel = formatWrappedWeek(summary.weekStartMillis, summary.weekEndMillis, zone)
                            val topic = summary.topCategoryId?.let { context.getString(brainCategoryLabel(it)) }
                            val renderModel = BrainWrappedRenderModel(
                                weekLabel = context.getString(R.string.brain_wrapped_week_header, weekLabel),
                                header = context.getString(R.string.brain_wrapped_header),
                                weeklyXpLabel = context.getString(R.string.brain_wrapped_xp_label),
                                weeklyXp = summary.weeklyXp,
                                streakLabel = context.getString(R.string.brain_wrapped_streak_label),
                                streakValue = context.getString(R.string.brain_wrapped_current_streak, summary.currentStreakDays),
                                topicLabel = context.getString(R.string.brain_wrapped_topic_label),
                                topicValue = topic,
                                topicEmptyLabel = context.getString(R.string.brain_wrapped_topic_empty),
                                tagline = context.getString(R.string.brain_wrapped_tagline),
                                watermark = context.getString(R.string.read_you),
                            )
                            viewModel.shareWeeklyWrapped(
                                renderModel,
                                context.getString(R.string.brain_wrapped_share_title),
                                activity::startActivity,
                            )
                        },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
}

@Composable
internal fun LevelProgressCard(progress: UserProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.brain_rpg_level, progress.level),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = progress.levelTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = progress.progressFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${progress.totalXp} XP",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.brain_rpg_target_xp, progress.nextLevelTargetXp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun StreakShieldCard(
    progress: UserProgress,
    onActivateShield: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔥",
                    fontSize = 32.sp
                )
                Column {
                    Text(
                        text = stringResource(R.string.brain_rpg_streak, progress.streakDays),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (progress.streakShieldActive) {
                            stringResource(R.string.brain_rpg_streak_shield_active)
                        } else {
                            stringResource(R.string.brain_rpg_streak_shield)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (progress.streakShieldActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!progress.streakShieldActive) {
                FilledTonalButton(
                    onClick = onActivateShield,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.brain_rpg_streak_shield_activate),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.brain_rpg_streak_shield_protected),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun KnowledgeSkillTreeCard(
    progress: UserProgress,
    recoveringCategoryId: String? = null,
    onRecover: ((String) -> Unit)? = null,
) {
    val categories = listOf(
        QuizGeneratorService.CATEGORY_TECH to (Icons.Outlined.Memory to stringResource(R.string.brain_rpg_category_tech)),
        QuizGeneratorService.CATEGORY_BUSINESS to (Icons.Outlined.TrendingUp to stringResource(R.string.brain_rpg_category_business)),
        QuizGeneratorService.CATEGORY_SCIENCE to (Icons.Outlined.Science to stringResource(R.string.brain_rpg_category_science)),
        QuizGeneratorService.CATEGORY_HEALTH to (Icons.Outlined.FitnessCenter to stringResource(R.string.brain_rpg_category_health)),
        QuizGeneratorService.CATEGORY_PHILOSOPHY to (Icons.Outlined.MenuBook to stringResource(R.string.brain_rpg_category_philosophy)),
        QuizGeneratorService.CATEGORY_GENERAL to (Icons.Outlined.Lightbulb to stringResource(R.string.brain_rpg_category_general))
    )

    val maxXp = (progress.categoryXp.values.maxOrNull() ?: 100L).coerceAtLeast(100L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "🌱 " + stringResource(R.string.brain_rpg_skill_tree_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            categories.forEach { (key, iconAndLabel) ->
                val (icon, label) = iconAndLabel
                val catXp = progress.categoryXp[key] ?: 0L
                val fraction = (catXp.toFloat() / maxXp).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$catXp XP",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val baseXp = progress.categoryBaseXp[key] ?: catXp
                                if (baseXp > catXp && baseXp > 0L) {
                                    val retainedPercent = (catXp.toDouble() * PERCENT_SCALE / baseXp).toInt()
                                    Text(
                                        text = stringResource(R.string.brain_rpg_xp_retained, baseXp, retainedPercent),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        val decayedBase = progress.categoryBaseXp[key] ?: catXp
                        if (onRecover != null && decayedBase > catXp) {
                            val isPending = recoveringCategoryId == key
                            TextButton(
                                onClick = { onRecover(key) },
                                // One ad at a time: every recovery button is locked while one is pending.
                                enabled = recoveringCategoryId == null,
                            ) {
                                Text(
                                    text = if (isPending) stringResource(R.string.brain_rpg_recover_pending)
                                    else stringResource(R.string.brain_rpg_recover_action),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = fraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private const val PERCENT_SCALE = 100L

@Composable
internal fun QuizStatsCard(progress: UserProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "🎯 " + stringResource(R.string.brain_rpg_stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(title = stringResource(R.string.brain_rpg_stats_attempted), value = "${progress.quizzesAttempted}")
                StatItem(title = stringResource(R.string.brain_rpg_stats_passed), value = "${progress.quizzesPassed}")
                StatItem(title = stringResource(R.string.brain_rpg_stats_accuracy), value = "${progress.quizAccuracyPercent}%")
            }
        }
    }
}

@Composable
private fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun WeeklyWrappedCard(
    progress: UserProgress,
    onShare: () -> Unit,
    weeklyState: WeeklyWrappedUiState = WeeklyWrappedUiState(),
    onRetry: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.brain_rpg_wrapped_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val summary = weeklyState.summary
            Text(
                text = if ((summary?.weeklyXp ?: 0L) > 0L) {
                    stringResource(R.string.brain_rpg_wrapped_desc, summary?.weeklyXp ?: 0L, summary?.currentStreakDays ?: progress.streakDays)
                } else {
                    stringResource(R.string.brain_wrapped_no_week_xp)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            if (weeklyState.isLoading || weeklyState.isSharing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(WRAPPED_PROGRESS_TAG))
            }
            if (weeklyState.error != null) {
                Text(stringResource(R.string.brain_wrapped_share_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onShare,
                enabled = weeklyState.canShare,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.fillMaxWidth().testTag(WRAPPED_SHARE_BUTTON_TAG)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(if (weeklyState.isSharing) R.string.brain_wrapped_sharing else R.string.brain_wrapped_share_story),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


internal const val WRAPPED_SHARE_BUTTON_TAG = "wrapped_share_button"
internal const val WRAPPED_PROGRESS_TAG = "wrapped_progress"

private fun brainCategoryLabel(category: String): Int = when (category) {
    QuizGeneratorService.CATEGORY_TECH -> R.string.brain_rpg_category_tech
    QuizGeneratorService.CATEGORY_BUSINESS -> R.string.brain_rpg_category_business
    QuizGeneratorService.CATEGORY_SCIENCE -> R.string.brain_rpg_category_science
    QuizGeneratorService.CATEGORY_HEALTH -> R.string.brain_rpg_category_health
    QuizGeneratorService.CATEGORY_PHILOSOPHY -> R.string.brain_rpg_category_philosophy
    else -> R.string.brain_rpg_category_general
}

private fun formatWrappedWeek(startMillis: Long, endMillis: Long, zone: ZoneId): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate().minusDays(1)
    return "${formatter.format(start)} – ${formatter.format(end)}"
}
