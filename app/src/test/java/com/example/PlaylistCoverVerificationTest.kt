package com.example

import com.example.data.api.YimlyApiService
import com.example.data.models.LoginRequest
import com.example.data.models.CreatePlaylistRequest
import com.example.data.models.AddPlaylistSongRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor

@RunWith(JUnit4::class)
class PlaylistCoverVerificationTest {

    private fun createDummyImageFile(name: String, color: Color, width: Int, height: Int): File {
        val file = File(System.getProperty("java.io.tmpdir"), name)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(image, "jpg", file)
        return file
    }

    @Test
    fun verifyPlaylistCoversAndVisibility() = runBlocking {
        var currentToken: String? = null

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (!currentToken.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $currentToken")
            }
            val request = builder.build()

            println("[HTTP REQUEST] ${request.method} ${request.url}")
            request.headers.forEach { (name, value) ->
                val displayVal = if (name.equals("Authorization", ignoreCase = true)) "Bearer [AUTHENTICATED_TOKEN]" else value
                println("  > Header $name: $displayVal")
            }
            request.body?.let { body ->
                println("  > Request Body Content-Type: ${body.contentType()}, Content-Length: ${body.contentLength()} bytes")
            }

            val response = chain.proceed(request)
            println("[HTTP RESPONSE] ${response.code} ${response.message} for ${request.url}")
            println("  < Response Content-Type: ${response.header("Content-Type")}, Content-Length: ${response.header("Content-Length")}")

            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        val apiService = Retrofit.Builder()
            .baseUrl("https://yimly.robinhort.link/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YimlyApiService::class.java)

        println("\n=================== STEP 1: AUTHENTICATE TEST USER ===================")
        val loginResp1 = apiService.login(LoginRequest("test", "1234"))
        assertNotNull("Login response must not be null", loginResp1)
        assertTrue("Token must not be empty", loginResp1.token.isNotEmpty())
        assertFalse("Token must not be mock test_bearer_token", loginResp1.token.contains("test_bearer_token"))
        assertEquals("test", loginResp1.user.username)
        currentToken = loginResp1.token
        println("User 'test' authenticated successfully with real server JWT token: ${currentToken.take(25)}...")

        val allSongs = apiService.getSongs()
        assertTrue("Library must contain songs from real server", allSongs.size >= 2)
        println("Retrieved ${allSongs.size} real songs from backend.")

        println("\n=================== STEP 2: CREATE PRIVATE PLAYLIST ===================")
        val reqPriv = CreatePlaylistRequest(name = "Private E2E Test Playlist", description = "Private test playlist", isPublic = false)
        val privatePlaylist = apiService.createPlaylist(reqPriv)
        val privatePlaylistId = privatePlaylist.id
        assertNotNull("Private playlist ID must not be null", privatePlaylistId)
        println("Created private playlist ID: $privatePlaylistId, name: ${privatePlaylist.name}")

        // Add 2 songs
        apiService.addSongToPlaylist(privatePlaylistId, AddPlaylistSongRequest(allSongs[0].id))
        apiService.addSongToPlaylist(privatePlaylistId, AddPlaylistSongRequest(allSongs[1].id))
        println("Added 2 real songs to private playlist: ${allSongs[0].title}, ${allSongs[1].title}")

        // Upload Real Image #1 (Blue, 150x150)
        println("\n=================== STEP 3: UPLOAD PRIVATE PLAYLIST COVER ===================")
        val privateImageFile = createDummyImageFile("private_cover_blue.jpg", Color.BLUE, 150, 150)
        assertTrue("Image file #1 exists", privateImageFile.exists())
        val reqFile1 = privateImageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val uploadResp1 = apiService.uploadPlaylistCover(privatePlaylistId, reqFile1)
        assertNotNull("Upload response must not be null", uploadResp1)
        println("Upload response: id=${uploadResp1.id}, coverUrl=${uploadResp1.coverUrl}")

        // Reload private playlist from server
        println("\n=================== STEP 4: RELOAD PRIVATE PLAYLIST ===================")
        val fetchedPrivate = apiService.getPlaylistById(privatePlaylistId)
        assertNotNull("Reloaded private playlist must not be null", fetchedPrivate)
        assertEquals("Private playlist song count must be 2", 2, fetchedPrivate.songs.size)
        assertNotNull("Cover URL must not be null", fetchedPrivate.coverUrl)
        println("Fetched private cover URL: ${fetchedPrivate.coverUrl}")

        assertTrue("Cover URL must be server URL", fetchedPrivate.coverUrl!!.startsWith("https://yimly.robinhort.link/api/playlists/"))
        assertFalse("Cover URL must NOT be file URI", fetchedPrivate.coverUrl!!.startsWith("file://"))
        assertFalse("Cover URL must NOT be content URI", fetchedPrivate.coverUrl!!.startsWith("content://"))
        assertFalse("Cover URL must NOT be base64", fetchedPrivate.coverUrl!!.startsWith("data:"))

        // Download private cover as 'test'
        val privateCoverReq = Request.Builder().url(fetchedPrivate.coverUrl!!).build()
        val privateCoverResp = client.newCall(privateCoverReq).execute()
        assertEquals("Private cover download by owner should return 200", 200, privateCoverResp.code)
        val privateCoverBytes = privateCoverResp.body?.bytes()
        assertNotNull("Private cover image bytes must not be null", privateCoverBytes)
        assertTrue("Private cover image bytes must not be empty", privateCoverBytes!!.isNotEmpty())
        println("Downloaded private cover image from server: ${privateCoverBytes.size} bytes (HTTP 200)")

        println("\n=================== STEP 5: CREATE PUBLIC PLAYLIST ===================")
        val reqPub = CreatePlaylistRequest(name = "Public E2E Test Playlist", description = "Public test playlist", isPublic = true)
        val publicPlaylist = apiService.createPlaylist(reqPub)
        val publicPlaylistId = publicPlaylist.id
        assertNotNull("Public playlist ID must not be null", publicPlaylistId)
        println("Created public playlist ID: $publicPlaylistId, name: ${publicPlaylist.name}")

        // Add 2 songs
        apiService.addSongToPlaylist(publicPlaylistId, AddPlaylistSongRequest(allSongs[0].id))
        apiService.addSongToPlaylist(publicPlaylistId, AddPlaylistSongRequest(allSongs[1].id))
        println("Added 2 real songs to public playlist: ${allSongs[0].title}, ${allSongs[1].title}")

        // Upload Real Image #2 (Red, 200x200 - different file, dimensions and bytes)
        println("\n=================== STEP 6: UPLOAD PUBLIC PLAYLIST COVER ===================")
        val publicImageFile = createDummyImageFile("public_cover_red.jpg", Color.RED, 200, 200)
        assertTrue("Image file #2 exists", publicImageFile.exists())
        val reqFile2 = publicImageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val uploadResp2 = apiService.uploadPlaylistCover(publicPlaylistId, reqFile2)
        assertNotNull("Upload response must not be null", uploadResp2)
        println("Upload response: id=${uploadResp2.id}, coverUrl=${uploadResp2.coverUrl}")

        // Reload public playlist from server
        println("\n=================== STEP 7: RELOAD PUBLIC PLAYLIST ===================")
        val fetchedPublic = apiService.getPlaylistById(publicPlaylistId)
        assertNotNull("Reloaded public playlist must not be null", fetchedPublic)
        assertEquals("Public playlist song count must be 2", 2, fetchedPublic.songs.size)
        assertNotNull("Cover URL must not be null", fetchedPublic.coverUrl)
        println("Fetched public cover URL: ${fetchedPublic.coverUrl}")

        assertTrue("Cover URL must be server URL", fetchedPublic.coverUrl!!.startsWith("https://yimly.robinhort.link/api/playlists/"))
        assertFalse("Cover URL must NOT be file URI", fetchedPublic.coverUrl!!.startsWith("file://"))
        assertFalse("Cover URL must NOT be content URI", fetchedPublic.coverUrl!!.startsWith("content://"))
        assertFalse("Cover URL must NOT be base64", fetchedPublic.coverUrl!!.startsWith("data:"))

        // Download public cover as 'test'
        val publicCoverReq = Request.Builder().url(fetchedPublic.coverUrl!!).build()
        val publicCoverResp = client.newCall(publicCoverReq).execute()
        assertEquals("Public cover download by owner should return 200", 200, publicCoverResp.code)
        val publicCoverBytes = publicCoverResp.body?.bytes()
        assertNotNull("Public cover image bytes must not be null", publicCoverBytes)
        assertTrue("Public cover image bytes must not be empty", publicCoverBytes!!.isNotEmpty())
        println("Downloaded public cover image from server: ${publicCoverBytes.size} bytes (HTTP 200)")

        println("\n=================== STEP 8: AUTHENTICATE TEST2 USER ===================")
        val loginResp2 = apiService.login(LoginRequest("test2", "1234"))
        assertNotNull("Login response for test2 must not be null", loginResp2)
        assertTrue("Token must not be empty", loginResp2.token.isNotEmpty())
        assertFalse("Token must not be mock test_bearer_token", loginResp2.token.contains("test_bearer_token"))
        assertEquals("test2", loginResp2.user.username)
        currentToken = loginResp2.token
        println("User 'test2' authenticated successfully with real server JWT token: ${currentToken.take(25)}...")

        println("\n=================== STEP 9: TEST2 ACCESS PERMISSIONS ===================")
        // 1. Verify test2 CANNOT access private playlist
        var test2SawPrivate = false
        try {
            apiService.getPlaylistById(privatePlaylistId)
            test2SawPrivate = true
        } catch (e: retrofit2.HttpException) {
            println("Expected denial for test2 accessing private playlist: HTTP ${e.code()} ${e.message()}")
        }
        assertFalse("test2 MUST NOT have access to private playlist", test2SawPrivate)

        // 2. Verify test2 CAN access public playlist
        var test2SawPublic = false
        var test2FetchedPublic: com.example.data.models.Playlist? = null
        try {
            test2FetchedPublic = apiService.getPlaylistById(publicPlaylistId)
            test2SawPublic = true
        } catch (e: Exception) {
            println("Unexpected failure for test2 accessing public playlist: ${e.message}")
        }
        assertTrue("test2 MUST have access to public playlist", test2SawPublic)
        assertNotNull(test2FetchedPublic)
        assertEquals("Public playlist ID matches", publicPlaylistId, test2FetchedPublic!!.id)
        assertEquals("Public playlist cover URL matches", fetchedPublic.coverUrl, test2FetchedPublic.coverUrl)

        // 3. Verify test2 CAN download the public playlist cover from the server
        val test2CoverReq = Request.Builder().url(test2FetchedPublic.coverUrl!!).build()
        val test2CoverResp = client.newCall(test2CoverReq).execute()
        assertEquals("test2 should receive HTTP 200 when downloading public cover", 200, test2CoverResp.code)
        val test2CoverBytes = test2CoverResp.body?.bytes()
        assertNotNull("test2 downloaded cover bytes must not be null", test2CoverBytes)
        assertEquals("Downloaded cover bytes size should match", publicCoverBytes.size, test2CoverBytes!!.size)
        println("User 'test2' successfully downloaded public cover: ${test2CoverBytes.size} bytes (HTTP 200)")

        println("\n=================== FINAL REPORT ===================")
        println("REPORT_START")
        println("Private Playlist: Name=${privatePlaylist.name}, ID=$privatePlaylistId")
        println("Public Playlist: Name=${publicPlaylist.name}, ID=$publicPlaylistId")
        println("Private Playlist Songs: ${fetchedPrivate.songs.size}")
        println("Public Playlist Songs: ${fetchedPublic.songs.size}")
        println("Private Cover Upload: ${fetchedPrivate.coverUrl != null}")
        println("Public Cover Upload: ${fetchedPublic.coverUrl != null}")
        println("Private Cover URL: ${fetchedPrivate.coverUrl}")
        println("Public Cover URL: ${fetchedPublic.coverUrl}")
        println("Test2 Access Private: ${if (test2SawPrivate) "Granted" else "Denied"}")
        println("Test2 Access Public: ${if (test2SawPublic) "Granted" else "Denied"}")
        println("Test2 Cover Download: ${test2CoverResp.code == 200}")
        println("REPORT_END")
    }
}
