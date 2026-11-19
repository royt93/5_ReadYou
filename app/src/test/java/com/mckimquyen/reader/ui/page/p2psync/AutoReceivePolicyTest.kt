package com.mckimquyen.reader.ui.page.p2psync

import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import com.mckimquyen.reader.domain.p2psync.SessionTokenDerivation
import com.mckimquyen.reader.infrastructure.p2psync.DiscoveredPeer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AutoReceivePolicyTest {
    private fun paired(id: String, needsRepair: Boolean = false, status: PairingStatus = PairingStatus.CONFIRMED) =
        PairedPeer(id, "label-$id", status, 1L, needsRepair)

    private fun nearby(name: String) = DiscoveredPeer(name, "192.168.1.30", 4321, SessionTokenDerivation.newNonce())

    private fun pick(
        paired: List<PairedPeer> = listOf(paired("p1")),
        discovered: List<DiscoveredPeer> = listOf(nearby("n1")),
        idle: Boolean = true,
        isLocal: Boolean = true,
        tried: Set<String> = emptySet(),
    ) = AutoReceivePolicy.pick(paired, discovered, idle, isLocal, tried)

    @Test fun oneUsablePairedDevice_andOneBroadcaster_isPicked() {
        val broadcaster = nearby("n1")
        val result = pick(discovered = listOf(broadcaster))
        assertNotNull(result)
        assertEquals("p1", result!!.peer.peerId)
        assertSame(broadcaster, result.broadcaster)
    }

    @Test fun anUnconfirmedPairedDevice_isPickedToo() {
        assertNotNull(pick(paired = listOf(paired("p1", status = PairingStatus.UNCONFIRMED))))
    }

    @Test fun noPairedDevice_picksNothing() = assertNull(pick(paired = emptyList()))

    @Test fun twoUsablePairedDevices_pickNothing() = assertNull(pick(paired = listOf(paired("p1"), paired("p2"))))

    @Test fun aDeviceThatNeedsRepair_isNotUsable_andDoesNotCount() {
        assertNull(pick(paired = listOf(paired("p1", needsRepair = true))))
        assertEquals("p2", pick(paired = listOf(paired("p1", needsRepair = true), paired("p2")))!!.peer.peerId)
    }

    @Test fun noBroadcaster_picksNothing() = assertNull(pick(discovered = emptyList()))

    @Test fun twoBroadcasters_pickNothing() = assertNull(pick(discovered = listOf(nearby("n1"), nearby("n2"))))

    @Test fun aScreenThatIsNotIdle_picksNothing() = assertNull(pick(idle = false))

    @Test fun anAccountThatIsNotLocal_picksNothing() = assertNull(pick(isLocal = false))

    @Test fun aBroadcastAlreadyTried_isNotPickedAgain_butAnotherOneIs() {
        val first = nearby("n1")
        assertNull(pick(discovered = listOf(first), tried = setOf(AutoReceivePolicy.nonceKey(first))))
        assertNotNull(pick(discovered = listOf(nearby("n2")), tried = setOf(AutoReceivePolicy.nonceKey(first))))
    }
}
