package com.mckimquyen.reader.infrastructure.social.nostr

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/** One Nostr event (NIP-01) read from a relay. Nothing here is trusted until [isValid] says so. */
class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** A reply carries an `e` tag (it answers another note); the feed shows originals only. */
    val isReply: Boolean get() = tags.any { it.firstOrNull() == REPLY_TAG }

    fun isValid(expectedPubkeyHex: String): Boolean {
        if (kind != TEXT_NOTE_KIND) return false
        return hasValidSignature(expectedPubkeyHex)
    }

    /** A profile (kind 0): same id, signature and author checks as a note, only the kind differs. Never accepted as a note. */
    fun isValidProfile(expectedPubkeyHex: String): Boolean {
        if (kind != PROFILE_KIND) return false
        return hasValidSignature(expectedPubkeyHex)
    }

    private fun hasValidSignature(expectedPubkeyHex: String): Boolean {
        if (!isLowerHex(id, ID_HEX_LENGTH) || !isLowerHex(pubkey, ID_HEX_LENGTH) || !isLowerHex(sig, SIG_HEX_LENGTH)) return false
        if (pubkey != expectedPubkeyHex) return false
        if (computeId() != id) return false
        return Bip340.verify(pubkey, id, sig)
    }

    private fun computeId(): String {
        val canonical = buildString {
            append("[0,").append(quote(pubkey)).append(',').append(createdAt).append(',').append(kind).append(',')
            append('[')
            tags.forEachIndexed { i, tag ->
                if (i > 0) append(',')
                append('[').append(tag.joinToString(",") { quote(it) }).append(']')
            }
            append("],").append(quote(content)).append(']')
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        const val TEXT_NOTE_KIND = 1
        const val PROFILE_KIND = 0
        private const val REPLY_TAG = "e"
        private const val ID_HEX_LENGTH = 64
        private const val SIG_HEX_LENGTH = 128

        fun parse(json: String): NostrEvent? = try {
            val o = JSONObject(json)
            NostrEvent(
                id = o.strictString("id"),
                pubkey = o.strictString("pubkey"),
                createdAt = o.strictLong("created_at"),
                kind = o.strictInt("kind"),
                tags = o.strictTags("tags"),
                content = o.strictString("content"),
                sig = o.strictString("sig"),
            )
        } catch (_: JSONException) {
            null
        }

        private fun JSONObject.strictString(name: String): String = get(name) as? String ?: throw JSONException("$name")
        private fun JSONObject.strictLong(name: String): Long = (get(name) as? Number)?.takeIf { it is Int || it is Long }?.toLong() ?: throw JSONException("$name")
        private fun JSONObject.strictInt(name: String): Int = (get(name) as? Number)?.takeIf { it is Int }?.toInt() ?: throw JSONException("$name")

        private fun JSONObject.strictTags(name: String): List<List<String>> {
            val outer = get(name) as? JSONArray ?: throw JSONException(name)
            return List(outer.length()) { i ->
                val inner = outer.get(i) as? JSONArray ?: throw JSONException(name)
                List(inner.length()) { j -> inner.get(j) as? String ?: throw JSONException(name) }
            }
        }

        private fun isLowerHex(value: String, length: Int): Boolean =
            value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

        /** NIP-01 escaping: only the seven JSON escapes below; any other character stays as it is. */
        private fun quote(text: String): String = buildString {
            append('"')
            for (c in text) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}
