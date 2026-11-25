package com.mckimquyen.reader.infrastructure.backup

import com.mckimquyen.reader.infrastructure.di.SecureHttp
import com.mckimquyen.reader.infrastructure.net.SelfSignedTrust
import android.content.Context
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.encodeBase64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.executeAsync
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastBackupTime: Long? = null,
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

@Singleton
class WebDavBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opmlSv: OpmlSv,
    // The shared client accepts any certificate; it is used only for a server the user allowed (see SelfSignedTrust).
    private val okHttpClient: OkHttpClient,
    @SecureHttp private val secureClient: OkHttpClient,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val trust by lazy { SelfSignedTrust.of(context) }

    /** WebDAV sends a password: verify the certificate unless the user switched the exception on for this server. */
    fun clientFor(serverUrl: String): OkHttpClient = if (trust.isTrusted(serverUrl)) okHttpClient else secureClient

    fun isSelfSignedTrusted(serverUrl: String): Boolean = trust.isTrusted(serverUrl)

    fun setSelfSignedTrusted(serverUrl: String, trusted: Boolean) = trust.setTrusted(serverUrl, trusted)

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun getConfig(): WebDavConfig {
        return WebDavConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            lastBackupTime = prefs.getLong(KEY_LAST_BACKUP_TIME, -1L).takeIf { it > 0 },
        )
    }

    fun saveConfig(serverUrl: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl.trim())
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    suspend fun backup(accountId: Int): Result<String> = withContext(ioDispatcher) {
        val config = getConfig()
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("WebDAV is not configured"))
        }

        try {
            val opmlXml = opmlSv.saveToString(accountId)
            val targetUrl = buildFileUrl(config.serverUrl)
            val authHeader = "Basic " + "${config.username}:${config.password}".encodeBase64().trim()

            val requestBody = opmlXml.toRequestBody("text/xml; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(targetUrl)
                .put(requestBody)
                .header("Authorization", authHeader)
                .build()

            val response = clientFor(config.serverUrl).newCall(request).executeAsync()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("WebDAV PUT failed with HTTP code ${response.code}: ${response.message}")
                )
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_BACKUP_TIME, now).apply()
            Result.success(opmlXml)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restore(): Result<Unit> = withContext(ioDispatcher) {
        val config = getConfig()
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("WebDAV is not configured"))
        }

        try {
            val targetUrl = buildFileUrl(config.serverUrl)
            val authHeader = "Basic " + "${config.username}:${config.password}".encodeBase64().trim()

            val request = Request.Builder()
                .url(targetUrl)
                .get()
                .header("Authorization", authHeader)
                .build()

            val response = clientFor(config.serverUrl).newCall(request).executeAsync()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("WebDAV GET failed with HTTP code ${response.code}: ${response.message}")
                )
            }

            // The server is not trusted: read at most MAX_RESTORE_BYTES (counting the bytes, because the announced length can be absent or a
            // lie), and only then parse. Nothing reaches the importer, and so nothing reaches Room, from an oversized answer.
            val announced = response.body.contentLength()
            if (announced > MAX_RESTORE_BYTES) {
                return@withContext Result.failure(IOException("WebDAV restore file is too large ($announced bytes)"))
            }
            val bytes = response.body.byteStream().use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(RESTORE_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESTORE_BYTES) {
                        return@withContext Result.failure(IOException("WebDAV restore file is too large (over $MAX_RESTORE_BYTES bytes)"))
                    }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            opmlSv.saveToDatabase(java.io.ByteArrayInputStream(bytes))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildFileUrl(serverUrl: String): String {
        val base = serverUrl.trim()
        val normalized = if (base.endsWith("/")) base else "$base/"
        return normalized + BACKUP_FILENAME
    }

    companion object {
        private const val PREFS_NAME = "webdav_backup_prefs"
        private const val KEY_SERVER_URL = "webdav_server_url"
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_PASSWORD = "webdav_password"
        private const val KEY_LAST_BACKUP_TIME = "webdav_last_backup_time"

        const val BACKUP_FILENAME = "rss_hub_backup.opml"

        /** A restore file bigger than this is refused (the server is not trusted); an OPML of thousands of feeds is far smaller. */
        const val MAX_RESTORE_BYTES = 10L * 1024L * 1024L
        private const val RESTORE_BUFFER_BYTES = 8 * 1024
    }
}
