package com.mckimquyen.reader.domain.model.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class FeedErrorClassifierTest {

    @Test
    fun classify_timeout() {
        assertEquals(FeedErrorType.TIMEOUT, FeedErrorClassifier.classify(SocketTimeoutException()))
    }

    @Test
    fun classify_networkUnreachable() {
        assertEquals(FeedErrorType.NETWORK, FeedErrorClassifier.classify(UnknownHostException()))
        assertEquals(FeedErrorType.NETWORK, FeedErrorClassifier.classify(SSLException("Handshake failed")))
    }

    @Test
    fun classify_httpError() {
        assertEquals(FeedErrorType.HTTP, FeedErrorClassifier.classify(IOException("HTTP 404 Not Found")))
        assertEquals(FeedErrorType.HTTP, FeedErrorClassifier.classify(IOException("500 Internal Server Error")))
    }

    @Test
    fun classify_parserError() {
        class XmlParserException : RuntimeException("Malformed XML")
        assertEquals(FeedErrorType.PARSER, FeedErrorClassifier.classify(XmlParserException()))
    }

    @Test
    fun feedHealthRecord_isFailingReflectsErrorType() {
        val healthy = FeedHealthRecord(feedId = "f1", lastErrorType = FeedErrorType.NONE)
        val failing = FeedHealthRecord(feedId = "f2", lastErrorType = FeedErrorType.TIMEOUT)

        assertEquals(false, healthy.isFailing)
        assertEquals(true, failing.isFailing)
    }
}
