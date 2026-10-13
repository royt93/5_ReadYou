package com.mckimquyen.reader.ui.component.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

/** Where the article body sits inside the reader LazyColumn: first item index and item count. */
class BodyItemRange {
    @Volatile var start: Int = NO_BODY
    @Volatile var count: Int = 0

    fun clear() {
        start = NO_BODY
        count = 0
    }

    companion object {
        const val NO_BODY = -1
    }
}

/**
 * Counts the LazyColumn items added through it, so the Reader-appended body range can be known without
 * hardcoding how many metadata/loader items precede it. Sticky headers count as one item each.
 */
class ItemCountingScope(private val delegate: LazyListScope) : LazyListScope by delegate {
    var itemCount: Int = 0
        private set

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        itemCount++
        delegate.item(key, contentType, content)
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        itemCount++
        delegate.stickyHeader(key, contentType, content)
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        itemCount += count
        delegate.items(count, key, contentType, itemContent)
    }
}

/** Runs [block] and records the index/count of the items it appended into [range]. */
fun ItemCountingScope.recordRange(range: BodyItemRange, block: LazyListScope.() -> Unit) {
    val before = itemCount
    block()
    range.start = before
    range.count = itemCount - before
}
