package com.mckimquyen.reader.ui.page.home.read.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

private val ACTION_CORNER = 16.dp
private val ACTION_MARGIN_H = 12.dp
private val ACTION_MARGIN_V = 6.dp

@Composable
fun PodcastEpisodeAction(title: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val playTitle = stringResource(R.string.podcast_play_episode, title)
    Surface(
        modifier = modifier.padding(horizontal = ACTION_MARGIN_H, vertical = ACTION_MARGIN_V).fillMaxWidth(),
        shape = RoundedCornerShape(ACTION_CORNER),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onPlay, modifier = Modifier.semantics { contentDescription = playTitle }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            }
        }
    }
}
