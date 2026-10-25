package com.mckimquyen.reader.ui.page.triage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TriageCardStack(
    currentArticle: ArticleListItemWithFeed?,
    nextArticle: ArticleListItemWithFeed?,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Next card (underneath)
        if (nextArticle != null) {
            TriageCard(
                item = nextArticle,
                modifier = Modifier
                    .scale(0.95f)
                    .alpha(0.7f),
            )
        }

        // Active card (on top)
        if (currentArticle != null) {
            val scope = rememberCoroutineScope()
            val offsetX = remember(currentArticle.article.id) { Animatable(0f) }
            val offsetY = remember(currentArticle.article.id) { Animatable(0f) }
            val density = LocalDensity.current
            val swipeThresholdPx = with(density) { 100.dp.toPx() }

            val rotationDegrees = (offsetX.value / 300f).coerceIn(-20f, 20f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .rotate(rotationDegrees)
                    .pointerInput(currentArticle.article.id) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value < -swipeThresholdPx -> {
                                            offsetX.animateTo(-1200f, tween(200))
                                            onSwipeLeft()
                                        }
                                        offsetX.value > swipeThresholdPx -> {
                                            offsetX.animateTo(1200f, tween(200))
                                            onSwipeRight()
                                        }
                                        offsetY.value < -swipeThresholdPx -> {
                                            offsetY.animateTo(-1400f, tween(200))
                                            onSwipeUp()
                                        }
                                        else -> {
                                            // Snap back
                                            launch { offsetX.animateTo(0f, tween(250)) }
                                            launch { offsetY.animateTo(0f, tween(250)) }
                                        }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo(offsetY.value + dragAmount.y)
                                }
                            },
                        )
                    },
            ) {
                TriageCard(item = currentArticle)

                // Visual Indicator Badges when dragging
                if (offsetX.value < -40f) {
                    // Skip Badge (Top-Right)
                    val badgeAlpha = (-offsetX.value / swipeThresholdPx).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .alpha(badgeAlpha)
                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.triage_swipe_left_skip),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (offsetX.value > 40f) {
                    // Save Badge (Top-Left)
                    val badgeAlpha = (offsetX.value / swipeThresholdPx).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(24.dp)
                            .alpha(badgeAlpha)
                            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.triage_swipe_right_save),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (offsetY.value < -40f) {
                    // Read Now Badge (Bottom-Center)
                    val badgeAlpha = (-offsetY.value / swipeThresholdPx).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                            .alpha(badgeAlpha)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.triage_swipe_up_read),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
