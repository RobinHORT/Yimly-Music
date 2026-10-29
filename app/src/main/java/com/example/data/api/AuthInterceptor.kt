package com.example.data.api

import com.example.data.datastore.AuthTokenHolder
import com.example.data.datastore.PreferencesManager
import com.example.data.datastore.ServerUrlConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val preferencesManager: PreferencesManager,
    private val tokenProvider: (() -> String?)? = null,
    scope: CoroutineScope? = null
) : Interceptor {

    @Volatile
    private var cachedServerUrl: String = ServerUrlConfig.activeServerUrl

    private val effectiveScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        effectiveScope.launch {
            preferencesManager.serverUrlFlow.collect { url ->
                cachedServerUrl = url
                ServerUrlConfig.activeServerUrl = url
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val token = tokenProvider?.invoke()?.takeIf { it.isNotBlank() }
            ?: AuthTokenHolder.token

        val defaultBaseUrl = PreferencesManager.DEFAULT_SERVER_URL.toHttpUrlOrNull()
        val serverUrl = ServerUrlConfig.activeServerUrl.takeIf { it.isNotBlank() }
            ?: cachedServerUrl.takeIf { it.isNotBlank() }
            ?: PreferencesManager.DEFAULT_SERVER_URL
        val dynamicBaseUrl = serverUrl.toHttpUrlOrNull() ?: defaultBaseUrl

        val requestBuilder = originalRequest.newBuilder()

        val isDefaultBackend = defaultBaseUrl != null && isBackendUrl(originalUrl, defaultBaseUrl)
        val isDynamicBackend = dynamicBaseUrl != null && isBackendUrl(originalUrl, dynamicBaseUrl)
        val isBackendRequest = isDefaultBackend || isDynamicBackend

        if (isBackendRequest && dynamicBaseUrl != null) {
            val newUrl = originalUrl.newBuilder()
                .scheme(dynamicBaseUrl.scheme)
                .host(dynamicBaseUrl.host)
                .port(dynamicBaseUrl.port)
                .build()
            requestBuilder.url(newUrl)
        }

        // Attach Authorization header only for verified backend requests if authenticated
        if (isBackendRequest && !token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val isAudioRequest = originalUrl.encodedPath.contains("/audio", ignoreCase = true)
        if (isAudioRequest) {
            requestBuilder.header("Accept", "audio/*, */*")
        } else {
            requestBuilder.header("Accept", "application/json")
        }
        requestBuilder.header("User-Agent", "Yimly-Android-Client/1.0")

        return chain.proceed(requestBuilder.build())
    }

    companion object {
        fun isBackendUrl(requestUrl: HttpUrl, baseUrl: HttpUrl?): Boolean {
            if (baseUrl == null) return false
            val schemeMatches = requestUrl.scheme.equals(baseUrl.scheme, ignoreCase = true)
            val hostMatches = requestUrl.host.equals(baseUrl.host, ignoreCase = true)
            val portMatches = requestUrl.port == baseUrl.port
            return schemeMatches && hostMatches && portMatches
        }
    }
}
