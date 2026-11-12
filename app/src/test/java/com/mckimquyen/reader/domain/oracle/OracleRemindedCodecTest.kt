package com.mckimquyen.reader.domain.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleRemindedCodecTest {

    @Test fun capIsAHardCodedNumber() = assertEquals(200, OracleRemindedCodec.REMINDED_CAP)

    @Test fun decode_nullOrBlank_isEmpty() {
        assertTrue(OracleRemindedCodec.decode(null).isEmpty())
        assertTrue(OracleRemindedCodec.decode("").isEmpty())
        assertTrue(OracleRemindedCodec.decode("\n \n").isEmpty())
    }

    @Test fun roundTrip_keepsOrderAndIds() {
        val ids = listOf("1\$aaa", "1\$bbb", "2\$ccc")
        assertEquals(ids, OracleRemindedCodec.decode(OracleRemindedCodec.encode(ids)))
    }

    @Test fun decode_trimsAndDropsDuplicates_keepingTheFirst() {
        assertEquals(listOf("a", "b"), OracleRemindedCodec.decode(" a \nb\na\n\nb"))
    }

    @Test fun append_addsNewIdsAtTheEnd_withoutDuplicates() {
        assertEquals(listOf("a", "b", "c"), OracleRemindedCodec.append(listOf("a", "b"), listOf("b", "c")))
    }

    @Test fun append_capsToTheNewestTwoHundred_droppingTheOldest() {
        val existing = (1..200).map { "old$it" }
        val out = OracleRemindedCodec.append(existing, listOf("new1", "new2"))
        assertEquals(200, out.size)
        assertEquals("old3", out.first())
        assertEquals("new2", out.last())
        assertTrue("old1" !in out && "old2" !in out)
    }

    @Test fun append_exactlyAtTheCap_dropsNothing() {
        val existing = (1..199).map { "old$it" }
        val out = OracleRemindedCodec.append(existing, listOf("new1"))
        assertEquals(200, out.size)
        assertEquals("old1", out.first())
    }

    @Test fun prune_keepsOnlyIdsThatStillHaveAnOpenBet() {
        assertEquals(listOf("b"), OracleRemindedCodec.prune(listOf("a", "b", "c"), setOf("b", "z")))
        assertTrue(OracleRemindedCodec.prune(listOf("a"), emptySet()).isEmpty())
    }

    @Test fun encode_emptyIsEmpty() = assertEquals("", OracleRemindedCodec.encode(emptyList()))
}
