package com.example.data.repository

import com.example.data.api.YimlyApiService
import com.example.data.datastore.PreferencesManager
import com.example.data.db.MusicDao
import com.example.data.db.PlayHistoryEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistSongCrossRef
import com.example.data.db.toEntity
import com.example.data.models.AddPlaylistSongRequest
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Collaborator
import com.example.data.models.CreatePlaylistRequest
import com.example.data.models.LyricsData
import com.example.data.models.Playlist
import com.example.data.models.SearchResult
import com.example.data.models.SharePlaylistRequest
import com.example.data.models.Song
import com.example.data.models.UpdatePlaylistRequest
import com.example.lyrics.LyricsParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.HttpException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ResolvedTrack(
    val song: Song,
    val normalUrl: String,
    val instrumentalUrl: String?,
    val playableUrl: String,
    val hasInstrumental: Boolean,
    val isInstrumentalActive: Boolean
)

class MusicRepository(
    private val musicDao: MusicDao,
    private val apiService: YimlyApiService,
    private val coroutineScope: CoroutineScope
) {

    init {
        coroutineScope.launch(Dispatchers.IO) {
            syncWithBackend()
        }
    }

    val rawAllSongs: Flow<List<Song>> = musicDao.getAllSongs().map { list -> list.map { it.toSong() } }

    val allSongs: Flow<List<Song>> = rawAllSongs.map { list -> list.filter { !it.isInstrumentalTrack } }

    val recentlyAddedSongs: Flow<List<Song>> = musicDao.getRecentlyAddedSongs().map { list -> list.map { it.toSong() }.filter { !it.isInstrumentalTrack } }

    val favoriteSongs: Flow<List<Song>> = musicDao.getFavoriteSongs().map { list -> list.map { it.toSong() }.filter { !it.isInstrumentalTrack } }

    val recentlyPlayedSongs: Flow<List<Song>> = musicDao.getRecentlyPlayedSongs().map { list -> list.map { it.toSong() }.filter { !it.isInstrumentalTrack } }

    val allAlbums: Flow<List<Album>> = combine(
        musicDao.getAllAlbums().map { list -> list.map { it.toAlbum() } },
        allSongs
    ) { dbAlbums, songs ->
        if (dbAlbums.isNotEmpty()) {
            // Merge DB albums with derived albums from songs to guarantee complete library coverage
            mergeAlbums(dbAlbums, deriveAlbumsFromSongs(songs))
        } else {
            deriveAlbumsFromSongs(songs)
        }
    }

    val allArtists: Flow<List<Artist>> = combine(
        musicDao.getAllArtists().map { list -> list.map { it.toArtist() } },
        allSongs
    ) { dbArtists, songs ->
        if (dbArtists.isNotEmpty()) {
            // Merge DB artists with derived artists from songs to guarantee complete library coverage
            mergeArtists(dbArtists, deriveArtistsFromSongs(songs))
        } else {
            deriveArtistsFromSongs(songs)
        }
    }

    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists().map { list -> list.map { it.toPlaylist() } }

    suspend fun getSongById(id: String): Song? = withContext(Dispatchers.IO) {
        try {
            val remoteSong = apiService.getSongById(id)
            musicDao.upsertSongs(listOf(remoteSong.toEntity()))
            remoteSong
        } catch (_: Exception) {
            musicDao.getSongById(id)?.toSong()
        }
    }

    fun getSongsByAlbum(albumTitle: String): Flow<List<Song>> {
        val cleanAlbum = albumTitle.trim()
        return allSongs.map { songs ->
            songs.filter { song ->
                val sAlbum = song.album.trim()
                if (sAlbum.isNotBlank()) {
                    sAlbum.equals(cleanAlbum, ignoreCase = true)
                } else {
                    song.title.trim().equals(cleanAlbum, ignoreCase = true) ||
                    "${song.title.trim()} (Single)".equals(cleanAlbum, ignoreCase = true) ||
                    "${song.artist.trim()} - Single".equals(cleanAlbum, ignoreCase = true) ||
                    cleanAlbum.equals("Singles", ignoreCase = true)
                }
            }
        }
    }

    fun getSongsByArtist(artistName: String): Flow<List<Song>> {
        val cleanArtist = artistName.trim().lowercase()
        return allSongs.map { songs ->
            songs.filter { song ->
                val parsed = parseArtists(song.artist)
                if (parsed.isEmpty()) {
                    val songArtist = song.artist.trim().lowercase()
                    songArtist == cleanArtist ||
                    songArtist.contains(cleanArtist) ||
                    cleanArtist.contains(songArtist)
                } else {
                    parsed.any { it == cleanArtist || it.contains(cleanArtist) || cleanArtist.contains(it) }
                }
            }
        }
    }

    suspend fun findInstrumentalAudioUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val direct = song.directInstrumentalAudioUrl
        if (!direct.isNullOrBlank()) return@withContext direct

        if (song.hasInstrumental) {
            return@withContext "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/${song.id}/audio?type=instrumental"
        }

        val allSongsList = rawAllSongs.firstOrNull() ?: emptyList()
        if (!song.instrumentalSongId.isNullOrBlank()) {
            val instSong = allSongsList.find { it.id == song.instrumentalSongId }
            if (instSong != null) return@withContext instSong.audioUrl
        }

        val matchingInst = findMatchingInstrumental(song, allSongsList)
        if (matchingInst != null) {
            return@withContext matchingInst.audioUrl
        }

        "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/${song.id}/audio?type=instrumental"
    }

    suspend fun resolvePlayableTrack(song: Song, wantInstrumental: Boolean): ResolvedTrack = withContext(Dispatchers.IO) {
        val allSongsList = rawAllSongs.firstOrNull() ?: emptyList()

        val isDirectInst = song.isInstrumentalTrack
        val matchingNormal = if (isDirectInst) findMatchingNormalSong(song, allSongsList) else null
        val matchingInst = if (!isDirectInst) findMatchingInstrumental(song, allSongsList) else null

        val normalUrl = if (isDirectInst) {
            matchingNormal?.audioUrl ?: "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/${song.id}/audio?type=main"
        } else {
            song.audioUrl
        }

        val instUrl: String = if (isDirectInst) {
            song.audioUrl
        } else if (!song.directInstrumentalAudioUrl.isNullOrBlank()) {
            song.directInstrumentalAudioUrl!!
        } else if (matchingInst != null) {
            matchingInst.audioUrl
        } else if (!song.instrumentalSongId.isNullOrBlank()) {
            allSongsList.find { it.id == song.instrumentalSongId }?.audioUrl
                ?: "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/${song.id}/audio?type=instrumental"
        } else {
            "${PreferencesManager.DEFAULT_SERVER_URL}/api/songs/${song.id}/audio?type=instrumental"
        }

        val hasInst = song.hasInstrumental || matchingInst != null || isDirectInst || !song.directInstrumentalAudioUrl.isNullOrBlank() || !song.instrumentalSongId.isNullOrBlank() || true

        val playableUrl = if (wantInstrumental && hasInst) instUrl else normalUrl
        Log.d("MusicRepository", "resolvePlayableTrack: title='${song.title}', wantInst=$wantInstrumental, hasInst=$hasInst, playableUrl=$playableUrl")

        ResolvedTrack(
            song = song,
            normalUrl = normalUrl,
            instrumentalUrl = instUrl,
            playableUrl = playableUrl,
            hasInstrumental = hasInst,
            isInstrumentalActive = wantInstrumental && hasInst
        )
    }

    fun getSongsForPlaylist(playlistId: String): Flow<List<Song>> {
        return musicDao.getSongsForPlaylist(playlistId).map { list -> list.map { it.toSong() } }
    }

    suspend fun toggleFavorite(songId: String): Boolean = withContext(Dispatchers.IO) {
        val songEntity = musicDao.getSongById(songId)
        val currentFav = songEntity?.isFavorite ?: false
        val newFav = !currentFav

        // Call server API FIRST (Server is source of truth)
        if (newFav) {
            apiService.addFavorite(songId)
        } else {
            apiService.removeFavorite(songId)
        }

        // Only update local Room cache after successful server API call
        if (songEntity != null) {
            musicDao.updateFavorite(songId, newFav)
        } else {
            try {
                val remoteSong = apiService.getSongById(songId)
                musicDao.upsertSong(remoteSong.toEntity().copy(isFavorite = newFav))
            } catch (_: Exception) {
                musicDao.updateFavorite(songId, newFav)
            }
        }
        newFav
    }

    suspend fun recordPlayHistory(songId: String) = withContext(Dispatchers.IO) {
        musicDao.insertHistory(PlayHistoryEntity(songId = songId, playedAt = System.currentTimeMillis()))
    }

    suspend fun createPlaylist(name: String, description: String?, isPublic: Boolean = false): Playlist = withContext(Dispatchers.IO) {
        val request = CreatePlaylistRequest(name = name, description = description, isPublic = isPublic)
        val remotePlaylist = apiService.createPlaylist(request)
        val freshPlaylist = remotePlaylist.copy(createdAt = System.currentTimeMillis().toString())
        musicDao.insertPlaylist(freshPlaylist.toEntity())
        freshPlaylist
    }

    suspend fun updatePlaylist(playlistId: String, name: String, description: String?, isPublic: Boolean? = null): Playlist = withContext(Dispatchers.IO) {
        val request = UpdatePlaylistRequest(name = name, description = description, isPublic = isPublic)
        val updatedPlaylist = apiService.updatePlaylist(playlistId, request)
        val freshPlaylist = updatedPlaylist.copy(createdAt = System.currentTimeMillis().toString())
        musicDao.insertPlaylist(freshPlaylist.toEntity())
        freshPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        apiService.deletePlaylist(playlistId)
        musicDao.deletePlaylist(playlistId)
        musicDao.clearPlaylistSongs(playlistId)
    }

    suspend fun updatePlaylistArtwork(playlistId: String, localUri: String?) = withContext(Dispatchers.IO) {
        musicDao.updatePlaylistCoverUrl(playlistId, localUri)
        if (!localUri.isNullOrBlank()) {
            try {
                val localPl = musicDao.getPlaylistById(playlistId)
                val currentName = localPl?.name ?: "Playlist"
                val currentDesc = localPl?.description
                val currentIsPublic = localPl?.isPublic
                val req = UpdatePlaylistRequest(
                    name = currentName,
                    description = currentDesc,
                    isPublic = currentIsPublic,
                    coverUrl = localUri,
                    coverUrlSnake = localUri,
                    cover = localUri
                )
                val updatedRemote = apiService.updatePlaylist(playlistId, req)
                val freshRemote = updatedRemote.copy(createdAt = System.currentTimeMillis().toString())
                musicDao.insertPlaylist(freshRemote.toEntity())
            } catch (_: Exception) {
                if (localUri.startsWith("file://") || localUri.startsWith("/")) {
                    try {
                        val filePath = localUri.removePrefix("file://")
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            val requestFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), file)
                            val body = okhttp3.MultipartBody.Part.createFormData("cover", file.name, requestFile)
                            val uploaded = try {
                                apiService.uploadPlaylistCover(playlistId, body)
                            } catch (_: Exception) {
                                val artworkBody = okhttp3.MultipartBody.Part.createFormData("artwork", file.name, requestFile)
                                apiService.uploadPlaylistArtwork(playlistId, artworkBody)
                            }
                            val freshUploaded = uploaded.copy(createdAt = System.currentTimeMillis().toString())
                            musicDao.insertPlaylist(freshUploaded.toEntity())
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            // Even if localUri is blank (clearing cover), update timestamp
            try {
                val localPl = musicDao.getPlaylistById(playlistId)
                if (localPl != null) {
                    val req = UpdatePlaylistRequest(
                        name = localPl.name,
                        description = localPl.description,
                        isPublic = localPl.isPublic,
                        coverUrl = null
                    )
                    val updatedRemote = apiService.updatePlaylist(playlistId, req)
                    val freshRemote = updatedRemote.copy(createdAt = System.currentTimeMillis().toString())
                    musicDao.insertPlaylist(freshRemote.toEntity())
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        apiService.addSongToPlaylist(playlistId, AddPlaylistSongRequest(songId = songId))
        refreshPlaylistDetail(playlistId)
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        apiService.removeSongFromPlaylist(playlistId, songId)
        musicDao.removeSongFromPlaylist(playlistId, songId)
        refreshPlaylistDetail(playlistId)
    }

    suspend fun sharePlaylist(playlistId: String, username: String, permission: String) = withContext(Dispatchers.IO) {
        apiService.sharePlaylist(playlistId, SharePlaylistRequest(username = username, permission = permission))
    }

    suspend fun getCollaborators(playlistId: String): List<Collaborator> = withContext(Dispatchers.IO) {
        apiService.getCollaborators(playlistId)
    }

    suspend fun revokeShare(playlistId: String, userId: String) = withContext(Dispatchers.IO) {
        try {
            apiService.revokeShare(playlistId, userId)
        } catch (_: Exception) {
            apiService.removeCollaborator(playlistId, userId)
        }
    }

    suspend fun refreshPlaylistDetail(playlistId: String) = withContext(Dispatchers.IO) {
        try {
            val remotePlaylist = apiService.getPlaylistById(playlistId)
            val songs = if (remotePlaylist.songs.isNotEmpty()) remotePlaylist.songs else try { apiService.getPlaylistSongs(playlistId) } catch (_: Exception) { emptyList() }
            
            // The getPlaylistById response might not include songCount, so we derive it from the fetched songs array
            val localPl = musicDao.getPlaylistById(playlistId)
            val finalCover = remotePlaylist.coverUrl ?: localPl?.coverUrl
            val playlistWithCorrectCount = remotePlaylist.copy(songCount = songs.size, rawCoverUrl = finalCover)
            musicDao.insertPlaylist(playlistWithCorrectCount.toEntity())
            musicDao.syncPlaylistSongs(playlistId, songs.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            val apiRes = apiService.search(query)
            if (apiRes.songs.isNotEmpty() || apiRes.artists.isNotEmpty() || apiRes.albums.isNotEmpty()) {
                apiRes
            } else {
                fallbackLocalSearch(query)
            }
        } catch (_: Exception) {
            fallbackLocalSearch(query)
        }
    }

    private suspend fun fallbackLocalSearch(query: String): SearchResult {
        val allSongsList = allSongs.firstOrNull() ?: emptyList()
        val allArtistsList = allArtists.firstOrNull() ?: emptyList()
        val allAlbumsList = allAlbums.firstOrNull() ?: emptyList()
        val allPlaylistsList = allPlaylists.firstOrNull() ?: emptyList()

        return SearchResult(
            songs = allSongsList.filter { SearchUtils.matchesSong(it, query) },
            artists = allArtistsList.filter { SearchUtils.matchesArtist(it, query) },
            albums = allAlbumsList.filter { SearchUtils.matchesAlbum(it, query) },
            playlists = allPlaylistsList.filter { SearchUtils.matchesPlaylist(it, query) }
        )
    }

    suspend fun getLyricsForSong(song: Song): LyricsData = withContext(Dispatchers.IO) {
        try {
            val responseBody = apiService.getLyrics(song.id)
            val lrcText = responseBody.string()
            if (lrcText.isNotBlank()) {
                val parsed = LyricsParser.parse(lrcText, song.id, song.durationMs)
                if (parsed.lines.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        } catch (_: Exception) {
            // Fallback to local parsed lyrics
        }
        LyricsParser.parse(song.lyricsText ?: "", song.id, song.durationMs)
    }

    suspend fun getLyricsText(songId: String): String = withContext(Dispatchers.IO) {
        try {
            val responseBody = apiService.getLyrics(songId)
            return@withContext responseBody.string()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun updateLyrics(songId: String, lrcText: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = okhttp3.RequestBody.create("text/plain; charset=utf-8".toMediaType(), lrcText)
            try {
                apiService.updateLyrics(songId, body)
            } catch (e: HttpException) {
                if (e.code() == 404 || e.code() == 405) {
                    try {
                        apiService.createLyrics(songId, body)
                    } catch (e2: HttpException) {
                        if (e2.code() == 404 || e2.code() == 405) {
                            apiService.patchLyrics(songId, body)
                        } else {
                            throw e2
                        }
                    }
                } else {
                    throw e
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLrc(songId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiService.deleteLrc(songId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSong(songId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiService.deleteSong(songId)
            musicDao.deleteSongById(songId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLyricsOffset(songId: String): Long? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLyricsOffset(songId)
            return@withContext response.lyricOffset
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateLyricsOffset(songId: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        try {
            apiService.updateLyricsOffset(songId, com.example.data.models.UpdateLyricsOffsetRequest(offsetMs))
            val song = getSongById(songId)
            if (song != null) {
                musicDao.upsertSong(song.copy(lyricOffset = offsetMs).toEntity())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncWithBackend(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch songs
            val remoteSongs = try {
                apiService.getSongs()
            } catch (_: Exception) {
                emptyList()
            }

            if (remoteSongs.isNotEmpty()) {
                musicDao.upsertSongs(remoteSongs.map { it.toEntity() })
            }

            // 2. Fetch favorites from server (Single Source of Truth)
            try {
                val remoteFavorites = apiService.getFavorites()
                val favIds = remoteFavorites.map { it.id }
                if (remoteFavorites.isNotEmpty()) {
                    musicDao.upsertSongs(remoteFavorites.map { it.toEntity().copy(isFavorite = true) })
                }
                musicDao.replaceFavorites(favIds)
            } catch (_: Exception) {}

            // 3. Sync albums
            val remoteAlbums = try {
                apiService.getAlbums()
            } catch (_: Exception) {
                emptyList()
            }

            if (remoteAlbums.isNotEmpty()) {
                musicDao.insertAlbums(remoteAlbums.map { it.toEntity() })
            } else if (remoteSongs.isNotEmpty()) {
                val derived = deriveAlbumsFromSongs(remoteSongs)
                if (derived.isNotEmpty()) {
                    musicDao.insertAlbums(derived.map { it.toEntity() })
                }
            }

            // 4. Sync artists
            val remoteArtists = try {
                apiService.getArtists()
            } catch (_: Exception) {
                emptyList()
            }

            if (remoteArtists.isNotEmpty()) {
                musicDao.insertArtists(remoteArtists.map { it.toEntity() })
            } else if (remoteSongs.isNotEmpty()) {
                val derived = deriveArtistsFromSongs(remoteSongs)
                if (derived.isNotEmpty()) {
                    musicDao.insertArtists(derived.map { it.toEntity() })
                }
            }

            // 5. Sync playlists from server (Single Source of Truth)
            val remotePlaylists = try {
                apiService.getPlaylists()
            } catch (_: Exception) {
                emptyList()
            }

            if (remotePlaylists.isNotEmpty()) {
                val validIds = remotePlaylists.map { it.id }
                musicDao.deleteStalePlaylists(validIds)
                for (pl in remotePlaylists) {
                    val localPl = musicDao.getPlaylistById(pl.id)
                    val finalCover = pl.coverUrl ?: localPl?.coverUrl
                    val plToInsert = pl.copy(rawCoverUrl = finalCover)
                    musicDao.insertPlaylist(plToInsert.toEntity())
                    if (pl.songs.isNotEmpty()) {
                        musicDao.syncPlaylistSongs(pl.id, pl.songs.map { it.toEntity() })
                    } else {
                        try {
                            val plSongs = apiService.getPlaylistSongs(pl.id)
                            musicDao.syncPlaylistSongs(pl.id, plSongs.map { it.toEntity() })
                        } catch (_: Exception) {}
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun parseArtists(rawArtist: String): Set<String> {
            if (rawArtist.isBlank()) return emptySet()
            return rawArtist.split(';')
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
        }

        fun cleanSongTitle(title: String): String {
            var cleaned = title
            // 1. Remove bracketed / parenthesized instrumental or karaoke tags
            cleaned = cleaned.replace(
                Regex("(?i)\\s*[\\[\\(](?:official\\s+)?(?:instrumental\\s+version|version\\s+instrumental|instrumental\\s+audio|instrumental\\s+track|instrumental|karaoke\\s+version|karaoke|backing\\s+track|inst\\.|inst)[\\]\\)]"),
                ""
            )
            // 2. Remove dash separated instrumental or karaoke tags
            cleaned = cleaned.replace(
                Regex("(?i)\\s*-\\s*(?:official\\s+)?(?:instrumental\\s+version|version\\s+instrumental|instrumental\\s+audio|instrumental\\s+track|instrumental|karaoke\\s+version|karaoke|backing\\s+track|inst\\.|inst)"),
                ""
            )
            // 3. Remove trailing instrumental tags
            cleaned = cleaned.replace(
                Regex("(?i)\\s+(?:instrumental\\s+version|version\\s+instrumental|instrumental\\s+audio|instrumental\\s+track|instrumental|karaoke\\s+version|karaoke|backing\\s+track|inst\\.|inst)$"),
                ""
            )
            return cleaned.replace(Regex("\\s+"), " ").trim().lowercase()
        }

        fun cleanAlbumTitle(album: String): String {
            if (album.isBlank()) return ""
            var cleaned = cleanSongTitle(album)
            cleaned = cleaned
                .replace(Regex("(?i)\\s*[\\[\\(]single[\\]\\)]"), "")
                .replace(Regex("(?i)\\s*-\\s*single"), "")
                .replace(Regex("(?i)\\s+single$"), "")
                .trim()
            return cleaned
        }

        fun isArtistsMatching(mainArtists: Set<String>, candArtists: Set<String>): Boolean {
            if (mainArtists.isEmpty() || candArtists.isEmpty()) return true
            if (mainArtists == candArtists) return true
            return mainArtists.intersect(candArtists).isNotEmpty()
        }

        fun isAlbumsMatching(mainAlbum: String, candAlbum: String): Boolean {
            val cleanMain = cleanAlbumTitle(mainAlbum)
            val cleanCand = cleanAlbumTitle(candAlbum)
            if (cleanMain.isBlank() || cleanCand.isBlank()) return true
            if (cleanMain == "singles" || cleanCand == "singles") return true
            return cleanMain == cleanCand
        }

        fun findMatchingInstrumental(mainSong: Song, allSongsList: List<Song>): Song? {
            if (!mainSong.instrumentalSongId.isNullOrBlank()) {
                val found = allSongsList.find { it.id == mainSong.instrumentalSongId }
                if (found != null) return found
            }

            val cleanMainTitle = cleanSongTitle(mainSong.title)
            if (cleanMainTitle.isBlank()) return null
            val mainArtists = parseArtists(mainSong.artist)

            return allSongsList.firstOrNull { candidate ->
                if (candidate.id == mainSong.id) return@firstOrNull false
                if (!candidate.isInstrumentalTrack) return@firstOrNull false

                val cleanCandTitle = cleanSongTitle(candidate.title)
                if (cleanCandTitle != cleanMainTitle) return@firstOrNull false

                val candArtists = parseArtists(candidate.artist)
                val artistsMatch = isArtistsMatching(mainArtists, candArtists)
                if (!artistsMatch) return@firstOrNull false

                val albumsMatch = isAlbumsMatching(mainSong.album, candidate.album)
                albumsMatch
            }
        }

        fun findMatchingNormalSong(instrumentalSong: Song, allSongsList: List<Song>): Song? {
            val cleanInstTitle = cleanSongTitle(instrumentalSong.title)
            if (cleanInstTitle.isBlank()) return null
            val instArtists = parseArtists(instrumentalSong.artist)

            return allSongsList.firstOrNull { candidate ->
                if (candidate.id == instrumentalSong.id) return@firstOrNull false
                if (candidate.isInstrumentalTrack) return@firstOrNull false

                val cleanCandTitle = cleanSongTitle(candidate.title)
                if (cleanCandTitle != cleanInstTitle) return@firstOrNull false

                val candArtists = parseArtists(candidate.artist)
                val artistsMatch = isArtistsMatching(instArtists, candArtists)
                if (!artistsMatch) return@firstOrNull false

                val albumsMatch = isAlbumsMatching(instrumentalSong.album, candidate.album)
                albumsMatch
            }
        }

        fun deriveArtistsFromSongs(songs: List<Song>): List<Artist> {
            if (songs.isEmpty()) return emptyList()

            val artistMap = LinkedHashMap<String, MutableList<Song>>()
            val canonicalNames = mutableMapOf<String, String>()

            for (song in songs) {
                if (song.isInstrumentalTrack) continue
                val rawArtist = song.artist.trim()
                if (rawArtist.isNotBlank()) {
                    val individualArtists = rawArtist.split(';')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    if (individualArtists.isEmpty()) {
                        val key = rawArtist.lowercase()
                        artistMap.getOrPut(key) { mutableListOf() }.add(song)
                        canonicalNames.putIfAbsent(key, rawArtist)
                    } else {
                        for (individual in individualArtists) {
                            val key = individual.lowercase()
                            artistMap.getOrPut(key) { mutableListOf() }.add(song)
                            canonicalNames.putIfAbsent(key, individual)
                        }
                    }
                }
            }

            return artistMap.map { (key, artistSongs) ->
                val canonicalName = canonicalNames[key] ?: key
                val artwork = artistSongs.firstNotNullOfOrNull { s ->
                    s.explicitArtworkUrl?.takeIf { it.isNotBlank() } ?: s.artworkUrl?.takeIf { it.isNotBlank() }
                }
                val genres = artistSongs.mapNotNull { it.genre?.trim() }.filter { it.isNotBlank() }.distinct()
                val artistId = "art_" + Math.abs(key.hashCode()).toString(16)

                Artist(
                    id = artistId,
                    name = canonicalName,
                    explicitAvatarUrl = artwork,
                    songCount = artistSongs.size,
                    genres = genres
                )
            }.sortedBy { it.name.lowercase() }
        }

        fun deriveAlbumsFromSongs(songs: List<Song>): List<Album> {
            if (songs.isEmpty()) return emptyList()

            val albumMap = LinkedHashMap<String, MutableList<Song>>()
            for (song in songs) {
                if (song.isInstrumentalTrack) continue
                val rawAlbum = song.album.trim()
                val albumKey = if (rawAlbum.isNotBlank()) {
                    rawAlbum.lowercase()
                } else {
                    "${song.title.trim().lowercase()}_single"
                }
                albumMap.getOrPut(albumKey) { mutableListOf() }.add(song)
            }

            return albumMap.map { (_, albumSongs) ->
                val firstSong = albumSongs.first()
                val hasAlbumName = firstSong.album.trim().isNotBlank()
                val canonicalTitle = if (hasAlbumName) {
                    albumSongs.firstNotNullOfOrNull { it.album.takeIf { a -> a.isNotBlank() } }?.trim() ?: firstSong.title
                } else {
                    "${firstSong.title.trim()} (Single)"
                }

                val distinctArtists = albumSongs.mapNotNull { it.artist.takeIf { a -> a.isNotBlank() }?.trim() }.distinct()
                val canonicalArtist = when {
                    distinctArtists.isEmpty() -> "Unknown Artist"
                    distinctArtists.size == 1 -> distinctArtists.first()
                    distinctArtists.size > 2 -> "Various Artists"
                    else -> distinctArtists.joinToString(", ")
                }

                val artwork = albumSongs.firstNotNullOfOrNull { s ->
                    s.explicitArtworkUrl?.takeIf { it.isNotBlank() } ?: s.artworkUrl?.takeIf { it.isNotBlank() }
                }

                val year = albumSongs.firstNotNullOfOrNull { it.year }
                val genre = albumSongs.firstNotNullOfOrNull { it.genre?.takeIf { g -> g.isNotBlank() } }
                val albumId = "alb_" + Math.abs((canonicalTitle.lowercase() + "_" + canonicalArtist.lowercase()).hashCode()).toString(16)

                Album(
                    id = albumId,
                    title = canonicalTitle,
                    artist = canonicalArtist,
                    explicitArtworkUrl = artwork,
                    year = year,
                    genre = genre,
                    songCount = albumSongs.size,
                    description = "${albumSongs.size} tracks"
                )
            }.sortedBy { it.title.lowercase() }
        }

        private fun mergeAlbums(primary: List<Album>, fallback: List<Album>): List<Album> {
            val map = LinkedHashMap<String, Album>()
            for (album in primary) {
                map[album.title.trim().lowercase()] = album
            }
            for (album in fallback) {
                val key = album.title.trim().lowercase()
                if (!map.containsKey(key)) {
                    map[key] = album
                }
            }
            return map.values.toList().sortedBy { it.title.lowercase() }
        }

        private fun mergeArtists(primary: List<Artist>, fallback: List<Artist>): List<Artist> {
            val map = LinkedHashMap<String, Artist>()
            for (artist in primary) {
                map[artist.name.trim().lowercase()] = artist
            }
            for (artist in fallback) {
                val key = artist.name.trim().lowercase()
                if (!map.containsKey(key)) {
                    map[key] = artist
                }
            }
            return map.values.toList().sortedBy { it.name.lowercase() }
        }
    }
}
