package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressTest {
    @Test fun acceptsExactlyThePrivateIpv4Ranges() {
        listOf("10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.254", "192.168.0.1", "192.168.255.254").forEach {
            assertTrue(it, LanAddress.isPrivateIpv4(it))
        }
    }

    @Test fun rejectsPublicLoopbackLinkLocalAnyAndOutOfRangeAddresses() {
        listOf("0.0.0.0", "127.0.0.1", "169.254.1.1", "8.8.8.8", "172.15.255.255", "172.32.0.1", "192.167.1.1", "192.169.1.1",
            "100.64.0.1", "224.0.0.1", "255.255.255.255", "11.0.0.1").forEach {
            assertFalse(it, LanAddress.isPrivateIpv4(it))
        }
    }

    @Test fun rejectsMalformedAndNonIpv4Text() {
        listOf("", " ", "10.0.0", "10.0.0.256", "10.0.0.1.1", "10.0.0.x", "010.0.0.1", "fe80::1", "::1", "10.0.0.-1", "10.0.0.1 ", "+10.0.0.1")
            .forEach { assertFalse("'$it'", LanAddress.isPrivateIpv4(it)) }
    }

    @Test fun pickReturnsTheFirstPrivateAddressInGivenOrder() {
        assertEquals("192.168.1.20", LanAddress.pick(listOf("fe80::1", "8.8.8.8", "192.168.1.20", "10.0.0.5")))
    }

    @Test fun pickReturnsNullWhenThereIsNoPrivateAddress() {
        assertNull(LanAddress.pick(emptyList()))
        assertNull(LanAddress.pick(listOf("127.0.0.1", "8.8.8.8", "169.254.9.9")))
    }
}
