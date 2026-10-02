package com.example.data.api

import com.example.data.datastore.PreferencesManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val preferencesManager: PreferencesManager,
    private val tokenProvider: (() -> String?)? = null,
    scope: kotlinx.coroutines.CoroutineScope? = null
) : Interceptor {

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedServerUrl: String = PreferencesManager.DEFAULT_SERVER_URL

    init {
        scope?.launch {
            preferencesManager.authTokenFlow.collect { token ->
                cachedToken = token
            }
        }
        scope?.launch {
            preferencesManager.serverUrlFlow.collect { url ->
                cachedServerUrl = url
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val token = tokenProvider?.invoke() ?: cachedToken ?: runBlocking {
            preferencesManager.authTokenFlow.firstOrNull()
        }

        val serverUrl = cachedServerUrl

        val requestBuilder = originalRequest.newBuilder()

        val dynamicBaseUrl = serverUrl.toHttpUrlOrNull()
        if (dynamicBaseUrl != null) {
            val newUrl = originalUrl.newBuilder()
                .scheme(dynamicBaseUrl.scheme)
                .host(dynamicBaseUrl.host)
                .port(dynamicBaseUrl.port)
                .build()
            requestBuilder.url(newUrl)
        }

        // Attach Authorization header if authenticated
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        requestBuilder.header("Accept", "application/json")
        requestBuilder.header("User-Agent", "Yimly-Android-Client/1.0")

        return chain.proceed(requestBuilder.build())
    }
}
