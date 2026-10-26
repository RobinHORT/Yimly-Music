package com.example.playback

import android.app.Service
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
import com.example.YimlyApplication
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

    val isPlayerInitialized: Boolean
        get() = _player != null

    val player: ExoPlayer?
        get() = _player

    fun ensurePlayerInitialized(): ExoPlayer {
        _player?.let { return it }
        val newPlayer = ExoPlayer.Builder(context).build().apply {
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
                            releaseTransitionWakeLock()
                        }
                        else -> {}
                    }
                    updatePlaybackInfo()
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
        val validIndex = if (startIndex in finalQueue.indices) startIndex else 0
        
        _playbackInfo.value = _playbackInfo.value.copy(
            queue = finalQueue,
            currentQueueIndex = validIndex,
            currentSong = finalQueue[validIndex],
            hasInstrumental = finalQueue[validIndex].hasInstrumentalEffective,
            isInstrumental = false
        )
        playCurrentQueueIndex()
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

    fun next() {
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) {
            isTransitioning = false
            releaseTransitionWakeLock()
            return
        }
        
        val nextIndex = if (info.isShuffle) {
            info.queue.indices.random()
        } else {
            when (info.repeatMode) {
                RepeatMode.ONE -> info.currentQueueIndex
                RepeatMode.ALL -> (info.currentQueueIndex + 1) % info.queue.size
                RepeatMode.OFF -> {
                    if (info.currentQueueIndex + 1 < info.queue.size) {
                        info.currentQueueIndex + 1
                    } else {
                        isTransitioning = false
                        releaseTransitionWakeLock()
                        return
                    }
                }
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
        next()
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
        
        coroutineScope.launch(Dispatchers.Main) {
            try {
                acquireTransitionWakeLock()
                val resolvedTrack = musicRepository.resolvePlayableTrack(song, wantInst)
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
                _playbackInfo.value = _playbackInfo.value.copy(
                    isInstrumental = wantInst,
                    currentSong = song,
                    hasInstrumental = if (!wantInst && targetInstrumental == false && !song.hasInstrumentalEffective) false else song.hasInstrumentalEffective
                )
                updatePlaybackInfo()
            } catch (e: kotlinx.coroutines.CancellationException) {
                isTransitioning = false
                releaseTransitionWakeLock()
                throw e
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error playing song: ${song.title}", e)
                isTransitioning = false
                releaseTransitionWakeLock()
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
        coroutineScope.launch(Dispatchers.IO) {
            musicRepository.toggleFavorite(songId)
        }
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

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val info = _playbackInfo.value
        val queue = info.queue.toMutableList()
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            val item = queue.removeAt(fromIndex)
            queue.add(toIndex, item)
            var newIndex = info.currentQueueIndex
            if (info.currentSong != null) {
                newIndex = queue.indexOf(info.currentSong)
            }
            _playbackInfo.value = info.copy(queue = queue, currentQueueIndex = newIndex)
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
