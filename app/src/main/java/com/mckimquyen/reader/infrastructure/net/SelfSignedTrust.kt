package com.mckimquyen.reader.infrastructure.net

import android.content.Context
import java.net.URI
import java.util.Locale

/**
 * Which servers the user has told the app to accept a self-signed or otherwise untrusted certificate from.
 *
 * Fever, Google Reader (FreshRSS and others) and WebDAV send a password. They used a client that accepted every certificate and skipped the host name check, so anyone on
 * the same Wi-Fi could read the password. Those three now verify the certificate like a browser does, unless the user has switched the exception on for that one server.
 * The default is off, and an exception is keyed by host (and port), never global.
 */
class SelfSignedTrust(private val store: Store) {

    /** The persistence behind the list, so the rule can be tested without Android. */
    interface Store {
        fun read(): Set<String>
        fun write(hosts: Set<String>)
    }

    fun isTrusted(serverUrl: String?): Boolean {
        val key = keyOf(serverUrl) ?: return false
        return key in store.read()
    }

    fun setTrusted(serverUrl: String?, trusted: Boolean) {
        val key = keyOf(serverUrl) ?: return
        val current = store.read()
        val next = if (trusted) current + key else current - key
        if (next != current) store.write(next)
    }

    companion object {
        private const val PREFS = "self_signed_trust"
        private const val KEY_HOSTS = "hosts"

        /** `host` or `host:port`, lower case; null for anything that is not a usable http(s) URL. */
        fun keyOf(serverUrl: String?): String? {
            val raw = serverUrl?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val uri = runCatching { URI(if ("://" in raw) raw else "https://$raw") }.getOrNull() ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            return if (uri.port > 0) "$host:${uri.port}" else host
        }

        fun of(context: Context): SelfSignedTrust = SelfSignedTrust(object : Store {
            private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            override fun read(): Set<String> = prefs.getStringSet(KEY_HOSTS, emptySet()).orEmpty().toSet()
            override fun write(hosts: Set<String>) {
                prefs.edit().putStringSet(KEY_HOSTS, hosts).apply()
            }
        })
    }
}
