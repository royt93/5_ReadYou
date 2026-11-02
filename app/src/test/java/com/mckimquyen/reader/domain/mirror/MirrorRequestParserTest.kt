package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorRequestParserTest {
    private fun get(path: String) = "GET $path HTTP/1.1\r\nHost: x\r\n\r\n"
    private fun rejected(head: String) = (MirrorRequestParser.parse(head) as MirrorParseResult.Rejected).status

    @Test fun parsesAGetRequestLine() {
        assertEquals(MirrorParseResult.Ok(MirrorRequest("/m/abc")), MirrorRequestParser.parse(get("/m/abc")))
        assertEquals(MirrorParseResult.Ok(MirrorRequest("/")), MirrorRequestParser.parse("GET / HTTP/1.0\r\n\r\n"))
    }

    @Test fun nonGetMethodsGet405() {
        listOf("POST", "HEAD", "PUT", "DELETE", "OPTIONS", "get").forEach {
            assertEquals(it, 405, rejected("$it / HTTP/1.1\r\n\r\n"))
        }
    }

    @Test fun malformedRequestLinesGet400() {
        listOf("GET /\r\n\r\n", "GET / HTTP/1.1 extra\r\n\r\n", "\r\n\r\n", "GET  / HTTP/1.1\r\n\r\n",
            "GET / FTP/1.1\r\n\r\n", "GET http://x/ HTTP/1.1\r\n\r\n", "GET m HTTP/1.1\r\n\r\n").forEach {
            assertEquals(it, 400, rejected(it))
        }
    }

    @Test fun lineEndingsMustBeCrlf() {
        assertEquals(400, rejected("GET / HTTP/1.1\n\n"))
    }

    @Test fun controlAndNonAsciiCharactersGet400() {
        assertEquals(400, rejected(get("/m/\u0000")))
        assertEquals(400, rejected(get("/m/é")))
        assertEquals(400, rejected(get("/m/a\tb")))
    }

    @Test fun overlongPathGets414() {
        assertEquals(414, rejected(get("/" + "a".repeat(MirrorLimits.MAX_PATH_CHARS))))
    }

    @Test fun tooManyHeaderLinesGet400() {
        val headers = (1..MirrorLimits.MAX_HEADER_LINES + 1).joinToString("") { "H$it: v\r\n" }
        assertEquals(400, rejected("GET / HTTP/1.1\r\n$headers\r\n"))
    }

    @Test fun oversizedHeadGets431() {
        val big = "X: " + "a".repeat(MirrorLimits.MAX_REQUEST_BYTES) + "\r\n"
        assertEquals(431, rejected("GET / HTTP/1.1\r\n$big\r\n"))
    }
}
