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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.example.data.api.NetworkModule
import com.example.YimlyApplication
import com.example.MainActivity
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
    val snapshotRepository: PlaybackSnapshotRepository,
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
        preferencesManager = PreferencesManager(context),
        snapshotRepository = PlaybackSnapshotRepository(PreferencesManager(context))
    )

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var transitionWakeLock: PowerManager.WakeLock? = null

    val isTransitionWakeLockHeld: Boolean
        get() = transitionWakeLock?.isHeld == true

    @Volatile
    var isTransitioning: Boolean = false
        private set

    @Volatile
    private var _player: ExoPlayer? = null
    private val playerInitLock = Any()
    private var playbackRequestId: Long = 0L
    private var networkRecoveryJob: kotlinx.coroutines.Job? = null
    private var networkRecoveryAttempts: Int = 0
    private var networkRecoverySongId: String? = null
    private var androidAutoQueueSyncJob: kotlinx.coroutines.Job? = null
    private var androidAutoQueueSyncVersion: Long = 0L
    private var lastSnapshotPersistAtMs = 0L
    private var snapshotWriteJob: kotlinx.coroutines.Job? = null
    private val snapshotRestoreCompleted = CompletableDeferred<Unit>()

    private val queueManager = PlaybackQueueManager(
        coroutineScope = coroutineScope,
        mediaItemBuilder = { song, inst -> buildMediaItem(song, inst) },
        syncListener = object : QueueMediaSyncListener {
            override fun onMediaItemAdded(index: Int, mediaItem: MediaItem) {
                _player?.addMediaItem(index.coerceIn(0, _player?.mediaItemCount ?: 0), mediaItem)
            }
            override fun onMediaItemRemoved(index: Int) {
                _player?.removeMediaItem(index)
            }
            override fun onMediaItemMoved(fromIndex: Int, toIndex: Int) {
                _player?.moveMediaItem(fromIndex, toIndex)
            }
            override fun onQueueCleared() {
                _player?.stop()
                _player?.clearMediaItems()
            }
        }
    )

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
        val currentLooper = android.os.Looper.myLooper()
        val mainLooper = android.os.Looper.getMainLooper()
        check(currentLooper == mainLooper) {
            "Player initialization must occur on the main thread (current thread: ${Thread.currentThread().name}, looper: $currentLooper, expected: $mainLooper)"
        }
        return synchronized(playerInitLock) {
            _player?.let { return it }
            val networkModule = NetworkModule(preferencesManager, scope = coroutineScope)
            val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(networkModule.mediaOkHttpClient)
            val newPlayer = ExoPlayer.Builder(context)
                .setLooper(mainLooper)
                .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_ENDED -> {
                                    Log.d("PlaybackManager", "Song ended, advancing queue...")
                                    handleSongEnded()
                                }
                                Player.STATE_READY -> {
                                    networkRecoveryJob?.cancel()
                                    networkRecoveryJob = null
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
            newPlayer
        }
    }

    suspend fun getOrInitializePlayerOnMain(): ExoPlayer {
        _player?.let { return it }
        return withContext(Dispatchers.Main.immediate) {
            ensurePlayerInitialized()
        }
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
                .setCustomLayout(
                    listOf(
                        CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                            .setDisplayName("Shuffle Playlist")
                            .setSessionCommand(
                                androidx.media3.session.SessionCommand(
                                    YimlySessionCallback.ACTION_SHUFFLE_PLAYLIST,
                                    android.os.Bundle.EMPTY
                                )
                            )
                            .build()
                    )
                )
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
        restorePlaybackSnapshot()
        
        coroutineScope.launch {
            queueManager.queueState.collect { state ->
                val info = _playbackInfo.value
                val activeEntry = state.entries.find { it.entryId == state.currentEntryId }
                val currentSong = activeEntry?.song
                
                _playbackInfo.value = info.copy(
                    queue = state.entries.map { it.song },
                    currentQueueIndex = state.entries.indexOfFirst { it.entryId == state.currentEntryId }.coerceAtLeast(-1),
                    currentSong = currentSong ?: info.currentSong,
                    hasInstrumental = currentSong?.hasInstrumentalEffective ?: info.hasInstrumental
                )
            }
        }
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

        val isDecoderOrResourceError =
            (error.errorCode in 3000..3999) ||
            (error.message?.contains("MediaCodec", ignoreCase = true) == true) ||
            (error.message?.contains("resource manager", ignoreCase = true) == true) ||
            (error.message?.contains("Decoder", ignoreCase = true) == true)

        if ((isTransientNetworkError || isDecoderOrResourceError) && info.currentSong != null) {
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
                val delayMs = if (isDecoderOrResourceError) 300L else (NETWORK_RECOVERY_BASE_DELAY_MS * (1L shl (attempt - 1)))
                networkRecoveryJob?.cancel()
                Log.w(
                    "PlaybackManager",
                    "Playback error (code=${error.errorCode}, message=${error.message}); retry ${attempt}/${MAX_NETWORK_RECOVERY_ATTEMPTS} in ${delayMs}ms"
                )
                networkRecoveryJob = coroutineScope.launch {
                    delay(delayMs)
                    val latest = _playbackInfo.value
                    if (requestIdAtFailure != playbackRequestId ||
                        latest.currentSong?.id != songIdAtFailure ||
                        latest.currentQueueIndex !in latest.queue.indices
                    ) {
                        Log.d("PlaybackManager", "Skipping stale recovery for song $songIdAtFailure")
                        return@launch
                    }
                    networkRecoveryJob = null
                    isTransitioning = true
                    acquireTransitionWakeLock()
                    if (isDecoderOrResourceError) {
                        withContext(Dispatchers.Main.immediate) {
                            synchronized(playerInitLock) {
                                try {
                                    _player?.release()
                                } catch (e: Exception) {
                                    Log.e("PlaybackManager", "Error releasing ExoPlayer during decoder recovery", e)
                                }
                                _player = null
                                ensurePlayerInitialized()
                            }
                        }
                    }
                    val currentPos = _playbackPositionMs.value.coerceAtLeast(0L)
                    playCurrentQueueIndex(targetInstrumental = latest.isInstrumental, initialPositionMs = currentPos)
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
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        ensurePlayerInitialized()
        startPlaybackService()
        val finalQueue = if (queue.isEmpty()) listOf(song) else queue
        // Respect the selected song even when a screen omits or passes a stale index.
        val validIndex = startIndex.takeIf {
            it in finalQueue.indices && finalQueue[it].id == song.id
        } ?: finalQueue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        
        queueManager.updateQueueState(finalQueue, validIndex)
        persistPlaybackSnapshot(force = true)
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
        // Keep null placeholders while resolving so missing library entries do not
        // shift the active index or cause a duplicate song ID to select the wrong row.
        val songIds = timelineItems.map { androidAutoSongId(it.mediaId) }
        if (songIds.none { it != null }) return

        val syncVersion = ++androidAutoQueueSyncVersion
        val syncRequestId = playbackRequestId
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val resolvedSongs = songIds.map { id -> id?.let { musicRepository.getSongById(it) } }
                withContext(Dispatchers.Main) {
                    if (syncVersion != androidAutoQueueSyncVersion || syncRequestId != playbackRequestId) return@withContext
                    val activeId = _player?.currentMediaItem?.mediaId ?: return@withContext
                    val activeSongId = androidAutoSongId(activeId) ?: return@withContext
                    val mediaIndex = _player?.currentMediaItemIndex ?: return@withContext
                    val activeSong = resolvedSongs.getOrNull(mediaIndex) ?: return@withContext
                    if (activeSong.id != activeSongId) return@withContext
                    val activeQueueIndex = resolvedSongs.take(mediaIndex).count { it != null }
                    val songs = resolvedSongs.filterNotNull()
                    if (songs.isEmpty()) return@withContext

                    queueManager.updateQueueState(songs, activeQueueIndex)
                    persistPlaybackSnapshot(force = true)
                    Log.d(
                        "PlaybackManager",
                        "Synced Android Auto queue: ${songs.size} songs, activeIndex=${activeQueueIndex}, active=${activeSong.id}"
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
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { playQueueItem(index) }
            return
        }
        val info = _playbackInfo.value
        if (index in info.queue.indices) {
            androidAutoQueueSyncVersion++
            androidAutoQueueSyncJob?.cancel()
            androidAutoQueueSyncJob = null
            ensurePlayerInitialized()
            startPlaybackService()
            queueManager.setCurrentIndex(index)
            queueManager.clearShuffleHistory()
            persistPlaybackSnapshot(force = true)
            playCurrentQueueIndex()
        }
    }

    fun next(manual: Boolean = true) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { next(manual) }
            return
        }
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
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
        
        if (info.isShuffle && nextIndex != info.currentQueueIndex && info.currentQueueIndex in info.queue.indices) {
            queueManager.addShuffleHistory(info.currentQueueIndex)
        }
        
        queueManager.setCurrentIndex(nextIndex)
        persistPlaybackSnapshot(force = true)
        playCurrentQueueIndex()
    }

    fun previous() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { previous() }
            return
        }
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) return
        
        val currentP = _player
        if (currentP != null && currentP.currentPosition > 3000L) {
            currentP.seekTo(0)
            _playbackPositionMs.value = 0L
            updatePlaybackInfo()
            return
        }

        val prevIndex = if (info.isShuffle) {
            queueManager.removeLastShuffleHistory() ?: (info.currentQueueIndex - 1).coerceAtLeast(0)
        } else if (info.currentQueueIndex - 1 >= 0) {
            info.currentQueueIndex - 1
        } else {
            info.queue.size - 1
        }

        queueManager.setCurrentIndex(prevIndex)
        persistPlaybackSnapshot(force = true)
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

    private fun isQueueMatchingPlayerTimeline(player: Player, queue: List<Song>): Boolean {
        if (player.mediaItemCount != queue.size) return false
        for (i in queue.indices) {
            val itemMediaId = player.getMediaItemAt(i).mediaId.removeSuffix("_inst")
            if (itemMediaId != queue[i].id) {
                return false
            }
        }
        return true
    }

    internal suspend fun buildMediaItem(song: Song, wantInstrumental: Boolean): MediaItem {
        val resolvedTrack = musicRepository.resolvePlayableTrack(song, wantInstrumental)
        val audioUrl = resolvedTrack.playableUrl
        val parsedAudioUri = Uri.parse(audioUrl)
        val artworkUri = getResolvedArtworkUrl(song)?.let { Uri.parse(it) }

        return MediaItem.Builder()
            .setMediaId(if (wantInstrumental) "${song.id}_inst" else song.id)
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
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private fun playCurrentQueueIndex(
        targetInstrumental: Boolean? = null,
        autoPlay: Boolean = true,
        initialPositionMs: Long? = null
    ) {
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

                val currentQueue = info.queue
                val targetIndex = info.currentQueueIndex

                val timelineMatches = withContext(Dispatchers.Main) {
                    val p = _player
                    p != null && isQueueMatchingPlayerTimeline(p, currentQueue)
                }

                if (requestId != playbackRequestId) {
                    releaseTransitionWakeLock()
                    return@launch
                }

                val allMediaItems: List<MediaItem>? = if (!timelineMatches) {
                    currentQueue.mapIndexed { i, qSong ->
                        if (requestId != playbackRequestId) return@launch
                        val wantItemInst = if (i == targetIndex) {
                            wantInst
                        } else {
                            qSong.isInstrumentalTrack || (info.isInstrumental && qSong.hasInstrumentalEffective)
                        }
                        buildMediaItem(qSong, wantItemInst)
                    }
                } else {
                    null
                }

                val activeMediaItem = allMediaItems?.getOrNull(targetIndex) ?: buildMediaItem(song, wantInst)

                withContext(Dispatchers.Main.immediate) {
                    if (requestId != playbackRequestId) {
                        return@withContext
                    }
                    val p = ensurePlayerInitialized()
                    val activeMediaId = p.currentMediaItem?.mediaId
                    val isSameSong = activeMediaId == song.id || activeMediaId == "${song.id}_inst"
                    val currentPos = initialPositionMs?.coerceAtLeast(0L)
                        ?: if (isSameSong && p.playbackState != Player.STATE_ENDED) p.currentPosition.coerceAtLeast(0L) else 0L

                    if (allMediaItems != null) {
                        p.setMediaItems(allMediaItems, targetIndex, currentPos)
                    } else {
                        p.replaceMediaItem(targetIndex, activeMediaItem)
                        if (p.currentMediaItemIndex != targetIndex || currentPos > 0L) {
                            p.seekTo(targetIndex, currentPos)
                        }
                    }
                    p.prepare()
                    p.playWhenReady = autoPlay
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
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { togglePlayPause() }
            return
        }
        val p = _player
        if (p != null && p.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { play() }
            return
        }
        startPlaybackService()
        val p = ensurePlayerInitialized()
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
        }
        p.play()
        updatePlaybackInfo()
        persistPlaybackSnapshot(force = true)
    }

    fun pause() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { pause() }
            return
        }
        isTransitioning = false
        releaseTransitionWakeLock()
        _player?.pause()
        updatePlaybackInfo()
        persistPlaybackSnapshot(force = true)
    }

    fun seekTo(positionMs: Long) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { seekTo(positionMs) }
            return
        }
        ensurePlayerInitialized().seekTo(positionMs)
        _playbackPositionMs.value = positionMs
        _lyricsPositionMs.value = positionMs
        _playbackInfo.value = _playbackInfo.value.copy(currentPositionMs = positionMs)
        persistPlaybackSnapshot(force = true)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playbackInfo.value = _playbackInfo.value.copy(repeatMode = mode)
        persistPlaybackSnapshot(force = true)
    }

    fun cycleRepeatMode() {
        val current = _playbackInfo.value.repeatMode
        val next = current.next()
        setRepeatMode(next)
    }

    fun setShuffle(enabled: Boolean) {
        if (!enabled) queueManager.clearShuffleHistory()
        _playbackInfo.value = _playbackInfo.value.copy(isShuffle = enabled)
        persistPlaybackSnapshot(force = true)
    }

    fun toggleShuffle() {
        val current = _playbackInfo.value.isShuffle
        setShuffle(!current)
    }
    /** Start the current Android Auto/app queue at a random track and enable shuffle. */
    fun shuffleCurrentQueue() {
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) return
        setShuffle(true)
        if (info.queue.size <= 1) return

        val candidates = info.queue.indices.filter { it != info.currentQueueIndex }
        val nextIndex = candidates.random()
        val nextSong = info.queue[nextIndex]
        val useInstrumental = info.isInstrumental && nextSong.hasInstrumentalEffective
        queueManager.setCurrentIndex(nextIndex)
        queueManager.clearShuffleHistory()
        persistPlaybackSnapshot(force = true)
        playCurrentQueueIndex(targetInstrumental = useInstrumental)
    }


    fun updateFavoriteStatus(songId: String, isFavorite: Boolean) {
        queueManager.updateSongFavoriteStatus(songId, isFavorite)
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
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { addToQueue(song) }
            return
        }
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        val newQueue = info.queue + song
        // shuffleHistory.clear() is handled by PQM
        // _playbackInfo.value = info.copy(queue = newQueue) // Handled by reactive update
        persistPlaybackSnapshot(force = true)
        if (info.currentSong == null) {
            playSong(song, newQueue, 0)
        } else {
            queueManager.addToQueue(song, false) { s, q, i -> playSong(s, q, i) }
        }
    }

    fun playNext(song: Song) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { playNext(song) }
            return
        }
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        
        if (info.currentSong == null) {
            playSong(song, info.queue + song, 0)
        } else {
            queueManager.playNext(song, false, info.currentQueueIndex) { s, q, i -> playSong(s, q, i) }
        }
    }

    fun removeFromQueue(index: Int) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { removeFromQueue(index) }
            return
        }
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        val queue = info.queue.toMutableList()
        if (index !in queue.indices) return

        val removedCurrent = index == info.currentQueueIndex
        queueManager.removeFromQueue(index)
        
        persistPlaybackSnapshot(force = true)
        
        if (removedCurrent && queueManager.currentQueueIndex.value >= 0) {
            playCurrentQueueIndex(targetInstrumental = false)
        }
    }

    /** Remove every occurrence of a deleted library song from the active queue. */
    fun removeSongFromQueue(songId: String) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { removeSongFromQueue(songId) }
            return
        }
        val info = _playbackInfo.value
        val oldIndex = info.currentQueueIndex
        val currentWasRemoved = info.currentSong?.id == songId
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null

        queueManager.removeSongFromQueue(songId, info.queue, oldIndex)

        if (queueManager.queue.value.isEmpty()) {
            _player?.stop()
            _player?.clearMediaItems()
            isTransitioning = false
            releaseTransitionWakeLock()
            _playbackPositionMs.value = 0L
            _lyricsPositionMs.value = 0L
            clearPersistedPlaybackSnapshot()
            return
        }

        persistPlaybackSnapshot(force = true)

        if (currentWasRemoved && queueManager.currentQueueIndex.value >= 0) {
            _player?.pause()
            _playbackPositionMs.value = 0L
            _lyricsPositionMs.value = 0L
            playCurrentQueueIndex(targetInstrumental = false)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            coroutineScope.launch(Dispatchers.Main.immediate) { reorderQueue(fromIndex, toIndex) }
            return
        }
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        if (fromIndex in info.queue.indices && toIndex in info.queue.indices) {
            queueManager.reorderQueue(fromIndex, toIndex)
            persistPlaybackSnapshot(force = true)
        }
    }

    /** Stop account playback and discard its queue when the user signs out. */
    fun clearQueueAndStop() {
        playbackRequestId++
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        isTransitioning = false
        releaseTransitionWakeLock()
        _player?.stop()
        _player?.clearMediaItems()
        _playbackPositionMs.value = 0L
        _lyricsPositionMs.value = 0L
        queueManager.clearQueue()
        clearPersistedPlaybackSnapshot()
    }

    fun clearUpcomingQueue() {
        queueManager.keepOnlyCurrent()
        persistPlaybackSnapshot(force = true)
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
            persistPlaybackSnapshot()
        }
    }

    private fun persistPlaybackSnapshot(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSnapshotPersistAtMs < 5_000L) return
        val info = _playbackInfo.value
        if (info.currentSong == null || info.currentQueueIndex !in info.queue.indices) return
        lastSnapshotPersistAtMs = now
        val snapshot = PlaybackSnapshot(
            queue = info.queue,
            currentQueueIndex = info.currentQueueIndex,
            currentPositionMs = info.currentPositionMs.coerceAtLeast(0L),
            isShuffle = info.isShuffle,
            repeatMode = info.repeatMode,
            isInstrumental = info.isInstrumental,
            shuffleHistory = queueManager.queueState.value.let { state ->
                state.shuffleHistory.mapNotNull { entryId ->
                    state.entries.indexOfFirst { it.entryId == entryId }.takeIf { it >= 0 }
                }
            }
        )
        snapshotWriteJob?.cancel()
        snapshotWriteJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                snapshotRepository.saveSnapshot(snapshot)
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Failed to persist playback snapshot", e)
            }
        }
    }

    private fun clearPersistedPlaybackSnapshot() {
        snapshotWriteJob?.cancel()
        snapshotWriteJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                snapshotRepository.clearSnapshot()
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Failed to clear playback snapshot", e)
            }
        }
    }

    /** Wait until the persisted queue has either been restored or confirmed empty. */
    suspend fun awaitPlaybackSnapshotRestoration() {
        snapshotRestoreCompleted.await()
    }

    internal suspend fun restoreFromSnapshot(snapshot: PlaybackSnapshot): Boolean {
        val restoreRequestId = playbackRequestId
        if (snapshot.queue.isEmpty()) return false
        if (restoreRequestId != playbackRequestId) return false

        val originalIndex = snapshot.currentQueueIndex.coerceIn(snapshot.queue.indices)

        val resolvedEntries = mutableListOf<ResolvedQueueEntry>()
        for ((i, song) in snapshot.queue.withIndex()) {
            if (restoreRequestId != playbackRequestId) return false
            try {
                val wantInst = if (i == originalIndex) {
                    snapshot.isInstrumental && song.hasInstrumentalEffective
                } else {
                    song.isInstrumentalTrack || (snapshot.isInstrumental && song.hasInstrumentalEffective)
                }
                val mediaItem = buildMediaItem(song, wantInst)
                resolvedEntries.add(
                    ResolvedQueueEntry(
                        song = song,
                        mediaItem = mediaItem,
                        isOriginalCurrent = (i == originalIndex),
                        isInstrumental = wantInst
                    )
                )
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Failed to resolve track ${song.id} in snapshot", e)
            }
        }

        if (resolvedEntries.isEmpty()) {
            Log.w("PlaybackManager", "No tracks in saved queue could be resolved")
            return false
        }

        val targetEntryIndex = resolvedEntries.indexOfFirst { it.isOriginalCurrent }.let { idx ->
            if (idx >= 0) idx else 0
        }
        val activeEntry = resolvedEntries[targetEntryIndex]
        val restoredPositionMs = if (activeEntry.isOriginalCurrent) {
            snapshot.currentPositionMs.coerceAtLeast(0L)
        } else {
            0L
        }

        withContext(Dispatchers.Main.immediate) {
            if (restoreRequestId != playbackRequestId) return@withContext
            if (_playbackInfo.value.currentSong != null || _playbackInfo.value.queue.isNotEmpty()) return@withContext

            val validQueue = resolvedEntries.map { it.song }
            val mediaItems = resolvedEntries.map { it.mediaItem }

            val historyIndices = snapshot.shuffleHistory
                .filter { it in snapshot.queue.indices }
                .mapNotNull { origIdx ->
                    val origSong = snapshot.queue.getOrNull(origIdx) ?: return@mapNotNull null
                    validQueue.indexOfFirst { it.id == origSong.id }.takeIf { it >= 0 }
                }
            queueManager.restoreShuffleHistory(historyIndices)

            val p = ensurePlayerInitialized()
            p.repeatMode = when (snapshot.repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
            p.shuffleModeEnabled = snapshot.isShuffle

            p.setMediaItems(mediaItems, targetEntryIndex, restoredPositionMs)
            p.prepare()
            p.playWhenReady = false

            _playbackInfo.value = PlaybackInfo(
                currentSong = activeEntry.song,
                currentPositionMs = restoredPositionMs,
                queue = validQueue,
                currentQueueIndex = targetEntryIndex,
                isShuffle = snapshot.isShuffle,
                repeatMode = snapshot.repeatMode,
                hasInstrumental = activeEntry.song.hasInstrumentalEffective,
                isInstrumental = activeEntry.isInstrumental
            )
            queueManager.updateQueueState(validQueue, targetEntryIndex)
            _playbackPositionMs.value = restoredPositionMs
            _lyricsPositionMs.value = restoredPositionMs
            updatePlaybackInfo()

            Log.i(
                "PlaybackManager",
                "Restored ${mediaItems.size} queued tracks into Media3 player timeline at index $targetEntryIndex"
            )
        }
        return true
    }

    private fun restorePlaybackSnapshot() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val snapshot = try {
                    snapshotRepository.loadSnapshot()
                } catch (e: Exception) {
                    Log.w("PlaybackManager", "Failed to load playback snapshot", e)
                    null
                }
                if (snapshot != null) {
                    restoreFromSnapshot(snapshot)
                }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error during playback snapshot restoration", e)
            } finally {
                snapshotRestoreCompleted.complete(Unit)
            }
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
        persistPlaybackSnapshot(force = true)
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

private data class ResolvedQueueEntry(
    val song: Song,
    val mediaItem: MediaItem,
    val isOriginalCurrent: Boolean,
    val isInstrumental: Boolean
)
