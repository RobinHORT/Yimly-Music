package com.example.data.api

import com.example.data.models.AddPlaylistSongRequest
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Collaborator
import com.example.data.models.CreatePlaylistRequest
import com.example.data.models.GuestSongRequest
import com.example.data.models.LoginRequest
import com.example.data.models.LoginResponse
import com.example.data.models.LyricsData
import com.example.data.models.Playlist
import com.example.data.models.SearchResult
import com.example.data.models.SharePlaylistRequest
import com.example.data.models.Song
import com.example.data.models.UpdatePlaylistRequest
import com.example.data.models.UserProfile
import com.example.data.models.YimlySession
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface YimlyApiService {

    // Auth
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun getProfile(): UserProfile

    @POST("api/auth/logout")
    suspend fun logout(): Map<String, Any>

    // Songs
    @GET("api/songs")
    suspend fun getSongs(
        @Query("q") query: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Song>

    @GET("api/songs/{id}")
    suspend fun getSongById(@Path("id") id: String): Song

    // Albums
    @GET("api/albums")
    suspend fun getAlbums(): List<Album>

    @GET("api/albums/{id}/songs")
    suspend fun getAlbumSongs(@Path("id") id: String): List<Song>

    // Artists
    @GET("api/artists")
    suspend fun getArtists(): List<Artist>

    @GET("api/artists/{id}/songs")
    suspend fun getArtistSongs(@Path("id") id: String): List<Song>

    // Playlists
    @GET("api/playlists")
    suspend fun getPlaylists(): List<Playlist>

    @GET("api/playlists/{id}")
    suspend fun getPlaylistById(@Path("id") id: String): Playlist

    @GET("api/playlists/{id}/songs")
    suspend fun getPlaylistSongs(@Path("id") id: String): List<Song>

    @POST("api/playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): Playlist

    @PUT("api/playlists/{id}")
    suspend fun updatePlaylist(@Path("id") id: String, @Body request: UpdatePlaylistRequest): Playlist

    @POST("api/playlists/{id}/cover")
    suspend fun uploadPlaylistCover(
        @Path("id") id: String,
        @Body coverBody: okhttp3.RequestBody
    ): Playlist

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: String): Map<String, Any>

    @POST("api/playlists/{id}/songs")
    suspend fun addSongToPlaylist(@Path("id") id: String, @Body request: AddPlaylistSongRequest): Map<String, Any>

    @DELETE("api/playlists/{id}/songs/{songId}")
    suspend fun removeSongFromPlaylist(@Path("id") playlistId: String, @Path("songId") songId: String): Map<String, Any>

    // Sharing & Collaborators
    @POST("api/playlists/{id}/share")
    suspend fun sharePlaylist(@Path("id") id: String, @Body request: SharePlaylistRequest): Map<String, Any>

    @GET("api/playlists/{id}/collaborators")
    suspend fun getCollaborators(@Path("id") id: String): List<Collaborator>

    @DELETE("api/playlists/{id}/collaborators/{userId}")
    suspend fun removeCollaborator(@Path("id") playlistId: String, @Path("userId") userId: String): Map<String, Any>

    @DELETE("api/playlists/{id}/share/{userId}")
    suspend fun revokeShare(@Path("id") playlistId: String, @Path("userId") userId: String): Map<String, Any>

    // Favorites
    @GET("api/favorites")
    suspend fun getFavorites(): List<Song>

    @POST("api/favorites/{songId}")
    suspend fun addFavorite(@Path("songId") songId: String): Map<String, Any>

    @DELETE("api/favorites/{songId}")
    suspend fun removeFavorite(@Path("songId") songId: String): Map<String, Any>

    // Search
    @GET("api/search")
    suspend fun search(@Query("q") query: String): SearchResult

    // Lyrics
    @GET("api/songs/{songId}/lyrics")
    suspend fun getLyrics(@Path("songId") songId: String): okhttp3.ResponseBody

    @POST("api/songs/{id}/lrc")
    suspend fun createLyrics(@Path("id") id: String, @Body body: okhttp3.RequestBody): okhttp3.ResponseBody

    @PUT("api/songs/{id}/lrc")
    suspend fun updateLyrics(@Path("id") id: String, @Body body: okhttp3.RequestBody): okhttp3.ResponseBody

    @retrofit2.http.PATCH("api/songs/{id}/lrc")
    suspend fun patchLyrics(@Path("id") id: String, @Body body: okhttp3.RequestBody): okhttp3.ResponseBody

    @PUT("api/songs/{id}/lrc")
    suspend fun updateLyricsJson(@Path("id") id: String, @Body request: Map<String, String>): Map<String, Any>

    @DELETE("api/songs/{id}/lrc")
    suspend fun deleteLrc(@Path("id") id: String): Map<String, Any>

    @DELETE("api/songs/{id}")
    suspend fun deleteSong(@Path("id") id: String): Map<String, Any>

    @GET("api/songs/{id}/lyrics/offset")
    suspend fun getLyricsOffset(@Path("id") id: String): com.example.data.models.LyricsOffsetResponse

    @PUT("api/songs/{id}/lyrics/offset")
    suspend fun updateLyricsOffset(@Path("id") id: String, @Body request: com.example.data.models.UpdateLyricsOffsetRequest): Map<String, Any>

    // Sessions
    @GET("api/sessions/{code}")
    suspend fun getSession(@Path("code") code: String): YimlySession

    @POST("api/sessions/create")
    suspend fun createSession(): YimlySession

    @POST("api/sessions/{code}/join")
    suspend fun joinSession(@Path("code") code: String): YimlySession

    @POST("api/sessions/{code}/request")
    suspend fun requestSongInSession(
        @Path("code") code: String,
        @Body song: Song
    ): Map<String, Any>
}
