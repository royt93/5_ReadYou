package com.mckimquyen.reader.ui.component.rpg

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.ui.ext.findActivity

@Composable
fun BrainQuizCard(
    quiz: QuizQuestion,
    onAnswerSubmitted: (isCorrect: Boolean) -> Unit,
    onDoubleXpRequested: (activity: Activity, onDone: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var selectedIndex by remember(quiz.articleId) { mutableStateOf<Int?>(null) }
    var isDoubled by remember(quiz.articleId) { mutableStateOf(false) }

    val isAnswered = selectedIndex != null
    val isCorrect = selectedIndex == quiz.correctAnswerIndex

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.brain_rpg_quiz_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = quiz.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = if (isDoubled) "+300 XP" else stringResource(R.string.brain_rpg_quiz_reward),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Question
            Text(
                text = quiz.question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Options
            quiz.options.forEachIndexed { index, option ->
                val optionBorderColor: Color
                val optionBgColor: Color
                val optionTextColor: Color

                when {
                    !isAnswered -> {
                        optionBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        optionBgColor = MaterialTheme.colorScheme.surface
                        optionTextColor = MaterialTheme.colorScheme.onSurface
                    }
                    index == quiz.correctAnswerIndex -> {
                        optionBorderColor = MaterialTheme.colorScheme.primary
                        optionBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        optionTextColor = MaterialTheme.colorScheme.primary
                    }
                    index == selectedIndex -> {
                        optionBorderColor = MaterialTheme.colorScheme.error
                        optionBgColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        optionTextColor = MaterialTheme.colorScheme.error
                    }
                    else -> {
                        optionBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        optionBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        optionTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isAnswered) {
                            selectedIndex = index
                            val correct = (index == quiz.correctAnswerIndex)
                            onAnswerSubmitted(correct)
                        },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, optionBorderColor),
                    color = optionBgColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isAnswered && index == quiz.correctAnswerIndex) FontWeight.Bold else FontWeight.Normal,
                            color = optionTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (isAnswered) {
                            if (index == quiz.correctAnswerIndex) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (index == selectedIndex) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Post-answer Feedback & Rewarded Actions
            AnimatedVisibility(
                visible = isAnswered,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    if (isCorrect) {
                        Text(
                            text = stringResource(R.string.brain_rpg_correct),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = quiz.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!isDoubled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val act = activity ?: return@Button
                                    onDoubleXpRequested(act) { success ->
                                        if (success) {
                                            isDoubled = true
                                            Toast.makeText(context, "+300 XP! 🎉", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.brain_rpg_double_xp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.brain_rpg_wrong),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = quiz.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val act = activity ?: return@OutlinedButton
                                onDoubleXpRequested(act) { success ->
                                    if (success) {
                                        selectedIndex = null // Reset to try again!
                                        Toast.makeText(context, context.getString(R.string.retry), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.brain_rpg_retry_quiz),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
