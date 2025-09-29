package com.mckimquyen.reader.domain.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RssSourcesTest {

    private val sampleJsonString = """
        {
          "en": [
            {
              "name": "BBC News - World",
              "link": "https://feeds.bbci.co.uk/news/world/rss.xml"
            },
            {
              "name": "CNN - Top Stories",
              "link": "http://rss.cnn.com/rss/edition.rss"
            }
          ],
          "vi": [
            {
              "name": "VnExpress",
              "link": "https://vnexpress.net/rss/tin-moi-nhat.rss"
            },
            {
              "name": "Dân Trí",
              "link": "https://dantri.com.vn/rss.htm"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `test JSON parsing structure`() {
        val jsonObject = JSONObject(sampleJsonString)

        assertTrue("Should contain 'en' key", jsonObject.has("en"))
        assertTrue("Should contain 'vi' key", jsonObject.has("vi"))

        val enArray = jsonObject.getJSONArray("en")
        val viArray = jsonObject.getJSONArray("vi")

        assertEquals("EN array should have 2 items", 2, enArray.length())
        assertEquals("VI array should have 2 items", 2, viArray.length())
    }

    @Test
    fun `test RSS source parsing`() {
        val jsonObject = JSONObject(sampleJsonString)
        val enArray = jsonObject.getJSONArray("en")
        val firstSource = enArray.getJSONObject(0)

        assertTrue("Source should have 'name' field", firstSource.has("name"))
        assertTrue("Source should have 'link' field", firstSource.has("link"))

        assertEquals("BBC News - World", firstSource.getString("name"))
        assertEquals("https://feeds.bbci.co.uk/news/world/rss.xml", firstSource.getString("link"))
    }

    @Test
    fun `test Vietnamese sources parsing`() {
        val jsonObject = JSONObject(sampleJsonString)
        val viArray = jsonObject.getJSONArray("vi")
        val firstSource = viArray.getJSONObject(0)

        assertEquals("VnExpress", firstSource.getString("name"))
        assertEquals("https://vnexpress.net/rss/tin-moi-nhat.rss", firstSource.getString("link"))
    }

    @Test
    fun `test RSS source links are valid URLs`() {
        val jsonObject = JSONObject(sampleJsonString)

        // Test English sources
        val enArray = jsonObject.getJSONArray("en")
        for (i in 0 until enArray.length()) {
            val source = enArray.getJSONObject(i)
            val link = source.getString("link")

            assertTrue("Link should start with http:// or https://",
                link.startsWith("http://") || link.startsWith("https://"))
        }

        // Test Vietnamese sources
        val viArray = jsonObject.getJSONArray("vi")
        for (i in 0 until viArray.length()) {
            val source = viArray.getJSONObject(i)
            val link = source.getString("link")

            assertTrue("Link should start with http:// or https://",
                link.startsWith("http://") || link.startsWith("https://"))
        }
    }

    @Test
    fun `test source names are not empty`() {
        val jsonObject = JSONObject(sampleJsonString)

        // Test English sources
        val enArray = jsonObject.getJSONArray("en")
        for (i in 0 until enArray.length()) {
            val source = enArray.getJSONObject(i)
            val name = source.getString("name")

            assertFalse("Name should not be empty", name.isEmpty())
            assertTrue("Name should not be blank", name.isNotBlank())
        }

        // Test Vietnamese sources
        val viArray = jsonObject.getJSONArray("vi")
        for (i in 0 until viArray.length()) {
            val source = viArray.getJSONObject(i)
            val name = source.getString("name")

            assertFalse("Name should not be empty", name.isEmpty())
            assertTrue("Name should not be blank", name.isNotBlank())
        }
    }

    @Test
    fun `test no duplicate RSS links per language`() {
        val jsonObject = JSONObject(sampleJsonString)

        // Test English sources
        val enArray = jsonObject.getJSONArray("en")
        val enLinks = mutableSetOf<String>()
        for (i in 0 until enArray.length()) {
            val link = enArray.getJSONObject(i).getString("link")
            assertFalse("EN links should be unique", enLinks.contains(link))
            enLinks.add(link)
        }

        // Test Vietnamese sources
        val viArray = jsonObject.getJSONArray("vi")
        val viLinks = mutableSetOf<String>()
        for (i in 0 until viArray.length()) {
            val link = viArray.getJSONObject(i).getString("link")
            assertFalse("VI links should be unique", viLinks.contains(link))
            viLinks.add(link)
        }
    }

    @Test
    fun `test RSS links contain typical RSS indicators`() {
        val jsonObject = JSONObject(sampleJsonString)
        val allLinks = mutableListOf<String>()

        // Collect all links
        val enArray = jsonObject.getJSONArray("en")
        for (i in 0 until enArray.length()) {
            allLinks.add(enArray.getJSONObject(i).getString("link"))
        }

        val viArray = jsonObject.getJSONArray("vi")
        for (i in 0 until viArray.length()) {
            allLinks.add(viArray.getJSONObject(i).getString("link"))
        }

        // Test that links contain RSS-like patterns
        allLinks.forEach { link ->
            val containsRssIndicator = link.contains("rss", ignoreCase = true) ||
                                     link.contains("feed", ignoreCase = true) ||
                                     link.contains("xml", ignoreCase = true)

            assertTrue("Link should contain RSS indicators: $link", containsRssIndicator)
        }
    }
}