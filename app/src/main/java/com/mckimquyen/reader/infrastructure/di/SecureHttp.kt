package com.mckimquyen.reader.infrastructure.di

import javax.inject.Qualifier

/**
 * An [okhttp3.OkHttpClient] that verifies TLS certificates and host names with the system trust store.
 *
 * The app-wide client accepts any certificate (self-signed feeds). Calls that carry a secret, such as the Gemini API key in the query string, must not use it:
 * anyone on the same Wi-Fi could read the key.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SecureHttp
