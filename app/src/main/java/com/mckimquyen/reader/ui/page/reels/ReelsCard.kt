package com.mckimquyen.reader.ui.page.reels

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Precision
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.component.base.BaseAsyncImage

/**
 * One full-screen reel: cover image behind a dark gradient, with the headline, source, time and
 * offline summary bullets stacked at the bottom.
 */
@Composable
fun ReelsCard(
    item: ReelItem,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (item.coverImageUrl != null) {
            BaseAsyncImage(
                modifier = Modifier.fillMaxSize(),
                data = item.coverImageUrl,
                contentScale = ContentScale.Crop,
                precision = Precision.INEXACT,
                contentDescription = item.title,
                enableClipping = false,
                placeholder = null,
                error = null,
            )
        } else {
            // No cover art: a flat surface tint keeps the card readable instead of leaving a hole.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // The scrim guarantees white-on-image contrast whatever the photo looks like.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = SCRIM_ALPHA_TOP),
                            Color.Black.copy(alpha = SCRIM_ALPHA_MIDDLE),
                            Color.Black.copy(alpha = SCRIM_ALPHA_BOTTOM),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                // Edge-to-edge window: without this inset the text slides under the nav bar.
                .navigationBarsPadding()
                .padding(horizontal = CONTENT_PADDING.dp)
                .padding(bottom = BOTTOM_PADDING.dp),
            verticalArrangement = Arrangement.spacedBy(BLOCK_SPACING.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.feedName,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = SECONDARY_TEXT_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " · ${relativeTimeOf(item.publishedAtMillis)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = SECONDARY_TEXT_ALPHA),
                    maxLines = 1,
                )
                if (item.hasVideo) {
                    Spacer(modifier = Modifier.width(BLOCK_SPACING.dp))
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = stringResource(R.string.reels_has_video),
                        tint = Color.White.copy(alpha = SECONDARY_TEXT_ALPHA),
                        modifier = Modifier.size(VIDEO_ICON_SIZE.dp),
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )

            item.bullets.forEach { bullet ->
                Row {
                    Text(
                        text = "•  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = SECONDARY_TEXT_ALPHA),
                    )
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = SECONDARY_TEXT_ALPHA),
                        maxLines = BULLET_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(BLOCK_SPACING.dp))

            Text(
                text = stringResource(R.string.reels_swipe_hint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = HINT_TEXT_ALPHA),
            )
        }
    }
}

/** Human-readable "3 hours ago", localized by the platform. */
private fun relativeTimeOf(millis: Long): CharSequence = DateUtils.getRelativeTimeSpanString(
    millis,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
)

private const val SCRIM_ALPHA_TOP = 0.35f
private const val SCRIM_ALPHA_MIDDLE = 0.55f
private const val SCRIM_ALPHA_BOTTOM = 0.88f
private const val SECONDARY_TEXT_ALPHA = 0.85f
private const val HINT_TEXT_ALPHA = 0.6f
private const val CONTENT_PADDING = 24
private const val BOTTOM_PADDING = 48
private const val BLOCK_SPACING = 8
private const val VIDEO_ICON_SIZE = 18
private const val TITLE_MAX_LINES = 4
private const val BULLET_MAX_LINES = 3
