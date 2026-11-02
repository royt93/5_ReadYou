package com.mckimquyen.reader.domain.mirror

data class MirrorRequest(val path: String)

sealed interface MirrorParseResult {
    data class Ok(val request: MirrorRequest) : MirrorParseResult

    /** [status] is the HTTP status the server should answer with before closing the connection. */
    data class Rejected(val status: Int) : MirrorParseResult
}

/**
 * Reads only what the mirror needs: the request line of a plain `GET`. Headers are counted and bounded but never
 * interpreted, so nothing a client sends can change server behaviour.
 */
object MirrorRequestParser {
    private const val STATUS_BAD_REQUEST = 400
    private const val STATUS_METHOD_NOT_ALLOWED = 405
    private const val STATUS_URI_TOO_LONG = 414
    private const val STATUS_HEADERS_TOO_LARGE = 431
    private const val CRLF = "\r\n"
    private const val FIRST_PRINTABLE = 0x21
    private const val LAST_PRINTABLE = 0x7E
    private val versionPattern = Regex("^HTTP/1\\.[01]$")

    fun parse(head: String): MirrorParseResult {
        if (head.toByteArray(Charsets.UTF_8).size > MirrorLimits.MAX_REQUEST_BYTES) return reject(STATUS_HEADERS_TOO_LARGE)
        if (hasBareLineFeed(head)) return reject(STATUS_BAD_REQUEST)

        val lines = head.split(CRLF)
        val headerCount = lines.drop(1).takeWhile { it.isNotEmpty() }.size
        if (headerCount > MirrorLimits.MAX_HEADER_LINES) return reject(STATUS_BAD_REQUEST)

        val parts = lines.first().split(' ')
        if (parts.size != 3 || parts.any { it.isEmpty() }) return reject(STATUS_BAD_REQUEST)
        val (method, path, version) = parts
        if (method != "GET") return reject(STATUS_METHOD_NOT_ALLOWED)
        if (!versionPattern.matches(version)) return reject(STATUS_BAD_REQUEST)
        if (!path.startsWith("/")) return reject(STATUS_BAD_REQUEST)
        if (path.length > MirrorLimits.MAX_PATH_CHARS) return reject(STATUS_URI_TOO_LONG)
        if (path.any { it.code !in FIRST_PRINTABLE..LAST_PRINTABLE }) return reject(STATUS_BAD_REQUEST)
        return MirrorParseResult.Ok(MirrorRequest(path))
    }

    private fun reject(status: Int) = MirrorParseResult.Rejected(status)

    private fun hasBareLineFeed(text: String): Boolean =
        text.indices.any { i -> text[i] == '\n' && (i == 0 || text[i - 1] != '\r') }
}
