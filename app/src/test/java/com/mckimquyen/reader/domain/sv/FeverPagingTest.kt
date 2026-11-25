package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeverPagingTest {
    private fun next(size: Int = 50, page: Int = 1, prev: String = "100", now: String = "150") =
        FeverPaging.shouldFetchNextPage(size, 50, page, prev, now)

    @Test fun aFullPageWithAMovingCursorAsksForTheNextPage() = assertTrue(next())

    @Test fun aShortPageIsTheLastPage() = assertFalse(next(size = 49))

    @Test fun aCursorThatDidNotMoveStopsTheLoop() = assertFalse(next(prev = "150", now = "150"))

    @Test fun thePageCapStopsTheLoop() {
        assertTrue(next(page = FeverPaging.MAX_PAGES_PER_SYNC - 1))
        assertFalse(next(page = FeverPaging.MAX_PAGES_PER_SYNC))
    }

    @Test fun aServerThatKeepsAnsweringTheSamePageEndsAfterOneRepeat() {
        // Simulates the loop: the server ignores since_id and always returns 50 items ending in id 150.
        var pages = 0
        var since = "100"
        var go = true
        while (go && pages < 1_000) {
            pages++
            val prev = since
            since = "150"
            go = FeverPaging.shouldFetchNextPage(50, 50, pages, prev, since)
        }
        assertTrue("must stop after the second page, not run away (pages=$pages)", pages <= 2)
    }

    @Test fun aServerWithEndlessNewIdsStillEndsAtTheCap() {
        var pages = 0
        var since = 0
        var go = true
        while (go && pages < 10_000) {
            pages++
            val prev = since.toString()
            since += 50
            go = FeverPaging.shouldFetchNextPage(50, 50, pages, prev, since.toString())
        }
        assertTrue("pages=$pages", pages == FeverPaging.MAX_PAGES_PER_SYNC)
    }

    @Test fun theFeverLoopUsesThePagingRule() {
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val fever = File(src, "com/mckimquyen/reader/domain/sv/FeverRssSv.kt").readText()
        assertTrue("FeverRssSv must ask FeverPaging", "FeverPaging.shouldFetchNextPage(" in fever)
        assertFalse("the unbounded condition must be gone", "if (remoteItems.size >= FEVER_PAGE_SIZE) {" in fever)
    }
}
