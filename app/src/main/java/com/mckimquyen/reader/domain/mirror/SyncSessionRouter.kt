package com.mckimquyen.reader.domain.mirror

sealed interface SyncSessionResponse {
    data class Ciphertext(val bytes: ByteArray) : SyncSessionResponse
    data object NotFound : SyncSessionResponse
}

/** Exact-match route for a single immutable encrypted envelope; never exposes the mirror's JSON/SSE routes. */
class SyncSessionRouter(
    private val token: () -> MirrorToken?,
    private val ciphertext: () -> ByteArray?,
) {
    private val pattern = Regex("^/s/([0-9a-f]{32})$")

    fun route(request: MirrorRequest, nowMillis: Long): SyncSessionResponse {
        val match = pattern.matchEntire(request.path) ?: return SyncSessionResponse.NotFound
        val current = token() ?: return SyncSessionResponse.NotFound
        if (!current.matches(match.groupValues[1], nowMillis)) return SyncSessionResponse.NotFound
        return ciphertext()?.let { SyncSessionResponse.Ciphertext(it) } ?: SyncSessionResponse.NotFound
    }
}
