package com.mckimquyen.reader.infrastructure.social.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An nprofile (NIP-19) names a person by public key, optionally with relay hints. The app reads ONLY the key: the hints are walked
 * over and never used, so whoever sent the text cannot choose which server the app talks to.
 * Sample strings below were computed by an independent encoder (checksums are not produced by the app).
 */
class NostrNprofileTest {
    private val hex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val npub = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
    private val noRelay = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsz6vqwn"
    private val oneRelay = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nspzamhxue69uhhyetvv9ujuetcv9khqmr99e3k7mg3lnphh"
    private val relayFirst = "nprofile1qythwumn8ghj7un9d3shjtn90psk6urvv5hxxmmdqqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7ns6myyzp"
    private val unknownMiddle = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsfqfa85qg0waehxw309asjuetcv9khqmr9efyt4k"
    private val twoSameKey = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsqypl8a8zz4ydlauvl4y57tldpkuhqa0q6fsg5zee7y72zxnvx4h05us4erak"
    private val twoKeys = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsqyqalp33lewf5vdq847t6te0wvnags0gs0mu72kz8938tn24wlfze6420n09"
    private val onlyRelay = "nprofile1qythwumn8ghj7un9d3shjtn90psk6urvv5hxxmmdcxc3h6"
    private val key31 = "nprofile1qq0hul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7tflntw"
    private val key33 = "nprofile1qqshul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsq4am05q"
    private val cutValue = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nspxfmhxue69uhhsvs4pjp"
    private val cutHeader = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsp5l4cu7"
    private val neventWithKey = "nevent1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nspqaj0w"
    private val official = "nprofile1qqsrhuxx8l9ex335q7he0f09aej04zpazpl0ne2cgukyawd24mayt8gpp4mhxue69uhhytnc9e3k7mgpz4mhxue69uhkg6nzv9ejuumpv34kytnrdaksjlyr9p"
    private val officialHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val keyBytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"

    /** Longest relay hint and type bytes the test encoder may use, so a TLV length always fits one byte. */
    private companion object {
        const val TLV_KEY = 0
        const val TLV_RELAY = 1
        const val MAX_TLV_VALUE = 255
        const val KEY_TLV_BYTES = 34
        const val PAYLOAD_FOR_999_CHARS = 615
        const val PAYLOAD_FOR_1001_CHARS = 616
    }

    private fun tlv(type: Int, value: ByteArray): ByteArray = byteArrayOf(type.toByte(), value.size.toByte()) + value

    private fun profile(vararg parts: ByteArray, hrp: String = "nprofile"): String = Bech32.encode(hrp, parts.fold(ByteArray(0)) { a, b -> a + b })

    /** A valid nprofile whose payload is exactly [payloadBytes] long: the key, then relay hints of "a" characters. */
    private fun profileOfPayload(payloadBytes: Int): String {
        val parts = mutableListOf(tlv(TLV_KEY, keyBytes))
        var rest = payloadBytes - KEY_TLV_BYTES
        while (rest > 0) {
            val size = minOf(rest - 2, MAX_TLV_VALUE)
            parts += tlv(TLV_RELAY, ByteArray(size) { 'a'.code.toByte() })
            rest -= size + 2
        }
        return profile(*parts.toTypedArray())
    }

    // ---- the helper used here must agree with the independent encoder, or no other expectation means anything
    @Test fun helperAgreesWithTheIndependentEncoder() {
        assertEquals(noRelay, profile(tlv(TLV_KEY, keyBytes)))
        assertEquals(oneRelay, profile(tlv(TLV_KEY, keyBytes), tlv(TLV_RELAY, "wss://relay.example.com".toByteArray())))
        assertEquals(npub, Bech32.encode("npub", keyBytes))
        assertEquals(110, oneRelay.length)
    }

    // ---- the official NIP-19 example
    @Test fun officialVector_decodesToTheKnownKey() {
        assertEquals(131, official.length)
        assertEquals(officialHex, NostrKeys.nprofileToHex(official))
    }

    @Test fun anNpubAndAnNprofileOfOnePerson_giveOneHex() {
        assertEquals(hex, NostrKeys.npubToHex(npub))
        assertEquals(hex, NostrKeys.nprofileToHex(noRelay))
        assertEquals(NostrKeys.npubToHex(npub), NostrKeys.nprofileToHex(oneRelay))
        assertEquals(npub, NostrKeys.hexToNpub(requireNotNull(NostrKeys.nprofileToHex(oneRelay))))
    }

    // ---- what is accepted
    @Test fun aProfileWithoutRelays_isAccepted() = assertEquals(hex, NostrKeys.nprofileToHex(noRelay))

    @Test fun aProfileWithRelayHints_isAccepted_andTheHintsAreNotUsed() {
        assertEquals(hex, NostrKeys.nprofileToHex(oneRelay))
        assertEquals("a hint before the key changes nothing", hex, NostrKeys.nprofileToHex(relayFirst))
        // The result is only the key: nothing of the hint can be in it.
        assertEquals(64, NostrKeys.nprofileToHex(oneRelay)?.length)
    }

