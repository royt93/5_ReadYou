package com.mckimquyen.reader.infrastructure.social.nostr

import org.json.JSONException
import org.json.JSONObject

/** Reads a display name out of the `content` of a Nostr profile event. The text is self-declared and untrusted. */
object NostrProfileName {
    const val MAX_NAME_CODE_POINTS = 40
    private val FIELDS = listOf("display_name", "name")

    fun fromContent(json: String): String? {
        val profile = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        for (field in FIELDS) {
            val raw = profile.opt(field) as? String ?: continue
            val cleaned = clean(raw)
            if (cleaned.isNotEmpty()) return cut(cleaned)
        }
        return null
    }

    private fun clean(raw: String): String {
        val spaced = buildString(raw.length) {
            for (c in raw) {
                val blank = Character.getType(c) == Character.CONTROL.toInt() || Character.isSpaceChar(c)
                append(if (blank) ' ' else c)
            }
        }
        return spaced.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun cut(text: String): String {
        if (text.codePointCount(0, text.length) <= MAX_NAME_CODE_POINTS) return text
        return text.substring(0, text.offsetByCodePoints(0, MAX_NAME_CODE_POINTS)).trimEnd()
    }
}
