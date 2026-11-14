package com.mckimquyen.reader.infrastructure.social.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bip340Test {
    private class Vec(val index: Int, val pubkey: String, val message: String, val sig: String, val valid: Boolean, val note: String)

    private val vectors = listOf(
        Vec(0, "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", "0000000000000000000000000000000000000000000000000000000000000000", "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0", true, ""),
        Vec(1, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a", true, ""),
        Vec(2, "dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8", "7e2d58d8b3bcdf1abadec7829054f90dda9805aab56c77333024b9d0a508b75c", "5831aaeed7b44bb74e5eab94ba9d4294c49bcf2a60728d8b4c200f50dd313c1bab745879a5ad954a72c45a91c3a51d3c7adea98d82f8481e0e1e03674a6f3fb7", true, ""),
        Vec(3, "25d1dff95105f5253c4022f628a996ad3a0d95fbf21d468a1b33f8c160d8f517", "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "7eb0509757e246f19449885651611cb965ecc1a187dd51b64fda1edc9637d5ec97582b9cb13db3933705b32ba982af5af25fd78881ebb32771fc5922efc66ea3", true, "test fails if msg is reduced modulo p or n"),
        Vec(4, "d69c3509bb99e412e68b0fe8544e72837dfa30746d8be2aa65975f29d22dc7b9", "4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703", "00000000000000000000003b78ce563f89a0ed9414f5aa28ad0d96d6795f9c6376afb1548af603b3eb45c9f8207dee1060cb71c04e80f593060b07d28308d7f4", true, ""),
        Vec(5, "eefdea4cdb677750a420fee807eacf21eb9898ae79b9768766e4faa04a2d4a34", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e17776969e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "public key not on the curve"),
        Vec(6, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a14602975563cc27944640ac607cd107ae10923d9ef7a73c643e166be5ebeafa34b1ac553e2", false, "has_even_y(R) is false"),
        Vec(7, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "1fa62e331edbc21c394792d2ab1100a7b432b013df3f6ff4f99fcb33e0e1515f28890b3edb6e7189b630448b515ce4f8622a954cfe545735aaea5134fccdb2bd", false, "negated message"),
        Vec(8, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e177769961764b3aa9b2ffcb6ef947b6887a226e8d7c93e00c5ed0c1834ff0d0c2e6da6", false, "negated s value"),
        Vec(9, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "0000000000000000000000000000000000000000000000000000000000000000123dda8328af9c23a94c1feecfd123ba4fb73476f0d594dcb65c6425bd186051", false, "sG - eP is infinite. Test fails in single verification if has_even_y(i"),
        Vec(10, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "00000000000000000000000000000000000000000000000000000000000000017615fbaf5ae28864013c099742deadb4dba87f11ac6754f93780d5a1837cf197", false, "sG - eP is infinite. Test fails in single verification if has_even_y(i"),
        Vec(11, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "4a298dacae57395a15d0795ddbfd1dcb564da82b0f269bc70a74f8220429ba1d69e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "sig[0:32] is not an X coordinate on the curve"),
        Vec(12, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f69e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "sig[0:32] is equal to field size"),
        Vec(13, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e177769fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", false, "sig[32:64] is equal to curve order"),
        Vec(14, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e17776969e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "public key is not a valid X coordinate because it exceeds the field si"),
        Vec(15, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "", "71535db165ecd9fbbc046e5ffaea61186bb6ad436732fccc25291a55895464cf6069ce26bf03466228f19a3a62db8a649f2d560fac652827d1af0574e427ab63", true, "message of size 0 (added 2022-12)"),
        Vec(16, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "11", "08a20a0afef64124649232e0693c583ab1b9934ae63b4c3511f3ae1134c6a303ea3173bfea6683bd101fa5aa5dbc1996fe7cacfc5a577d33ec14564cec2bacbf", true, "message of size 1 (added 2022-12)"),
        Vec(17, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "0102030405060708090a0b0c0d0e0f1011", "5130f39a4059b43bc7cac09a19ece52b5d8699d1a71e3c52da9afdb6b50ac370c4a482b77bf960f8681540e25b6771ece1e5a37fd80e5a51897c5566a97ea5a5", true, "message of size 17 (added 2022-12)"),
        Vec(18, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "99999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999", "403b12b0d8555a344175ea7ec746566303321e5dbfa8be6f091635163eca79a8585ed3e3170807e7c03b720fc54c7b23897fcba0e9d0b4a06894cfd249f22367", true, "message of size 100 (added 2022-12)"),
    )

    @Test fun officialVectors_giveTheExpectedAnswer() {
        vectors.forEach {
            assertEquals("vector ${it.index} ${it.note}", it.valid, Bip340.verify(it.pubkey, it.message, it.sig))
        }
    }

    private val good = vectors[1]

    @Test fun aSingleFlippedBit_inTheSignature_isRejected() {
        for (i in listOf(0, 31, 32, 63)) {
            val bytes = good.sig.chunked(2).toMutableList()
            bytes[i] = "%02x".format(bytes[i].toInt(16) xor 1)
            assertFalse("byte $i", Bip340.verify(good.pubkey, good.message, bytes.joinToString("")))
        }
    }

    @Test fun aSingleFlippedBit_inTheMessage_isRejected() {
        val flipped = "%02x".format(good.message.take(2).toInt(16) xor 1) + good.message.drop(2)
        assertFalse(Bip340.verify(good.pubkey, flipped, good.sig))
    }

    @Test fun aDifferentPublicKey_isRejected() = assertFalse(Bip340.verify(vectors[2].pubkey, good.message, good.sig))

    @Test fun uppercaseHex_isAccepted() =
        assertTrue(Bip340.verify(good.pubkey.uppercase(), good.message.uppercase(), good.sig.uppercase()))

    @Test fun malformedInput_returnsFalseInsteadOfThrowing() {
        assertFalse(Bip340.verify("", good.message, good.sig))
        assertFalse(Bip340.verify(good.pubkey.dropLast(2), good.message, good.sig))
        assertFalse(Bip340.verify(good.pubkey, good.message, good.sig.dropLast(2)))
        assertFalse(Bip340.verify(good.pubkey, good.message, good.sig + "00"))
        assertFalse(Bip340.verify("zz".repeat(32), good.message, good.sig))
        assertFalse(Bip340.verify(good.pubkey, "abc", good.sig))
        assertFalse(Bip340.verify(good.pubkey, good.message, "g".repeat(128)))
    }

    // "+f" is accepted by String.toInt(16) as 15, so a hex check that only looks at the length would read it as 0x0f.
    @Test fun aPlusSignIsNotAHexDigit() {
        val pubkey = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
        val sig = "eaa29ccc4a78283dc0b24320d45feca6ec32ed34b18f892538a6f6b5ea622766bb9d8faac6b04c9c7db85fabd81b78605631be3df0ae2d472f15d694852f580f"
        assertTrue(Bip340.verify(pubkey, "0f", sig))
        assertFalse(Bip340.verify(pubkey, "+f", sig))
        assertFalse(Bip340.verify(pubkey, "-f", sig))
    }

    // A note signed by the independent Python signer (see Task 3 fixtures): its id is the signed message.
    @Test fun aSignedNostrEventId_verifies() = assertTrue(
        Bip340.verify(
            "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659",
            "c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e",
            "4993b5aead88e3b16b5c15a8616d296ef70124ea8e8c0374c80e9f220dbd82facfe4ff1559285d79e2ce89b8b9bf5425b5255314bc0887d62c43d5bedb798623",
        ),
    )
}
