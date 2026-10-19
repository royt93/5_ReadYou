package com.mckimquyen.reader.ui.component.quote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Gói TextToolbar nền tảng. Toolbar Android không cho thêm mục tuỳ biến (đã kiểm bằng javap
 * AndroidTextToolbar), nên lớp này giữ nguyên menu gốc và phơi ra trạng thái để chip
 * "Chia sẻ trích dẫn" ([QuoteShareChip]) hiện cạnh menu.
 */
class QuoteTextToolbar(
    private val delegate: TextToolbar,
    private val onShareQuote: () -> Unit,
) : TextToolbar {

    private var copyRequested: (() -> Unit)? = null

    /** Rect của lần showMenu gần nhất, null khi menu đã ẩn. */
    var anchorRect: Rect? by mutableStateOf(null)
        private set

    /** true khi đang có đoạn chọn (có onCopyRequested) và có thể chia sẻ. */
    var shareAvailable: Boolean by mutableStateOf(false)
        private set

    override val status: TextToolbarStatus get() = delegate.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        copyRequested = onCopyRequested
        anchorRect = rect
        shareAvailable = onCopyRequested != null
        delegate.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() {
        copyRequested = null
        anchorRect = null
        shareAvailable = false
        delegate.hide()
    }

    /** Gọi copy đã lưu (đưa đoạn chọn vào clipboard) rồi [onShareQuote]; no-op khi chưa có đoạn chọn. */
    fun triggerShare() {
        val copy = copyRequested ?: return
        copy()
        onShareQuote()
    }
}
