package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.infrastructure.net.SelfSignedTrust
import com.mckimquyen.reader.infrastructure.net.hostTrustingClientFrom
import android.annotation.SuppressLint
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.infrastructure.net.AppDns
import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import com.mckimquyen.reader.ui.ext.dohProviderId
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
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Provides singleton [OkHttpClient] for the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object OkHttpClientModule {

    @Provides
    @Singleton
    fun provideAppDns(
        @ApplicationContext context: Context,
    ): AppDns = AppDns(
        dohProviderSupplier = { DohProviderPref.fromId(context.dohProviderId) },
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        appDns: AppDns,
    ): OkHttpClient = hostTrustingClientFrom(
        base = cachingHttpClient(
            cacheDirectory = context.cacheDir.resolve(HTTP_CACHE_DIRECTORY),
            cacheSize = HTTP_CACHE_SIZE_BYTES,
            // Feeds, images, Bluesky and Nostr verify certificates like a browser; a server the user allowed is the only exception (see SelfSignedTrust).
            trustAllCerts = false,
        ).newBuilder()
            .dns(appDns)
            // Force stale validation so OkHttp attaches cached ETag/Last-Modified validators.
            // A 304 response is merged with the cached body before callers see it.
            .addInterceptor(ConditionalCacheInterceptor)
            .addNetworkInterceptor(UserAgentInterceptor)
            .build(),
        trust = SelfSignedTrust.of(context),
    )

    @Provides
    @Singleton
    @SecureHttp
    fun provideSecureHttpClient(
        base: OkHttpClient,
    ): OkHttpClient = secureHttpClientFrom(base) // never relaxed, not even for a host the user allowed: it carries the Gemini API key
}

/**
 * Same DNS, timeouts and cache as [base], but certificates and host names are checked by the platform again.
 * [OkHttpClient.Builder.sslSocketFactory] without arguments for the trust manager is not available, so the default factory and the system trust manager are set explicitly.
 */
fun secureHttpClientFrom(base: OkHttpClient): OkHttpClient {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as java.security.KeyStore?)
    val systemTrust = factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    val ssl = SSLContext.getInstance("TLS")
    ssl.init(null, arrayOf<TrustManager>(systemTrust), null)
    return base.newBuilder()
        .sslSocketFactory(ssl.socketFactory, systemTrust)
        .hostnameVerifier(javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier())
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

/**
 * The HTTP client for a server the user logs in to (Fever, Google Reader, WebDAV). It verifies the certificate and host name like a browser, unless [trustSelfSigned] is
 * true, which the caller sets only for a host the user has explicitly allowed (see SelfSignedTrust).
 */
fun credentialHttpClientFrom(base: OkHttpClient, trustSelfSigned: Boolean): OkHttpClient =
    if (trustSelfSigned) base.newBuilder().apply { trustAllCerts() }.build() else secureHttpClientFrom(base)

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
