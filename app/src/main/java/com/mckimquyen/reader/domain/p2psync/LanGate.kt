package com.mckimquyen.reader.domain.p2psync

/** Answers "may this app use the local network right now?" so view models never touch Android permissions. */
fun interface LanGate {
    fun granted(): Boolean
}
