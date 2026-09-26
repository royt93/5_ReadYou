package com.mckimquyen.reader.domain.model.feed

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object FeedErrorClassifier {
    fun classify(e: Throwable): FeedErrorType = when (e) {
        is SocketTimeoutException -> FeedErrorType.TIMEOUT
        is UnknownHostException, is SSLException -> FeedErrorType.NETWORK
        is IOException -> {
            val msg = e.message.orEmpty()
            if (msg.contains("4") || msg.contains("5") || msg.contains("HTTP", ignoreCase = true)) {
                FeedErrorType.HTTP
            } else {
                FeedErrorType.NETWORK
            }
        }
        else -> {
            val className = e.javaClass.simpleName
            if (className.contains("Parser", ignoreCase = true) || className.contains("Xml", ignoreCase = true)) {
                FeedErrorType.PARSER
            } else {
                FeedErrorType.NETWORK
            }
        }
    }
}
