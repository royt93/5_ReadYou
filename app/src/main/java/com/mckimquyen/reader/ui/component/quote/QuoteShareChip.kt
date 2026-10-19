package com.mckimquyen.reader.ui.component.quote

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.mckimquyen.reader.R

const val QUOTE_SHARE_CHIP_TAG = "quote_share_chip"

private val CHIP_GAP = 8.dp

/**
 * Chip "Chia sẻ trích dẫn" hiện cạnh menu chọn chữ khi đang có đoạn chọn.
 * Vị trí lấy từ [QuoteTextToolbar.anchorRect] (toạ độ trong cửa sổ): đặt ngay dưới vùng chọn,
 * nếu không đủ chỗ thì đặt phía trên; luôn kẹp trong cửa sổ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteShareChip(toolbar: QuoteTextToolbar, modifier: Modifier = Modifier) {
    val anchor = toolbar.anchorRect
    if (anchor == null || !toolbar.shareAvailable || toolbar.status != TextToolbarStatus.Shown) return

    val gapPx = with(LocalDensity.current) { CHIP_GAP.roundToPx() }
    val provider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ): IntOffset = QuoteChipPlacement.place(
            anchorLeft = anchor.left.toInt(),
            anchorTop = anchor.top.toInt(),
            anchorBottom = anchor.bottom.toInt(),
            windowWidth = windowSize.width,
            windowHeight = windowSize.height,
            chipWidth = popupContentSize.width,
            chipHeight = popupContentSize.height,
            gap = gapPx,
        )
    }
    Popup(popupPositionProvider = provider) {
        AssistChip(
            onClick = {
                toolbar.triggerShare()
                toolbar.hide()
            },
            label = { Text(stringResource(R.string.quote_card_title)) },
            modifier = modifier
                .padding(CHIP_GAP)
                .testTag(QUOTE_SHARE_CHIP_TAG),
        )
    }
}

/** Tính vị trí chip, tách ra để test thuần JVM. */
object QuoteChipPlacement {
    fun place(
        anchorLeft: Int,
        anchorTop: Int,
        anchorBottom: Int,
        windowWidth: Int,
        windowHeight: Int,
        chipWidth: Int,
        chipHeight: Int,
        gap: Int,
    ): IntOffset {
        val x = anchorLeft.coerceIn(0, (windowWidth - chipWidth).coerceAtLeast(0))
        val below = anchorBottom + gap
        val above = anchorTop - gap - chipHeight
        val y = when {
            below + chipHeight <= windowHeight -> below
            above >= 0 -> above
            else -> (windowHeight - chipHeight).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
