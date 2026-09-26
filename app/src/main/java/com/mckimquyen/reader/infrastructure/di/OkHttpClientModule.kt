package com.mckimquyen.reader.infrastructure.di

import android.annotation.SuppressLint
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.mckimquyen.reader.BuildConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Provides singleton [OkHttpClient] for the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object OkHttpClientModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient = cachingHttpClient(
        cacheDirectory = context.cacheDir.resolve(HTTP_CACHE_DIRECTORY),
        cacheSize = HTTP_CACHE_SIZE_BYTES,
    ).newBuilder()
        // Force stale validation so OkHttp attaches cached ETag/Last-Modified validators.
        // A 304 response is merged with the cached body before callers see it.
        .addInterceptor(ConditionalCacheInterceptor)
        .addNetworkInterceptor(UserAgentInterceptor)
        .build()
}

fun cachingHttpClient(
    cacheDirectory: File? = null,
    cacheSize: Long = 10L * 1024L * 1024L,
    trustAllCerts: Boolean = true,
    connectTimeoutSecs: Long = 30L,
    readTimeoutSecs: Long = 30L,
): OkHttpClient {
    val builder: OkHttpClient.Builder = OkHttpClient.Builder()

    if (cacheDirectory != null) {
        builder.cache(Cache(cacheDirectory, cacheSize))
    }

    builder
        .connectTimeout(connectTimeoutSecs, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSecs, TimeUnit.SECONDS)
        .followRedirects(true)

    if (trustAllCerts) {
        builder.trustAllCerts()
    }

    return builder.build()
}

fun OkHttpClient.Builder.trustAllCerts() {
    try {
        val trustManager = @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
        val sslSocketFactory = sslContext.socketFactory

        sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
    } catch (e: NoSuchAlgorithmException) {
        e.printStackTrace()
    } catch (e: KeyManagementException) {
        e.printStackTrace()
    }
}

object ConditionalCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(
            if (request.method == "GET") {
                request.newBuilder()
                    .header("Cache-Control", "max-age=0")
                    .build()
            } else {
                request
            }
        )
    }
}

object UserAgentInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(
            chain.request()
                .newBuilder()
                .header("User-Agent", USER_AGENT_STRING)
                .build()
        )
    }
}

const val USER_AGENT_STRING = "ReadYou / ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
private const val HTTP_CACHE_DIRECTORY = "http"
private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024L * 1024L
