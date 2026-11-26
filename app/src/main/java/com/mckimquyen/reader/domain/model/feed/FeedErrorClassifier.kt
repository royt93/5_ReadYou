package com.mckimquyen.reader.domain.model.feed

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

object FeedErrorClassifier {
    /** True when [e] or anything it was caused by says the server's certificate or host name was rejected. */
    fun isCertificateProblem(e: Throwable): Boolean =
        generateSequence(e) { it.cause?.takeIf { cause -> cause !== it } }.take(CAUSE_DEPTH_LIMIT)
            .any { it is CertificateException || it is SSLPeerUnverifiedException || it is java.security.cert.CertPathValidatorException }

    private const val CAUSE_DEPTH_LIMIT = 8

    fun classify(e: Throwable): FeedErrorType = when {
        isCertificateProblem(e) -> FeedErrorType.CERTIFICATE
        else -> classifyOther(e)
    }

    private fun classifyOther(e: Throwable): FeedErrorType = when (e) {
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
