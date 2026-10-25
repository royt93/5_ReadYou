package com.mckimquyen.reader.infrastructure.net

import com.google.gson.JsonParser
import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class AppDns(
    private val dohProviderSupplier: () -> DohProviderPref,
    private val bootstrapClient: OkHttpClient = createDefaultBootstrapClient(),
    private val systemDns: Dns = Dns.SYSTEM,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. IP literal check
        val ipLiteral = parseNumericAddress(hostname)
        if (ipLiteral != null) {
            return listOf(ipLiteral)
        }

        // 2. Check current preference
        val provider = dohProviderSupplier()
        if (provider == DohProviderPref.SYSTEM || provider.endpoint.isBlank()) {
            return systemDns.lookup(hostname)
        }

        // 3. Query DoH endpoint
        try {
            val resolved = queryDoh(hostname, provider)
            if (resolved.isNotEmpty()) {
                return resolved
            }
        } catch (_: Exception) {
            // DoH lookup failed; fallback to systemDns
        }

        // 4. Graceful fallback
        return systemDns.lookup(hostname)
    }

    private fun queryDoh(hostname: String, provider: DohProviderPref): List<InetAddress> {
        val url = provider.endpoint.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("name", hostname)
            ?.addQueryParameter("type", "A")
            ?.build() ?: return emptyList()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()

        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val bodyString = response.body?.string().orEmpty()
            return parseDnsJsonResponse(bodyString)
        }
    }

    private fun parseDnsJsonResponse(json: String): List<InetAddress> {
        if (json.isBlank()) return emptyList()
        val jsonElement = JsonParser.parseString(json)
        if (!jsonElement.isJsonObject) return emptyList()
        val jsonObject = jsonElement.asJsonObject
        val answerArray = jsonObject.getAsJsonArray("Answer") ?: return emptyList()

        val result = mutableListOf<InetAddress>()
        for (i in 0 until answerArray.size()) {
            val answerElement = answerArray.get(i)
            if (!answerElement.isJsonObject) continue
            val answer = answerElement.asJsonObject
            // Type 1 is DNS Record Type A (IPv4)
            val type = if (answer.has("type")) answer.get("type").asInt else 0
            val data = if (answer.has("data")) answer.get("data").asString.trim() else ""
            if (type == 1 && data.isNotEmpty()) {
                try {
                    result.add(InetAddress.getByName(data))
                } catch (_: UnknownHostException) {
                }
            }
        }
        return result
    }

    private fun parseNumericAddress(hostname: String): InetAddress? {
        val parts = hostname.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            return try {
                InetAddress.getByAddress(hostname, parts.map { it.toInt().toByte() }.toByteArray())
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    companion object {
        fun createDefaultBootstrapClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .dns(Dns.SYSTEM)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .build()
        }
    }
}
