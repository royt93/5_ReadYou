package com.mckimquyen.reader.domain.mirror

/**
 * Decides which address the mirror may bind to. Only RFC 1918 private IPv4 ranges qualify, so the server can never be
 * reachable from a public address or a cellular interface even if the platform reports one.
 */
object LanAddress {
    private val dotted = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private const val MAX_OCTET = 255
    private const val CLASS_A = 10
    private const val CLASS_B = 172
    private const val CLASS_B_FIRST = 16
    private const val CLASS_B_LAST = 31
    private const val CLASS_C = 192
    private const val CLASS_C_SECOND = 168

    fun isPrivateIpv4(address: String): Boolean {
        val groups = dotted.matchEntire(address)?.groupValues?.drop(1) ?: return false
        // "010" is rejected: some stacks read a leading zero as octal, which would make the text mean another address.
        if (groups.any { it.length > 1 && it.startsWith("0") }) return false
        val (a, b) = groups.map { it.toInt() }.also { octets -> if (octets.any { it > MAX_OCTET }) return false }
        return a == CLASS_A ||
            (a == CLASS_B && b in CLASS_B_FIRST..CLASS_B_LAST) ||
            (a == CLASS_C && b == CLASS_C_SECOND)
    }

    fun pick(addresses: List<String>): String? = addresses.firstOrNull(::isPrivateIpv4)
}
