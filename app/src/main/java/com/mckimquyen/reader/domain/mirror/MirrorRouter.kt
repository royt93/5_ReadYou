package com.mckimquyen.reader.domain.mirror

sealed interface MirrorResponse {
    data class Page(val html: String) : MirrorResponse
    data class State(val json: String) : MirrorResponse
    data object Events : MirrorResponse

    /** One shared answer for a wrong token, an expired token, no session and every unknown path. */
    data object NotFound : MirrorResponse

    companion object {
        const val NOT_FOUND_BODY = "Not found"
    }
}

/**
 * Exact-match routing for the three mirror endpoints. Nothing is decoded or normalised: percent signs, dots, queries
 * and trailing slashes simply do not match, so there is no path to traverse. The snapshot is only read once the token
 * checked out.
 */
class MirrorRouter(
    private val token: () -> MirrorToken?,
    private val snapshot: () -> MirrorSnapshot?,
) {
    private val pattern = Regex("^/m/([0-9a-f]{32})(/state|/events)?$")

    fun route(request: MirrorRequest, nowMillis: Long): MirrorResponse {
        val match = pattern.matchEntire(request.path) ?: return MirrorResponse.NotFound
        val current = token() ?: return MirrorResponse.NotFound
        if (!current.matches(match.groupValues[1], nowMillis)) return MirrorResponse.NotFound
        return when (match.groupValues[2]) {
            "" -> MirrorResponse.Page(MirrorHtml.PAGE)
            "/state" -> MirrorResponse.State(snapshot()?.toJson() ?: MirrorSnapshot.EMPTY_JSON)
            else -> MirrorResponse.Events
        }
    }
}
