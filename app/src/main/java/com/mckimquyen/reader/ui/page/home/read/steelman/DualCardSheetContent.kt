package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.quote.DualCardRenderer

const val DUAL_SHEET_PREVIEW_TAG = "dual_sheet_preview"
const val DUAL_SHEET_CUT_NOTE_TAG = "dual_sheet_cut_note"
const val DUAL_SHEET_SHARE_TAG = "dual_sheet_share"
const val DUAL_SHEET_ERROR_TAG = "dual_sheet_error"
const val DUAL_SHEET_RETRY_TAG = "dual_sheet_retry"

private val PREVIEW_MAX_WIDTH = 240.dp
private const val PREVIEW_MAX_SCREEN_HEIGHT_FRACTION = 0.45f
private val PREVIEW_CORNER = 12.dp
private val SECTION_GAP = 12.dp
private val SHARE_HEIGHT = 48.dp
private const val CARD_ASPECT = DualCardRenderer.CARD_WIDTH.toFloat() / DualCardRenderer.CARD_HEIGHT

@Composable
fun DualCardSheetContent(
    state: DualCardUiState,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewMaxHeight = (LocalConfiguration.current.screenHeightDp * PREVIEW_MAX_SCREEN_HEIGHT_FRACTION).dp
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        Text(
            text = stringResource(R.string.echo_dual_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        when (state) {
            is DualCardUiState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .heightIn(max = previewMaxHeight)
                    .widthIn(max = PREVIEW_MAX_WIDTH)
                    .aspectRatio(CARD_ASPECT)
                    .clip(RoundedCornerShape(PREVIEW_CORNER))
                    .testTag(DUAL_SHEET_PREVIEW_TAG),
            )
            is DualCardUiState.Error -> Column(
                modifier = Modifier.testTag(DUAL_SHEET_ERROR_TAG),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            ) {
                Text(stringResource(state.messageRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry, modifier = Modifier.testTag(DUAL_SHEET_RETRY_TAG)) { Text(stringResource(R.string.retry)) }
            }
            else -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        if ((state as? DualCardUiState.Ready)?.wasCut == true) {
            Text(
                text = stringResource(R.string.echo_dual_cut_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(DUAL_SHEET_CUT_NOTE_TAG),
            )
        }
        Button(
            onClick = onShare,
            enabled = state is DualCardUiState.Ready,
            modifier = Modifier.fillMaxWidth().height(SHARE_HEIGHT).padding(horizontal = 8.dp).testTag(DUAL_SHEET_SHARE_TAG),
        ) { Text(stringResource(R.string.echo_dual_share)) }
    }
}
