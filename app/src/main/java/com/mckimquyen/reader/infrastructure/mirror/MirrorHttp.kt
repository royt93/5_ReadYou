package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorLimits
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/** The few HTTP details the mirror needs: read a bounded request head, write a closed reply or open an event stream. */
internal object MirrorHttp {
    private val terminator = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
    private const val DRAIN_TIMEOUT_MILLIS = 500
    private const val DRAIN_CHUNK = 4096
    private const val DRAIN_LIMIT_BYTES = 64 * 1024
    private const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'"

    sealed interface Head {
        data class Text(val value: String) : Head
        data object TooLarge : Head
        data object Incomplete : Head
    }

    /** Reads up to [MirrorLimits.MAX_REQUEST_BYTES] bytes until the blank line; never buffers more than that. */
    fun readHead(input: InputStream): Head {
        val buffer = ByteArray(MirrorLimits.MAX_REQUEST_BYTES + 1)
        var size = 0
        while (true) {
            if (size == buffer.size) return Head.TooLarge
            val next = input.read()
            if (next < 0) return Head.Incomplete
            buffer[size++] = next.toByte()
            if (endsWithTerminator(buffer, size)) {
                return if (size > MirrorLimits.MAX_REQUEST_BYTES) Head.TooLarge
                else Head.Text(String(buffer, 0, size, Charsets.ISO_8859_1))
            }
        }
    }

    private fun endsWithTerminator(buffer: ByteArray, size: Int): Boolean {
        if (size < terminator.size) return false
        return terminator.indices.all { buffer[size - terminator.size + it] == terminator[it] }
    }

    fun reply(
        out: OutputStream,
        status: Int,
        contentType: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap(),
        isPage: Boolean = false,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = StringBuilder("HTTP/1.1 $status ${reason(status)}\r\n")
        head.append("Content-Type: $contentType\r\nContent-Length: ${bytes.size}\r\n")
        appendCommon(head)
        if (isPage) head.append("Content-Security-Policy: $CONTENT_SECURITY_POLICY\r\n")
        extraHeaders.forEach { (k, v) -> head.append("$k: $v\r\n") }
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    fun reply(
        out: OutputStream,
        status: Int,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val head = StringBuilder("HTTP/1.1 $status ${reason(status)}\r\n")
        head.append("Content-Type: $contentType\r\nContent-Length: ${body.size}\r\n")
        appendCommon(head)
        extraHeaders.forEach { (key, value) -> head.append("$key: $value\r\n") }
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    fun openEventStream(out: OutputStream) {
        val head = StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\n")
        appendCommon(head)
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    fun event(out: OutputStream, name: String, data: String) {
        out.write("event: $name\ndata: $data\n\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    fun keepAlive(out: OutputStream) {
        out.write(": keepalive\n\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun appendCommon(head: StringBuilder) {
        head.append("Cache-Control: no-store\r\n")
        head.append("X-Content-Type-Options: nosniff\r\n")
        head.append("Referrer-Policy: no-referrer\r\n")
        head.append("Connection: close\r\n")
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        414 -> "URI Too Long"
        429 -> "Too Many Requests"
        431 -> "Request Header Fields Too Large"
        503 -> "Service Unavailable"
        else -> "Error"
    }

    /** Throws away what the client is still sending, bounded in bytes and time, so the reply can be read before close. */
    fun drain(socket: Socket) {
        runCatching {
            socket.soTimeout = DRAIN_TIMEOUT_MILLIS
            val input = socket.getInputStream()
            val scratch = ByteArray(DRAIN_CHUNK)
            var total = 0
            while (total < DRAIN_LIMIT_BYTES) {
                val n = input.read(scratch)
                if (n < 0) break
                total += n
            }
        }
    }

    fun closeQuietly(socket: Socket) {
        runCatching { socket.close() }
    }
}
