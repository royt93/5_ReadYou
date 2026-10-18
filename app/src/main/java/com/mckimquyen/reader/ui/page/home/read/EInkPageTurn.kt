package com.mckimquyen.reader.ui.page.home.read

import android.view.KeyEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.sqrt

private const val OVERLAP_DIVISOR = 10

enum class TapZone { PREVIOUS, CENTER, NEXT }
enum class PageDirection { PREVIOUS, NEXT }
data class PageItem(val index: Int, val offset: Int, val size: Int)
data class PageTarget(val index: Int, val offset: Int)

fun resolveTapZone(x: Float, width: Float): TapZone {
    if (!x.isFinite() || !width.isFinite() || width <= 0f || x < 0f || x > width) return TapZone.CENTER
    return when {
        x < width / 3f -> TapZone.PREVIOUS
        x > width * 2f / 3f -> TapZone.NEXT
        else -> TapZone.CENTER
    }
}

fun TapZone.toDirection(): PageDirection? = when (this) {
    TapZone.PREVIOUS -> PageDirection.PREVIOUS
    TapZone.CENTER -> null
    TapZone.NEXT -> PageDirection.NEXT
}

fun resolvePageTurn(
    direction: PageDirection,
    visible: List<PageItem>,
    totalCount: Int,
    viewportHeight: Int,
): PageTarget? {
    if (visible.isEmpty() || totalCount <= 0 || viewportHeight <= 0) return null
    if (visible.any { it.index !in 0 until totalCount || it.size <= 0 }) return null
    val first = visible.minByOrNull { it.index } ?: return null
    val last = visible.maxByOrNull { it.index } ?: return null
    val lastIndex = totalCount - 1
    if (direction == PageDirection.NEXT && last.index >= lastIndex) return null

    val overlap = (viewportHeight / OVERLAP_DIVISOR).coerceAtLeast(1)
    return when (direction) {
        PageDirection.NEXT -> {
            if (visible.size == 1 && first.size > viewportHeight) {
                val targetOffset = -first.offset + viewportHeight - overlap
                if (targetOffset + viewportHeight >= first.size && first.index < lastIndex) {
                    PageTarget(first.index + 1, 0)
                } else {
                    PageTarget(first.index, targetOffset.coerceAtLeast(0))
                }
            } else {
                PageTarget(last.index.coerceAtMost(lastIndex), 0)
            }
        }
        PageDirection.PREVIOUS -> {
            if (first.size > viewportHeight && visible.size == 1) {
                val currentOffset = -first.offset
                val targetOffset = (currentOffset - (viewportHeight - overlap)).coerceAtLeast(0)
                if (targetOffset == currentOffset) null else PageTarget(first.index, targetOffset)
            } else {
                val advance = (visible.size - 1).coerceAtLeast(1)
                val target = (first.index - advance).coerceAtLeast(0)
                if (target == first.index && first.offset >= 0) null else PageTarget(target, 0)
            }
        }
    }
}

fun resolveVolumeKeyTurn(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    eInk: Boolean,
    ttsPlaying: Boolean,
    readerActive: Boolean,
): PageDirection? {
    if (!eInk || ttsPlaying || !readerActive || action != KeyEvent.ACTION_DOWN || repeatCount != 0) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> PageDirection.PREVIOUS
        KeyEvent.KEYCODE_VOLUME_UP -> PageDirection.NEXT
        else -> null
    }
}

/**
 * Routes hardware volume keys from [com.mckimquyen.reader.infrastructure.android.MainActivity] to
 * the Reader while it is composed with E-Ink mode on. Main-thread only. At most one handler is
 * live; the Reader unregisters on dispose, and a stale unregister never removes a newer handler.
 */
object ReaderVolumeKeyBridge {
    private var handler: ((keyCode: Int, action: Int, repeatCount: Int) -> Boolean)? = null

    fun register(handler: (keyCode: Int, action: Int, repeatCount: Int) -> Boolean) {
        this.handler = handler
    }

    fun unregister(handler: (keyCode: Int, action: Int, repeatCount: Int) -> Boolean) {
        if (this.handler === handler) this.handler = null
    }

    /** @return true when the key was consumed and must not reach the system volume handler. */
    fun dispatch(keyCode: Int, action: Int, repeatCount: Int): Boolean =
        handler?.invoke(keyCode, action, repeatCount) ?: false

    internal fun clearForTest() {
        handler = null
    }
}

/**
 * Claims hardware volume keys for page turning while [enabled] and TTS is not playing. The handler
 * is registered only for this composition's lifetime, so leaving the Reader, turning E-Ink off or
 * recreating the Activity always hands the keys back to the system.
 */
@Composable
internal fun ReaderVolumeKeys(
    enabled: Boolean,
    ttsPlaying: Boolean,
    onTurn: (PageDirection) -> Unit,
) {
    val currentTtsPlaying by rememberUpdatedState(ttsPlaying)
    val currentOnTurn by rememberUpdatedState(onTurn)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val handler: (Int, Int, Int) -> Boolean = { keyCode, action, repeatCount ->
            val direction = resolveVolumeKeyTurn(
                keyCode = keyCode,
                action = action,
                repeatCount = repeatCount,
                eInk = true,
                ttsPlaying = currentTtsPlaying,
                readerActive = true,
            )
            direction?.let(currentOnTurn)
            direction != null
        }
        ReaderVolumeKeyBridge.register(handler)
        onDispose { ReaderVolumeKeyBridge.unregister(handler) }
    }
}

/**
 * Intercepts only quick single-finger taps that land in the left/right third; everything else
 * (center taps, drags, long-press selection, multi-touch) is left untouched for child handlers.
 */
internal fun Modifier.eInkTapZones(
    enabled: Boolean,
    key: Any?,
    onTurn: (PageDirection) -> Unit,
): Modifier = if (!enabled) this else pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val downTime = down.uptimeMillis
        val slop = viewConfiguration.touchSlop
        val deadline = downTime + viewConfiguration.longPressTimeoutMillis
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size != 1) return@awaitEachGesture
            val change = event.changes.first()
            if (change.isConsumed || change.uptimeMillis > deadline) return@awaitEachGesture
            val delta = change.position - down.position
            if (sqrt(delta.x * delta.x + delta.y * delta.y) > slop) return@awaitEachGesture
            if (!change.pressed) {
                val direction = resolveTapZone(down.position.x, size.width.toFloat()).toDirection()
                    ?: return@awaitEachGesture
                change.consume()
                onTurn(direction)
                return@awaitEachGesture
            }
        }
    }
}

internal suspend fun LazyListState.turnPage(direction: PageDirection) {
    val info = layoutInfo
    val target = resolvePageTurn(
        direction = direction,
        visible = info.visibleItemsInfo.map { PageItem(it.index, it.offset, it.size) },
        totalCount = info.totalItemsCount,
        viewportHeight = info.viewportEndOffset - info.viewportStartOffset,
    ) ?: return
    scrollToItem(target.index, target.offset)
}
