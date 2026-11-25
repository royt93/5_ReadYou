package com.mckimquyen.reader.infrastructure.rss.provider

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mckimquyen.reader.infrastructure.di.UserAgentInterceptor
import com.mckimquyen.reader.infrastructure.di.cachingHttpClient
import com.mckimquyen.reader.infrastructure.di.credentialHttpClientFrom
import okhttp3.OkHttpClient

abstract class ProviderAPI(
    // ponytail: overridable only so tests can inject a client pointed at a local stub server;
    // production code always uses the default. Both Fever and Google Reader send a password, so the default checks the certificate unless the user allowed this server.
    protected val client: OkHttpClient = credentialApiClient(trustSelfSigned = false)
) {

    protected val gson: Gson = GsonBuilder().create()

    protected inline fun <reified T> toDTO(jsonStr: String): T =
        gson.fromJson(jsonStr, T::class.java)!!
}

/** The shared client is trust-all (public feeds); a client that carries a password verifies TLS unless [trustSelfSigned] was switched on for that server. */
fun credentialApiClient(trustSelfSigned: Boolean): OkHttpClient =
    credentialHttpClientFrom(cachingHttpClient(), trustSelfSigned)
        .newBuilder()
        .addNetworkInterceptor(UserAgentInterceptor)
        .build()
