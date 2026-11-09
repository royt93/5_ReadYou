package com.mckimquyen.reader.infrastructure.p2psync

import android.content.Context
import android.util.Base64
import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.PairingSecret
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores paired-peer metadata and device-wrapped pairing secrets in a separate SharedPreferences file.
 * This file is explicitly excluded from cloud and device-transfer backup because the wrapping Keystore key is device-bound.
 */
@Singleton
class PairingStore @Inject constructor(
    private val context: Context,
    private val wrapper: SecretWrapper,
) {
    internal constructor(context: Context, wrapper: SecretWrapper, clock: () -> Long) : this(context, wrapper) {
        this.clock = clock
    }

    internal var clock: () -> Long = System::currentTimeMillis
    private val prefs by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }
    private val lock = Any()

    fun list(): List<PairedPeer> = synchronized(lock) {
        val entries = parseEntries() ?: return@synchronized emptyList()
        (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }.map { entry ->
            val wrapped = decodeWrapped(entry)
            val needsRepair = wrapped == null || wrapper.unwrap(wrapped)?.let { plain ->
                plain.fill(0)
                false
            } != false
            PairedPeer(
                peerId = entry.optString(KEY_PEER_ID),
                label = entry.optString(KEY_LABEL),
                status = PairingStatus.entries.firstOrNull { it.name == entry.optString(KEY_STATUS) } ?: PairingStatus.UNCONFIRMED,
                createdAtMillis = entry.optLong(KEY_CREATED_AT, 0L),
                needsRepair = needsRepair,
            )
        }.sortedWith(compareBy<PairedPeer> { it.createdAtMillis }.thenBy { it.peerId })
    }

    fun add(label: String, secret: PairingSecret, status: PairingStatus): PairedPeer = synchronized(lock) {
        val cleaned = validateLabel(label)
        val entries = parseEntries() ?: JSONArray()
        require(entries.length() < MAX_PEERS) { "Too many paired devices" }
        val peerId = newPeerId(entries)
        val plain = secret.hex.toByteArray(Charsets.US_ASCII)
        val wrapped = try {
            wrapper.wrap(plain)
        } finally {
            plain.fill(0)
        }
        require(wrapped.isNotEmpty()) { "Secret wrapper returned empty ciphertext" }
        val createdAt = clock()
        entries.put(JSONObject()
            .put(KEY_PEER_ID, peerId)
            .put(KEY_LABEL, cleaned)
            .put(KEY_STATUS, status.name)
            .put(KEY_CREATED_AT, createdAt)
            .put(KEY_CIPHERTEXT, Base64.encodeToString(wrapped, Base64.NO_WRAP)))
        save(entries)
        PairedPeer(peerId, cleaned, status, createdAt, false)
    }

    fun secretOf(peerId: String): PairingSecret? = synchronized(lock) {
        val entry = findEntry(peerId) ?: return@synchronized null
        val wrapped = decodeWrapped(entry) ?: return@synchronized null
        val plain = wrapper.unwrap(wrapped) ?: return@synchronized null
        try {
            PairingSecret.fromHex(String(plain, Charsets.US_ASCII))
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            plain.fill(0)
        }
    }

    fun markConfirmed(peerId: String) = update(peerId) { it.put(KEY_STATUS, PairingStatus.CONFIRMED.name) }

    fun forget(peerId: String) = synchronized(lock) {
        val entries = parseEntries() ?: return@synchronized
        val remaining = JSONArray()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optString(KEY_PEER_ID) != peerId) remaining.put(entry)
        }
        if (remaining.length() != entries.length()) save(remaining)
    }

    private fun update(peerId: String, change: (JSONObject) -> Unit) = synchronized(lock) {
        val entries = parseEntries() ?: return@synchronized
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optString(KEY_PEER_ID) == peerId) {
                change(entry)
                save(entries)
                return@synchronized
            }
        }
    }

    private fun findEntry(peerId: String): JSONObject? {
        val entries = parseEntries() ?: return null
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optString(KEY_PEER_ID) == peerId) return entry
        }
        return null
    }

    private fun parseEntries(): JSONArray? = try {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return JSONArray()
        JSONObject(raw).optJSONArray(KEY_ENTRIES) ?: JSONArray()
    } catch (_: JSONException) {
        null
    }

    private fun decodeWrapped(entry: JSONObject): ByteArray? = try {
        val encoded = entry.optString(KEY_CIPHERTEXT).takeIf { it.isNotEmpty() } ?: return null
        Base64.decode(encoded, Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun save(entries: JSONArray) {
        check(prefs.edit().putString(KEY_ENTRIES, JSONObject().put(KEY_ENTRIES, entries).toString()).commit()) {
            "Could not update paired devices"
        }
    }

    private fun validateLabel(raw: String): String {
        val cleaned = raw.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= MAX_LABEL_CHARS && cleaned.none { it.isISOControl() }) {
            "Invalid paired-device label"
        }
        return cleaned
    }

    private fun newPeerId(entries: JSONArray): String {
        val existing = (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.optString(KEY_PEER_ID) }.toSet()
        repeat(MAX_PEERS * PEER_ID_RETRIES) {
            val id = ByteArray(PEER_ID_BYTES).also(SecureRandom()::nextBytes).toHex()
            if (id !in existing) return id
        }
        error("Could not create a unique pairing id")
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xff
            append(LOWER_HEX[value ushr 4]).append(LOWER_HEX[value and 0x0f])
        }
    }

    companion object {
        const val FILE_NAME = "pairing_store"
        const val MAX_LABEL_CHARS = 60
        const val MAX_PEERS = 20
        private const val PEER_ID_BYTES = 16
        private const val PEER_ID_RETRIES = 8
        private const val KEY_ENTRIES = "entries"
        private const val KEY_PEER_ID = "peerId"
        private const val KEY_LABEL = "label"
        private const val KEY_STATUS = "status"
        private const val KEY_CREATED_AT = "createdAtMillis"
        private const val KEY_CIPHERTEXT = "wrappedSecret"
        private const val LOWER_HEX = "0123456789abcdef"
    }
}
