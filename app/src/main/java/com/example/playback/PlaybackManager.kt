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
import com.example.BuildConfig
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

    private var transitionRequestId: Long = 0L
    private var _player: ExoPlayer? = null
    internal var playbackRequestId: Long = 0L
    internal var playbackJob: kotlinx.coroutines.Job? = null
    private var networkRecoveryJob: kotlinx.coroutines.Job? = null
    private var networkRecoveryAttempts: Int = 0
    private var networkRecoverySongId: String? = null
    internal var androidAutoQueueSyncJob: kotlinx.coroutines.Job? = null
    private var androidAutoQueueSyncVersion: Long = 0L
    private val shuffleHistory = mutableListOf<Int>()
    private var lastSnapshotPersistAtMs = 0L
    private var snapshotWriteJob: kotlinx.coroutines.Job? = null
    private val snapshotRestoreCompleted = CompletableDeferred<Unit>()

    private companion object {
        const val MAX_NETWORK_RECOVERY_ATTEMPTS = 3
        const val NETWORK_RECOVERY_BASE_DELAY_MS = 1_000L
    }

    val isPlayerInitialized: Boolean
        get() = _player != null

    val isRestoringSnapshot: Boolean
        get() = !snapshotRestoreCompleted.isCompleted

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
            setRepeatMode(
                when (_playbackInfo.value.repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
            )
            setShuffleModeEnabled(_playbackInfo.value.isShuffle)
            addListener(playerListener)
        }
        _player = newPlayer
        return newPlayer
    }

    internal val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    if (BuildConfig.DEBUG) Log.d("PlaybackManager", "Song ended, checking queue...")
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
            handleMediaItemTransition(mediaItem, reason)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
            if (_playbackInfo.value.repeatMode != mapped) {
                _playbackInfo.value = _playbackInfo.value.copy(repeatMode = mapped)
                persistPlaybackSnapshot(force = true)
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (_playbackInfo.value.isShuffle != shuffleModeEnabled) {
                if (!shuffleModeEnabled) shuffleHistory.clear()
                _playbackInfo.value = _playbackInfo.value.copy(isShuffle = shuffleModeEnabled)
                persistPlaybackSnapshot(force = true)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && isTransitioning) {
                val p = _player
                val currentMediaId = p?.currentMediaItem?.mediaId?.let { extractBaseSongId(it) }
                val targetSongId = _playbackInfo.value.currentSong?.id
                if (currentMediaId == targetSongId && transitionRequestId == playbackRequestId) {
                    isTransitioning = false
                    releaseTransitionWakeLock()
                }
            }
            updatePlaybackInfo()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlaybackManager", "ExoPlayer error occurred: ${error.message}", error)
            if (isTransitioning && transitionRequestId == playbackRequestId) {
                isTransitioning = false
            }
            handlePlayerError(error)
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
            if (BuildConfig.DEBUG) Log.d("PlaybackManager", "Acquired transition WakeLock for ${timeoutMs}ms")
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to acquire transition WakeLock", e)
        }
    }

    fun releaseTransitionWakeLock() {
        try {
            if (transitionWakeLock?.isHeld == true) {
                transitionWakeLock?.release()
                if (BuildConfig.DEBUG) Log.d("PlaybackManager", "Released transition WakeLock")
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to release transition WakeLock", e)
        }
    }

    fun handlePlayerError(error: PlaybackException) {
        if (isTransitioning && transitionRequestId == playbackRequestId) {
            isTransitioning = false
            releaseTransitionWakeLock()
        }
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
                    "Playback error code=${error.errorCode}; retry ${attempt}/${MAX_NETWORK_RECOVERY_ATTEMPTS} in ${delayMs}ms"
                )
                networkRecoveryJob = coroutineScope.launch {
                    delay(delayMs)
                    val latest = _playbackInfo.value
                    if (requestIdAtFailure != playbackRequestId ||
                        latest.currentSong?.id != songIdAtFailure ||
                        latest.currentQueueIndex !in latest.queue.indices
                    ) {
                        if (BuildConfig.DEBUG) Log.d("PlaybackManager", "Skipping stale recovery for song $songIdAtFailure")
                        return@launch
                    }
                    networkRecoveryJob = null
                    withContext(Dispatchers.Main.immediate) {
                        isTransitioning = true
                        acquireTransitionWakeLock()
                        if (isDecoderOrResourceError) {
                            try {
                                _player?.release()
                            } catch (e: Exception) {
                                // ignore
                            }
                            _player = null
                            ensurePlayerInitialized()
                        }
                        val currentPos = _playbackPositionMs.value.coerceAtLeast(0L)
                        playCurrentQueueIndex(targetInstrumental = latest.isInstrumental, initialPositionMs = currentPos)
                    }
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
        
        shuffleHistory.clear()
        _playbackInfo.value = _playbackInfo.value.copy(
            queue = finalQueue,
            currentQueueIndex = validIndex,
            currentSong = finalQueue[validIndex],
            currentPositionMs = 0L,
            hasInstrumental = finalQueue[validIndex].hasInstrumentalEffective,
            isInstrumental = _playbackInfo.value.isInstrumental
        )
        persistPlaybackSnapshot(force = true)
        playCurrentQueueIndex()
    }

    internal fun extractBaseSongId(mediaId: String): String {
        val trimmed = mediaId.trim()
        val fromAuto = androidAutoSongId(trimmed)
        val raw = fromAuto ?: if (trimmed.contains("|")) trimmed.substringAfterLast("|") else trimmed
        return raw.removeSuffix("_inst")
    }

    /**
     * Resolve a Media3 timeline item to its logical app-queue occurrence.
     * Media item indices are timeline indices, not necessarily app queue indices;
     * matching by ID also handles Android Auto-prefixed and instrumental IDs.
     * The occurrence count disambiguates repeated instances of the same song.
     */
    internal fun findQueueIndexForMediaItem(mediaId: String, timelineIndex: Int): Int {
        val player = _player ?: return -1
        val queue = _playbackInfo.value.queue
        if (timelineIndex !in 0 until player.mediaItemCount) return -1

        val targetSongId = extractBaseSongId(mediaId)
        if (targetSongId.isBlank()) return -1

        val occurrence = (0 until timelineIndex).count { index ->
            extractBaseSongId(player.getMediaItemAt(index).mediaId) == targetSongId
        }
        return queue.indices
            .filter { queue[it].id == targetSongId }
            .getOrNull(occurrence) ?: -1
    }

    /**
     * Media3 applies Android Auto selections directly to the session player after
     * onAddMediaItems resolves their URIs. Mirror that resulting timeline into the
     * app queue so Yimly's controls, UI and next/previous logic use the same songs.
     */
    internal fun syncAndroidAutoQueueFromPlayer(currentMediaItem: MediaItem?) {
        val player = _player ?: return
        val currentMediaId = currentMediaItem?.mediaId ?: return
        if (!isAndroidAutoMediaId(currentMediaId)) return

        val timelineItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        // Keep null placeholders while resolving so missing library entries do not
        // shift the active index or cause a duplicate song ID to select the wrong row.
        val songIds = timelineItems.map { androidAutoSongId(it.mediaId) }
        if (songIds.none { it != null }) return

        val userPrefInstrumental = _playbackInfo.value.isInstrumental
        val syncVersion = ++androidAutoQueueSyncVersion
        val syncRequestId = playbackRequestId
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val resolvedSongs = songIds.map { id -> id?.let { musicRepository.getSongById(it) } }
                val mediaIndex = withContext(Dispatchers.Main) { _player?.currentMediaItemIndex } ?: return@launch
                val activeSong = resolvedSongs.getOrNull(mediaIndex)
                val effectiveWantInst = activeSong != null && (activeSong.isInstrumentalTrack || (userPrefInstrumental && activeSong.hasInstrumentalEffective))
                val expectedActiveMediaItem = if (activeSong != null) {
                    buildMediaItem(activeSong, effectiveWantInst)
                } else null

                withContext(Dispatchers.Main) {
                    if (syncVersion != androidAutoQueueSyncVersion || syncRequestId != playbackRequestId) return@withContext
                    val p = _player ?: return@withContext
                    val activeId = p.currentMediaItem?.mediaId ?: return@withContext
                    val activeSongId = androidAutoSongId(activeId) ?: return@withContext
                    val currentMediaIndex = p.currentMediaItemIndex
                    val currentActiveSong = resolvedSongs.getOrNull(currentMediaIndex) ?: return@withContext
                    if (currentActiveSong.id != activeSongId) return@withContext
                    val activeQueueIndex = resolvedSongs.take(currentMediaIndex).count { it != null }
                    val songs = resolvedSongs.filterNotNull()
                    if (songs.isEmpty()) return@withContext

                    // Verify that the actual playing Media3 item has the correct audio variant URI
                    if (expectedActiveMediaItem != null && currentMediaIndex in 0 until p.mediaItemCount) {
                        val currentItem = p.getMediaItemAt(currentMediaIndex)
                        val currentUri = currentItem.localConfiguration?.uri ?: currentItem.requestMetadata.mediaUri
                        val expectedUri = expectedActiveMediaItem.localConfiguration?.uri ?: expectedActiveMediaItem.requestMetadata.mediaUri
                        if (currentUri != expectedUri) {
                            p.replaceMediaItem(currentMediaIndex, expectedActiveMediaItem)
                        }
                    }

                    shuffleHistory.clear()
                    _playbackInfo.value = _playbackInfo.value.copy(
                        queue = songs,
                        currentQueueIndex = activeQueueIndex,
                        currentSong = currentActiveSong,
                        currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                        hasInstrumental = currentActiveSong.hasInstrumentalEffective,
                        isInstrumental = userPrefInstrumental
                    )
                    updatePlaybackInfo()
                    persistPlaybackSnapshot(force = true)
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "PlaybackManager",
                            "Synced Android Auto queue: ${songs.size} songs, activeIndex=${activeQueueIndex}, active=${currentActiveSong.id}, isInstrumental=${userPrefInstrumental}"
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Failed to sync Android Auto queue", e)
            }
        }
    }

    internal fun isAndroidAutoMediaId(mediaId: String): Boolean =
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

    fun playQueueItem(index: Int, initialPositionMs: Long? = null) {
        val info = _playbackInfo.value
        if (index in info.queue.indices) {
            androidAutoQueueSyncVersion++
            androidAutoQueueSyncJob?.cancel()
            androidAutoQueueSyncJob = null
            ensurePlayerInitialized()
            startPlaybackService()
            val currentInst = info.isInstrumental
            _playbackInfo.value = info.copy(
                currentQueueIndex = index,
                currentSong = info.queue[index],
                currentPositionMs = initialPositionMs ?: 0L,
                hasInstrumental = info.queue[index].hasInstrumentalEffective,
                isInstrumental = currentInst
            )
            shuffleHistory.clear()
            persistPlaybackSnapshot(force = true)
            playCurrentQueueIndex(targetInstrumental = currentInst, initialPositionMs = initialPositionMs)
        }
    }

    fun next(manual: Boolean = true) {
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
                if (transitionRequestId == playbackRequestId) {
                    isTransitioning = false
                    releaseTransitionWakeLock()
                }
                return
            }
        }
        
        if (info.isShuffle && nextIndex != info.currentQueueIndex && info.currentQueueIndex in info.queue.indices) {
            shuffleHistory.add(info.currentQueueIndex)
        }
        val currentInst = info.isInstrumental
        _playbackInfo.value = info.copy(
            currentQueueIndex = nextIndex,
            currentSong = info.queue[nextIndex],
            currentPositionMs = 0L,
            hasInstrumental = info.queue[nextIndex].hasInstrumentalEffective,
            isInstrumental = currentInst
        )
        persistPlaybackSnapshot(force = true)
        playCurrentQueueIndex()
    }

    fun previous() {
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

        val prevIndex = if (info.isShuffle && shuffleHistory.isNotEmpty()) {
            shuffleHistory.removeAt(shuffleHistory.lastIndex)
        } else if (info.currentQueueIndex - 1 >= 0) {
            info.currentQueueIndex - 1
        } else {
            info.queue.size - 1
        }

        val currentInst = info.isInstrumental
        _playbackInfo.value = info.copy(
            currentQueueIndex = prevIndex,
            currentSong = info.queue[prevIndex],
            currentPositionMs = 0L,
            hasInstrumental = info.queue[prevIndex].hasInstrumentalEffective,
            isInstrumental = currentInst
        )
        persistPlaybackSnapshot(force = true)
        playCurrentQueueIndex()
    }

    internal fun handleMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem == null) return
        val currentMediaId = mediaItem.mediaId

        if (isAndroidAutoMediaId(currentMediaId)) {
            syncAndroidAutoQueueFromPlayer(mediaItem)
        }

        val songId = extractBaseSongId(currentMediaId).takeIf { it.isNotBlank() } ?: return

        val info = _playbackInfo.value
        val queue = info.queue
        if (queue.isEmpty()) return

        val p = _player
        val currentMediaIndex = p?.currentMediaItemIndex ?: -1
        val matchedIndex = if (currentMediaIndex in queue.indices && queue[currentMediaIndex].id == songId) {
            currentMediaIndex
        } else {
            queue.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
        } ?: return

        val newSong = queue[matchedIndex]
        val keepInstrumental = info.isInstrumental

        if (info.currentQueueIndex != matchedIndex || info.currentSong?.id != newSong.id || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            val newPos = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) 0L else (p?.currentPosition?.coerceAtLeast(0L) ?: 0L)
            _playbackInfo.value = info.copy(
                currentQueueIndex = matchedIndex,
                currentSong = newSong,
                currentPositionMs = newPos,
                hasInstrumental = newSong.hasInstrumentalEffective,
                isInstrumental = keepInstrumental
            )
            _playbackPositionMs.value = newPos
            _lyricsPositionMs.value = newPos
            updatePlaybackInfo()
            persistPlaybackSnapshot()
            coroutineScope.launch(Dispatchers.IO) {
                musicRepository.recordPlayHistory(newSong.id)
            }
            if (BuildConfig.DEBUG) Log.d("PlaybackManager", "Transitioned to ${newSong.title} (index $matchedIndex, reason $reason)")
        }
    }

    private fun handleSongEnded() {
        val p = _player
        val info = _playbackInfo.value
        val hasMultiItemTimeline = p != null && p.mediaItemCount > 1 && p.mediaItemCount == info.queue.size
        if (hasMultiItemTimeline && info.repeatMode == RepeatMode.OFF) {
            // When timeline contains all items and repeat is OFF, STATE_ENDED means the queue has completed naturally.
            // Do not call next() to avoid duplicate/unexpected advances.
            if (transitionRequestId == playbackRequestId) {
                isTransitioning = false
                releaseTransitionWakeLock()
            }
            updatePlaybackInfo()
            return
        }

        if (isTransitioning) {
            if (BuildConfig.DEBUG) Log.d("PlaybackManager", "handleSongEnded ignored: already transitioning")
            return
        }
        isTransitioning = true
        acquireTransitionWakeLock()
        next(manual = false)
    }

    private fun isQueueMatchingPlayerTimeline(player: Player, queue: List<Song>): Boolean {
        if (player.mediaItemCount != queue.size) return false
        for (i in queue.indices) {
            val itemSongId = extractBaseSongId(player.getMediaItemAt(i).mediaId)
            if (itemSongId != queue[i].id) {
                return false
            }
        }
        return true
    }

    internal suspend fun buildMediaItem(song: Song, wantInstrumental: Boolean): MediaItem {
        val effectiveWantInst = song.isInstrumentalTrack || (wantInstrumental && song.hasInstrumentalEffective)
        val resolvedTrack = musicRepository.resolvePlayableTrack(song, effectiveWantInst)
        val audioUrl = resolvedTrack.playableUrl
        val parsedAudioUri = Uri.parse(audioUrl)
        val artworkUri = getResolvedArtworkUrl(song)?.let { Uri.parse(it) }

        return MediaItem.Builder()
            .setMediaId(if (effectiveWantInst) "${song.id}_inst" else song.id)
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

    internal fun playCurrentQueueIndex(
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
        transitionRequestId = requestId
        isTransitioning = true
        acquireTransitionWakeLock()

        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            var handedOffToPlayer = false
            try {
                if (requestId != playbackRequestId) {
                    return@launch
                }

                val currentQueue = info.queue
                val targetIndex = info.currentQueueIndex

                val timelineMatches = withContext(Dispatchers.Main) {
                    val p = _player
                    p != null && isQueueMatchingPlayerTimeline(p, currentQueue)
                }

                if (requestId != playbackRequestId) {
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

                withContext(Dispatchers.Main) {
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
                if (requestId == playbackRequestId && transitionRequestId == requestId) {
                    isTransitioning = false
                    releaseTransitionWakeLock()
                }
                throw e
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error playing song: ${song.title}", e)
                if (requestId == playbackRequestId && transitionRequestId == requestId) {
                    isTransitioning = false
                    releaseTransitionWakeLock()
                }
            } finally {
                if (requestId == playbackRequestId && transitionRequestId == requestId && !handedOffToPlayer) {
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
        val requestId = ++playbackRequestId
        transitionRequestId = requestId
        isTransitioning = true
        coroutineScope.launch {
            try {
                awaitPlaybackSnapshotRestoration()
                if (requestId != playbackRequestId) return@launch

                // PlaybackManager may be constructed with a background coroutine scope.
                // Keep every ExoPlayer access (including timeline/state reads) on its
                // application thread, even when this coroutine resumes on Dispatchers.Default.
                withContext(Dispatchers.Main.immediate) {
                    if (requestId != playbackRequestId) return@withContext

                    val p = ensurePlayerInitialized()
                    val info = _playbackInfo.value

                    // Do not start the service or play if the queue is empty after restoration.
                    if (p.mediaItemCount == 0 && info.queue.isEmpty()) {
                        return@withContext
                    }

                    startPlaybackService()

                    // If the player timeline is empty but the logical queue is non-empty,
                    // populate the player before attempting to play.
                    if (p.mediaItemCount == 0 && info.queue.isNotEmpty()) {
                        playCurrentQueueIndex(autoPlay = true)
                        return@withContext
                    }

                    if (p.playbackState == Player.STATE_ENDED) {
                        p.seekTo(0)
                    } else if (p.playbackState == Player.STATE_IDLE) {
                        p.prepare()
                    }
                    p.play()
                    updatePlaybackInfo()
                    persistPlaybackSnapshot(force = true)
                }
            } finally {
                if (requestId == playbackRequestId && transitionRequestId == requestId) {
                    isTransitioning = false
                }
            }
        }
    }

    fun pause() {
        playbackRequestId++
        transitionRequestId = 0L
        isTransitioning = false
        releaseTransitionWakeLock()
        _player?.pause()
        updatePlaybackInfo()
        persistPlaybackSnapshot(force = true)
    }

    fun seekTo(positionMs: Long) {
        ensurePlayerInitialized().seekTo(positionMs)
        _playbackPositionMs.value = positionMs
        _lyricsPositionMs.value = positionMs
        _playbackInfo.value = _playbackInfo.value.copy(currentPositionMs = positionMs)
        persistPlaybackSnapshot(force = true)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _player?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        _playbackInfo.value = _playbackInfo.value.copy(repeatMode = mode)
        persistPlaybackSnapshot(force = true)
    }

    fun cycleRepeatMode() {
        val current = _playbackInfo.value.repeatMode
        val next = current.next()
        setRepeatMode(next)
    }

    fun setShuffle(enabled: Boolean) {
        if (!enabled) shuffleHistory.clear()
        _player?.shuffleModeEnabled = enabled
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
        shuffleHistory.clear()
        _playbackInfo.value = info.copy(
            isShuffle = true,
            currentQueueIndex = nextIndex,
            currentSong = nextSong,
            currentPositionMs = 0L,
            hasInstrumental = nextSong.hasInstrumentalEffective,
            isInstrumental = useInstrumental
        )
        persistPlaybackSnapshot(force = true)
        playCurrentQueueIndex(targetInstrumental = useInstrumental)
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
        playbackRequestId++
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        val newQueue = info.queue + song
        shuffleHistory.clear()
        if (info.currentSong == null) {
            // The newly appended occurrence is the item the user just requested,
            // even when the same song ID already exists earlier in the queue.
            val playIndex = newQueue.lastIndex
            playSong(song, newQueue, playIndex)
        } else {
            _playbackInfo.value = info.copy(queue = newQueue)
            persistPlaybackSnapshot(force = true)
            val syncVer = androidAutoQueueSyncVersion
            val syncReqId = playbackRequestId
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val wantInst = song.isInstrumentalTrack || (info.isInstrumental && song.hasInstrumentalEffective)
                    val mediaItem = buildMediaItem(song, wantInst)
                    withContext(Dispatchers.Main) {
                        if (syncVer != androidAutoQueueSyncVersion || syncReqId != playbackRequestId) return@withContext
                        val p = _player ?: return@withContext
                        if (p.mediaItemCount == newQueue.size - 1) {
                            p.addMediaItem(mediaItem)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PlaybackManager", "Failed to add media item to player timeline", e)
                }
            }
        }
    }

    fun playNext(song: Song) {
        playbackRequestId++
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        val queue = info.queue.toMutableList()
        val insertIndex = if (info.currentQueueIndex in queue.indices) info.currentQueueIndex + 1 else queue.size
        queue.add(insertIndex, song)
        shuffleHistory.clear()
        if (info.currentSong == null) {
            playSong(song, queue, insertIndex)
        } else {
            _playbackInfo.value = info.copy(queue = queue)
            persistPlaybackSnapshot(force = true)
            val syncVer = androidAutoQueueSyncVersion
            val syncReqId = playbackRequestId
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val wantInst = song.isInstrumentalTrack || (info.isInstrumental && song.hasInstrumentalEffective)
                    val mediaItem = buildMediaItem(song, wantInst)
                    withContext(Dispatchers.Main) {
                        if (syncVer != androidAutoQueueSyncVersion || syncReqId != playbackRequestId) return@withContext
                        val p = _player ?: return@withContext
                        if (p.mediaItemCount == queue.size - 1 && insertIndex <= p.mediaItemCount) {
                            p.addMediaItem(insertIndex, mediaItem)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PlaybackManager", "Failed to insert media item into player timeline", e)
                }
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val info = _playbackInfo.value
        if (index !in info.queue.indices) return

        // Only a valid mutation may invalidate in-flight playback or AA sync work.
        playbackRequestId++
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val queue = info.queue.toMutableList()

        val wasPlaying = info.isPlaying || (_player?.isPlaying == true)
        val userPrefInstrumental = info.isInstrumental
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
                isInstrumental = userPrefInstrumental,
                hasInstrumental = false
            )
            clearPersistedPlaybackSnapshot()
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
            currentPositionMs = if (removedCurrent) 0L else info.currentPositionMs,
            hasInstrumental = newSong?.hasInstrumentalEffective ?: false,
            isInstrumental = userPrefInstrumental
        )

        shuffleHistory.clear()
        persistPlaybackSnapshot(force = true)

        val p = _player
        if (p != null && index < p.mediaItemCount && p.mediaItemCount == queue.size + 1) {
            p.removeMediaItem(index)
        }

        if (removedCurrent && newSong != null) {
            _playbackPositionMs.value = 0L
            _lyricsPositionMs.value = 0L
            playCurrentQueueIndex(
                targetInstrumental = userPrefInstrumental,
                autoPlay = wasPlaying,
                initialPositionMs = 0L
            )
        }
    }

    /** Remove every occurrence of a deleted library song from the active queue. */
    fun removeSongFromQueue(songId: String) {
        val targetSongId = extractBaseSongId(songId)
        if (targetSongId.isBlank()) return

        val info = _playbackInfo.value
        val matchingIndices = info.queue.mapIndexedNotNull { index, song ->
            if (song.id == targetSongId) index else null
        }
        if (matchingIndices.isEmpty()) return

        // Do not cancel an unrelated playback request for a song that is not queued.
        val oldIndex = info.currentQueueIndex
        val wasPlaying = info.isPlaying || (_player?.isPlaying == true)
        val userPrefInstrumental = info.isInstrumental
        playbackRequestId++
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null

        val currentWasRemoved = (oldIndex in matchingIndices) || (info.currentSong?.id == targetSongId)
        val removedBeforeCurrent = matchingIndices.count { it < oldIndex }
        val newQueue = info.queue.filterIndexed { index, _ -> index !in matchingIndices }

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
                durationMs = 0L, isInstrumental = userPrefInstrumental, hasInstrumental = false
            )
            clearPersistedPlaybackSnapshot()
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
            isInstrumental = userPrefInstrumental
        )

        shuffleHistory.clear()
        persistPlaybackSnapshot(force = true)

        val p = _player
        if (p != null) {
            if (p.mediaItemCount == info.queue.size) {
                matchingIndices.sortedDescending().forEach { idx ->
                    if (idx < p.mediaItemCount) {
                        p.removeMediaItem(idx)
                    }
                }
            } else {
                val playerMatchingIndices = (0 until p.mediaItemCount).filter { idx ->
                    extractBaseSongId(p.getMediaItemAt(idx).mediaId) == targetSongId
                }.sortedDescending()
                playerMatchingIndices.forEach { idx ->
                    p.removeMediaItem(idx)
                }
            }
        }

        if (currentWasRemoved && newSong != null) {
            _playbackPositionMs.value = 0L
            _lyricsPositionMs.value = 0L
            playCurrentQueueIndex(
                targetInstrumental = userPrefInstrumental,
                autoPlay = wasPlaying,
                initialPositionMs = 0L
            )
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val info = _playbackInfo.value
        val queue = info.queue
        if (fromIndex !in queue.indices || toIndex !in queue.indices) {
            return
        }
        playbackRequestId++
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val mutableQueue = queue.toMutableList()
        val item = mutableQueue.removeAt(fromIndex)
        mutableQueue.add(toIndex, item)
        val oldIndex = info.currentQueueIndex
        val newIndex = when {
            oldIndex !in queue.indices -> oldIndex
            fromIndex == oldIndex -> toIndex
            fromIndex < oldIndex && toIndex >= oldIndex -> oldIndex - 1
            fromIndex > oldIndex && toIndex <= oldIndex -> oldIndex + 1
            else -> oldIndex
        }
        _playbackInfo.value = info.copy(
            queue = mutableQueue,
            currentQueueIndex = newIndex,
            currentSong = mutableQueue.getOrNull(newIndex)
        )
        shuffleHistory.clear()
        persistPlaybackSnapshot(force = true)

        val p = _player
        if (p != null && fromIndex < p.mediaItemCount && toIndex < p.mediaItemCount && p.mediaItemCount == mutableQueue.size) {
            p.moveMediaItem(fromIndex, toIndex)
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
        _playbackInfo.value = PlaybackInfo(currentQueueIndex = -1)
        clearPersistedPlaybackSnapshot()
    }

    fun clearUpcomingQueue() {
        playbackRequestId++
        androidAutoQueueSyncVersion++
        androidAutoQueueSyncJob?.cancel()
        androidAutoQueueSyncJob = null
        val info = _playbackInfo.value
        val currentSong = info.currentSong
        if (currentSong != null) {
            val activeIndex = info.currentQueueIndex
            _playbackInfo.value = info.copy(
                queue = listOf(currentSong),
                currentQueueIndex = 0
            )
            shuffleHistory.clear()
            persistPlaybackSnapshot(force = true)

            val p = _player
            if (p != null && activeIndex in 0 until p.mediaItemCount) {
                if (activeIndex + 1 < p.mediaItemCount) {
                    p.removeMediaItems(activeIndex + 1, p.mediaItemCount)
                }
                if (activeIndex > 0) {
                    p.removeMediaItems(0, activeIndex)
                }
            }
        } else {
            _playbackInfo.value = info.copy(
                queue = emptyList(),
                currentQueueIndex = -1
            )
            _player?.clearMediaItems()
            clearPersistedPlaybackSnapshot()
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
            shuffleHistory = shuffleHistory.toList()
        )
        snapshotWriteJob?.cancel()
        snapshotWriteJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                preferencesManager.savePlaybackSnapshot(snapshot)
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Failed to persist playback snapshot", e)
            }
        }
    }

    private fun clearPersistedPlaybackSnapshot() {
        snapshotWriteJob?.cancel()
        snapshotWriteJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                preferencesManager.clearPlaybackSnapshot()
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
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

        withContext(Dispatchers.Main) {
            if (restoreRequestId != playbackRequestId) return@withContext
            if (_playbackInfo.value.currentSong != null || _playbackInfo.value.queue.isNotEmpty()) return@withContext

            val validQueue = resolvedEntries.map { it.song }
            val mediaItems = resolvedEntries.map { it.mediaItem }

            shuffleHistory.clear()
            shuffleHistory.addAll(
                snapshot.shuffleHistory
                    .filter { it in snapshot.queue.indices }
                    .mapNotNull { origIdx ->
                        val origSong = snapshot.queue.getOrNull(origIdx) ?: return@mapNotNull null
                        validQueue.indexOfFirst { it.id == origSong.id }.takeIf { it >= 0 }
                    }
            )

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
                isInstrumental = snapshot.isInstrumental
            )
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
                    preferencesManager.loadPlaybackSnapshot()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Log.w("PlaybackManager", "Failed to load playback snapshot", e)
                    null
                }
                if (snapshot != null) {
                    restoreFromSnapshot(snapshot)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
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
