package com.example.data.models

import com.example.data.datastore.PreferencesManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Song(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String = "",
    @Json(name = "album") val album: String = "",
    @Json(name = "duration") val durationSeconds: Long = 0L,
    @Json(name = "artwork_url") val explicitArtworkUrl: String? = null,
    @Json(name = "audio_url") val explicitAudioUrl: String? = null,
    @Json(name = "artworkPath") val artworkPath: String? = null,
    @Json(name = "albumArtworkPath") val albumArtworkPath: String? = null,
    @Json(name = "mainAudioPath") val mainAudioPath: String? = null,
    @Json(name = "main_audio_path") val mainAudioPathSnake: String? = null,
    @Json(name = "instrumentalAudioPath") val instrumentalAudioPath: String? = null,
    @Json(name = "instrumental_audio_path") val instrumentalAudioPathSnake: String? = null,
    @Json(name = "instrumentalPath") val instrumentalPath: String? = null,
    @Json(name = "instrumental_path") val instrumentalPathSnake: String? = null,
    @Json(name = "instrumental_url") val instrumentalUrl: String? = null,
    @Json(name = "instrumentalAudioUrl") val instrumentalAudioUrl: String? = null,
    @Json(name = "hasInstrumental") val hasInstrumental: Boolean = false,
    @Json(name = "has_instrumental") val hasInstrumentalSnake: Boolean? = null,
    @Json(name = "is_instrumental") val isInstrumentalField: Boolean? = null,
    @Json(name = "isInstrumental") val isInstrumental: Boolean = false,
    @Json(name = "version") val version: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "trackType") val trackType: String? = null,
    @Json(name = "instrumentalSongId") val instrumentalSongId: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "hasLrc") val hasLrc: Boolean = false,
    @Json(name = "hasArtwork") val hasArtwork: Boolean = false,
    @Json(name = "lyrics") val lyricsText: String? = null,
    @Json(name = "is_favorite") val isFavorite: Boolean = false,
    @Json(name = "lyricOffset") val lyricOffset: Long? = null,
    @Json(name = "added_at") val addedAt: Long = System.currentTimeMillis()
) {
    val artworkUrl: String?
        get() {
            if (!explicitArtworkUrl.isNullOrBlank()) return explicitArtworkUrl
            return "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/$id/artwork"
        }

    val effectiveMainAudioPath: String?
        get() = mainAudioPath ?: mainAudioPathSnake

    val effectiveInstrumentalAudioPath: String?
        get() = instrumentalAudioPath ?: instrumentalAudioPathSnake ?: instrumentalPath ?: instrumentalPathSnake

    val hasInstrumentalEffective: Boolean
        get() = hasInstrumental || (hasInstrumentalSnake == true) || !effectiveInstrumentalAudioPath.isNullOrBlank() || !instrumentalAudioUrl.isNullOrBlank() || !instrumentalUrl.isNullOrBlank()

    val audioUrl: String
        get() {
            if (!explicitAudioUrl.isNullOrBlank()) return explicitAudioUrl
            return effectiveMainAudioPath?.let {
                if (it.startsWith("http")) it else "${PreferencesManager.DEFAULT_SERVER_URL}$it"
            } ?: "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/$id/audio?type=main"
        }

    val directInstrumentalAudioUrl: String?
        get() {
            val direct = instrumentalAudioUrl ?: instrumentalUrl
            if (!direct.isNullOrBlank()) return direct
            val path = effectiveInstrumentalAudioPath
            if (!path.isNullOrBlank()) {
                return if (path.startsWith("http")) path else "${PreferencesManager.DEFAULT_SERVER_URL}$path"
            }
            if (hasInstrumentalEffective) {
                return "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/$id/audio?type=instrumental"
            }
            return null
        }

    val isInstrumentalTrack: Boolean
        get() {
            if (isInstrumental || isInstrumentalField == true) return true
            if (version?.trim()?.equals("instrumental", ignoreCase = true) == true) return true
            if (type?.trim()?.equals("instrumental", ignoreCase = true) == true) return true
            if (trackType?.trim()?.equals("instrumental", ignoreCase = true) == true) return true
            val lowTitle = title.lowercase().trim()
            if (lowTitle.contains("(instrumental)") || lowTitle.contains("[instrumental]") ||
                lowTitle.endsWith(" instrumental") || lowTitle.contains("- instrumental") ||
                lowTitle.contains("(inst)") || lowTitle.contains("[inst]") ||
                lowTitle.contains("(inst.)") || lowTitle.contains("[inst.]") ||
                lowTitle.contains("- inst") || lowTitle.endsWith(" inst") ||
                lowTitle.contains("instrumental version") || lowTitle.contains("version instrumental") ||
                lowTitle.contains("instrumental audio") || lowTitle.contains("instrumental track") ||
                lowTitle.contains("instrumental mix") ||
                lowTitle.contains("(karaoke)") || lowTitle.contains("[karaoke]") ||
                lowTitle.contains("- karaoke") || lowTitle.endsWith(" karaoke") ||
                lowTitle.contains("(backing track)") || lowTitle.contains("[backing track]") ||
                lowTitle.contains("- backing track") || lowTitle.endsWith(" backing track") ||
                lowTitle.contains("(off vocal)") || lowTitle.contains("[off vocal]") ||
                lowTitle.contains("- off vocal") || lowTitle.endsWith(" off vocal") ||
                lowTitle.contains("(vocal off)") || lowTitle.contains("[vocal off]")
            ) {
                return true
            }
            val audioPathLower = (mainAudioPath ?: explicitAudioUrl)?.lowercase() ?: ""
            if (audioPathLower.contains("instrumental") ||
                audioPathLower.contains("_inst") ||
                audioPathLower.contains("-inst") ||
                audioPathLower.contains("[inst]") ||
                audioPathLower.contains("(inst)") ||
                audioPathLower.contains("backing_track") ||
                audioPathLower.contains("off_vocal")
            ) {
                return true
            }
            return false
        }

    val durationMs: Long
        get() = durationSeconds * 1000L

    fun formattedDuration(): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}

