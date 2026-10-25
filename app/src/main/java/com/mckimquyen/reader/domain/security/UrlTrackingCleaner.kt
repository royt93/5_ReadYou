package com.mckimquyen.reader.domain.security

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlTrackingCleaner {

    private val TRACKING_PARAMS = setOf(
        // Google Analytics & Marketing
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "utm_cid",
        "utm_reader",
        "utm_referrer",
        "_ga",
        "_gl",
        // Google Ads
        "gclid",
        "gbraid",
        "wbraid",
        // Meta / Facebook / Instagram
        "fbclid",
        "igshid",
        // Microsoft / Bing
        "msclkid",
        // Twitter / X
        "twclid",
        // MailChimp & Newsletters
        "mc_cid",
        "mc_eid",
        // Hubspot
        "_hsenc",
        "_hsmi",
    )

    fun clean(url: String): String {
        if (url.isBlank()) return url
        val httpUrl = url.toHttpUrlOrNull() ?: return url

        val names = httpUrl.queryParameterNames
        val hasTracking = names.any { isTrackingParam(it) }
        if (!hasTracking) return url

        val builder = httpUrl.newBuilder()
        for (name in names) {
            if (isTrackingParam(name)) {
                builder.removeAllQueryParameters(name)
            }
        }
        return builder.build().toString()
    }

    private fun isTrackingParam(name: String): Boolean {
        val lower = name.lowercase()
        return lower in TRACKING_PARAMS || lower.startsWith("utm_")
    }
}
