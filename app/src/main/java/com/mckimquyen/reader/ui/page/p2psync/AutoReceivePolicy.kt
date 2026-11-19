package com.mckimquyen.reader.ui.page.p2psync

import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.SessionTokenDerivation
import com.mckimquyen.reader.infrastructure.p2psync.DiscoveredPeer

/** The one paired device and the one broadcasting device that [AutoReceivePolicy] decided to receive from. */
class AutoReceivePick(val peer: PairedPeer, val broadcaster: DiscoveredPeer)

/**
 * When the sync screen may start a receive by itself: only when the choice is not a guess. The screen must be idle and on a Local
 * account, exactly one paired device must be usable (not [PairedPeer.needsRepair]), exactly one device must be broadcasting, and
 * that broadcast (its nonce) must not have been tried already, so a Cancel, an Apply or an error is never followed by the same
 * receive again. The result is only a preview: nothing is written before the user presses Apply.
 */
object AutoReceivePolicy {
    fun pick(
        paired: List<PairedPeer>,
        discovered: List<DiscoveredPeer>,
        idle: Boolean,
        isLocal: Boolean,
        triedNonces: Set<String>,
    ): AutoReceivePick? {
        if (!idle || !isLocal) return null
        val usable = paired.filter { !it.needsRepair }
        if (usable.size != 1 || discovered.size != 1) return null
        val broadcaster = discovered.single()
        if (nonceKey(broadcaster) in triedNonces) return null
        return AutoReceivePick(usable.single(), broadcaster)
    }

    fun nonceKey(peer: DiscoveredPeer): String = SessionTokenDerivation.nonceHex(peer.nonce)
}
