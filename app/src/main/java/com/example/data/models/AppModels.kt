package com.example.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "token") val token: String,
    @Json(name = "user") val user: UserProfile,
    @Json(name = "expires_in") val expiresIn: Long? = null
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "id") val id: String,
    @Json(name = "username") val username: String,
    @Json(name = "email") val email: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_admin") val rawIsAdmin: Boolean = false,
    @Json(name = "role") val role: String? = null
) {
    val isAdmin: Boolean
        get() = role.equals("administrator", ignoreCase = true) || role.equals("admin", ignoreCase = true) || rawIsAdmin
}

sealed class AuthState {
    data object Authenticating : AuthState()
    data class Authenticated(val user: UserProfile, val token: String) : AuthState()
    data class Unauthenticated(val errorMessage: String? = null) : AuthState()
}

@JsonClass(generateAdapter = true)
data class SearchResult(
    @Json(name = "songs") val songs: List<Song> = emptyList(),
    @Json(name = "artists") val artists: List<Artist> = emptyList(),
    @Json(name = "albums") val albums: List<Album> = emptyList(),
    @Json(name = "playlists") val playlists: List<Playlist> = emptyList(),
    @Json(name = "externalSongs") val externalSongs: List<ExternalTrack> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExternalTrack(
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String = "",
    @Json(name = "album") val album: String = "",
    @Json(name = "duration") val durationSeconds: Long = 0L,
    @Json(name = "artworkUrl") val artworkUrl: String? = null,
    @Json(name = "releaseYear") val releaseYear: Int? = null,
    @Json(name = "trackNumber") val trackNumber: Int? = null,
    @Json(name = "discNumber") val discNumber: Int? = null,
    @Json(name = "sourceUrl") val sourceUrl: String? = null,
    @Json(name = "spotifyId") val spotifyId: String? = null,
    @Json(name = "type") val type: String? = null
) {
    val durationMs: Long
        get() = durationSeconds * 1000L

    fun formattedDuration(): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}

@JsonClass(generateAdapter = true)
data class DownloaderResolveRequest(
    @Json(name = "urlOrQuery") val urlOrQuery: String,
    @Json(name = "offset") val offset: Int? = null
)

@JsonClass(generateAdapter = true)
data class DownloaderResolveResponse(
    @Json(name = "tracks") val tracks: List<ExternalTrack> = emptyList(),
    @Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class DownloaderStartRequest(
    @Json(name = "libraryId") val libraryId: String,
    @Json(name = "tracks") val tracks: List<ExternalTrack>
)

@JsonClass(generateAdapter = true)
data class DownloaderStartResponse(
    @Json(name = "jobId") val jobId: String,
    @Json(name = "message") val message: String? = null,
    @Json(name = "totalTracks") val totalTracks: Int = 0,
    @Json(name = "libraryName") val libraryName: String? = null,
    @Json(name = "libraryPath") val libraryPath: String? = null
)

@JsonClass(generateAdapter = true)
data class DownloaderJobTrack(
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String = "",
    @Json(name = "status") val status: String = "queued", // queued, searching, downloading, tagging, completed, failed
    @Json(name = "progress") val progress: Int = 0,
    @Json(name = "error") val error: String? = null,
    @Json(name = "retryCount") val retryCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class DownloaderJobResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "jobId") val rawJobId: String? = null,
    @Json(name = "status") val status: String = "running",
    @Json(name = "tracks") val tracks: List<DownloaderJobTrack> = emptyList()
) {
    val jobId: String
        get() = rawJobId ?: id ?: ""
}

@JsonClass(generateAdapter = true)
data class DownloaderSettingsResponse(
    @Json(name = "libraryId") val libraryId: String? = null,
    @Json(name = "libraryName") val libraryName: String? = null
)

@JsonClass(generateAdapter = true)
data class GuestSongRequest(
    @Json(name = "id") val id: String,
    @Json(name = "song") val song: Song,
    @Json(name = "requested_by") val requestedBy: String,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class YimlySession(
    @Json(name = "room_code") val roomCode: String,
    @Json(name = "host_name") val hostName: String,
    @Json(name = "host_id") val hostId: String,
    @Json(name = "current_song") val currentSong: Song? = null,
    @Json(name = "current_position_ms") val currentPositionMs: Long = 0L,
    @Json(name = "is_playing") val isPlaying: Boolean = false,
    @Json(name = "participants_count") val participantsCount: Int = 1,
    @Json(name = "queue") val queue: List<Song> = emptyList(),
    @Json(name = "guest_requests") val guestRequests: List<GuestSongRequest> = emptyList(),
    @Json(name = "allow_guest_requests") val allowGuestRequests: Boolean = true
)
