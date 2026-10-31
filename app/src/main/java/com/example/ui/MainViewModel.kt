package com.example.ui

import com.example.BuildConfig
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin

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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

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

    val isOnline: StateFlow<Boolean> = musicRepository.networkConnectivityFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    private val activeDownloadJobs = mutableSetOf<Job>()
    private val completedTrackSongIdMap = mutableMapOf<String, String>()
    private val pendingPlaylistRequests = mutableMapOf<String, MutableList<String>>() // Key: track, Value: PlaylistIDs
    
    private val _completedTrackSongIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val completedTrackSongIds: StateFlow<Map<String, String>> = _completedTrackSongIds.asStateFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    private val _downloadStatusMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, String>> = _downloadStatusMap.asStateFlow()

    private fun trackActiveDownloadJob(job: Job) {
        synchronized(activeDownloadJobs) { activeDownloadJobs.add(job) }
        job.invokeOnCompletion {
            synchronized(activeDownloadJobs) { activeDownloadJobs.remove(job) }
        }
    }

    private suspend fun cancelActiveDownloads() {
        val jobs = synchronized(activeDownloadJobs) { activeDownloadJobs.toList() }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
        synchronized(activeDownloadJobs) { activeDownloadJobs.removeAll(jobs.toSet()) }
        synchronized(downloadingTrackKeys) { downloadingTrackKeys.clear() }
        synchronized(pendingPlaylistRequests) { pendingPlaylistRequests.clear() }
    }

    internal var downloaderPollDelayMs: Long = 1500L
    internal var downloaderIndexingDelayMs: Long = 1500L

    fun getCompletedSongId(track: com.example.data.models.ExternalTrack): String? {
        val key = MusicRepository.externalTrackIdentity(track)
        val id = completedTrackSongIdMap[key]
        if (id != null && allSongs.value.any { it.id == id }) return id
        if (id != null) {
            completedTrackSongIdMap.remove(key)
            _completedTrackSongIds.value = _completedTrackSongIds.value - key
        }

        // Fallback to the currently observed library snapshot.
        val existing = allSongs.value.firstOrNull { MusicRepository.isSameExternalTrack(it, track) }
        if (existing != null) {
            completedTrackSongIdMap[key] = existing.id
            _completedTrackSongIds.value = _completedTrackSongIds.value + (key to existing.id)
        }
        return existing?.id
    }

    fun downloadExternalTrack(track: com.example.data.models.ExternalTrack, onComplete: ((String?) -> Unit)? = null) {
        val key = MusicRepository.externalTrackIdentity(track)

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val existing = try {
                musicRepository.getAllSongsSnapshot().firstOrNull {
                    MusicRepository.isSameExternalTrack(it, track)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val message = "Failed to check the local library: ${e.message ?: "Unknown error"}"
                _downloadStatusMap.value = _downloadStatusMap.value + (key to message)
                _uiMessage.value = message
                onComplete?.invoke(null)
                return@launch
            }

            if (existing != null) {
                completedTrackSongIdMap[key] = existing.id
                _completedTrackSongIds.value = _completedTrackSongIds.value + (key to existing.id)
                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Completed")
                _downloadProgressMap.value = _downloadProgressMap.value + (key to 100)
                processPendingPlaylistRequests(key, existing.id)
                onComplete?.invoke(existing.id)
                return@launch
            }

            synchronized(downloadingTrackKeys) {
                if (key in downloadingTrackKeys) return@launch
                downloadingTrackKeys.add(key)
            }

            try {
                val preExistingIds = musicRepository.getAllSongsSnapshot().map { it.id }.toSet()
                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Resolving library...")

                val libraryId = try {
                    val settings = musicRepository.getDownloaderSettings()
                    settings.libraryId?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("No library configured")
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    val message = if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                        "Access denied: Administrator privileges required."
                    } else if (e.message == "No library configured") {
                        "Failed: No library configured"
                    } else {
                        "Failed: ${e.message ?: "Unknown error"}"
                    }
                    _downloadStatusMap.value = _downloadStatusMap.value + (key to message)
                    _uiMessage.value = message
                    onComplete?.invoke(null)
                    return@launch
                }

                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Starting download...")
                val jobId = try {
                    musicRepository.startDownload(libraryId, listOf(track)).jobId
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    val message = "Failed: Download rejected"
                    _downloadStatusMap.value = _downloadStatusMap.value + (key to message)
                    _uiMessage.value = "Failed to start download: ${e.message ?: "Unknown error"}"
                    onComplete?.invoke(null)
                    return@launch
                }

                var isJobFinished = false
                var isDownloadSuccessful = false
                var failureReason: String? = null
                var pollCount = 0
                var consecutivePollFailures = 0
                val maxPolls = 120
                val maxConsecutivePollFailures = 5
                var hasAttemptedRetry = false
                val activeStatuses = setOf("queued", "pending", "downloading", "running", "searching", "tagging", "processing", "retrying")

                while (!isJobFinished && pollCount < maxPolls) {
                    kotlinx.coroutines.delay(downloaderPollDelayMs)
                    pollCount++
                    try {
                        val job = musicRepository.getDownloadJob(jobId)
                        consecutivePollFailures = 0
                        val overallStatus = job.status.trim().lowercase()
                        val jobTrack = job.tracks.firstOrNull {
                            it.title.equals(track.title, ignoreCase = true) &&
                                it.artist.equals(track.artist, ignoreCase = true)
                        }
                        val trackStatus = jobTrack?.status?.trim()?.lowercase()
                        val progress = jobTrack?.progress ?: if (overallStatus == "completed") 100 else 0
                        _downloadProgressMap.value = _downloadProgressMap.value + (key to progress.coerceIn(0, 100))

                        when {
                            trackStatus == "failed" || overallStatus == "failed" -> {
                                if (!hasAttemptedRetry) {
                                    hasAttemptedRetry = true
                                    _downloadStatusMap.value = _downloadStatusMap.value + (key to "Retrying")
                                    musicRepository.retryDownloadJob(jobId)
                                } else {
                                    isJobFinished = true
                                    failureReason = "Failed: Job failed"
                                }
                            }
                            trackStatus == "cancelled" || overallStatus == "cancelled" -> {
                                isJobFinished = true
                                failureReason = "Cancelled"
                            }
                            overallStatus == "completed" || trackStatus == "completed" -> {
                                isJobFinished = true
                                isDownloadSuccessful = true
                                _downloadProgressMap.value = _downloadProgressMap.value + (key to 100)
                                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Completed")
                            }
                            overallStatus !in activeStatuses && (trackStatus == null || trackStatus !in activeStatuses) -> {
                                isJobFinished = true
                                failureReason = "Failed: Unexpected download status: ${overallStatus.ifBlank { "unknown" }}"
                            }
                            else -> {
                                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Downloading")
                            }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        consecutivePollFailures++
                        _downloadStatusMap.value = _downloadStatusMap.value +
                            (key to "Checking download status ($consecutivePollFailures/$maxConsecutivePollFailures)")
                        android.util.Log.w("MainViewModel", "Download status poll failed for job $jobId", e)
                        if (consecutivePollFailures >= maxConsecutivePollFailures) {
                            isJobFinished = true
                            failureReason = "Failed: Unable to check download status"
                        }
                    }
                }

                if (!isJobFinished && pollCount >= maxPolls) {
                    failureReason = "Failed: Download status timed out"
                }

                if (isDownloadSuccessful) {
                    syncAndIndexTrack(track, preExistingIds, onComplete)
                } else {
                    val message = failureReason ?: "Failed: Download did not complete"
                    _downloadStatusMap.value = _downloadStatusMap.value + (key to message)
                    if (message.startsWith("Failed")) _uiMessage.value = message
                    onComplete?.invoke(null)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _downloadStatusMap.value = _downloadStatusMap.value + (key to "Cancelled")
                throw cancelled
            } catch (e: Exception) {
                val message = "Failed: ${e.message ?: "Unexpected download error"}"
                _downloadStatusMap.value = _downloadStatusMap.value + (key to message)
                _uiMessage.value = message
                onComplete?.invoke(null)
            } finally {
                val finalStatus = _downloadStatusMap.value[key].orEmpty()
                if (finalStatus.startsWith("Failed") ||
                    finalStatus.startsWith("Access denied") ||
                    finalStatus == "Cancelled"
                ) {
                    synchronized(pendingPlaylistRequests) {
                        pendingPlaylistRequests.remove(key)
                    }
                }
                synchronized(downloadingTrackKeys) {
                    downloadingTrackKeys.remove(key)
                }
            }
        }
        trackActiveDownloadJob(job)
        job.start()
    }

    private suspend fun processPendingPlaylistRequests(key: String, songId: String) {
        val pending = synchronized(pendingPlaylistRequests) {
            pendingPlaylistRequests.remove(key) ?: emptyList()
        }
        val failed = mutableListOf<String>()
        for (playlistId in pending) {
            try {
                musicRepository.addSongToPlaylist(playlistId, songId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                synchronized(pendingPlaylistRequests) {
                    val existing = pendingPlaylistRequests.getOrPut(key) { mutableListOf() }
                    pending.filterNot { it in existing }.forEach(existing::add)
                }
                throw cancelled
            } catch (e: Exception) {
                failed.add(playlistId)
                _uiMessage.value = "Failed to add song to playlist: ${e.message ?: "Unknown error"}"
            }
        }
        if (failed.isNotEmpty()) {
            synchronized(pendingPlaylistRequests) {
                val existing = pendingPlaylistRequests.getOrPut(key) { mutableListOf() }
                failed.filterNot { it in existing }.forEach(existing::add)
            }
        } else if (pending.isNotEmpty()) {
            _uiMessage.value = "Added song to playlist"
        }
    }

    fun registerPendingPlaylistAdd(track: com.example.data.models.ExternalTrack, playlistId: String) {
        val key = MusicRepository.externalTrackIdentity(track)
        viewModelScope.launch {
            try {
                val existingId = getCompletedSongId(track)
                    ?: musicRepository.getAllSongsSnapshot()
                        .firstOrNull { MusicRepository.isSameExternalTrack(it, track) }
                        ?.id
                if (existingId != null) {
                    completedTrackSongIdMap[key] = existingId
                    _completedTrackSongIds.value = _completedTrackSongIds.value + (key to existingId)
                    synchronized(pendingPlaylistRequests) {
                        val pending = pendingPlaylistRequests[key]
                        pending?.remove(playlistId)
                        if (pending.isNullOrEmpty()) pendingPlaylistRequests.remove(key)
                    }
                    musicRepository.addSongToPlaylist(playlistId, existingId)
                    _uiMessage.value = "Added to playlist"
                    return@launch
                }

                synchronized(pendingPlaylistRequests) {
                    val list = pendingPlaylistRequests.getOrPut(key) { mutableListOf() }
                    if (playlistId !in list) list.add(playlistId)
                }
                downloadExternalTrack(track)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiMessage.value = "Failed to add song to playlist: ${e.message ?: "Unknown error"}"
            }
        }
    }

    private suspend fun syncAndIndexTrack(
        track: com.example.data.models.ExternalTrack,
        preExistingIds: Set<String>,
        onComplete: ((String?) -> Unit)?
    ) {
        val key = MusicRepository.externalTrackIdentity(track)
        _downloadStatusMap.value = _downloadStatusMap.value + (key to "Indexing...")

        var newlyIndexedId: String? = null
        var lastSyncError: Throwable? = null
        for (syncAttempt in 1..8) {
            kotlinx.coroutines.delay(downloaderIndexingDelayMs)
            try {
                val result = musicRepository.syncWithBackend()
                if (result.isFailure) lastSyncError = result.exceptionOrNull()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                lastSyncError = e
                android.util.Log.w("MainViewModel", "Library sync attempt $syncAttempt failed while indexing ${track.title}", e)
            }

            val currentSongs = try {
                musicRepository.getAllSongsSnapshot()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                lastSyncError = e
                emptyList()
            }
            val newSong = currentSongs.firstOrNull { song ->
                song.id !in preExistingIds && MusicRepository.isSameExternalTrack(song, track)
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
            processPendingPlaylistRequests(key, newlyIndexedId)
            onComplete?.invoke(newlyIndexedId)
        } else {
            _downloadStatusMap.value = _downloadStatusMap.value + (key to "Downloaded (Indexing pending)")
            if (lastSyncError != null) {
                _uiMessage.value = "Downloaded, but library indexing is still pending: ${lastSyncError.message ?: "Sync failed"}"
            }
            onComplete?.invoke(null)
        }
    }

    fun retryLibraryIndexing(track: com.example.data.models.ExternalTrack, onComplete: ((String?) -> Unit)? = null) {
        val key = MusicRepository.externalTrackIdentity(track)
        synchronized(downloadingTrackKeys) {
            if (key in downloadingTrackKeys) return
            downloadingTrackKeys.add(key)
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                syncAndIndexTrack(track, preExistingIds = emptySet(), onComplete = onComplete)
            } finally {
                synchronized(downloadingTrackKeys) {
                    downloadingTrackKeys.remove(key)
                }
            }
        }
        trackActiveDownloadJob(job)
        job.start()
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
                            val savedOffset = preferencesManager.getSongLyricsOffset(song.id, mode)
                            _currentSongOffset.value = savedOffset ?: songElrcOffsets[song.id] ?: lyricsConfig.value.elrcOffsetMs
                        } else {
                            val savedOffset = preferencesManager.getSongLyricsOffset(song.id, mode)
                            val serverOffset = if (savedOffset == null) musicRepository.getLyricsOffset(song.id) else null
                            _currentSongOffset.value = savedOffset
                                ?: songLrcOffsets[song.id]
                                ?: serverOffset
                                ?: song.lyricOffset
                                ?: lyricsConfig.value.lrcOffsetMs
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
            cancelActiveDownloads()
            syncJob?.cancelAndJoin()
            try {
                authRepository.logout()
            } finally {
                playbackManager.clearQueueAndStop()
                musicRepository.clearLocalCache()
                completedTrackSongIdMap.clear()
                _completedTrackSongIds.value = emptyMap()
                _downloadProgressMap.value = emptyMap()
                _downloadStatusMap.value = emptyMap()
            }
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to update playlist"
            }
        }
    }

    fun updateArtwork(playlistId: String, uri: String) {
        viewModelScope.launch {
            try {
                musicRepository.updatePlaylistArtwork(playlistId, uri)
                val refreshResult = musicRepository.refreshPlaylistDetail(playlistId, force = true)
                _uiMessage.value = if (refreshResult.isSuccess) {
                    "Artwork updated"
                } else {
                    "Artwork updated, but playlist details could not be refreshed"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
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
            val result = musicRepository.refreshPlaylistDetail(playlistId, force)
            if (result.isFailure) {
                _uiMessage.value = "Couldn't refresh playlist; showing cached content: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
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
                playbackManager.removeSongFromQueue(songId)
                preferencesManager.clearSongLyricsOffsets(songId)
                completedTrackSongIdMap.entries.removeAll { it.value == songId }
                _completedTrackSongIds.value = _completedTrackSongIds.value.filterValues { it != songId }
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
        } else {
            songLrcOffsets[songId] = offsetMs
        }
        _currentSongOffset.value = offsetMs

        offsetUpdateJob?.cancel()
        offsetUpdateJob = viewModelScope.launch {
            try {
                preferencesManager.setSongLyricsOffset(songId, mode, offsetMs)
                val isAdmin = userProfile.value?.isAdmin == true || userProfile.value?.role == "admin"
                if (mode == LyricsFormatMode.LRC && isAdmin) {
                    val result = musicRepository.updateLyricsOffset(songId, offsetMs)
                    if (result.isFailure) {
                        _uiMessage.value = "Offset saved on this device, but server sync failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiMessage.value = "Failed to save lyrics offset: ${e.message ?: "Unknown error"}"
            }
        }
    }

    // Settings Actions
    fun updateServerUrl(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        val parsed = android.net.Uri.parse(cleanUrl)
        val validScheme = parsed.scheme.equals("https", ignoreCase = true) ||
            (BuildConfig.DEBUG && parsed.scheme.equals("http", ignoreCase = true))
        if (!validScheme || parsed.host.isNullOrBlank()) {
            _uiMessage.value = if (BuildConfig.DEBUG) {
                "Enter a valid HTTP or HTTPS server URL."
            } else {
                "Use a valid HTTPS server URL in release builds."
            }
            return
        }

        viewModelScope.launch {
            try {
                preferencesManager.setServerUrl(cleanUrl)
                _uiMessage.value = "Server URL saved"
                syncLibrary(showResult = true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiMessage.value = "Failed to save server URL: ${e.message ?: "Unknown error"}"
            }
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    fun syncLibrary(showResult: Boolean = false) {
        if (syncJob?.isActive == true) {
            if (showResult) _uiMessage.value = "Library sync is already running"
            return
        }
        syncJob = viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = musicRepository.syncWithBackend()
                if (showResult) {
                    _uiMessage.value = if (result.isSuccess) {
                        "Library synced successfully"
                    } else {
                        "Library sync failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (showResult) _uiMessage.value = "Library sync failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isSyncing.value = false
            }
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
