package com.mckimquyen.reader.ui.component.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemCountingScopeTest {
    private class RecordingScope : LazyListScope {
        var items = 0
        override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) { items++ }
        @ExperimentalFoundationApi
        override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) { items++ }
        override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, itemContent: @Composable LazyItemScope.(Int) -> Unit) { items += count }
    }

    @Test fun countsSingleAndBulkItemsAndForwardsThemToTheRealScope() {
        val real = RecordingScope()
        val scope = ItemCountingScope(real)
        scope.item { }
        scope.items(3) { }
        assertEquals(4, scope.itemCount)
        assertEquals(4, real.items)
    }

    @Test fun recordRange_capturesOnlyTheItemsAddedInsideTheBlock() {
        val scope = ItemCountingScope(RecordingScope())
        val range = BodyItemRange()
        scope.item { }
        scope.item { }
        scope.recordRange(range) { item { }; items(4) { } }
        scope.item { }
        assertEquals(2, range.start)
        assertEquals(5, range.count)
        assertEquals(8, scope.itemCount)
    }

    @Test fun recordRange_emptyBlockYieldsEmptyBody_soTheGateNeverAwards() {
        val scope = ItemCountingScope(RecordingScope())
        val range = BodyItemRange()
        scope.item { }
        scope.recordRange(range) { }
        assertEquals(1, range.start)
        assertEquals(0, range.count)
    }

    @Test fun clear_resetsToNoBody() {
        val range = BodyItemRange().apply { start = 3; count = 9 }
        range.clear()
        assertEquals(-1, range.start)
        assertEquals(0, range.count)
    }
}
