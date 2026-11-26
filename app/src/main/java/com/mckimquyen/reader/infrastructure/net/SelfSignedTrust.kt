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

    /**
     * Called after a host's exception changes, so the caller can drop connections that were opened under the old rule. A connection kept alive in OkHttp's pool was
     * handshaken while the exception was on and would go on serving that host after the user switched it off.
     */
    var onChanged: (host: String) -> Unit = {}

    fun setTrusted(serverUrl: String?, trusted: Boolean) {
        val key = keyOf(serverUrl) ?: return
        val current = store.read()
        val next = if (trusted) current + key else current - key
        if (next != current) {
            store.write(next)
            onChanged(key)
        }
    }

    companion object {
        private const val PREFS = "self_signed_trust"
        private const val KEY_HOSTS = "hosts"

        /** `host` or `host:port`, lower case; null for anything that is not a usable http(s) URL. */
        fun keyOf(serverUrl: String?): String? {
            val raw = serverUrl?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val uri = runCatching { URI(if ("://" in raw) raw else "https://$raw") }.getOrNull() ?: return null
            return keyFor(uri.host, uri.port)
        }

        /** Lower-case host, plus `:port` unless the port is absent or 443 (the https default), so a URL and a live socket give the same key. */
        fun keyFor(host: String?, port: Int): String? {
            val name = host?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
            return if (port > 0 && port != HTTPS_DEFAULT_PORT) "$name:$port" else name
        }

        private const val HTTPS_DEFAULT_PORT = 443

        @Volatile
        private var shared: SelfSignedTrust? = null

        /**
         * One instance per process, so the change hook that ends pooled connections (see [onChanged]) is the same one every screen writes through.
         */
        fun of(context: Context): SelfSignedTrust =
            shared ?: synchronized(this) {
                shared ?: SelfSignedTrust(object : Store {
                    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    override fun read(): Set<String> = prefs.getStringSet(KEY_HOSTS, emptySet()).orEmpty().toSet()
                    override fun write(hosts: Set<String>) {
                        prefs.edit().putStringSet(KEY_HOSTS, hosts).apply()
                    }
                }).also { shared = it }
            }
    }
}
