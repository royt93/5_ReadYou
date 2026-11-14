package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrEventTest {
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private val stranger = "dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8"

    private val plain = """{"id":"c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000300,"kind":1,"tags":[],"content":"Hello nostr, first note","sig":"4993b5aead88e3b16b5c15a8616d296ef70124ea8e8c0374c80e9f220dbd82facfe4ff1559285d79e2ce89b8b9bf5425b5255314bc0887d62c43d5bedb798623"}"""
    private val rich = """{"id":"a0c97278dbf284ce180d75923c1c5c50c82b9111ea1c61d7a02b97c84b811b49","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000200,"kind":1,"tags":[["t","news"]],"content":"Quote \" backslash \\ tab\t newline\nline2 <script>alert(1)</script> & cafe é 中文 😀","sig":"b32be964eb698aa9b4d7ea2b187c293905267b036886416e67773ab009e7dff007df9edae1adcfb959d5695ff53acaf02f44ffbc9372d95772ee283e76a3871e"}"""
    private val reply = """{"id":"36432db1e953c2945198a3326740ce3c57c06f0fffd075d3762bb743704b0bed","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000250,"kind":1,"tags":[["e","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","","reply"]],"content":"This one is a reply","sig":"637c1169b9905f2bac6fb28489852e649571dd16dad71ffc8ca19ca2e2aa321ba44ed8d3404af3b9a3cf38ad68bd52cd6ca00369b5afd84a6ce0188d5eb92582"}"""
    private val kind7 = """{"id":"409f5262d409c9d1d2af688942d2bf7be21a261a9a564da94fcfdd7dda577f29","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000400,"kind":7,"tags":[],"content":"+","sig":"a31d82905a8910278e8aa117f7e8768fcce5b908ea5a889a12cc6a9f71c900d246b8c0c46a400aa671c156dcbe75cf0d081ab9b8c7283bf492f1431c710b2616"}"""
    private val foreign = """{"id":"3f6c370c58122e1e43fe8ad81de0da6f642cbf359ecfee4c1fae8bb0a6f977a9","pubkey":"dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8","created_at":1700000300,"kind":1,"tags":[],"content":"Written by somebody else","sig":"a8a86899daf244626261ab4193936db94ed53f026b8dad7e9c0cda80a28bd436cc57bf213c158093619b25daa52d1f6064adec8e19dcb4f30cf153fb791ea290"}"""
    private val future = """{"id":"e13f00d75f8562723d5cf49f79b2c76c4070f51b548f3d715719ed5da2f1f956","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":4102444800,"kind":1,"tags":[],"content":"Dated far in the future","sig":"03671993de847550a7b9d81b6b0cf7ca1c764dee0f29edce59842bb96dd1723ad2afcb4deececd61bad5ba525e12b82e7a1b8ab823781a9b1a9f0b8ce30faf4e"}"""
    private val escapes = """{"id":"22131b2a5383313d46530e6cadf52eb6966b532d0740810dc318dd8c8ea7bbde","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000500,"kind":1,"tags":[],"content":"cr\rbs\bff\fend","sig":"c073d868e6ee74bfc63192dbfa42cdf0a4f570c38feb10cb0445fdc4860cf3148ec4e8a42913f22166d12c70fb433ff83b8cbe216dcc362b390ec8f78e8db844"}"""
    private val rawControl = """{"id":"7932a1fd0a83cc7a3a476beab7fc131285a582faa6d259ea816554e33f3e76c9","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000600,"kind":1,"tags":[],"content":"ctl\u0001end","sig":"66a6da199b415ae84b868a78f4b81b3f27d79155ee160a5ab9b71556f01997b918f754bff410e4edd4b9c7ecbb2d1ef17078c690e46ac1394e2357da447fafc7"}"""

    private fun ev(json: String) = requireNotNull(NostrEvent.parse(json)) { "fixture must parse" }
    private fun replaceFirst(json: String, old: String, new: String): String {
        require(json.contains(old)) { "pattern not in fixture: $old" }
        return json.replaceFirst(old, new)
    }

    // ---- parse
    @Test fun parse_readsEveryField() {
        val e = ev(plain)
        assertEquals("c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e", e.id)
        assertEquals(author, e.pubkey)
        assertEquals(1_700_000_300L, e.createdAt)
        assertEquals(1, e.kind)
        assertEquals("Hello nostr, first note", e.content)
        assertEquals(emptyList<List<String>>(), e.tags)
    }

    @Test fun parse_keepsTagsAndUnicodeContent() {
        val e = ev(rich)
        assertEquals(listOf(listOf("t", "news")), e.tags)
        assertTrue(e.content.contains("\"") && e.content.contains("\\") && e.content.contains("\t") && e.content.contains("\n"))
        assertTrue(e.content.contains("中文") && e.content.contains("😀"))
    }

    @Test fun parse_ignoresUnknownFields() = assertNotNull(NostrEvent.parse(plain.dropLast(1) + ""","extra":{"a":1}}"""))

    @Test fun parse_rejectsBrokenOrIncompleteJson() {
        listOf("", "not json", "[]", "{}", "null", plain.dropLast(1), plain.replace("\"sig\"", "\"sog\""), plain.replace("\"content\"", "\"cont\""))
            .forEach { assertNull(it, NostrEvent.parse(it)) }
    }

    @Test fun parse_rejectsAFractionalKind() =
        assertNull(NostrEvent.parse(replaceFirst(plain, "\"kind\":1", "\"kind\":1.5")))

    @Test fun parse_rejectsWrongTypes() {
        listOf(
            replaceFirst(plain, "\"created_at\":1700000300", "\"created_at\":\"1700000300\""),
            replaceFirst(plain, "\"created_at\":1700000300", "\"created_at\":1.5"),
            replaceFirst(plain, "\"kind\":1", "\"kind\":\"1\""),
            replaceFirst(plain, "\"tags\":[]", "\"tags\":{}"),
            replaceFirst(plain, "\"tags\":[]", "\"tags\":[1]"),
            replaceFirst(plain, "\"tags\":[]", "\"tags\":[[1]]"),
            replaceFirst(plain, "\"content\":\"Hello nostr, first note\"", "\"content\":5"),
            replaceFirst(plain, "\"content\":\"Hello nostr, first note\"", "\"content\":null"),
        ).forEach { assertNull(it, NostrEvent.parse(it)) }
    }

    // ---- isValid
    @Test fun isValid_acceptsASignedNote() = assertTrue(ev(plain).isValid(author))

    @Test fun isValid_acceptsContentThatNeedsEscaping() = assertTrue(ev(rich).isValid(author))

    @Test fun isValid_acceptsEveryJsonEscape_CR_BS_FF() = assertTrue(ev(escapes).isValid(author))

    // NIP-01 escapes only seven characters; any other control character (here U+0001) is hashed as it is.
    @Test fun isValid_hashesOtherControlCharactersVerbatim() = assertTrue(ev(rawControl).isValid(author))

    @Test fun isValid_rejectsAnUppercaseSignature_evenThoughTheMathIsTheSame() {
        val sig = ev(plain).sig
        assertFalse(ev(replaceFirst(plain, sig, sig.uppercase())).isValid(author))
    }

    @Test fun isValid_acceptsAFutureDatedNote_theMapperClampsIt() = assertTrue(ev(future).isValid(author))

    @Test fun isValid_rejectsANoteFromSomeoneElse() {
        assertFalse(ev(foreign).isValid(author))
        assertTrue(ev(foreign).isValid(stranger))
    }

    @Test fun isValid_rejectsAnotherKind_evenWhenCorrectlySigned() = assertFalse(ev(kind7).isValid(author))

    @Test fun isValid_rejectsTamperedContent_becauseTheIdNoLongerMatches() =
        assertFalse(ev(replaceFirst(plain, "first note", "forged note")).isValid(author))

    @Test fun isValid_rejectsATamperedTimestampOrTags() {
        assertFalse(ev(replaceFirst(plain, "1700000300", "1700000301")).isValid(author))
        assertFalse(ev(replaceFirst(rich, "[[\"t\",\"news\"]]", "[[\"t\",\"fake\"]]")).isValid(author))
    }

    @Test fun isValid_rejectsAWrongSignature_evenWithAMatchingId() {
        val sig = ev(plain).sig
        val flipped = (if (sig.first() == '0') "1" else "0") + sig.drop(1)
        assertFalse(ev(replaceFirst(plain, sig, flipped)).isValid(author))
    }

    @Test fun isValid_rejectsASignatureBorrowedFromAnotherNote() =
        assertFalse(ev(replaceFirst(plain, ev(plain).sig, ev(rich).sig)).isValid(author))

    @Test fun isValid_rejectsUppercaseOrShortHex() {
        val id = ev(plain).id
        assertFalse(ev(replaceFirst(plain, id, id.uppercase())).isValid(author))
        assertFalse(ev(replaceFirst(plain, id, id.dropLast(2))).isValid(author))
        assertFalse(ev(plain).isValid(author.uppercase()))
    }

    // ---- isReply
    @Test fun isReply_isTrueOnlyWithAnETag() {
        assertTrue(ev(reply).isReply)
        assertFalse(ev(plain).isReply)
        assertFalse(ev(rich).isReply)
    }
}
