package com.example.data.api

import com.example.data.datastore.PreferencesManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val preferencesManager: PreferencesManager,
    private val tokenProvider: (() -> String?)? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = tokenProvider?.invoke() ?: runBlocking {
            preferencesManager.authTokenFlow.firstOrNull()
        }

        val requestBuilder = originalRequest.newBuilder()

        // Attach Authorization header if authenticated
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        requestBuilder.header("Accept", "application/json")
        requestBuilder.header("User-Agent", "Yimly-Android-Client/1.0")

        return chain.proceed(requestBuilder.build())
    }
}
