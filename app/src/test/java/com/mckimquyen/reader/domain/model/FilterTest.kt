package com.mckimquyen.reader.domain.model

import org.junit.Assert.*
import org.junit.Test

class RssSourceTest {

    @Test
    fun `test RSS source creation`() {
        val rssSource = RssSource(
            name = "Test RSS Feed",
            link = "https://test.com/rss"
        )

        assertEquals("Name should match", "Test RSS Feed", rssSource.name)
        assertEquals("Link should match", "https://test.com/rss", rssSource.link)
    }

    @Test
    fun `test RSS source with empty values`() {
        val rssSource = RssSource(
            name = "",
            link = ""
        )

        assertEquals("Empty name should be allowed", "", rssSource.name)
        assertEquals("Empty link should be allowed", "", rssSource.link)
    }

    @Test
    fun `test RSS sources data structure`() {
        val enSources = listOf(
            RssSource("BBC News", "https://bbc.com/rss"),
            RssSource("CNN", "https://cnn.com/rss")
        )

        val viSources = listOf(
            RssSource("VnExpress", "https://vnexpress.net/rss"),
            RssSource("Dân Trí", "https://dantri.com.vn/rss")
        )

        val rssSourcesData = RssSourcesData(
            en = enSources,
            vi = viSources
        )

        assertEquals("English sources should match", 2, rssSourcesData.en?.size)
        assertEquals("Vietnamese sources should match", 2, rssSourcesData.vi?.size)

        assertEquals("First EN source name should match", "BBC News", rssSourcesData.en?.get(0)?.name)
        assertEquals("First VI source name should match", "VnExpress", rssSourcesData.vi?.get(0)?.name)
    }

    @Test
    fun `test RSS sources data with null values`() {
        val rssSourcesData = RssSourcesData()

        assertNull("English sources should be null by default", rssSourcesData.en)
        assertNull("Vietnamese sources should be null by default", rssSourcesData.vi)
    }
}