    @Test fun unknownTypesAreSkipped() = assertEquals(hex, NostrKeys.nprofileToHex(unknownMiddle))

    @Test fun theNostrPrefixTheCaseAndTheSpaces_areAccepted() {
        assertEquals(hex, NostrKeys.nprofileToHex("nostr:$noRelay"))
        assertEquals(hex, NostrKeys.nprofileToHex("  NOSTR:${noRelay.uppercase()} \n"))
        assertEquals(hex, NostrKeys.nprofileToHex(noRelay.uppercase()))
        assertEquals(hex, NostrKeys.nprofileToHex("  $noRelay\t"))
    }

    // ---- what is refused
    @Test fun aKeyThatIsNot32Bytes_isRejected() {
        assertNull(NostrKeys.nprofileToHex(key31))
        assertNull(NostrKeys.nprofileToHex(key33))
    }

    @Test fun twoKeys_areRejected() {
        assertNull("two different keys", NostrKeys.nprofileToHex(twoKeys))
        assertNull("the same key twice", NostrKeys.nprofileToHex(twoSameKey))
    }

    @Test fun noKey_isRejected() {
        assertNull(NostrKeys.nprofileToHex(onlyRelay))
        assertNull(NostrKeys.nprofileToHex(Bech32.encode("nprofile", ByteArray(0))))
    }

    @Test fun aCutTlv_isRejected() {
        assertNull("a value longer than what is left", NostrKeys.nprofileToHex(cutValue))
        assertNull("a header with no length byte", NostrKeys.nprofileToHex(cutHeader))
    }

    @Test fun aStringOverAThousand_isRejected() {
        val text = profileOfPayload(PAYLOAD_FOR_1001_CHARS)
        assertEquals(1001, text.length)
        assertNull(NostrKeys.nprofileToHex(text))
    }

    @Test fun aLongProfileUnderTheLimit_isAccepted() {
        val text = profileOfPayload(PAYLOAD_FOR_999_CHARS)
        assertEquals(999, text.length)
        assertEquals(hex, NostrKeys.nprofileToHex(text))
        assertTrue("longer than an npub could ever be", text.length > Bech32.DEFAULT_MAX_LENGTH)
    }

    @Test fun otherKindsOfIdentifier_areRejected() {
        assertNull("a bare npub", NostrKeys.nprofileToHex(npub))
        assertNull("an nevent that carries a valid key entry", NostrKeys.nprofileToHex(neventWithKey))
        assertNull("a secret key", NostrKeys.nprofileToHex(nsec))
        assertNull(NostrKeys.nprofileToHex("nostr:$nsec"))
        listOf("", "   ", "nprofile1", "https://njump.me/$noRelay", "hello", "nostr:").forEach { assertNull(it, NostrKeys.nprofileToHex(it)) }
    }

    // ---- the one entry point the Add Source box uses
    @Test fun authorHexFrom_takesBothAndRefusesJunk() {
        assertEquals(hex, NostrKeys.authorHexFrom(npub))
        assertEquals(hex, NostrKeys.authorHexFrom("nostr:$npub"))
        assertEquals(hex, NostrKeys.authorHexFrom(noRelay))
        assertEquals(hex, NostrKeys.authorHexFrom(oneRelay))
        assertEquals(hex, NostrKeys.authorHexFrom("nostr:$oneRelay"))
        listOf("", "hello", nsec, "https://example.com", neventWithKey).forEach { assertNull(it, NostrKeys.authorHexFrom(it)) }
    }

    @Test fun anNpubOverNinety_isStillRefused() {
        val longNpub = Bech32.encode("npub", ByteArray(64))
        assertTrue(longNpub.length > Bech32.DEFAULT_MAX_LENGTH)
        assertNull(NostrKeys.npubToHex(longNpub))
        assertNull(NostrKeys.authorHexFrom(longNpub))
    }

    @Test fun isSecretKey_stillCatchesASecretKeyBeforeAnythingElse() {
        assertTrue(NostrKeys.isSecretKey(nsec))
        assertTrue(NostrKeys.isSecretKey("nostr:$nsec"))
        assertFalse(NostrKeys.isSecretKey(noRelay))
        assertFalse(NostrKeys.isSecretKey(oneRelay))
    }

    // ---- structure: nothing in the relay client can take a relay from a pasted text
    @Test fun theRelayClientCannotBeGivenARelay() {
        val fetches = NostrRelayClient::class.java.declaredMethods.filter { it.name == "fetch" }
        assertNotNull(fetches.singleOrNull())
        // fetch(authorHex: String) is a suspend function: one String plus the continuation, nothing else.
        val parameters = fetches.single().parameterTypes.toList()
        assertEquals(2, parameters.size)
        assertEquals(String::class.java, parameters.first())
    }
}
