package com.mckimquyen.reader.domain.p2psync

import com.mckimquyen.reader.domain.mirror.LanAddress
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class SyncSessionTransport internal constructor(
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val isAllowedHost: (String) -> Boolean,
) {
    constructor() : this(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS, LanAddress::isPrivateIpv4)

    fun fetch(rawUrl: String): ByteArray {
        val session = requireNotNull(SyncSessionUrl.parse(rawUrl, isAllowedHost)) { "Invalid sync session URL" }
        val connection = URL(URI(rawUrl).toASCIIString()).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/octet-stream")
            val status = connection.responseCode
            require(status == HTTP_OK) { "Sync session request failed" }
            val contentType = connection.contentType?.substringBefore(';')?.trim()
            require(contentType == "application/octet-stream") { "Unexpected sync response type" }
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            require(contentLength in SyncEnvelopeCrypto.OVERHEAD_BYTES.toLong()..SyncLimits.MAX_ENVELOPE_BYTES.toLong()) {
                "Sync response size is outside the allowed range"
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(minOf(contentLength.toInt(), INITIAL_BUFFER_BYTES))
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= SyncLimits.MAX_ENVELOPE_BYTES) { "Sync response exceeds size limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(body.size.toLong() == contentLength) { "Incomplete sync response" }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000
        const val HTTP_OK = 200
        const val READ_BUFFER_BYTES = 8 * 1024
        const val INITIAL_BUFFER_BYTES = 32 * 1024
    }
}
