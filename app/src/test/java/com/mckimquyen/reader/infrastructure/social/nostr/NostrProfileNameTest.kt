package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrProfileNameTest {
    private fun content(name: String?, display: String? = null): String = buildString {
        append('{')
        val parts = mutableListOf<String>()
        if (display != null) parts += "\"display_name\":\"" + display + "\""
        if (name != null) parts += "\"name\":\"" + name + "\""
        append(parts.joinToString(","))
        append('}')
    }

    @Test fun prefersDisplayName_overName() =
        assertEquals("Alice Liddell", NostrProfileName.fromContent(content("alice", display = " Alice Liddell ")))

    @Test fun fallsBackToName_whenDisplayNameIsBlank() =
        assertEquals("alice", NostrProfileName.fromContent(content("alice", display = "   ")))

    @Test fun fallsBackToName_whenDisplayNameIsNotAString() =
        assertEquals("alice", NostrProfileName.fromContent("""{"display_name":42,"name":"alice"}"""))

    @Test fun fallsBackToName_whenDisplayNameIsNull() =
        assertEquals("alice", NostrProfileName.fromContent("""{"display_name":null,"name":"alice"}"""))

    @Test fun returnsNull_forAnythingWithoutAUsableName() {
        listOf("", "no json", "{}", "null", "[]", """{"name":null}""", """{"name":17}""", """{"name":"   "}""", """{"name":["x"]}""")
            .forEach { assertNull(it, NostrProfileName.fromContent(it)) }
    }

    @Test fun collapsesWhitespaceAndTrims() =
        assertEquals("Ada Lovelace", NostrProfileName.fromContent("{\"name\":\"  Ada\\n\\t Lovelace  \"}"))

    @Test fun controlCharactersBecomeASpace_soWordsDoNotGlueTogether() =
        assertEquals("A B C", NostrProfileName.fromContent("{\"name\":\"A\\u0001B\\u0085C\"}"))

    @Test fun aNonBreakingSpaceIsASpace() =
        assertEquals("a b", NostrProfileName.fromContent("{\"name\":\"a\\u00a0b\"}"))

    // U+2007 (figure space) and U+202F (narrow no-break space) are spaces to Character.isSpaceChar but not to isWhitespace.
    @Test fun figureAndNarrowNoBreakSpaces_areSpaces() {
        assertEquals("a b", NostrProfileName.fromContent("{\"name\":\"a\\u2007b\"}"))
        assertEquals("a b", NostrProfileName.fromContent("{\"name\":\"a\\u202fb\"}"))
    }

    // The cut falls right after a space: the result must not end with it.
    @Test fun aCutThatLandsAfterASpace_leavesNoTrailingSpace() {
        val long = "a".repeat(39) + " " + "b".repeat(5)
        assertEquals("a".repeat(39), NostrProfileName.fromContent("{\"name\":\"$long\"}"))
    }

    @Test fun aNameThatIsOnlyControlCharacters_isNotAName() =
        assertNull(NostrProfileName.fromContent("{\"name\":\"\\u0001\\u0002\\n\"}"))

    @Test fun displayNameOfOnlyControlCharacters_fallsBackToName() =
        assertEquals("alice", NostrProfileName.fromContent("{\"display_name\":\"\\u0001\",\"name\":\"alice\"}"))

    @Test fun exactlyFortyCodePoints_isKeptWhole() {
        val forty = "a".repeat(39) + "\uD83D\uDE00"
        assertEquals(40, forty.codePointCount(0, forty.length))
        assertEquals(forty, NostrProfileName.fromContent(content(forty)))
    }

    @Test fun fortyOneCodePoints_areCutToForty_withoutBreakingTheEmoji() {
        val long = "a".repeat(39) + "\uD83D\uDE00" + "Z"
        val name = requireNotNull(NostrProfileName.fromContent(content(long)))
        assertEquals("a".repeat(39) + "\uD83D\uDE00", name)
        assertEquals(40, name.codePointCount(0, name.length))
    }

    @Test fun anEmojiThatWouldStraddleTheLimit_isDroppedWhole() {
        val long = "a".repeat(40) + "\uD83D\uDE00"
        assertEquals("a".repeat(40), NostrProfileName.fromContent(content(long)))
    }

    @Test fun htmlLikeTextStaysPlainText() =
        assertEquals("<img src=x>", NostrProfileName.fromContent(content("<img src=x>")))

    @Test fun theParserNeverLogs() {
        val source = File("src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileName.kt").readLines()
            .filterNot { it.trimStart().let { t -> t.startsWith("import ") || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
            .joinToString("\n")
        listOf("Log.", "println", "printStackTrace").forEach { assertFalse(it, source.contains(it)) }
    }
}
