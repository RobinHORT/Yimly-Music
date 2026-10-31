package com.example.playback

import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.example.data.api.NetworkModule
import com.example.YimlyApplication
import com.example.MainActivity
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlaybackManager(
    val context: Context,
    val musicRepository: MusicRepository,
    val preferencesManager: PreferencesManager,
    val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    constructor(context: Context) : this(
        context = context,
        musicRepository = MusicRepository(
            musicDao = com.example.data.db.YimlyDatabase.getDatabase(context).musicDao(),
            apiService = com.example.data.api.NetworkModule(
                preferencesManager = PreferencesManager(context),
                scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            ).apiService,
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            context = context,
            preferencesManager = PreferencesManager(context)
        ),
        preferencesManager = PreferencesManager(context)
    )

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var transitionWakeLock: PowerManager.WakeLock? = null

    val isTransitionWakeLockHeld: Boolean
        get() = transitionWakeLock?.isHeld == true

    @Volatile
    var isTransitioning: Boolean = false
        private set

    private var _player: ExoPlayer? = null
    private var playbackRequestId: Long = 0L
    private var networkRecoveryJob: kotlinx.coroutines.Job? = null
    private var networkRecoveryAttempts: Int = 0
    private var networkRecoverySongId: String? = null
    private var androidAutoQueueSyncJob: kotlinx.coroutines.Job? = null
    private var androidAutoQueueSyncVersion: Long = 0L

    private companion object {
        const val MAX_NETWORK_RECOVERY_ATTEMPTS = 3
        const val NETWORK_RECOVERY_BASE_DELAY_MS = 1_000L
    }

    val isPlayerInitialized: Boolean
        get() = _player != null

    val player: ExoPlayer?
        get() = _player

    fun ensurePlayerInitialized(): ExoPlayer {
        _player?.let { return it }
        val networkModule = NetworkModule(preferencesManager, scope = coroutineScope)
        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(networkModule.mediaOkHttpClient)
        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
            setWakeMode(C.WAKE_MODE_NETWORK)
            setHandleAudioBecomingNoisy(true)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            Log.d("PlaybackManager", "Song ended, advancing queue...")
                            handleSongEnded()
                        }
                        Player.STATE_READY -> {
                            isTransitioning = false
                            networkRecoveryJob?.cancel()
                            networkRecoveryJob = null
                            releaseTransitionWakeLock()
                        }
                        else -> {}
                    }
                    updatePlaybackInfo()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    syncAndroidAutoQueueFromPlayer(mediaItem)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        isTransitioning = false
                        releaseTransitionWakeLock()
                    }
                    updatePlaybackInfo()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("PlaybackManager", "ExoPlayer error occurred: ${error.message}", error)
                    isTransitioning = false
                    handlePlayerError(error)
                }
            })
        }
        _player = newPlayer
        return newPlayer
    }

    private var _mediaSession: MediaLibrarySession? = null
    val mediaSession: MediaLibrarySession
        get() {
            return _mediaSession ?: run {
                val session = MediaLibrarySession.Builder(
                    context,
                    YimlyForwardingPlayer(ensurePlayerInitialized(), this),
                    YimlySessionCallback(this)
                )
                .setId("yimly_session_${System.identityHashCode(this)}_${System.nanoTime()}")
                .setSessionActivity(
                    PendingIntent.getActivity(
                        context,
                        1002,
                        Intent(context, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
                _mediaSession = session
                session
            }
        }

    private val _playbackInfo = MutableStateFlow(PlaybackInfo())
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _lyricsPositionMs = MutableStateFlow(0L)
    val lyricsPositionMs: StateFlow<Long> = _lyricsPositionMs.asStateFlow()

    private var isLyricsActive = false

    init {
        startProgressTracker()
    }

    fun startPlaybackService() {
        try {
            val intent = Intent(context, YimlyPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to start playback service", e)
        }
    }

    fun acquireTransitionWakeLock(timeoutMs: Long = 30000L) {
        try {
            if (transitionWakeLock == null) {
                transitionWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "YimlyMusic::TransitionWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            transitionWakeLock?.acquire(timeoutMs)
            Log.d("PlaybackManager", "Acquired transition WakeLock for ${timeoutMs}ms")
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to acquire transition WakeLock", e)
        }
    }

    fun releaseTransitionWakeLock() {
        try {
            if (transitionWakeLock?.isHeld == true) {
                transitionWakeLock?.release()
                Log.d("PlaybackManager", "Released transition WakeLock")
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to release transition WakeLock", e)
        }
    }

    fun handlePlayerError(error: PlaybackException) {
        releaseTransitionWakeLock()
        val info = _playbackInfo.value
        val isTransientNetworkError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

        if (isTransientNetworkError && info.currentSong != null) {
            // Bound retries per song, not per prepare cycle; STATE_READY alone must not
            // reset the limit or repeated mid-stream disconnects could retry forever.
            if (networkRecoverySongId != info.currentSong.id) {
                networkRecoverySongId = info.currentSong.id
                networkRecoveryAttempts = 0
            }
            if (networkRecoveryAttempts < MAX_NETWORK_RECOVERY_ATTEMPTS) {
                val attempt = ++networkRecoveryAttempts
                val requestIdAtFailure = playbackRequestId
                val songIdAtFailure = info.currentSong.id
                val delayMs = NETWORK_RECOVERY_BASE_DELAY_MS * (1L shl (attempt - 1))
                networkRecoveryJob?.cancel()
                Log.w(
                    "PlaybackManager",
                    "Transient network playback error (code=${error.errorCode}); retry ${attempt}/${MAX_NETWORK_RECOVERY_ATTEMPTS} in ${delayMs}ms"
                )
                networkRecoveryJob = coroutineScope.launch {
                    delay(delayMs)
                    val latest = _playbackInfo.value
                    if (requestIdAtFailure != playbackRequestId ||
                        latest.currentSong?.id != songIdAtFailure ||
                        latest.currentQueueIndex !in latest.queue.indices
                    ) {
                        Log.d("PlaybackManager", "Skipping stale network recovery for song $songIdAtFailure")
                        return@launch
                    }
                    networkRecoveryJob = null
                    isTransitioning = true
                    acquireTransitionWakeLock()
                    playCurrentQueueIndex(targetInstrumental = latest.isInstrumental)
                }
                return
            }
            Log.e(
                "PlaybackManager",
                "Network playback recovery exhausted after $networkRecoveryAttempts retries (code=${error.errorCode})",
                error
            )
        } else {
            Log.e(
                "PlaybackManager",
                "Playback failed without automatic network retry (code=${error.errorCode}, message=${error.message})",
                error
            )
        }

        // Preserve the existing instrumental-to-vocal fallback after retry attempts
        // are exhausted or when the failure is not a transient network error.
        if (info.isInstrumental) {
            Log.w("PlaybackManager", "Instrumental stream failed, falling back to vocal track: ${error.message}")
            val currentSong = info.currentSong
            if (currentSong != null) {
                val updatedSong = currentSong.copy(hasInstrumental = false)
                val updatedQueue = info.queue.map { if (it.id == currentSong.id) updatedSong else it }
                _playbackInfo.value = info.copy(
                    hasInstrumental = false,
                    currentSong = updatedSong,
                    queue = updatedQueue
                )
                playCurrentQueueIndex(targetInstrumental = false)
            } else {
                _playbackInfo.value = info.copy(
                    isInstrumental = false,
                    hasInstrumental = false
                )
            }
        }
    }

    fun getResolvedArtworkUrl(song: Song): String? {
        return song.artworkUrl
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song), startIndex: Int = 0) {
        ensurePlayerInitialized()
        startPlaybackService()
        val finalQueue = if (queue.isEmpty()) listOf(song) else queue
        // A list screen may omit the index or pass an index from a filtered list.
        // Trust the index only when it points at the selected song; otherwise resolve by ID.
        val validIndex = startIndex.takeIf {
            it in finalQueue.indices && finalQueue[it].id == song.id
        } ?: finalQueue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        
        _playbackInfo.value = _playbackInfo.value.copy(
            queue = finalQueue,
            currentQueueIndex = validIndex,
            currentSong = finalQueue[validIndex],
            hasInstrumental = finalQueue[validIndex].hasInstrumentalEffective,
            isInstrumental = false
        )
        playCurrentQueueIndex()
    }

    /**
     * Media3 applies Android Auto selections directly to the session player after
     * onAddMediaItems resolves their URIs. Mirror that resulting timeline into the
     * app queue so Yimly's controls, UI and next/previous logic use the same songs.
     */
    private fun syncAndroidAutoQueueFromPlayer(currentMediaItem: MediaItem?) {
        val player = _player ?: return
        val currentMediaId = currentMediaItem?.mediaId ?: return
        if (!isAndroidAutoMediaId(currentMediaId)) return

        val timelineItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val songIds = timelineItems.mapNotNull { androidAutoSongId(it.mediaId) }
        if (songIds.isEmpty()) return

        val syncVersion = ++androidAutoQueueSyncVersion
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val songs = songIds.mapNotNull { musicRepository.getSongById(it) }
                withContext(Dispatchers.Main) {
                    if (syncVersion != androidAutoQueueSyncVersion) return@withContext
                    val activeId = _player?.currentMediaItem?.mediaId ?: return@withContext
                    val activeSongId = androidAutoSongId(activeId) ?: return@withContext
                    val activeIndex = songs.indexOfFirst { it.id == activeSongId }
                    if (activeIndex < 0 || songs.isEmpty()) return@withContext

                    val activeSong = songs[activeIndex]
                    _playbackInfo.value = _playbackInfo.value.copy(
                        queue = songs,
                        currentQueueIndex = activeIndex,
                        currentSong = activeSong,
                        hasInstrumental = activeSong.hasInstrumentalEffective,
                        isInstrumental = false
                    )
                    updatePlaybackInfo()
                    Log.d(
                        "PlaybackManager",
                        "Synced Android Auto queue: ${songs.size} songs, active=${activeSong.id}"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Failed to sync Android Auto queue", e)
            }
        }
    }

    private fun isAndroidAutoMediaId(mediaId: String): Boolean =
        mediaId.startsWith("playlist_song|") ||
            mediaId.startsWith("favorite_song|") ||
            mediaId.startsWith("recent_song|") ||
            mediaId.startsWith("all_song|")

    private fun androidAutoSongId(mediaId: String): String? = when {
        mediaId.startsWith("playlist_song|") -> mediaId.substringAfterLast("|").takeIf { it.isNotBlank() }
        mediaId.startsWith("favorite_song|") ||
            mediaId.startsWith("recent_song|") ||
            mediaId.startsWith("all_song|") -> mediaId.substringAfter("|").takeIf { it.isNotBlank() }
        else -> null
    }

    fun playSongFromAuto(songId: String, type: String?, playlistId: String?) {
        coroutineScope.launch(Dispatchers.IO) {
            val song = musicRepository.getSongById(songId)
            if (song != null) {
                withContext(Dispatchers.Main) {
                    playSong(song)
                }
            }
        }
    }

    fun playQueueItem(index: Int) {
        val info = _playbackInfo.value
        if (index in info.queue.indices) {
            ensurePlayerInitialized()
            startPlaybackService()
            _playbackInfo.value = info.copy(
                currentQueueIndex = index,
                currentSong = info.queue[index],
                hasInstrumental = info.queue[index].hasInstrumentalEffective,
                isInstrumental = false
            )
            playCurrentQueueIndex()
        }
    }

    fun next(manual: Boolean = true) {
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) {
            isTransitioning = false
            releaseTransitionWakeLock()
            return
        }
        
        val nextIndex = when {
            !manual && info.repeatMode == RepeatMode.ONE -> info.currentQueueIndex
            info.isShuffle && info.queue.size > 1 -> info.queue.indices.filter { it != info.currentQueueIndex }.random()
            info.isShuffle -> info.currentQueueIndex
            info.repeatMode == RepeatMode.ALL -> (info.currentQueueIndex + 1) % info.queue.size
            info.currentQueueIndex + 1 < info.queue.size -> info.currentQueueIndex + 1
            else -> {
                isTransitioning = false
                releaseTransitionWakeLock()
                return
            }
        }
        
        val currentInst = info.isInstrumental
        _playbackInfo.value = info.copy(
            currentQueueIndex = nextIndex,
            currentSong = info.queue[nextIndex],
            hasInstrumental = info.queue[nextIndex].hasInstrumentalEffective,
            isInstrumental = currentInst
        )
        playCurrentQueueIndex()
    }

    fun previous() {
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) return
        
        val currentP = _player
        if (currentP != null && currentP.currentPosition > 3000L) {
            currentP.seekTo(0)
            _playbackPositionMs.value = 0L
            updatePlaybackInfo()
            return
        }

        val prevIndex = if (info.currentQueueIndex - 1 >= 0) {
            info.currentQueueIndex - 1
        } else {
            info.queue.size - 1
        }

        val currentInst = info.isInstrumental
        _playbackInfo.value = info.copy(
            currentQueueIndex = prevIndex,
            currentSong = info.queue[prevIndex],
            hasInstrumental = info.queue[prevIndex].hasInstrumentalEffective,
            isInstrumental = currentInst
        )
        playCurrentQueueIndex()
    }

    private fun handleSongEnded() {
        if (isTransitioning) {
            Log.d("PlaybackManager", "handleSongEnded ignored: already transitioning")
            return
        }
        isTransitioning = true
        acquireTransitionWakeLock()
        next(manual = false)
    }

    private fun playCurrentQueueIndex(targetInstrumental: Boolean? = null) {
        val info = _playbackInfo.value
        if (info.currentQueueIndex !in info.queue.indices) {
            isTransitioning = false
            releaseTransitionWakeLock()
            return
        }
        val song = info.queue[info.currentQueueIndex]
        val wantInst = targetInstrumental ?: info.isInstrumental
        val requestId = ++playbackRequestId

        acquireTransitionWakeLock()

        coroutineScope.launch(Dispatchers.IO) {
            var handedOffToPlayer = false
            try {
                if (requestId != playbackRequestId) {
                    releaseTransitionWakeLock()
                    return@launch
                }
                val resolvedTrack = musicRepository.resolvePlayableTrack(song, wantInst)
                if (requestId != playbackRequestId) {
                    releaseTransitionWakeLock()
                    return@launch
                }
                val audioUrl = resolvedTrack.playableUrl
                
                val artworkUri = getResolvedArtworkUrl(song)?.let { Uri.parse(it) }
                val parsedAudioUri = Uri.parse(audioUrl)

                val mediaItem = MediaItem.Builder()
                    .setMediaId(if (wantInst) "${song.id}_inst" else song.id)
                    .setUri(parsedAudioUri)
                    .setRequestMetadata(
                        MediaItem.RequestMetadata.Builder()
                            .setMediaUri(parsedAudioUri)
                            .build()
                    )
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(artworkUri)
                            .build()
                    )
                    .build()

                withContext(Dispatchers.Main) {
                    if (requestId != playbackRequestId) {
                        return@withContext
                    }
                    val p = ensurePlayerInitialized()
                    val isSameSong = p.currentMediaItem?.mediaId?.startsWith(song.id) == true
                    val currentPos = if (isSameSong && p.playbackState != Player.STATE_ENDED) p.currentPosition.coerceAtLeast(0L) else 0L

                    val activeIndex = p.currentMediaItemIndex
                    if (isSameSong && activeIndex in 0 until p.mediaItemCount) {
                        p.replaceMediaItem(activeIndex, mediaItem)
                        p.seekTo(activeIndex, currentPos)
                    } else {
                        p.setMediaItem(mediaItem, currentPos)
                    }
                    p.prepare()
                    p.playWhenReady = true
                    handedOffToPlayer = true
                    _playbackInfo.value = _playbackInfo.value.copy(
                        isInstrumental = wantInst,
                        currentSong = song,
                        hasInstrumental = if (!wantInst && targetInstrumental == false && !song.hasInstrumentalEffective) false else song.hasInstrumentalEffective
                    )
                    updatePlaybackInfo()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (requestId == playbackRequestId) {
                    isTransitioning = false
                    releaseTransitionWakeLock()
                }
                throw e
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error playing song: ${song.title}", e)
                if (requestId == playbackRequestId) {
                    isTransitioning = false
                    releaseTransitionWakeLock()
                }
            } finally {
                if (requestId == playbackRequestId && !handedOffToPlayer) {
                    isTransitioning = false
                    releaseTransitionWakeLock()
                }
            }
        }
    }

    fun togglePlayPause() {
        val p = _player
        if (p != null && p.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        startPlaybackService()
        val p = ensurePlayerInitialized()
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
        }
        p.play()
        updatePlaybackInfo()
    }

    fun pause() {
        isTransitioning = false
        releaseTransitionWakeLock()
        _player?.pause()
        updatePlaybackInfo()
    }

    fun seekTo(positionMs: Long) {
        ensurePlayerInitialized().seekTo(positionMs)
        _playbackPositionMs.value = positionMs
        _lyricsPositionMs.value = positionMs
        _playbackInfo.value = _playbackInfo.value.copy(currentPositionMs = positionMs)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playbackInfo.value = _playbackInfo.value.copy(repeatMode = mode)
    }

    fun cycleRepeatMode() {
        val current = _playbackInfo.value.repeatMode
        val next = current.next()
        setRepeatMode(next)
    }

    fun setShuffle(enabled: Boolean) {
        _playbackInfo.value = _playbackInfo.value.copy(isShuffle = enabled)
    }

    fun toggleShuffle() {
        val current = _playbackInfo.value.isShuffle
        setShuffle(!current)
    }

    fun updateFavoriteStatus(songId: String, isFavorite: Boolean) {
        val info = _playbackInfo.value
        val updatedCurrentSong = info.currentSong?.takeIf { it.id == songId }?.copy(isFavorite = isFavorite) ?: info.currentSong
        val updatedQueue = info.queue.map { song ->
            if (song.id == songId) song.copy(isFavorite = isFavorite) else song
        }
        _playbackInfo.value = info.copy(
            currentSong = updatedCurrentSong,
            queue = updatedQueue
        )
    }

    fun toggleInstrumental() {
        val info = _playbackInfo.value
        val newInst = !info.isInstrumental
        if (info.currentSong != null) {
            playCurrentQueueIndex(targetInstrumental = newInst)
        } else {
            _playbackInfo.value = info.copy(isInstrumental = newInst)
        }
    }

    fun addToQueue(song: Song) {
        val info = _playbackInfo.value
        val newQueue = info.queue + song
        _playbackInfo.value = info.copy(queue = newQueue)
        if (info.currentSong == null) {
            playSong(song, newQueue, 0)
        }
    }

    fun playNext(song: Song) {
        val info = _playbackInfo.value
        val queue = info.queue.toMutableList()
        val insertIndex = if (info.currentQueueIndex in queue.indices) info.currentQueueIndex + 1 else queue.size
        queue.add(insertIndex, song)
        _playbackInfo.value = info.copy(queue = queue)
        if (info.currentSong == null) {
            playSong(song, queue, insertIndex)
        }
    }

    fun removeFromQueue(index: Int) {
        val info = _playbackInfo.value
        val queue = info.queue.toMutableList()
        if (index !in queue.indices) return

        val removedCurrent = index == info.currentQueueIndex
        queue.removeAt(index)

        if (queue.isEmpty()) {
            _player?.stop()
            _player?.clearMediaItems()
            releaseTransitionWakeLock()
            _playbackPositionMs.value = 0L
            _lyricsPositionMs.value = 0L
            _playbackInfo.value = info.copy(
                queue = emptyList(),
                currentQueueIndex = -1,
                currentSong = null,
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                isInstrumental = false,
                hasInstrumental = false
            )
            return
        }

        val newIndex = when {
            info.currentQueueIndex < 0 -> -1
            index < info.currentQueueIndex -> info.currentQueueIndex - 1
            removedCurrent -> info.currentQueueIndex.coerceAtMost(queue.lastIndex)
            else -> info.currentQueueIndex
        }
        val newSong = queue.getOrNull(newIndex)
        _playbackInfo.value = info.copy(
            queue = queue,
            currentQueueIndex = newIndex,
            currentSong = newSong,
            hasInstrumental = newSong?.hasInstrumentalEffective ?: false,
            isInstrumental = if (removedCurrent) false else info.isInstrumental
        )

        // Removing the active item must also move ExoPlayer to the replacement item.
        if (removedCurrent && newSong != null) {
            playCurrentQueueIndex(targetInstrumental = false)
        }
    }

    /** Remove every occurrence of a deleted library song from the active queue. */
    fun removeSongFromQueue(songId: String) {
        val info = _playbackInfo.value
        val oldIndex = info.currentQueueIndex
        val currentWasRemoved = info.currentSong?.id == songId
        val removedBeforeCurrent = if (oldIndex >= 0) {
            info.queue.take(oldIndex).count { it.id == songId }
        } else 0
        val newQueue = info.queue.filterNot { it.id == songId }
        if (newQueue.size == info.queue.size) return

        if (newQueue.isEmpty()) {
            _player?.stop()
            _player?.clearMediaItems()
            isTransitioning = false
            releaseTransitionWakeLock()
            _playbackPositionMs.value = 0L
            _lyricsPositionMs.value = 0L
            _playbackInfo.value = info.copy(
                queue = emptyList(), currentQueueIndex = -1, currentSong = null,
                isPlaying = false, isBuffering = false, currentPositionMs = 0L,
                durationMs = 0L, isInstrumental = false, hasInstrumental = false
            )
            return
        }

        val newIndex = when {
            oldIndex < 0 -> -1
            currentWasRemoved -> (oldIndex - removedBeforeCurrent).coerceAtMost(newQueue.lastIndex)
            else -> oldIndex - removedBeforeCurrent
        }
        val newSong = newQueue.getOrNull(newIndex)
        _playbackInfo.value = info.copy(
            queue = newQueue,
            currentQueueIndex = newIndex,
            currentSong = newSong,
            currentPositionMs = if (currentWasRemoved) 0L else info.currentPositionMs,
            hasInstrumental = newSong?.hasInstrumentalEffective ?: false,
            isInstrumental = if (currentWasRemoved) false else info.isInstrumental
        )

        if (currentWasRemoved && newSong != null) {
            _player?.pause()
            _playbackPositionMs.value = 0L
            _lyricsPositionMs.value = 0L
            playCurrentQueueIndex(targetInstrumental = false)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val info = _playbackInfo.value
        val queue = info.queue.toMutableList()
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            val item = queue.removeAt(fromIndex)
            queue.add(toIndex, item)
            val oldIndex = info.currentQueueIndex
            val newIndex = when {
                oldIndex !in info.queue.indices -> oldIndex
                fromIndex == oldIndex -> toIndex
                fromIndex < oldIndex && toIndex >= oldIndex -> oldIndex - 1
                fromIndex > oldIndex && toIndex <= oldIndex -> oldIndex + 1
                else -> oldIndex
            }
            _playbackInfo.value = info.copy(
                queue = queue,
                currentQueueIndex = newIndex,
                currentSong = queue.getOrNull(newIndex)
            )
        }
    }

    fun clearUpcomingQueue() {
        val info = _playbackInfo.value
        val currentSong = info.currentSong
        if (currentSong != null) {
            _playbackInfo.value = info.copy(
                queue = listOf(currentSong),
                currentQueueIndex = 0
            )
        } else {
            _playbackInfo.value = info.copy(
                queue = emptyList(),
                currentQueueIndex = -1
            )
        }
    }

    fun setLyricsActive(active: Boolean) {
        isLyricsActive = active
    }

    private fun updatePlaybackInfo() {
        val p = _player
        if (p != null) {
            _playbackInfo.value = _playbackInfo.value.copy(
                isPlaying = p.isPlaying,
                currentPositionMs = p.currentPosition,
                durationMs = if (p.duration > 0) p.duration else 0L,
                isBuffering = p.playbackState == Player.STATE_BUFFERING
            )
        }
    }

    private fun startProgressTracker() {
        coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                val p = _player
                if (p != null && p.isPlaying) {
                    _playbackPositionMs.value = p.currentPosition
                    _lyricsPositionMs.value = p.currentPosition
                    updatePlaybackInfo()
                }
                kotlinx.coroutines.delay(500L)
            }
        }
    }

    fun release() {
        playbackRequestId++
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
        isTransitioning = false
        releaseTransitionWakeLock()
        _mediaSession?.release()
        _mediaSession = null
        _player?.release()
        _player = null
        _playbackPositionMs.value = 0L
        _lyricsPositionMs.value = 0L
        _playbackInfo.value = PlaybackInfo()
    }
}
