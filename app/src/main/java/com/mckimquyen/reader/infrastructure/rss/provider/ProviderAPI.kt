package com.mckimquyen.reader.infrastructure.rss.provider

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mckimquyen.reader.infrastructure.di.UserAgentInterceptor
import com.mckimquyen.reader.infrastructure.di.cachingHttpClient
import okhttp3.OkHttpClient

abstract class ProviderAPI(
    // ponytail: overridable only so tests can inject a client pointed at a local stub server;
    // production code always uses the default.
    protected val client: OkHttpClient = cachingHttpClient()
        .newBuilder()
        .addNetworkInterceptor(UserAgentInterceptor)
        .build()
) {

    protected val gson: Gson = GsonBuilder().create()

    protected inline fun <reified T> toDTO(jsonStr: String): T =
        gson.fromJson(jsonStr, T::class.java)!!
}
