package com.mckimquyen.reader.domain.sv

/**
 * Decides whether the Fever item loop asks for another page. The loop used to continue whenever a page was full, trusting that `since_id` always moves forward; a server that
 * answers newest first, or ignores `since_id`, then returned the same 50 items for ever until WorkManager killed the worker, re-writing them every time.
 */
object FeverPaging {
    /** 100 pages of 50 is 5,000 items per sync; the next sync continues from where `lastArticleId` stopped. */
    const val MAX_PAGES_PER_SYNC = 100

    /**
     * [pageNumber] is 1 for the first page just read. [previousSinceId] and [newSinceId] are the cursor before and after reading it. A cursor that did not change means the
     * server gave nothing new for it, so asking again would return the same page.
     */
    fun shouldFetchNextPage(pageSize: Int, fullPageSize: Int, pageNumber: Int, previousSinceId: String, newSinceId: String): Boolean {
        if (pageSize < fullPageSize) return false
        if (pageNumber >= MAX_PAGES_PER_SYNC) return false
        if (newSinceId == previousSinceId) return false
        return true
    }
}
