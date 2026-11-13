package com.example.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.data.models.Song

enum class MediaIdSource {
    DIRECT,
    ALL_SONGS,
    RECENTLY_PLAYED,
    FAVORITES,
    PLAYLIST
}

data class ParsedMediaId(
    val rawMediaId: String,
    val canonicalSongId: String,
    val isInstrumental: Boolean,
    val source: MediaIdSource,
    val playlistId: String? = null
) {
    val isAndroidAutoSource: Boolean
        get() = source != MediaIdSource.DIRECT
}

object CanonicalMediaId {
    const val PREFIX_ALL_SONG = "all_song"
    const val PREFIX_RECENT_SONG = "recent_song"
    const val PREFIX_FAVORITE_SONG = "favorite_song"
    const val PREFIX_PLAYLIST_SONG = "playlist_song"
    const val INSTRUMENTAL_SUFFIX = "_inst"

    fun parse(mediaId: String?): ParsedMediaId? {
        if (mediaId.isNullOrBlank()) return null

        return if (mediaId.contains('|')) {
            val parts = mediaId.split('|')
            when (parts[0]) {
                PREFIX_ALL_SONG -> parseSingleSegment(mediaId, parts, MediaIdSource.ALL_SONGS)
                PREFIX_RECENT_SONG -> parseSingleSegment(mediaId, parts, MediaIdSource.RECENTLY_PLAYED)
                PREFIX_FAVORITE_SONG -> parseSingleSegment(mediaId, parts, MediaIdSource.FAVORITES)
                PREFIX_PLAYLIST_SONG -> parsePlaylistSegment(mediaId, parts)
                else -> null
            }
        } else {
            val isInstrumental = mediaId.endsWith(INSTRUMENTAL_SUFFIX)
            val songId = if (isInstrumental) mediaId.removeSuffix(INSTRUMENTAL_SUFFIX) else mediaId
            if (songId.isBlank()) return null
            ParsedMediaId(
                rawMediaId = mediaId,
                canonicalSongId = songId,
                isInstrumental = isInstrumental,
                source = MediaIdSource.DIRECT
            )
        }
    }

    private fun parseSingleSegment(
        rawMediaId: String,
        parts: List<String>,
        source: MediaIdSource
    ): ParsedMediaId? {
        if (parts.size != 2) return null
        val songToken = parts[1]
        val isInstrumental = songToken.endsWith(INSTRUMENTAL_SUFFIX)
        val songId = if (isInstrumental) songToken.removeSuffix(INSTRUMENTAL_SUFFIX) else songToken
        if (songId.isBlank()) return null
        return ParsedMediaId(
            rawMediaId = rawMediaId,
            canonicalSongId = songId,
            isInstrumental = isInstrumental,
            source = source
        )
    }

    private fun parsePlaylistSegment(
        rawMediaId: String,
        parts: List<String>
    ): ParsedMediaId? {
        if (parts.size != 3) return null
        val playlistId = parts[1]
        if (playlistId.isBlank()) return null
        val songToken = parts[2]
        val isInstrumental = songToken.endsWith(INSTRUMENTAL_SUFFIX)
        val songId = if (isInstrumental) songToken.removeSuffix(INSTRUMENTAL_SUFFIX) else songToken
        if (songId.isBlank()) return null
        return ParsedMediaId(
            rawMediaId = rawMediaId,
            canonicalSongId = songId,
            isInstrumental = isInstrumental,
            source = MediaIdSource.PLAYLIST,
            playlistId = playlistId
        )
    }

    fun extractCanonicalSongId(mediaId: String?): String? = parse(mediaId)?.canonicalSongId

    fun isAndroidAutoMediaId(mediaId: String?): Boolean = parse(mediaId)?.isAndroidAutoSource == true

    fun forPlayback(songId: String, isInstrumental: Boolean): String =
        if (isInstrumental) "$songId$INSTRUMENTAL_SUFFIX" else songId

    fun forAllSongs(songId: String): String = "$PREFIX_ALL_SONG|$songId"

    fun forRecentlyPlayed(songId: String): String = "$PREFIX_RECENT_SONG|$songId"

    fun forFavorites(songId: String): String = "$PREFIX_FAVORITE_SONG|$songId"

    fun forPlaylist(playlistId: String, songId: String): String = "$PREFIX_PLAYLIST_SONG|$playlistId|$songId"
}

object PlaybackMediaMetadata {
    const val EXTRA_CANONICAL_SONG_ID = "com.example.playback.extra.CANONICAL_SONG_ID"
    const val EXTRA_HAS_INSTRUMENTAL = "com.example.playback.extra.HAS_INSTRUMENTAL"
    const val EXTRA_IS_INSTRUMENTAL_ACTIVE = "com.example.playback.extra.IS_INSTRUMENTAL_ACTIVE"
    const val EXTRA_IS_FAVORITE = "com.example.playback.extra.IS_FAVORITE"
    const val EXTRA_DURATION_SECONDS = "com.example.playback.extra.DURATION_SECONDS"

    fun buildForSong(
        song: Song,
        isInstrumentalActive: Boolean,
        artworkUri: Uri?
    ): MediaMetadata {
        val extras = Bundle().apply {
            putString(EXTRA_CANONICAL_SONG_ID, song.id)
            putBoolean(EXTRA_HAS_INSTRUMENTAL, song.hasInstrumentalEffective)
            putBoolean(EXTRA_IS_INSTRUMENTAL_ACTIVE, isInstrumentalActive)
            putBoolean(EXTRA_IS_FAVORITE, song.isFavorite)
            if (song.durationSeconds > 0L) {
                putLong(EXTRA_DURATION_SECONDS, song.durationSeconds)
            }
        }
        return MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(artworkUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                if (song.durationSeconds > 0L) {
                    setDurationMs(song.durationMs)
                }
            }
            .setExtras(extras)
            .build()
    }

    fun withFavoriteStatus(
        metadata: MediaMetadata,
        canonicalSongId: String,
        isFavorite: Boolean
    ): MediaMetadata {
        val newExtras = (metadata.extras?.let { Bundle(it) } ?: Bundle()).apply {
            if (getString(EXTRA_CANONICAL_SONG_ID).isNullOrBlank()) {
                putString(EXTRA_CANONICAL_SONG_ID, canonicalSongId)
            }
            putBoolean(EXTRA_IS_FAVORITE, isFavorite)
        }
        return metadata.buildUpon()
            .setExtras(newExtras)
            .build()
    }

    fun resolveCanonicalSongId(mediaItem: MediaItem): String? {
        val extraId = mediaItem.mediaMetadata.extras
            ?.getString(EXTRA_CANONICAL_SONG_ID)
            ?.takeIf { it.isNotBlank() }
        return extraId ?: CanonicalMediaId.extractCanonicalSongId(mediaItem.mediaId)
    }
}
