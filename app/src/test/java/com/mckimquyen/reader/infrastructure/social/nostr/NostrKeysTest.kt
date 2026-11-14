package com.mckimquyen.reader.infrastructure.social.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrKeysTest {
    private val npub = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
    private val hex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"

    // ---- Bech32 (BIP-173 test vectors)
    @Test fun bech32_validVectors_decode() {
        listOf("A12UEL5L", "a12uel5l", "abcdef1qpzry9x8gf2tvdw0s3jn54khce6mua7lmqqqxw", "?1ezyfcl")
            .forEach { assertNotNull(it, Bech32.decode(it)) }
    }

    @Test fun bech32_invalidVectors_areRejected() {
        listOf("A12uEL5L", "pzry9x0s0muk", "1pzry9x0s0muk", "x1b4n0q5v", "li1dgmt3", "de1lg7wtÿ", "a12uel5m", "", "1")
            .forEach { assertNull(it, Bech32.decode(it)) }
    }

    @Test fun bech32_isCaseInsensitiveButNotMixed() {
        assertNotNull(Bech32.decode(npub.uppercase()))
        assertNull(Bech32.decode(npub.take(10).uppercase() + npub.drop(10)))
    }

    @Test fun bech32_oneChangedCharacter_failsTheChecksum() {
        val broken = npub.dropLast(1) + (if (npub.last() == 'g') 'h' else 'g')
        assertNull(Bech32.decode(broken))
    }

    @Test fun bech32_rejectsNonZeroPadding_evenWhenChecksumIsValid() {
        assertNull(Bech32.decode("npub1qqqsyqcyq5rqwzqfpg9scrgwpugpzysnzs23v9ccrydpk8qarc03kzr9xj"))
    }

    @Test fun bech32_rejectsNonPrintableCharactersInTheHrp_evenWithAValidChecksum() {
        assertNull(Bech32.decode(Bech32.encode("np b", ByteArray(32))))
        assertNull(Bech32.decode(Bech32.encode("np\u0001b", ByteArray(32))))
    }

    @Test fun bech32_rejectsValidButOverlongStrings() {
        assertTrue(Bech32.encode("npub", ByteArray(64)).length > 90)
        assertNull(Bech32.decode(Bech32.encode("npub", ByteArray(64))))
    }

    @Test fun bech32_encodeThenDecode_roundTrips() {
        val payload = ByteArray(32) { it.toByte() }
        val text = Bech32.encode("npub", payload)
        val back = Bech32.decode(text)
        assertEquals("npub", back?.hrp)
        assertEquals(payload.toList(), back?.payload?.toList())
    }

    // ---- NIP-19 npub
    @Test fun npubToHex_decodesTheKnownExample() = assertEquals(hex, NostrKeys.npubToHex(npub))

    @Test fun npubToHex_trimsSurroundingSpaces() = assertEquals(hex, NostrKeys.npubToHex("  $npub \n"))

    @Test fun npubToHex_acceptsAllUppercase() = assertEquals(hex, NostrKeys.npubToHex(npub.uppercase()))

    @Test fun npubToHex_rejectsOtherPrefixes() {
        assertNull(NostrKeys.npubToHex(nsec))
        assertNull(NostrKeys.npubToHex(Bech32.encode("note", ByteArray(32))))
        assertNull(NostrKeys.npubToHex(Bech32.encode("nprofile", ByteArray(32))))
    }

    @Test fun npubToHex_rejectsWrongLength() {
        assertNull(NostrKeys.npubToHex(Bech32.encode("npub", ByteArray(31))))
        assertNull(NostrKeys.npubToHex(Bech32.encode("npub", ByteArray(33))))
    }

    @Test fun npubToHex_rejectsJunk() {
        listOf("", "   ", "npub1", "https://example.com", "@bsky.app", "npub1 abc", "hello").forEach { assertNull(it, NostrKeys.npubToHex(it)) }
    }

    @Test fun hexToNpub_isTheInverse() {
        assertEquals(npub, NostrKeys.hexToNpub(hex))
        assertNull(NostrKeys.hexToNpub("zz"))
        assertNull(NostrKeys.hexToNpub(hex.dropLast(2)))
    }

    // ---- NIP-19 note id
    @Test fun noteId_encodesAKnownEventId() {
        assertEquals(
            "note1cqneqau89xzdllkft58klqecyrjdte7cgv5svy9u92yc9lxewdlqw0n65r",
            NostrKeys.noteId("c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e"),
        )
    }

    @Test fun noteId_rejectsAnInvalidId() {
        assertNull(NostrKeys.noteId(""))
        assertNull(NostrKeys.noteId("abc"))
        assertNull(NostrKeys.noteId("g".repeat(64)))
    }

    // ---- secret keys
    @Test fun isSecretKey_flagsNsecInAnyCase() {
        assertTrue(NostrKeys.isSecretKey(nsec))
        assertTrue(NostrKeys.isSecretKey("  " + nsec.uppercase()))
        assertTrue(NostrKeys.isSecretKey("nsec1abc"))
    }

    @Test fun isSecretKey_onlyLooksAtTheStart_notTheMiddleOfText() {
        assertFalse(NostrKeys.isSecretKey("see nsec1abc"))
        assertFalse(NostrKeys.isSecretKey("https://example.com/nsec1abc"))
    }

    @Test fun isSecretKey_isFalseForPublicKeysAndText() {
        assertFalse(NostrKeys.isSecretKey(npub))
        assertFalse(NostrKeys.isSecretKey(""))
        assertFalse(NostrKeys.isSecretKey("nsecurity"))
    }
}