@JsonClass(generateAdapter = true)
data class Album(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String = "",
    @Json(name = "artwork_url") val explicitArtworkUrl: String? = null,
    @Json(name = "artworkPath") val artworkPath: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "song_count") val songCount: Int = 0,
    @Json(name = "description") val description: String? = null
) {
    val artworkUrl: String?
        get() {
            if (!explicitArtworkUrl.isNullOrBlank()) return explicitArtworkUrl
            return "${PreferencesManager.DEFAULT_SERVER_URL}/api/albums/$id/artwork"
        }
}

@JsonClass(generateAdapter = true)
data class Artist(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "avatar_url") val explicitAvatarUrl: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "monthly_listeners") val monthlyListeners: Long = 0L,
    @Json(name = "song_count") val songCount: Int = 0,
    @Json(name = "genres") val genres: List<String> = emptyList()
) {
    val avatarUrl: String?
        get() {
            if (!explicitAvatarUrl.isNullOrBlank()) return explicitAvatarUrl
            return "${PreferencesManager.DEFAULT_SERVER_URL}/api/artists/$id/artwork"
        }
}

@JsonClass(generateAdapter = true)
data class Playlist(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "coverUrl") val rawCoverUrl: String? = null,
    @Json(name = "cover_url") val rawCoverUrlSnake: String? = null,
    @Json(name = "coverImage") val rawCoverImage: String? = null,
    @Json(name = "cover_image") val rawCoverImageSnake: String? = null,
    @Json(name = "coverPath") val rawCoverPath: String? = null,
    @Json(name = "cover_path") val rawCoverPathSnake: String? = null,
    @Json(name = "cover") val rawCover: String? = null,
    @Json(name = "artwork_url") val rawArtworkUrl: String? = null,
    @Json(name = "songCount") val songCount: Int = 0,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "isCustom") val isCustom: Boolean = true,
    @Json(name = "userId") val userId: String? = null,
    @Json(name = "ownerName") val ownerName: String? = null,
    @Json(name = "isOwner") val isOwner: Boolean = true,
    @Json(name = "canEdit") val canEdit: Boolean = true,
    @Json(name = "permission") val permission: String = "owner",
    @Json(name = "isPublic") val isPublic: Boolean = false,
    @Json(name = "songs") val songs: List<Song> = emptyList()
) {
    val coverUrl: String?
        get() {
            val explicit = (rawCoverUrl
                ?: rawCoverUrlSnake
                ?: rawCoverImage
                ?: rawCoverImageSnake
                ?: rawCoverPath
                ?: rawCoverPathSnake
                ?: rawCover
                ?: rawArtworkUrl)
            if (!explicit.isNullOrBlank()) {
                val trimmed = explicit.trim()
                if (trimmed.startsWith("file://", ignoreCase = true) ||
                    trimmed.startsWith("content://", ignoreCase = true) ||
                    trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true)) {
                    if (!trimmed.contains("/api/playlists/") || trimmed.contains("/cover")) {
                        return trimmed
                    }
                }
            }
            val baseUrl = PreferencesManager.DEFAULT_SERVER_URL.trimEnd('/')
            val tParam = if (!createdAt.isNullOrBlank()) {
                val parsed = createdAt.toLongOrNull()
                if (parsed != null) parsed else createdAt.hashCode().toLong().let { if (it < 0) -it else it }
            } else {
                System.currentTimeMillis()
            }
            return "$baseUrl/api/playlists/$id/cover?t=$tParam"
        }
}

@JsonClass(generateAdapter = true)
data class CreatePlaylistRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "isPublic") val isPublic: Boolean? = false,
    @Json(name = "coverUrl") val coverUrl: String? = null,
    @Json(name = "cover_url") val coverUrlSnake: String? = null,
    @Json(name = "cover") val cover: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdatePlaylistRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "isPublic") val isPublic: Boolean? = null,
    @Json(name = "coverUrl") val coverUrl: String? = null,
    @Json(name = "cover_url") val coverUrlSnake: String? = null,
    @Json(name = "cover") val cover: String? = null
)

@JsonClass(generateAdapter = true)
data class AddPlaylistSongRequest(
    @Json(name = "songId") val songId: String
)

@JsonClass(generateAdapter = true)
data class SharePlaylistRequest(
    @Json(name = "username") val username: String,
    @Json(name = "permission") val permission: String
)

@JsonClass(generateAdapter = true)
data class Collaborator(
    @Json(name = "id") val id: String = "",
    @Json(name = "userId") val userId: String = "",
    @Json(name = "username") val username: String = "",
    @Json(name = "permission") val permission: String = "view",
    @Json(name = "addedAt") val addedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class LyricsOffsetResponse(
    @Json(name = "songId") val songId: String,
    @Json(name = "lyricOffset") val lyricOffset: Long
)

@JsonClass(generateAdapter = true)
data class UpdateLyricsOffsetRequest(
    @Json(name = "offset") val offset: Long
)
