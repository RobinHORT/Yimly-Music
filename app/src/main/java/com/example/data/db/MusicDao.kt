package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // Songs
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsList(): List<SongEntity>

    @Query("SELECT * FROM songs ORDER BY addedAt DESC LIMIT 20")
    fun getRecentlyAddedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE LOWER(TRIM(album)) = LOWER(TRIM(:albumTitle)) OR (album = '' AND (LOWER(TRIM(title)) = LOWER(TRIM(:albumTitle)) OR LOWER(TRIM(title)) || ' (single)' = LOWER(TRIM(:albumTitle)))) ORDER BY title ASC")
    fun getSongsByAlbum(albumTitle: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE LOWER(artist) LIKE '%' || LOWER(TRIM(:artistName)) || '%' ORDER BY title ASC")
    fun getSongsByArtist(artistName: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' OR LOWER(album) LIKE '%' || LOWER(:query) || '%' ORDER BY title ASC")
    suspend fun searchSongs(query: String): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Transaction
    suspend fun upsertSongs(songs: List<SongEntity>) {
        val distinctSongs = songs.distinctBy { it.id.trim() }
        for (song in distinctSongs) {
            val cleanId = song.id.trim()
            val existing = getSongById(cleanId)
            val finalSong = if (existing != null) {
                song.copy(
                    id = cleanId,
                    isFavorite = existing.isFavorite || song.isFavorite,
                    addedAt = existing.addedAt,
                    lyricOffset = song.lyricOffset ?: existing.lyricOffset
                )
            } else {
                song.copy(id = cleanId)
            }
            insertSong(finalSong)
        }
    }

    @Transaction
    suspend fun upsertSong(song: SongEntity) {
        val cleanId = song.id.trim()
        val existing = getSongById(cleanId)
        val finalSong = if (existing != null) {
            song.copy(
                id = cleanId,
                isFavorite = existing.isFavorite || song.isFavorite,
                addedAt = existing.addedAt,
                lyricOffset = song.lyricOffset ?: existing.lyricOffset
            )
        } else {
            song.copy(id = cleanId)
        }
        insertSong(finalSong)
    }

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavorite(songId: String, isFavorite: Boolean)

    @Query("UPDATE songs SET isFavorite = 0")
    suspend fun clearAllFavorites()

    @Query("UPDATE songs SET isFavorite = 1 WHERE id IN (:favoriteIds)")
    suspend fun setFavorites(favoriteIds: List<String>)

    @Transaction
    suspend fun replaceFavorites(favoriteIds: List<String>) {
        clearAllFavorites()
        if (favoriteIds.isNotEmpty()) {
            setFavorites(favoriteIds)
        }
    }

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: String)

    @Query("DELETE FROM songs WHERE id NOT IN (:validIds)")
    suspend fun deleteStaleSongs(validIds: List<String>)

    // Albums
    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id NOT IN (:validIds)")
    suspend fun deleteStaleAlbums(validIds: List<String>)

    // Artists
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id NOT IN (:validIds)")
    suspend fun deleteStaleArtists(validIds: List<String>)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET coverUrl = :coverUrl WHERE id = :playlistId")
    suspend fun updatePlaylistCoverUrl(playlistId: String, coverUrl: String?)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlists WHERE id NOT IN (:validIds)")
    suspend fun deleteStalePlaylists(validIds: List<String>)

    // Playlist Songs
    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId 
        WHERE playlist_songs.playlistId = :playlistId 
        ORDER BY playlist_songs.orderIndex ASC
    """)
    fun getSongsForPlaylist(playlistId: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Transaction
    suspend fun syncPlaylistSongs(playlistId: String, songs: List<SongEntity>) {
        clearPlaylistSongs(playlistId)
        upsertSongs(songs)
        songs.forEachIndexed { index, song ->
            insertPlaylistSongCrossRef(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = song.id,
                    orderIndex = index
                )
            )
        }
    }

    // History
    @Insert
    suspend fun insertHistory(history: PlayHistoryEntity)

    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN play_history ON songs.id = play_history.songId 
        GROUP BY songs.id
        ORDER BY MAX(play_history.playedAt) DESC 
        LIMIT 20
    """)
    fun getRecentlyPlayedSongs(): Flow<List<SongEntity>>

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    @Query("DELETE FROM playlist_songs WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun cleanOrphanPlaylistSongs()

    @Query("DELETE FROM play_history WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun cleanOrphanPlayHistory()

    // Lyric Resources (LRC & eLRC independent caches)
    @Query("SELECT * FROM lyric_resources WHERE songId = :songId AND format = :format LIMIT 1")
    suspend fun getLyricResource(songId: String, format: String): LyricResourceEntity?

    @Query("SELECT * FROM lyric_resources WHERE songId = :songId")
    suspend fun getAllLyricResourcesForSong(songId: String): List<LyricResourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyricResource(resource: LyricResourceEntity)

    @Query("DELETE FROM lyric_resources WHERE songId = :songId AND format = :format")
    suspend fun deleteLyricResource(songId: String, format: String)

    @Query("DELETE FROM lyric_resources WHERE songId = :songId")
    suspend fun deleteAllLyricResourcesForSong(songId: String)
}
