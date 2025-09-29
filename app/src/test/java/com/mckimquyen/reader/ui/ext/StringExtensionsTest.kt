package com.mckimquyen.reader.ui.ext

import org.junit.Assert.*
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun `test string formatting utilities`() {
        val testString = "   Hello World   "

        assertEquals("String should be trimmed correctly", "Hello World", testString.trim())
        assertFalse("Non-empty string should not be empty", testString.isEmpty())
        assertTrue("Non-blank string should not be blank", testString.isNotBlank())
    }

    @Test
    fun `test empty and null strings`() {
        val emptyString = ""
        val nullString: String? = null
        val blankString = "   "

        assertTrue("Empty string should be empty", emptyString.isEmpty())
        assertTrue("Blank string should be blank", blankString.isBlank())
        assertNull("Null string should be null", nullString)
    }

    @Test
    fun `test string comparison`() {
        val string1 = "test"
        val string2 = "test"
        val string3 = "TEST"

        assertEquals("Same strings should be equal", string1, string2)
        assertNotEquals("Different case strings should not be equal", string1, string3)
        assertTrue("Same strings should be equal ignoring case", string1.equals(string3, ignoreCase = true))
    }

    @Test
    fun `test string contains operations`() {
        val text = "This is a test message"

        assertTrue("String should contain substring", text.contains("test"))
        assertFalse("String should not contain non-existent substring", text.contains("xyz"))
        assertTrue("String should contain substring ignoring case", text.contains("TEST", ignoreCase = true))
    }

    @Test
    fun `test string URL validation`() {
        val validUrls = listOf(
            "https://example.com",
            "http://test.org",
            "https://news.site.com/rss"
        )

        val invalidUrls = listOf(
            "",
            "not-a-url",
            "ftp://example.com",
            "example.com"
        )

        validUrls.forEach { url ->
            assertTrue("$url should start with http or https",
                url.startsWith("http://") || url.startsWith("https://"))
        }

        invalidUrls.forEach { url ->
            if (url.isNotEmpty()) {
                assertFalse("$url should not be a valid HTTP URL",
                    url.startsWith("http://") || url.startsWith("https://"))
            }
        }
    }
}