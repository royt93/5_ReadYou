package com.mckimquyen.reader.infrastructure.rss.scraper

import java.net.InetAddress
import java.net.URI

internal enum class WebFeedUrlValidation {
    Valid,
    InvalidUrl,
    UnsafeAddress,
    DnsFailure,
}

internal object WebFeedUrlPolicy {
    fun validate(url: String, resolvedAddresses: List<InetAddress>): WebFeedUrlValidation {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return WebFeedUrlValidation.InvalidUrl
        if (uri.scheme != HTTPS_SCHEME || uri.userInfo != null || uri.host.isNullOrBlank()) {
            return WebFeedUrlValidation.InvalidUrl
        }
        val host = uri.host
        val normalizedHost = host.trimEnd('.').lowercase()
        if (normalizedHost == LOCALHOST || normalizedHost.endsWith(LOCAL_DOMAIN_SUFFIX) ||
            normalizedHost.endsWith(LOCALHOST_SUFFIX) || normalizedHost.endsWith(HOME_ARPA_SUFFIX)
        ) {
            return WebFeedUrlValidation.UnsafeAddress
        }
        val addresses = resolvedAddresses.ifEmpty {
            if (isLiteralAddress(host)) return WebFeedUrlValidation.UnsafeAddress
            return WebFeedUrlValidation.DnsFailure
        }
        if (addresses.any { it.isUnsafeForWebFeed() }) return WebFeedUrlValidation.UnsafeAddress
        return WebFeedUrlValidation.Valid
    }

    private fun isLiteralAddress(host: String): Boolean =
        host.any { it == ':' || it == '.' } && runCatching { InetAddress.getByName(host) }.isSuccess

    private fun InetAddress.isUnsafeForWebFeed(): Boolean {
        val bytes = address.map { it.toInt() and 0xff }
        if (bytes.size == IPV4_BYTE_COUNT) {
            val first = bytes[0]
            val second = bytes[1]
            val isCarrierGradeNat = first == 100 && second in 64..127
            val isProtocolAssignment = first == 192 && second == 0 && (bytes[2] == 0 || bytes[2] == 2)
            val isDocumentation = first == 198 && (second == 18 || second == 19 || second == 51 && bytes[2] == 100) ||
                first == 203 && second == 0 && bytes[2] == 113
            val isIetfProtocolAssignment = first == 192 && second == 31 && bytes[2] == 196 ||
                first == 192 && second == 52 && bytes[2] == 193
            val isReserved = first == 0 || first >= 240 || first == 192 && second == 88 && bytes[2] == 99
            return isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
                isSiteLocalAddress || isMulticastAddress || isCarrierGradeNat ||
                isProtocolAssignment || isIetfProtocolAssignment || isDocumentation || isReserved
        }
        return isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
            isSiteLocalAddress || isMulticastAddress || isIpv6UniqueLocal() || isReservedIpv6() ||
            isIpv6SpecialPurpose()
    }

    private fun InetAddress.isIpv6UniqueLocal(): Boolean =
        address.size == IPV6_BYTE_COUNT && (address[0].toInt() and 0xfe) == IPV6_UNIQUE_LOCAL_PREFIX

    private fun InetAddress.isIpv6SpecialPurpose(): Boolean {
        if (address.size != IPV6_BYTE_COUNT) return false
        val first = address[0].toInt() and 0xff
        val second = address[1].toInt() and 0xff
        val third = address[2].toInt() and 0xff
        val fourth = address[3].toInt() and 0xff
        val isDiscardOnly = first == 0x01 && second == 0x00
        val secondWord = (third shl 8) or fourth
        val isIetfProtocolAssignment = first == 0x20 && second == 0x01 && secondWord <= 0x01ff
        val isIpv4Mapped = address.take(10).all { it == 0.toByte() } &&
            address[10] == 0xff.toByte() && address[11] == 0xff.toByte()
        if (isIpv4Mapped) {
            val mappedIpv4 = InetAddress.getByAddress(address.copyOfRange(12, IPV6_BYTE_COUNT))
            if (mappedIpv4.isUnsafeForWebFeed()) return true
        }
        return isDiscardOnly || isIetfProtocolAssignment || isIpv4Mapped
    }

    private fun InetAddress.isReservedIpv6(): Boolean {
        if (address.size != IPV6_BYTE_COUNT) return false
        val first = address[0].toInt() and 0xff
        val second = address[1].toInt() and 0xff
        val third = address[2].toInt() and 0xff
        val fourth = address[3].toInt() and 0xff
        val isDocumentation = first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8
        val isTeredo = first == 0x20 && second == 0x01 && third == 0 && fourth == 0
        val isSixToFour = first == 0x20 && second == 0x02
        val isWellKnownNat64 = first == 0x00 && second == 0x64 && third == 0xff && fourth == 0x9b &&
            address.copyOfRange(4, 12).all { it == 0.toByte() }
        val isLocalUseNat64 = first == 0x00 && second == 0x64 && third == 0xff && fourth == 0x9b &&
            address[4].toInt() == 0x00 && address[5].toInt() == 0x01
        if (isWellKnownNat64 || isLocalUseNat64) return true
        if (isSixToFour || isTeredo) {
            val offset = if (isSixToFour) SIX_TO_FOUR_IPV4_OFFSET else TEREDO_IPV4_OFFSET
            val embeddedIpv4Bytes = address.copyOfRange(offset, offset + IPV4_BYTE_COUNT)
            if (isTeredo) embeddedIpv4Bytes.indices.forEach { index ->
                embeddedIpv4Bytes[index] = embeddedIpv4Bytes[index].toInt().inv().toByte()
            }
            if (InetAddress.getByAddress(embeddedIpv4Bytes).isUnsafeForWebFeed()) return true
        }
        return isDocumentation || isTeredo || isSixToFour
    }

    private const val IPV4_BYTE_COUNT = 4
    private const val IPV6_BYTE_COUNT = 16
    private const val SIX_TO_FOUR_IPV4_OFFSET = 2
    private const val TEREDO_IPV4_OFFSET = 12
    private const val IPV6_UNIQUE_LOCAL_PREFIX = 0xfc

    private const val HTTPS_SCHEME = "https"
    private const val LOCALHOST = "localhost"
    private const val LOCAL_DOMAIN_SUFFIX = ".local"
    private const val LOCALHOST_SUFFIX = ".localhost"
    private const val HOME_ARPA_SUFFIX = ".home.arpa"
}
