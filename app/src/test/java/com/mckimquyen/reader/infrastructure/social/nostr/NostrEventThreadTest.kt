package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How a note says "I answer that note" (NIP-10). The signature is not looked at here (never needed to read tags), so the notes are
 * built by hand with a well-formed but meaningless signature.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrEventThreadTest {
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private fun id(n: Int) = "%064x".format(n)
    private fun note(n: Int, vararg tags: List<String>): NostrEvent =
        requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", id(n)); put("pubkey", author); put("created_at", 1_700_000_000L + n); put("kind", 1)
                    put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                    put("content", "x"); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )

    @Test fun aNoteWithoutETags_namesNoTarget() {
        val plain = note(1, listOf("t", "news"))
        assertNull(plain.repliedToId)
    }

    @Test fun aMentionOnlyNote_namesNoTarget() {
        val quoting = note(2, listOf("e", id(1), "", "mention"))
        assertNull(quoting.repliedToId)
    }

    @Test fun aReplyMarker_namesItsTarget() {
        val reply = note(3, listOf("e", id(1), "", "reply"))
        assertEquals(id(1), reply.repliedToId)
    }

    @Test fun aRootOnlyNote_namesTheRoot() {
        val direct = note(4, listOf("e", id(1), "", "root"))
        assertEquals(id(1), direct.repliedToId)
    }

    @Test fun replyBeatsRoot_whenBothArePresent() {
        val deep = note(5, listOf("e", id(1), "", "root"), listOf("e", id(2), "", "reply"))
        assertEquals("the note being answered, not the root of the thread", id(2), deep.repliedToId)
        val deepReversed = note(6, listOf("e", id(2), "", "reply"), listOf("e", id(1), "", "root"))
        assertEquals(id(2), deepReversed.repliedToId)
    }

    @Test fun oldStyleOneTag_namesThatNote() {
        val old = note(7, listOf("e", id(1)))
        assertEquals(id(1), old.repliedToId)
    }

    @Test fun oldStyleManyTags_theLastUnmarkedOneIsTheTarget() {
        val old = note(8, listOf("e", id(1)), listOf("e", id(2)), listOf("e", id(3)))
        assertEquals("first is the root, last is the note being answered", id(3), old.repliedToId)
    }

    @Test fun aMentionAmongOldStyleTags_isNeverTheTarget() {
        val old = note(9, listOf("e", id(1)), listOf("e", id(9), "", "mention"))
        assertEquals("the mention comes last but is only a mention", id(1), old.repliedToId)
    }

    @Test fun aBrokenTargetId_namesNothing() {
        val upperId = "%064X".format(0xabc)
        listOf("XYZ", id(1).dropLast(2), upperId, "").forEach { bad ->
            val reply = note(10, listOf("e", bad, "", "reply"))
            assertNull(bad, reply.repliedToId)
        }
    }

    @Test fun severalReplyMarkers_theLastOneWins() {
        val odd = note(11, listOf("e", id(1), "", "reply"), listOf("e", id(2), "", "reply"))
        assertEquals(id(2), odd.repliedToId)
    }

    @Test fun anETagWithNoId_namesNothing() {
        val bare = note(12, listOf("e"))
        assertNull(bare.repliedToId)
    }
}
