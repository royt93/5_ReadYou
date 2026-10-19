package com.mckimquyen.reader.ui.page.home.read.quote

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import com.mckimquyen.reader.infrastructure.quote.QuoteCardRenderer

const val QUOTE_SHEET_PREVIEW_TAG = "quote_sheet_preview"
const val QUOTE_SHEET_CUT_NOTE_TAG = "quote_sheet_cut_note"
const val QUOTE_SHEET_SHARE_TAG = "quote_sheet_share"
const val QUOTE_SHEET_ERROR_TAG = "quote_sheet_error"
const val QUOTE_SHEET_TEMPLATE_TAG_PREFIX = "quote_sheet_template_"

private val PREVIEW_MAX_WIDTH = 280.dp
private const val PREVIEW_MAX_SCREEN_HEIGHT_FRACTION = 0.38f
private val PREVIEW_CORNER = 12.dp
private val SECTION_GAP = 12.dp
private const val CARD_ASPECT = QuoteCardRenderer.CARD_WIDTH.toFloat() / QuoteCardRenderer.CARD_HEIGHT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteCardSheetContent(
    state: QuoteCardUiState,
    selected: QuoteCardTemplate,
    onSelectTemplate: (QuoteCardTemplate) -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // BottomDrawer chỉ chừa 28dp ở đáy, nhỏ hơn thanh điều hướng 3 nút nên cần navigationBarsPadding() để nút
    // Chia sẻ không bị che. Preview giới hạn ~38% chiều cao màn hình và cột cuộn được khi màn hình quá thấp.
    val previewMaxHeight = (LocalConfiguration.current.screenHeightDp * PREVIEW_MAX_SCREEN_HEIGHT_FRACTION).dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        Text(
            text = stringResource(R.string.quote_card_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        when (state) {
            is QuoteCardUiState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .heightIn(max = previewMaxHeight)
                    .widthIn(max = PREVIEW_MAX_WIDTH)
                    .aspectRatio(CARD_ASPECT)
                    .clip(RoundedCornerShape(PREVIEW_CORNER))
                    .testTag(QUOTE_SHEET_PREVIEW_TAG),
            )

            is QuoteCardUiState.Error -> Column(
                modifier = Modifier.testTag(QUOTE_SHEET_ERROR_TAG),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            ) {
                Text(
                    text = stringResource(state.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }

            else -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuoteCardTemplate.entries.forEach { template ->
                FilterChip(
                    selected = template == selected,
                    onClick = { onSelectTemplate(template) },
                    label = { Text(stringResource(template.labelRes())) },
                    modifier = Modifier.testTag(QUOTE_SHEET_TEMPLATE_TAG_PREFIX + template.storageName),
                )
            }
        }

        if ((state as? QuoteCardUiState.Ready)?.wasCut == true) {
            Text(
                text = stringResource(R.string.quote_card_cut_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(QUOTE_SHEET_CUT_NOTE_TAG),
            )
        }

        Button(
            onClick = onShare,
            enabled = state is QuoteCardUiState.Ready,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp)
                .testTag(QUOTE_SHEET_SHARE_TAG),
        ) { Text(stringResource(R.string.quote_card_share)) }
    }
}

private fun QuoteCardTemplate.labelRes(): Int = when (this) {
    QuoteCardTemplate.MINIMALIST -> R.string.quote_card_template_minimalist
    QuoteCardTemplate.GRADIENT_MATERIAL -> R.string.quote_card_template_gradient
    QuoteCardTemplate.DARK_STUDIO -> R.string.quote_card_template_dark
}
