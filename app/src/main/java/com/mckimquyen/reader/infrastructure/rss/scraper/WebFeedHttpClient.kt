package com.mckimquyen.reader.infrastructure.rss.scraper

import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient

internal object WebFeedHttpClient {
    fun create(url: String, dns: Dns = Dns.SYSTEM): OkHttpClient {
        val uri = URI(url)
        require(uri.scheme == HTTPS_SCHEME && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "Only public HTTPS URLs are supported"
        }
        val host = uri.host
        require(!host.equals(LOCALHOST, ignoreCase = true) && !host.endsWith(LOCAL_DOMAIN_SUFFIX, ignoreCase = true)) {
            "Local destinations are not allowed"
        }
        val resolvedAddresses = if (isIpLiteral(host)) {
            listOf(InetAddress.getByName(host))
        } else {
            dns.lookup(host)
        }
        require(WebFeedUrlPolicy.validate(url, resolvedAddresses) == WebFeedUrlValidation.Valid) {
            "Unsafe or unresolved destination"
        }
        val pinnedDns = Dns { requestedHost ->
            require(requestedHost.equals(host, ignoreCase = true)) { "DNS lookup outside validated host" }
            resolvedAddresses
        }
        return newBuilder()
            .dns(pinnedDns)
            .build()
    }

    internal fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun isIpLiteral(host: String): Boolean =
        host.contains(':') || host.matches(IPV4_LITERAL_REGEX)

    private const val HTTPS_SCHEME = "https"
    private const val LOCALHOST = "localhost"
    private const val LOCAL_DOMAIN_SUFFIX = ".local"
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 15L
    private const val CALL_TIMEOUT_SECONDS = 20L
    private val IPV4_LITERAL_REGEX = Regex("[0-9.]+")
}
