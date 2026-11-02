package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRouterTest {
    private val now = 5_000L
    private val token = MirrorToken("0123456789abcdef0123456789abcdef", now + 60_000)
    private val snapshot = MirrorSnapshot("T", "S", 1L, listOf("p"), false, 1L)
    private fun router(t: MirrorToken? = token, s: MirrorSnapshot? = snapshot) = MirrorRouter({ t }, { s })
    private fun route(path: String, at: Long = now, r: MirrorRouter = router()) = r.route(MirrorRequest(path), at)
    private val t = token.value

    @Test fun threeRoutesWithAValidToken() {
        assertEquals(MirrorResponse.Page(MirrorHtml.PAGE), route("/m/$t"))
        assertEquals(MirrorResponse.State(snapshot.toJson()), route("/m/$t/state"))
        assertEquals(MirrorResponse.Events, route("/m/$t/events"))
    }

    @Test fun stateWithoutASnapshotIsAnEmptyButValidJson() {
        val state = route("/m/$t/state", r = router(s = null)) as MirrorResponse.State
        assertTrue(state.json.startsWith("{") && state.json.endsWith("}"))
    }

    @Test fun wrongExpiredAndMissingTokensAllGiveTheSameNotFound() {
        val wrong = route("/m/" + "f".repeat(32))
        val expired = route("/m/$t", at = token.expiresAtMillis)
        val none = route("/m/$t", r = router(t = null))
        assertEquals(MirrorResponse.NotFound, wrong)
        assertEquals(wrong, expired)
        assertEquals(wrong, none)
    }

    @Test fun everyOtherPathIsNotFound() {
        listOf("/", "/m", "/m/", "/m/$t/", "/m/$t/state/", "/m/$t/other", "/m/$t/../state", "/m/$t%2fstate",
            "/m/$t/%73tate", "/m/$t?x=1", "/m/${t.uppercase()}", "//m/$t", "/m/$t/events/x", "/m/$t/state?a", "/m//$t",
            "/favicon.ico", "/robots.txt", "/m/${t}x", "/m/${t.dropLast(1)}", "/../etc/passwd", "/m/$t/./state").forEach {
            assertEquals(it, MirrorResponse.NotFound, route(it))
        }
    }

    @Test fun routingNeverTouchesTheSnapshotForAWrongToken() {
        var reads = 0
        val r = MirrorRouter({ token }, { reads++; snapshot })
        r.route(MirrorRequest("/m/" + "0".repeat(32) + "/state"), now)
        assertEquals(0, reads)
    }
}
