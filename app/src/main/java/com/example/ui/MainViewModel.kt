package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.AuthState
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.data.models.Playlist
import com.example.data.models.SearchResult
import com.example.data.models.Song
import com.example.data.models.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.MusicRepository
import com.example.playback.PlaybackInfo
import com.example.playback.PlaybackManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // Auth & Profile
    val authState: StateFlow<AuthState> = authRepository.authStateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Unauthenticated())

    val userProfile: StateFlow<UserProfile?> = authState.map {
        if (it is AuthState.Authenticated) it.user else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isAdmin: StateFlow<Boolean> = userProfile.map { profile ->
        profile?.isAdmin == true || profile?.role == "admin"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    // Server URL & Preferences
    val serverUrl: StateFlow<String> = preferencesManager.serverUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_SERVER_URL)

    val lyricsConfig: StateFlow<LyricsDisplayConfig> = preferencesManager.lyricsConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, LyricsDisplayConfig())

    // Music Catalog Flows
    val allSongs: StateFlow<List<Song>> = musicRepository.allSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyAddedSongs: StateFlow<List<Song>> = musicRepository.recentlyAddedSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = musicRepository.recentlyPlayedSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteSongs: StateFlow<List<Song>> = musicRepository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allAlbums: StateFlow<List<Album>> = musicRepository.allAlbums
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allArtists: StateFlow<List<Artist>> = musicRepository.allArtists
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = musicRepository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Playback
    val playbackInfo: StateFlow<PlaybackInfo> = playbackManager.playbackInfo
    val playbackPositionMs: StateFlow<Long> = playbackManager.playbackPositionMs
    val lyricsPositionMs: StateFlow<Long> = playbackManager.lyricsPositionMs

    fun setLyricsActive(active: Boolean) {
        playbackManager.setLyricsActive(active)
    }

    // Lyrics State
    private val _currentLyricsData = MutableStateFlow<LyricsData?>(null)
    val currentLyricsData: StateFlow<LyricsData?> = _currentLyricsData.asStateFlow()

    private val _currentSongOffset = MutableStateFlow(0L)
    val currentSongOffset: StateFlow<Long> = _currentSongOffset.asStateFlow()

    // Search
    val searchQuery = MutableStateFlow("")
    val searchResult: StateFlow<SearchResult> = searchQuery
        .debounce(200)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(SearchResult())
            } else {
                musicRepository.searchFlow(q)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, SearchResult())

    // Downloader State
    private val downloadingTrackKeys = mutableSetOf<String>()
    private val completedTrackSongIdMap = mutableMapOf<String, String>()
    private val pendingPlaylistRequests = mutableMapOf<String, MutableList<String>>() // Key: track, Value: PlaylistIDs
    
    private val _completedTrackSongIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val completedTrackSongIds: StateFlow<Map<String, String>> = _completedTrackSongIds.asStateFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    private val _downloadStatusMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, String>> = _downloadStatusMap.asStateFlow()

    internal var downloaderPollDelayMs: Long = 1500L
    internal var downloaderIndexingDelayMs: Long = 1500L

    fun getCompletedSongId(track: com.example.data.models.ExternalTrack): String? {
        val key = "${track.title}|${track.artist}".lowercase()
        val id = completedTrackSongIdMap[key]
        if (id != null) return id
        
        // Fallback to DB
        val existing = allSongs.value.firstOrNull {
            it.title.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true)
        }
        return existing?.id
    }

    fun downloadExternalTrack(track: com.example.data.models.ExternalTrack, onComplete: ((String?) -> Unit)? = null) {
        val key = "${track.title}|${track.artist}".lowercase()

        viewModelScope.launch {
            // 1. Database Check for existing song
            val existing = musicRepository.getAllSongsSnapshot().firstOrNull {
                it.title.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true)
            }
            if (existing != null) {
                completedTrackSongIdMap[key] = existing.id
                _completedTrackSongIds.value = _completedTrackSongIds.value + (key to existing.id)
                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Completed")
                _downloadProgressMap.value = _downloadProgressMap.value + (key to 100)
                onComplete?.invoke(existing.id)
                return@launch
            }

            // 2. Prevent concurrent duplicate downloads for the same track
            synchronized(downloadingTrackKeys) {
                if (key in downloadingTrackKeys) return@launch
                downloadingTrackKeys.add(key)
            }

            try {
                // Snapshot existing song IDs before download
                val preExistingIds = musicRepository.getAllSongsSnapshot().map { it.id }.toSet()

                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Resolving library...")

                // Step 1: Query downloader settings.
                val libraryId: String = try {
                    val settings = musicRepository.getDownloaderSettings()
                    val id = settings.libraryId
                    if (id.isNullOrBlank()) {
                        throw Exception("No library configured")
                    }
                    id
                } catch (e: Exception) {
                    val errorMsg = if (e is retrofit2.HttpException && (e.code() == 403 || e.code() == 401)) {
                        "Access denied: Administrator privileges required."
                    } else if (e.message == "No library configured") {
                        "Failed: No library configured"
                    } else {
                        "Failed: ${e.message ?: "Unknown error"}"
                    }
                    _downloadStatusMap.value = _downloadStatusMap.value + (key to errorMsg)
                    _uiMessage.value = errorMsg
                    onComplete?.invoke(null)
                    return@launch
                }

                // Step 2: Start download
                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Starting download...")
                val jobId = try {
                    musicRepository.startDownload(libraryId, listOf(track)).jobId
                } catch (e: Exception) {
                    _downloadStatusMap.value = _downloadStatusMap.value + (key to "Failed: Download rejected")
                    _uiMessage.value = "Failed to start download: ${e.message}"
                    onComplete?.invoke(null)
                    return@launch
                }

                // Step 3: Poll job
                var isJobFinished = false
                var isDownloadSuccessful = false
                var failureReason: String? = null
                var pollCount = 0
                val maxPolls = 120
                var hasAttemptedRetry = false

                while (!isJobFinished && pollCount < maxPolls) {
                    kotlinx.coroutines.delay(downloaderPollDelayMs)
                    pollCount++
                    try {
                        val job = musicRepository.getDownloadJob(jobId)
                        val overallStatus = job.status.lowercase()
                        val jobTrack = job.tracks.firstOrNull { it.title.equals(track.title, true) && it.artist.equals(track.artist, true) }
                        val trackStatus = jobTrack?.status?.lowercase()
                        val prog = jobTrack?.progress ?: if (overallStatus == "completed") 100 else 0

                        _downloadProgressMap.value = _downloadProgressMap.value + (key to prog)
                        val statusStr = "${trackStatus ?: overallStatus} ($prog%)"
                        println("DownloaderDebug: Key=$key, Status=$statusStr, Overall=$overallStatus, Track=$trackStatus")
                        
                        if (overallStatus == "completed" || trackStatus == "completed") {
                            if (trackStatus == "failed") {
                                isJobFinished = true
                                isDownloadSuccessful = false
                                failureReason = "Failed: Track failed"
                                println("DownloaderDebug: Job failed: track failed for $key")
                            } else {
                                isJobFinished = true
                                isDownloadSuccessful = true
                                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Completed")
                                println("DownloaderDebug: Job completed successfully for $key")
                            }
                        } else if (overallStatus == "failed" || trackStatus == "failed") {
                            if (!hasAttemptedRetry) {
                                hasAttemptedRetry = true
                                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Retrying")
                                musicRepository.retryDownloadJob(jobId)
                                println("DownloaderDebug: Job failed, retrying $key")
                            } else {
                                isJobFinished = true
                                isDownloadSuccessful = false
                                failureReason = "Failed: Job failed"
                                println("DownloaderDebug: Job failed, retry failed for $key")
                            }
                        } else if (overallStatus == "cancelled" || trackStatus == "cancelled") {
                            isJobFinished = true
                            isDownloadSuccessful = false
                            failureReason = "Cancelled"
                            println("DownloaderDebug: Job cancelled for $key")
                        } else if (overallStatus !in listOf("queued", "downloading", "running", "searching", "tagging") &&
                            trackStatus !in listOf("queued", "downloading", "running", "searching", "tagging", null)) {
                             isJobFinished = true
                             isDownloadSuccessful = false
                             failureReason = "Failed: Unexpected status: $overallStatus"
                             _uiMessage.value = failureReason
                             println("DownloaderDebug: Job failed: $failureReason for $key")
                        } else {
                             _downloadStatusMap.value = _downloadStatusMap.value + (key to "Downloading")
                             println("DownloaderDebug: Job downloading $key")
                        }
                    } catch (_: Exception) {}
                }

                if (isDownloadSuccessful) {
                    syncAndIndexTrack(track, preExistingIds, onComplete)
                } else {
                    _downloadStatusMap.value = _downloadStatusMap.value + (key to (failureReason ?: "Failed"))
                    onComplete?.invoke(null)
                }

            } finally {
                synchronized(downloadingTrackKeys) {
                    downloadingTrackKeys.remove(key)
                }
            }
        }
    }

    fun registerPendingPlaylistAdd(track: com.example.data.models.ExternalTrack, playlistId: String) {
        val key = "${track.title}|${track.artist}".lowercase()
        
        // Check if already downloaded
        val existing = getCompletedSongId(track)
        if (existing != null) {
            viewModelScope.launch {
                try {
                    musicRepository.addSongToPlaylist(playlistId, existing)
                    _uiMessage.value = "Added to playlist"
                } catch (e: Exception) {
                    _uiMessage.value = "Failed to add song to playlist: ${e.message}"
                }
            }
            return
        }

        synchronized(pendingPlaylistRequests) {
            val list = pendingPlaylistRequests.getOrPut(key) { mutableListOf() }
            if (playlistId !in list) list.add(playlistId)
        }
        downloadExternalTrack(track)
    }

    private suspend fun syncAndIndexTrack(
        track: com.example.data.models.ExternalTrack,
        preExistingIds: Set<String>,
        onComplete: ((String?) -> Unit)?
    ) {
        val key = "${track.title}|${track.artist}".lowercase()
        _downloadStatusMap.value = _downloadStatusMap.value + (key to "Indexing...")

        var newlyIndexedId: String? = null
        for (syncAttempt in 1..8) {
            kotlinx.coroutines.delay(downloaderIndexingDelayMs)
            try {
                musicRepository.syncWithBackend()
            } catch (_: Exception) {}

            val currentSongs = musicRepository.getAllSongsSnapshot()
            val newSong = currentSongs.firstOrNull { s ->
                s.title.equals(track.title, ignoreCase = true) &&
                    s.artist.equals(track.artist, ignoreCase = true)
            }

            if (newSong != null) {
                newlyIndexedId = newSong.id
                break
            }
        }

        if (newlyIndexedId != null) {
            completedTrackSongIdMap[key] = newlyIndexedId
            _completedTrackSongIds.value = _completedTrackSongIds.value + (key to newlyIndexedId)
            _downloadStatusMap.value = _downloadStatusMap.value + (key to "Completed")
            
            // Process pending playlist requests
            val pending = synchronized(pendingPlaylistRequests) {
                pendingPlaylistRequests.remove(key) ?: emptyList()
            }
            for (playlistId in pending) {
                try {
                    musicRepository.addSongToPlaylist(playlistId, newlyIndexedId)
                } catch (e: Exception) {
                    _uiMessage.value = "Failed to add song to playlist: ${e.message}"
                }
            }

            onComplete?.invoke(newlyIndexedId)
        } else {
            _downloadStatusMap.value = _downloadStatusMap.value + (key to "Downloaded (Indexing pending)")
            onComplete?.invoke(null)
        }
    }

    fun retryLibraryIndexing(track: com.example.data.models.ExternalTrack, onComplete: ((String?) -> Unit)? = null) {
        val key = "${track.title}|${track.artist}".lowercase()
        synchronized(downloadingTrackKeys) {
            if (key in downloadingTrackKeys) return
            downloadingTrackKeys.add(key)
        }
        viewModelScope.launch {
            try {
                syncAndIndexTrack(track, preExistingIds = emptySet(), onComplete = onComplete)
            } finally {
                synchronized(downloadingTrackKeys) {
                    downloadingTrackKeys.remove(key)
                }
            }
        }
    }



    private val songLrcOffsets = mutableMapOf<String, Long>()
    private val songElrcOffsets = mutableMapOf<String, Long>()

    init {
        // Observe current playing song and formatMode to load appropriate lyrics resource
        viewModelScope.launch {
            var lyricsJob: kotlinx.coroutines.Job? = null
            combine(
                playbackInfo.map { it.currentSong }.distinctUntilChanged(),
                lyricsConfig.map { it.formatMode }.distinctUntilChanged()
            ) { song, mode ->
                Pair(song, mode)
            }.collect { (song, mode) ->
                lyricsJob?.cancel()
                _currentLyricsData.value = null // Clear lyrics immediately to prevent stale state from previous track
                if (song != null) {
                    lyricsJob = launch {
                        _currentLyricsData.value = musicRepository.getLyricsForSong(song, mode)
                        if (mode == LyricsFormatMode.ELRC) {
                            _currentSongOffset.value = songElrcOffsets[song.id] ?: 0L
                        } else {
                            val offset = musicRepository.getLyricsOffset(song.id)
                            _currentSongOffset.value = songLrcOffsets[song.id] ?: (offset ?: song.lyricOffset ?: 0L)
                        }
                    }
                } else {
                    _currentLyricsData.value = null
                }
            }
        }
        
        // Observe auth state for automatic sync
        viewModelScope.launch {
            authState
                .map { if (it is AuthState.Authenticated) it.user.id else null }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        syncLibrary()
                    }
                }
        }
    }

    // Auth Actions
    fun login(username: String, password: String, rememberMe: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            val result = authRepository.login(username, password, rememberMe)
            _isLoading.value = false
            result.onFailure {
                _authError.value = it.message ?: "Authentication failed. Check your credentials."
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            musicRepository.clearLocalCache()
        }
    }

    // Playback Actions
    fun playSong(song: Song, playlist: List<Song> = listOf(song), startIndex: Int = -1) {
        playbackManager.playSong(song, playlist, startIndex)
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun next() {
        playbackManager.next()
    }

    fun previous() {
        playbackManager.previous()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackManager.cycleRepeatMode()
    }

    private val togglingSongIds = mutableSetOf<String>()

    fun toggleFavorite(songId: String) {
        synchronized(togglingSongIds) {
            if (songId in togglingSongIds) return
            togglingSongIds.add(songId)
        }
        viewModelScope.launch {
            try {
                val newFav = musicRepository.toggleFavorite(songId)
                playbackManager.updateFavoriteStatus(songId, newFav)
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to update favorite on server"
            } finally {
                synchronized(togglingSongIds) {
                    togglingSongIds.remove(songId)
                }
            }
        }
    }

    fun toggleInstrumental() {
        android.util.Log.d("INSTRUMENTAL_DEBUG", "MainViewModel.toggleInstrumental() called")
        playbackManager.toggleInstrumental()
    }

    fun addToQueue(song: Song) {
        playbackManager.addToQueue(song)
    }

    fun playNext(song: Song) {
        playbackManager.playNext(song)
    }

    fun removeFromQueue(index: Int) {
        playbackManager.removeFromQueue(index)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        playbackManager.reorderQueue(fromIndex, toIndex)
    }

    fun clearQueue() {
        playbackManager.clearUpcomingQueue()
    }

    fun playQueueItem(index: Int) {
        playbackManager.playQueueItem(index)
    }

    // Playlist Actions
    fun createPlaylist(name: String, description: String?, isPublic: Boolean = false, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                musicRepository.createPlaylist(name, description, isPublic)
                _uiMessage.value = "Playlist '$name' created"
                onComplete?.invoke()
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to create playlist"
            }
        }
    }

    fun updatePlaylist(playlistId: String, name: String, description: String?, isPublic: Boolean? = null, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                musicRepository.updatePlaylist(playlistId, name, description, isPublic)
                _uiMessage.value = "Playlist updated"
                onComplete?.invoke()
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to update playlist"
            }
        }
    }

    fun updateArtwork(playlistId: String, uri: String) {
        viewModelScope.launch {
            try {
                musicRepository.updatePlaylistArtwork(playlistId, uri)
                musicRepository.refreshPlaylistDetail(playlistId, force = true)
                _uiMessage.value = "Artwork updated"
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to update artwork"
            }
        }
    }

    fun deletePlaylist(playlistId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                musicRepository.deletePlaylist(playlistId)
                _uiMessage.value = "Playlist deleted"
                onComplete?.invoke()
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to delete playlist"
            }
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            try {
                musicRepository.addSongToPlaylist(playlistId, songId)
                _uiMessage.value = "Added to playlist"
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to add song to playlist"
            }
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: String, description: String? = null, isPublic: Boolean = false) {
        viewModelScope.launch {
            try {
                val created = musicRepository.createPlaylist(name, description, isPublic)
                musicRepository.addSongToPlaylist(created.id, songId)
                _uiMessage.value = "Added to '$name'"
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to create playlist and add song"
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            try {
                musicRepository.removeSongFromPlaylist(playlistId, songId)
                _uiMessage.value = "Removed from playlist"
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to remove song from playlist"
            }
        }
    }

    fun sharePlaylist(playlistId: String, username: String, permission: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                musicRepository.sharePlaylist(playlistId, username, permission)
                _uiMessage.value = "Shared playlist with $username"
                onComplete?.invoke()
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to share playlist"
            }
        }
    }

    fun getCollaborators(playlistId: String, onResult: (List<com.example.data.models.Collaborator>) -> Unit) {
        viewModelScope.launch {
            try {
                val collaborators = musicRepository.getCollaborators(playlistId)
                onResult(collaborators)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }

    fun revokeShare(playlistId: String, userId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                musicRepository.revokeShare(playlistId, userId)
                _uiMessage.value = "Access revoked"
                onComplete?.invoke()
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to revoke share"
            }
        }
    }

    fun refreshPlaylistDetail(playlistId: String, force: Boolean = false) {
        viewModelScope.launch {
            musicRepository.refreshPlaylistDetail(playlistId, force)
        }
    }

    fun forceRefreshPlaylistDetail(playlistId: String) {
        refreshPlaylistDetail(playlistId, force = true)
    }

    fun getSongsForAlbum(albumTitle: String) = musicRepository.getSongsByAlbum(albumTitle)

    fun getSongsForArtist(artistName: String) = musicRepository.getSongsByArtist(artistName)

    fun getSongsForPlaylist(playlistId: String) = musicRepository.getSongsForPlaylist(playlistId)

    suspend fun getSongById(songId: String): Song? = musicRepository.getSongById(songId) ?: allSongs.value.firstOrNull { it.id == songId }

    // Lyrics Actions
    fun saveLyricsConfig(config: LyricsDisplayConfig) {
        viewModelScope.launch {
            preferencesManager.saveLyricsConfig(config)
        }
    }

    suspend fun getLyricsText(songId: String): String {
        return musicRepository.getLyricsText(songId)
    }

    fun updateLyrics(songId: String, lrcText: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = musicRepository.updateLyrics(songId, lrcText)
            if (res.isSuccess) {
                val current = playbackInfo.value.currentSong
                if (current?.id == songId) {
                    _currentLyricsData.value = musicRepository.getLyricsForSong(current, lyricsConfig.value.formatMode)
                }
                syncLibrary()
                _uiMessage.value = "Lyrics saved successfully"
            } else {
                val ex = res.exceptionOrNull()
                val msg = if (ex is retrofit2.HttpException && ex.code() == 403) {
                    "Access denied: Administrator privileges required."
                } else if (ex is retrofit2.HttpException && ex.code() == 404) {
                    "Song not found on server."
                } else {
                    "Failed to save lyrics: ${ex?.message ?: "Unknown error"}"
                }
                _uiMessage.value = msg
            }
            onResult?.invoke(res)
        }
    }

    fun deleteLrc(songId: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = musicRepository.deleteLrc(songId)
            if (res.isSuccess) {
                val current = playbackInfo.value.currentSong
                if (current?.id == songId) {
                    _currentLyricsData.value = musicRepository.getLyricsForSong(current, lyricsConfig.value.formatMode)
                }
                syncLibrary()
                _uiMessage.value = "LRC deleted successfully"
            } else {
                val ex = res.exceptionOrNull()
                val msg = if (ex is retrofit2.HttpException && ex.code() == 403) {
                    "Access denied: Administrator privileges required."
                } else if (ex is retrofit2.HttpException && ex.code() == 404) {
                    "Song not found on server."
                } else {
                    "Failed to delete LRC: ${ex?.message ?: "Unknown error"}"
                }
                _uiMessage.value = msg
            }
            onResult?.invoke(res)
        }
    }

    fun deleteSong(songId: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = musicRepository.deleteSong(songId)
            if (res.isSuccess) {
                if (playbackInfo.value.currentSong?.id == songId) {
                    playbackManager.pause()
                }
                syncLibrary()
                _uiMessage.value = "Song deleted successfully"
            } else {
                val ex = res.exceptionOrNull()
                val msg = if (ex is retrofit2.HttpException && ex.code() == 403) {
                    "Access denied: Administrator privileges required."
                } else if (ex is retrofit2.HttpException && ex.code() == 404) {
                    "Song not found on server."
                } else {
                    "Failed to delete song: ${ex?.message ?: "Unknown error"}"
                }
                _uiMessage.value = msg
            }
            onResult?.invoke(res)
        }
    }

    private var offsetUpdateJob: kotlinx.coroutines.Job? = null

    fun setLyricsOffset(offsetMs: Long) {
        val songId = playbackInfo.value.currentSong?.id ?: return
        val mode = lyricsConfig.value.formatMode
        if (mode == LyricsFormatMode.ELRC) {
            songElrcOffsets[songId] = offsetMs
            _currentSongOffset.value = offsetMs
        } else {
            songLrcOffsets[songId] = offsetMs
            _currentSongOffset.value = offsetMs
            val isAdmin = userProfile.value?.isAdmin == true || userProfile.value?.role == "admin"
            if (isAdmin) {
                offsetUpdateJob?.cancel()
                offsetUpdateJob = viewModelScope.launch {
                    musicRepository.updateLyricsOffset(songId, offsetMs)
                }
            }
        }
    }

    // Settings Actions
    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.setServerUrl(url)
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    fun syncLibrary() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _isLoading.value = true
            musicRepository.syncWithBackend()
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

class MainViewModelFactory(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                musicRepository,
                playbackManager,
                authRepository,
                preferencesManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
