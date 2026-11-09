package com.mckimquyen.reader.domain.p2psync

enum class PairingStatus { UNCONFIRMED, CONFIRMED }

data class PairedPeer(
    val peerId: String,
    val label: String,
    val status: PairingStatus,
    val createdAtMillis: Long,
    /** true when the device-bound Keystore key is gone or this record's ciphertext cannot be opened. */
    val needsRepair: Boolean,
)
