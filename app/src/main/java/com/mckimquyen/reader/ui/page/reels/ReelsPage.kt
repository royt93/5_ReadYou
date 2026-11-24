package com.mckimquyen.reader.ui.page.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.android.AppLog
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.page.common.RouteName
import kotlin.math.absoluteValue

/**
 * Full-screen vertical news reels.
 *
 * Swipe up/down to move between stories (each card tilts away in 3D as it leaves), swipe right to
 * open the full article.
 */
@Composable
fun ReelsPage(
    navController: NavHostController,
    reelsViewModel: ReelsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState = reelsViewModel.uiState.collectAsStateValue()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            uiState.isEmpty -> {
                Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(EMPTY_PADDING.dp),
                    text = stringResource(R.string.reels_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }

            else -> {
                ReelsPager(
                    items = uiState.items,
                    onOpenArticle = { articleId ->
                        navController.navigate("${RouteName.READING}/$articleId")
                    },
                )
            }
        }

        // The inset goes on a wrapper, not on FeedbackIconButton: that component forwards its
        // modifier to the inner Icon, so positioning it directly leaves the actual touch target at
        // 0,0 — under the status bar of this edge-to-edge window, where every tap is swallowed.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
        ) {
            FeedbackIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.White,
            ) {
                navController.popBackStack()
            }
        }
    }
}

/** Height of the left-edge band, in dp, that is withheld from the system Back gesture. Android honours at most 200dp per edge. */
internal const val LEFT_EDGE_EXCLUSION_HEIGHT = 200
internal const val LEFT_EDGE_EXCLUSION_WIDTH = 32

@Composable
private fun Modifier.leftEdgeGestureExclusion(): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    val lastTop = androidx.compose.runtime.remember { intArrayOf(Int.MIN_VALUE) }
    // The exclusion lives on the one view of MainActivity. Without this the band stayed on every screen after Reels was closed and swallowed Back there.
    androidx.compose.runtime.DisposableEffect(view) {
        onDispose {
            view.systemGestureExclusionRects = emptyList()
            AppLog.event("reels", "edge-exclusion-cleared")
        }
    }
    return this.onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val h = with(density) { LEFT_EDGE_EXCLUSION_HEIGHT.dp.toPx() }
        val w = with(density) { LEFT_EDGE_EXCLUSION_WIDTH.dp.toPx() }
        val top = (bounds.center.y - h / 2f).toInt()
        view.systemGestureExclusionRects = listOf(android.graphics.Rect(0, top, w.toInt(), top + h.toInt()))
        // Layout runs on every frame of a scroll; log only when the band actually moves.
        if (top != lastTop[0]) {
            lastTop[0] = top
            AppLog.event("reels", "edge-exclusion", "topPx" to top, "widthPx" to w.toInt(), "heightPx" to h.toInt())
        }
    }
}

/** The pager itself, kept free of navigation so it can be rendered in tests. */
@Composable
fun ReelsPager(
    items: List<ReelItem>,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    // A fixed dp threshold means the same physical swipe distance on every screen density.
    val openThresholdPx = with(LocalDensity.current) { OPEN_ARTICLE_THRESHOLD.dp.toPx() }

    VerticalPager(
        state = pagerState,
        // The right-swipe must be able to start near the left edge, where the system Back gesture would otherwise take it. Android caps the excluded
        // height (200dp), so only a band in the vertical middle is excluded and Back still works from the rest of the edge.
        modifier = modifier.fillMaxSize().leftEdgeGestureExclusion(),
    ) { page ->
        val item = items[page]
        // How far this page is from resting position: 0 centred, ±1 fully off-screen.
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val distance = pageOffset.absoluteValue.coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Tilt away from the viewer and shrink slightly while scrolling past.
                    rotationX = pageOffset * MAX_ROTATION_DEGREES
                    val scale = MIN_SCALE + (1f - MIN_SCALE) * (1f - distance)
                    scaleX = scale
                    scaleY = scale
                    alpha = MIN_ALPHA + (1f - MIN_ALPHA) * (1f - distance)
                    cameraDistance = CAMERA_DISTANCE
                }
                .pointerInput(item.articleId, openThresholdPx) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag >= openThresholdPx) {
                                AppLog.event("reels", "open", "via" to "swipe", "article" to item.articleId, "dragPx" to totalDrag.toInt())
                                onOpenArticle(item.articleId)
                            } else {
                                AppLog.event("reels", "swipe-too-short", "dragPx" to totalDrag.toInt(), "needPx" to openThresholdPx.toInt())
                            }
                        },
                        onDragCancel = { totalDrag = 0f },
                    ) { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    }
                },
        ) {
            ReelsCard(
                item = item,
                onOpen = {
                    AppLog.event("reels", "open", "via" to "button", "article" to item.articleId)
                    onOpenArticle(item.articleId)
                },
            )
        }
    }
}

/** Rightward drag distance that counts as "open the article". */
private const val OPEN_ARTICLE_THRESHOLD = 80
private const val MAX_ROTATION_DEGREES = 18f
private const val MIN_SCALE = 0.85f
private const val MIN_ALPHA = 0.5f

/** Higher values flatten the perspective; the Compose default is too strong for a full-screen card. */
private const val CAMERA_DISTANCE = 16f
private const val EMPTY_PADDING = 32
