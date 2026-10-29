package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.AuthInterceptor
import com.example.data.api.NetworkModule
import com.example.data.datastore.AuthTokenHolder
import com.example.data.datastore.PreferencesManager
import com.example.data.datastore.ServerUrlConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthInterceptorAndStreamingTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
        AuthTokenHolder.token = null
        ServerUrlConfig.activeServerUrl = PreferencesManager.DEFAULT_SERVER_URL
    }

    @Test
    fun testIsBackendUrlValidation() {
        val configuredBase = "https://yimly.robinhort.link".toHttpUrl()

        // 1. Exact match
        val backendApiUrl = "https://yimly.robinhort.link/api/songs".toHttpUrl()
        assertTrue(AuthInterceptor.isBackendUrl(backendApiUrl, configuredBase))

        val backendAudioUrl = "https://yimly.robinhort.link/api/songs/123/audio?type=main".toHttpUrl()
        assertTrue(AuthInterceptor.isBackendUrl(backendAudioUrl, configuredBase))

        // 2. Lookalike hostnames MUST be rejected
        val evilSuffix = "https://yimly.robinhort.link.evil.com/api/songs".toHttpUrl()
        assertFalse(AuthInterceptor.isBackendUrl(evilSuffix, configuredBase))

        val evilSubdomain = "https://evil.yimly.robinhort.link/api/songs".toHttpUrl()
        assertFalse(AuthInterceptor.isBackendUrl(evilSubdomain, configuredBase))

        // 3. Alternate ports MUST be rejected if base port does not match
        val customPortUrl = "https://yimly.robinhort.link:8443/api/songs".toHttpUrl()
        assertFalse(AuthInterceptor.isBackendUrl(customPortUrl, configuredBase))

        // But accepted if base URL explicitly uses that port
        val customBase = "https://yimly.robinhort.link:8443".toHttpUrl()
        assertTrue(AuthInterceptor.isBackendUrl(customPortUrl, customBase))

        // 4. Scheme mismatch (HTTP vs HTTPS)
        val httpUrl = "http://yimly.robinhort.link/api/songs".toHttpUrl()
        assertFalse(AuthInterceptor.isBackendUrl(httpUrl, configuredBase))

        // 5. External CDN / S3 hosts MUST be rejected
        val s3Url = "https://s3.amazonaws.com/my-bucket/audio.mp3".toHttpUrl()
        assertFalse(AuthInterceptor.isBackendUrl(s3Url, configuredBase))

        val cloudflareR2Url = "https://pub-abcdef.r2.dev/tracks/123.flac".toHttpUrl()
        assertFalse(AuthInterceptor.isBackendUrl(cloudflareR2Url, configuredBase))

        // 6. Null base URL
        assertFalse(AuthInterceptor.isBackendUrl(backendApiUrl, null))
    }

    @Test
    fun testBearerTokenNeverAttachedToExternalHosts() {
        AuthTokenHolder.token = "super_secret_session_token_123"

        val interceptor = AuthInterceptor(
            preferencesManager = preferencesManager,
            tokenProvider = null,
            scope = null
        )

        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()

        val req = Request.Builder()
            .url("https://s3.amazonaws.com/external-bucket/audio.mp3?X-Amz-Signature=xyz789")
            .build()

        client.newCall(req).execute().close()

        assertNotNull(capturedRequest)
        // External host must NOT receive Authorization header
        assertNull(capturedRequest?.header("Authorization"))
        // External host URL must NOT be rewritten
        assertEquals("s3.amazonaws.com", capturedRequest?.url?.host)
        assertEquals("https://s3.amazonaws.com/external-bucket/audio.mp3?X-Amz-Signature=xyz789", capturedRequest?.url.toString())
    }

    @Test
    fun testBearerTokenAttachedToBackendRequests() = kotlinx.coroutines.runBlocking {
        preferencesManager.saveAuthSession(
            token = "valid_user_jwt_456",
            userId = "usr_1",
            username = "testuser",
            email = "test@example.com",
            rememberMe = true
        )

        val interceptor = AuthInterceptor(
            preferencesManager = preferencesManager,
            tokenProvider = null,
            scope = null
        )

        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()

        val req = Request.Builder()
            .url("https://yimly.robinhort.link/api/songs/abc/audio")
            .build()

        client.newCall(req).execute().close()

        assertNotNull(capturedRequest)
        assertEquals("Bearer valid_user_jwt_456", capturedRequest?.header("Authorization"))
        assertEquals("audio/*, */*", capturedRequest?.header("Accept"))
    }

    @Test
    fun testAcceptHeadersDifferentiation() {
        val interceptor = AuthInterceptor(
            preferencesManager = preferencesManager,
            tokenProvider = null,
            scope = null
        )

        fun executeWithUrl(url: String): Request {
            var captured: Request? = null
            val client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor { chain ->
                    captured = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody())
                        .build()
                }
                .build()

            client.newCall(Request.Builder().url(url).build()).execute().close()
            return captured!!
        }

        val apiReq = executeWithUrl("https://yimly.robinhort.link/api/songs")
        assertEquals("application/json", apiReq.header("Accept"))

        val audioReq = executeWithUrl("https://yimly.robinhort.link/api/songs/123/audio")
        assertEquals("audio/*, */*", audioReq.header("Accept"))
    }

    @Test
    fun testTokenClearingOnLogout() {
        AuthTokenHolder.token = "initial_token"
        assertEquals("initial_token", AuthTokenHolder.token)

        // Clear token
        AuthTokenHolder.token = null
        assertNull(AuthTokenHolder.token)
    }

    @Test
    fun testMediaOkHttpClientDoesNotContainHttpLoggingInterceptor() {
        val networkModule = NetworkModule(preferencesManager, isDebug = true)
        val mediaClient = networkModule.mediaOkHttpClient
        val apiClient = networkModule.okHttpClient

        // Verify mediaOkHttpClient has NO HttpLoggingInterceptor
        val hasLoggingInMediaClient = mediaClient.interceptors.any { it is HttpLoggingInterceptor } ||
            mediaClient.networkInterceptors.any { it is HttpLoggingInterceptor }
        assertFalse("mediaOkHttpClient must not have HttpLoggingInterceptor installed", hasLoggingInMediaClient)

        // Verify apiClient DOES have HttpLoggingInterceptor installed in debug
        val hasLoggingInApiClient = apiClient.interceptors.any { it is HttpLoggingInterceptor }
        assertTrue("okHttpClient must have HttpLoggingInterceptor in debug mode", hasLoggingInApiClient)

        // Verify timeouts
        assertEquals(30000, mediaClient.readTimeoutMillis)
        assertEquals(30000, mediaClient.writeTimeoutMillis)
        assertEquals(15000, mediaClient.connectTimeoutMillis)
    }

    @Test
    fun testAuthCacheRaceStaleEmissionDoesNotOverwriteLogin() {
        AuthTokenHolder.token = "newer_login_token"
        val loginTimestamp = AuthTokenHolder.lastUpdatedEpochMs

        // Simulate lagging emission from an earlier disk read (older timestamp)
        AuthTokenHolder.updateFromPersistence("stale_token_from_disk", loginTimestamp - 5000L)
        assertEquals("Newer in-memory login token must not be overwritten by older persistence emission",
            "newer_login_token", AuthTokenHolder.token)

        // Simulate empty/null emission from older disk state
        AuthTokenHolder.updateFromPersistence(null, loginTimestamp - 1000L)
        assertEquals("Newer in-memory login token must not be overwritten by older empty emission",
            "newer_login_token", AuthTokenHolder.token)
    }

    @Test
    fun testAuthCacheRaceStaleEmissionDoesNotOverwriteLogout() {
        AuthTokenHolder.token = "active_session_token"
        // User logs out
        AuthTokenHolder.token = null
        val logoutTimestamp = AuthTokenHolder.lastUpdatedEpochMs

        // Simulate lagging emission containing the old token from before logout committed
        AuthTokenHolder.updateFromPersistence("active_session_token", logoutTimestamp - 2000L)
        assertNull("Stale persistence emission must not resurrect an expired/logged-out session",
            AuthTokenHolder.token)
    }

    @Test
    fun testColdStartLoadsPersistedToken() {
        AuthTokenHolder.reset()
        assertEquals(0L, AuthTokenHolder.lastUpdatedEpochMs)
        assertNull(AuthTokenHolder.token)

        // On cold start, persistence emits the stored token
        AuthTokenHolder.updateFromPersistence("persisted_cold_start_jwt", 1000L)
        assertEquals("Cold-start persistence read must initialize token when no in-memory mutation has occurred",
            "persisted_cold_start_jwt", AuthTokenHolder.token)
    }

    @Test
    fun testDynamicServerSwitchingRetrofitRewriteAndAuth() = kotlinx.coroutines.runBlocking {
        AuthTokenHolder.token = "dynamic_switch_user_token"
        preferencesManager.setServerUrl("http://custom-lan-host:8080")

        val interceptor = AuthInterceptor(
            preferencesManager = preferencesManager,
            tokenProvider = null,
            scope = null
        )

        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody())
                    .build()
            }
            .build()

        // Retrofit generates requests with default server URL
        val req = Request.Builder()
            .url("https://yimly.robinhort.link/api/songs")
            .build()

        client.newCall(req).execute().close()

        assertNotNull(capturedRequest)
        // Request URL must be rewritten to dynamic custom host, scheme, and port
        assertEquals("http", capturedRequest?.url?.scheme)
        assertEquals("custom-lan-host", capturedRequest?.url?.host)
        assertEquals(8080, capturedRequest?.url?.port)
        assertEquals("/api/songs", capturedRequest?.url?.encodedPath)
        // Authorization header must be attached
        assertEquals("Bearer dynamic_switch_user_token", capturedRequest?.header("Authorization"))
    }

    @Test
    fun testDynamicServerSwitchingExoPlayerDirectAudio() = kotlinx.coroutines.runBlocking {
        AuthTokenHolder.token = "media_stream_user_token"
        preferencesManager.setServerUrl("http://custom-lan-host:8080")

        val interceptor = AuthInterceptor(
            preferencesManager = preferencesManager,
            tokenProvider = null,
            scope = null
        )

        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("raw_audio_bytes".toResponseBody())
                    .build()
            }
            .build()

        // ExoPlayer generates audio request with current active server URL
        val req = Request.Builder()
            .url("http://custom-lan-host:8080/api/songs/456/audio?type=main")
            .build()

        client.newCall(req).execute().close()

        assertNotNull(capturedRequest)
        assertEquals("http://custom-lan-host:8080/api/songs/456/audio?type=main", capturedRequest?.url.toString())
        assertEquals("Bearer media_stream_user_token", capturedRequest?.header("Authorization"))
        assertEquals("audio/*, */*", capturedRequest?.header("Accept"))
    }

    @Test
    fun testDynamicServerSwitchingPreservesExternalCDN() = kotlinx.coroutines.runBlocking {
        AuthTokenHolder.token = "media_stream_user_token"
        preferencesManager.setServerUrl("http://custom-lan-host:8080")

        val interceptor = AuthInterceptor(
            preferencesManager = preferencesManager,
            tokenProvider = null,
            scope = null
        )

        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("raw_audio_bytes".toResponseBody())
                    .build()
            }
            .build()

        // External signed CDN URL
        val req = Request.Builder()
            .url("https://s3.amazonaws.com/music-storage/song.flac?X-Amz-Signature=secret123")
            .build()

        client.newCall(req).execute().close()

        assertNotNull(capturedRequest)
        assertEquals("https://s3.amazonaws.com/music-storage/song.flac?X-Amz-Signature=secret123", capturedRequest?.url.toString())
        assertNull("External CDN URL must never receive Authorization bearer token", capturedRequest?.header("Authorization"))
    }
}
