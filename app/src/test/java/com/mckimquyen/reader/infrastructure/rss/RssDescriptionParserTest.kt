package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssDescriptionParserTest {
    @Test fun malformedNestedGuardianDescriptionFallsBackToPlainTextInsteadOfThrowing() {
        val html = """<p>The judge who helped convict Jair Bolsonaro is caught up in a crisis.</p><ul><li><p><a href="https://example.test/a">Related report</a></p></li></ul><p>Thundering to a crowd of thousands, the senator focused on his opponent.</p> <a href="https://example.test/main">Continue reading...</a>"""

        val text = RssDescriptionParser.shortDescription(html)

        assertTrue(text.contains("judge who helped convict Jair Bolsonaro"))
        assertTrue(text.contains("Thundering to a crowd"))
        assertTrue("short description remains capped", text.length <= RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH)
    }

    @Test fun malformedFontColorFallsBackWithoutThrowing() {
        val html = """<p>The judge who helped convict Jair Bolsonaro is caught up in a crisis.</p><ul><li><p><a href="https://example.test/a">Related report</a></p></li></ul><p>Thundering to a crowd of thousands, the senator focused on his opponent.</p><font color="#zzzzzz">Readable fallback</font> <a href="https://example.test/main">Continue reading...</a>"""

        val result = RssDescriptionParser.shortDescription(html)

        assertTrue(result.length <= RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH)
    }

    @Test fun emptyAndPlainTextDescriptionsRemainSafe() {
        assertEquals("", RssDescriptionParser.shortDescription(null))
        assertEquals("", RssDescriptionParser.shortDescription("  \n\t "))
        assertEquals("A short description", RssDescriptionParser.shortDescription("A short description"))
    }

    @Test fun validHtmlDecodesEntitiesAndCollapsesWhitespace() {
        assertEquals(
            "Café & news update",
            RssDescriptionParser.shortDescription("<p>Café&nbsp; &amp;\nnews   update</p>"),
        )
    }

    @Test fun descriptionIsTruncatedAtExactlyTheConfiguredLimit() {
        val text = "x".repeat(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH + 20)

        val result = RssDescriptionParser.shortDescription(text)

        assertEquals(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH, result.length)
        assertEquals("x".repeat(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH), result)
    }
}
