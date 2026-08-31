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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
                flowOf(musicRepository.search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, SearchResult())



    init {
        // Observe current playing song and load its lyrics
        viewModelScope.launch {
            var lyricsJob: kotlinx.coroutines.Job? = null
            playbackInfo.map { it.currentSong }.distinctUntilChanged().collect { song ->
                lyricsJob?.cancel()
                if (song != null) {
                    lyricsJob = launch {
                        _currentLyricsData.value = musicRepository.getLyricsForSong(song)
                        val offset = musicRepository.getLyricsOffset(song.id)
                        _currentSongOffset.value = offset ?: song.lyricOffset ?: 0L
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

    fun toggleFavorite(songId: String) {
        viewModelScope.launch {
            try {
                val newFav = musicRepository.toggleFavorite(songId)
                playbackManager.updateFavoriteStatus(songId, newFav)
            } catch (e: Exception) {
                _uiMessage.value = e.message ?: "Failed to update favorite on server"
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
            musicRepository.updatePlaylistArtwork(playlistId, uri)
            musicRepository.refreshPlaylistDetail(playlistId)
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

    fun refreshPlaylistDetail(playlistId: String) {
        viewModelScope.launch {
            musicRepository.refreshPlaylistDetail(playlistId)
        }
    }

    fun getSongsForAlbum(albumTitle: String) = musicRepository.getSongsByAlbum(albumTitle)

    fun getSongsForArtist(artistName: String) = musicRepository.getSongsByArtist(artistName)

    fun getSongsForPlaylist(playlistId: String) = musicRepository.getSongsForPlaylist(playlistId)

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
                    _currentLyricsData.value = musicRepository.getLyricsForSong(current)
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
                    _currentLyricsData.value = musicRepository.getLyricsForSong(current)
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

    fun setLyricsOffset(offsetMs: Long) {
        val songId = playbackInfo.value.currentSong?.id ?: return
        _currentSongOffset.value = offsetMs
        val isAdmin = userProfile.value?.isAdmin == true || userProfile.value?.role == "admin"
        if (isAdmin) {
            viewModelScope.launch {
                musicRepository.updateLyricsOffset(songId, offsetMs)
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
