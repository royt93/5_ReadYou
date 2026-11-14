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
class RssTitleFromBodyTest {

    private fun title(html: String?, max: Int = RssDescriptionParser.FALLBACK_TITLE_MAX_LENGTH) =
        RssDescriptionParser.titleFromBody(html, max)

    @Test fun constants_areTheSpecifiedOnes() {
        assertEquals(80, RssDescriptionParser.FALLBACK_TITLE_MAX_LENGTH)
        assertEquals("…", RssDescriptionParser.ELLIPSIS)
    }

    @Test fun nullAndBlank_giveAnEmptyTitle() {
        assertEquals("", title(null)); assertEquals("", title("")); assertEquals("", title("   \n\t "))
    }

    @Test fun onlyTagsAndSpaces_giveAnEmptyTitle() {
        assertEquals("", title("<p></p>")); assertEquals("", title("<p>  </p><br/>"))
    }

    @Test fun shortBody_isUsedAsIs() = assertEquals("Hello fediverse", title("<p>Hello fediverse</p>"))

    @Test fun htmlTagsAreRemoved() = assertEquals("Hello world", title("<p>Hello <strong>world</strong></p>"))

    @Test fun entitiesAreDecoded() = assertEquals("Tom & Jerry <3", title("<p>Tom &amp; Jerry &lt;3</p>"))

    @Test fun whitespaceIsCollapsed() = assertEquals("one two three", title("<p>one\n\n  two\t three</p>"))

    @Test fun exactlyTheLimit_isNotCut_andGetsNoEllipsis() {
        val body = "a".repeat(80)
        assertEquals(body, title(body))
    }

    @Test fun oneOverTheLimit_isCutAndGetsAnEllipsis() {
        val out = title("a".repeat(81))
        assertEquals("a".repeat(80) + "…", out)
    }

    @Test fun aLongBody_isCutToTheLimit() {
        val out = title("word ".repeat(100))
        assertTrue(out.endsWith("…"))
        assertTrue("the cut text is at most the limit plus the ellipsis: ${out.length}", out.codePointCount(0, out.length) <= 81)
    }

    @Test fun trailingSpaceBeforeTheCut_isNotLeftBeforeTheEllipsis() {
        val body = "a".repeat(79) + " " + "b".repeat(20)    // the 80th character is a space
        val out = title(body)
        assertEquals("a".repeat(79) + "…", out)
    }

    @Test fun anEmojiAtTheBoundary_isNeverSplit() {
        val body = "a".repeat(79) + "😀" + "b".repeat(10)    // the emoji is the 80th code point (two UTF-16 chars)
        val out = title(body)
        assertEquals("a".repeat(79) + "😀…", out)
        assertTrue("no lone surrogate may remain", out.none { Character.isHighSurrogate(it) && !out.contains("😀") })
    }

    @Test fun anEmojiJustPastTheBoundary_isDroppedWhole() {
        val body = "a".repeat(80) + "😀"
        assertEquals("a".repeat(80) + "…", title(body))
    }

    @Test fun lengthIsCountedInCodePoints_notUtf16Units() {
        val body = "😀".repeat(80)        // 160 UTF-16 chars but only 80 code points: fits, so no cut
        assertEquals(body, title(body))
    }

    @Test fun vietnameseTextKeepsItsAccents() = assertEquals("Chào buổi sáng, đây là bản tin", title("<p>Chào buổi sáng, đây là bản tin</p>"))

    @Test fun aCustomLimit_isHonoured() = assertEquals("abc…", title("abcdef", 3))

    @Test fun aLimitOfZeroOrLess_givesAnEmptyTitle_notACrash() {
        assertEquals("", title("abc", 0)); assertEquals("", title("abc", -5))
    }
}
