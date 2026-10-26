package com.mckimquyen.reader.ui.page.radar

import com.mckimquyen.reader.domain.model.radar.ArticleRadarState
import com.mckimquyen.reader.domain.model.radar.ArticleSnapshot
import com.mckimquyen.reader.domain.radar.DiffType
import com.mckimquyen.reader.domain.repository.ArticleRadarDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleDiffViewModelTest {

    private fun snap(id: Long, title: String, text: String, at: Long) =
        ArticleSnapshot(id, "a1", at, "t", "c", title, text)

    private fun loader(first: ArticleSnapshot?, latest: ArticleSnapshot?, state: ArticleRadarState? = null): ArticleDiffLoader {
        val dao = mockk<ArticleRadarDao>()
        coEvery { dao.firstSnapshot("a1") } returns first
        coEvery { dao.snapshots("a1", 1) } returns listOfNotNull(latest)
        coEvery { dao.state("a1") } returns state
        return ArticleDiffLoader(dao, Dispatchers.Unconfined)
    }

    @Test
    fun editedArticle_diffsFirstSavedVersionAgainstLatest() = runBlocking {
        val result = loader(
            first = snap(1, "Minister resigns", "He quit on Monday", 10),
            latest = snap(3, "Minister stays", "He quit on Monday", 30),
            state = ArticleRadarState("a1", editCount = 2, lastEditedAt = 30),
        ).load("a1")

        assertTrue(result.titleDiff.any { it.type == DiffType.REMOVED && it.text == "resigns" })
        assertTrue(result.titleDiff.any { it.type == DiffType.ADDED && it.text == "stays" })
        assertTrue(result.bodyDiff.all { it.type == DiffType.SAME })
        assertFalse(result.removed)
        assertTrue(result.hasHistory)
    }

    @Test
    fun removedArticle_isMarkedAndShowsLastSavedVersion() = runBlocking {
        val only = snap(1, "Gone", "Body", 10)
        val result = loader(only, only, ArticleRadarState("a1", removedAt = 99)).load("a1")

        assertTrue(result.removed)
        assertTrue(result.titleDiff.all { it.type == DiffType.SAME })
    }

    @Test
    fun noSnapshots_reportsNoHistory() = runBlocking {
        val result = loader(null, null).load("a1")
        assertFalse(result.hasHistory)
        assertTrue(result.titleDiff.isEmpty())
        assertNull(result.editedAt)
    }
}
