package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song

@Entity(
    tableName = "songs",
    indices = [
        Index("title"),
        Index("artist"),
        Index("album")
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Long,
    val artworkPath: String? = null,
    val albumArtworkPath: String? = null,
    val mainAudioPath: String? = null,
    val instrumentalAudioPath: String? = null,
    val instrumentalAudioUrl: String? = null,
    val hasInstrumental: Boolean = false,
    val isInstrumentalTrack: Boolean = false,
    val version: String? = null,
    val type: String? = null,
    val instrumentalSongId: String? = null,
    val explicitArtworkUrl: String? = null,
    val explicitAudioUrl: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val hasLrc: Boolean = false,
    val hasArtwork: Boolean = false,
    val lyricsText: String? = null,
    val isFavorite: Boolean = false,
    val lyricOffset: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        explicitArtworkUrl = explicitArtworkUrl,
        explicitAudioUrl = explicitAudioUrl,
        artworkPath = artworkPath,
        albumArtworkPath = albumArtworkPath,
        mainAudioPath = mainAudioPath,
        instrumentalAudioPath = instrumentalAudioPath,
        instrumentalAudioUrl = instrumentalAudioUrl,
        hasInstrumental = hasInstrumental,
        isInstrumental = isInstrumentalTrack,
        version = version,
        type = type,
        instrumentalSongId = instrumentalSongId,
        year = year,
        genre = genre,
        hasLrc = hasLrc,
        hasArtwork = hasArtwork,
        lyricsText = lyricsText,
        isFavorite = isFavorite,
        lyricOffset = lyricOffset,
        addedAt = addedAt
    )
}

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = durationSeconds,
    artworkPath = artworkPath,
    albumArtworkPath = albumArtworkPath,
    mainAudioPath = mainAudioPath,
    instrumentalAudioPath = instrumentalAudioPath ?: instrumentalPath,
    instrumentalAudioUrl = directInstrumentalAudioUrl,
    hasInstrumental = hasInstrumental || !directInstrumentalAudioUrl.isNullOrBlank(),
    isInstrumentalTrack = isInstrumentalTrack,
    version = version,
    type = type,
    instrumentalSongId = instrumentalSongId,
    explicitArtworkUrl = explicitArtworkUrl ?: artworkUrl,
    explicitAudioUrl = explicitAudioUrl ?: audioUrl,
    year = year,
    genre = genre,
    hasLrc = hasLrc,
    hasArtwork = hasArtwork,
    lyricsText = lyricsText,
    isFavorite = isFavorite,
    lyricOffset = lyricOffset,
    addedAt = addedAt
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val year: Int?,
    val genre: String?,
    val songCount: Int,
    val description: String?
) {
    fun toAlbum(): Album = Album(
        id = id,
        title = title,
        artist = artist,
        explicitArtworkUrl = artworkUrl,
        year = year,
        genre = genre,
        songCount = songCount,
        description = description
    )
}

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    year = year,
    genre = genre,
    songCount = songCount,
    description = description
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String?,
    val bio: String?,
    val monthlyListeners: Long,
    val songCount: Int
) {
    fun toArtist(): Artist = Artist(
        id = id,
        name = name,
        explicitAvatarUrl = avatarUrl,
        bio = bio,
        monthlyListeners = monthlyListeners,
        songCount = songCount
    )
}

fun Artist.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    avatarUrl = avatarUrl,
    bio = bio,
    monthlyListeners = monthlyListeners,
    songCount = songCount
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val songCount: Int = 0,
    val createdAt: String? = null,
    val isCustom: Boolean = true,
    val userId: String? = null,
    val ownerName: String? = null,
    val isOwner: Boolean = true,
    val canEdit: Boolean = true,
    val permission: String = "owner",
    val isPublic: Boolean = false
) {
    fun toPlaylist(): Playlist = Playlist(
        id = id,
        name = name,
        description = description,
        rawCoverUrl = coverUrl,
        songCount = songCount,
        createdAt = createdAt,
        isCustom = isCustom,
        userId = userId,
        ownerName = ownerName,
        isOwner = isOwner,
        canEdit = canEdit,
        permission = permission,
        isPublic = isPublic
    )
}

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    coverUrl = coverUrl,
    songCount = songCount,
    createdAt = createdAt,
    isCustom = isCustom,
    userId = userId,
    ownerName = ownerName,
    isOwner = isOwner,
    canEdit = canEdit,
    permission = permission,
    isPublic = isPublic
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val orderIndex: Int
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis()
)
