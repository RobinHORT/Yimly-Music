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

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setWakeMode(C.WAKE_MODE_LOCAL)
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
                        releaseTransitionWakeLock()
                    }
                    else -> {}
                }
                updatePlaybackInfo()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    releaseTransitionWakeLock()
                }
                updatePlaybackInfo()
            }
        })
    }

    val mediaSession: MediaLibrarySession by lazy {
        MediaLibrarySession.Builder(
            (context as? Service) ?: (context.applicationContext as? Service) ?: (context.applicationContext as? YimlyApplication ?: context) as Service,
            YimlyForwardingPlayer(player, this),
            YimlySessionCallback(this)
        ).build()
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

    private fun acquireTransitionWakeLock() {
        try {
            if (transitionWakeLock == null) {
                transitionWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "YimlyMusic::TransitionWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (transitionWakeLock?.isHeld == false) {
                transitionWakeLock?.acquire(5000L)
                Log.d("PlaybackManager", "Acquired transition WakeLock")
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to acquire transition WakeLock", e)
        }
    }

    private fun releaseTransitionWakeLock() {
        try {
            if (transitionWakeLock?.isHeld == true) {
                transitionWakeLock?.release()
                Log.d("PlaybackManager", "Released transition WakeLock")
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to release transition WakeLock", e)
        }
    }

    fun getResolvedArtworkUrl(song: Song): String? {
        return song.artworkUrl
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song), startIndex: Int = 0) {
        startPlaybackService()
        val finalQueue = if (queue.isEmpty()) listOf(song) else queue
        val validIndex = if (startIndex in finalQueue.indices) startIndex else 0
        
        _playbackInfo.value = _playbackInfo.value.copy(
            queue = finalQueue,
            currentQueueIndex = validIndex,
            currentSong = finalQueue[validIndex],
            isInstrumental = finalQueue[validIndex].isInstrumental
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
            startPlaybackService()
            _playbackInfo.value = info.copy(
                currentQueueIndex = index,
                currentSong = info.queue[index],
                isInstrumental = info.queue[index].isInstrumental
            )
            playCurrentQueueIndex()
        }
    }

    fun next() {
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) return
        
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
                        return
                    }
                }
            }
        }
        
        _playbackInfo.value = info.copy(
            currentQueueIndex = nextIndex,
            currentSong = info.queue[nextIndex],
            isInstrumental = info.queue[nextIndex].isInstrumental
        )
        playCurrentQueueIndex()
    }

    fun previous() {
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) return
        
        if (player.currentPosition > 3000L) {
            player.seekTo(0)
            return
        }

        val prevIndex = if (info.currentQueueIndex - 1 >= 0) {
            info.currentQueueIndex - 1
        } else {
            info.queue.size - 1
        }

        _playbackInfo.value = info.copy(
            currentQueueIndex = prevIndex,
            currentSong = info.queue[prevIndex],
            isInstrumental = info.queue[prevIndex].isInstrumental
        )
        playCurrentQueueIndex()
    }

    private fun handleSongEnded() {
        acquireTransitionWakeLock()
        next()
    }

    private fun playCurrentQueueIndex() {
        val info = _playbackInfo.value
        if (info.currentQueueIndex !in info.queue.indices) {
            releaseTransitionWakeLock()
            return
        }
        val song = info.queue[info.currentQueueIndex]
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                acquireTransitionWakeLock()
                val resolvedTrack = musicRepository.resolvePlayableTrack(song, info.isInstrumental)
                val audioUrl = resolvedTrack.playableUrl
                
                val artworkUri = getResolvedArtworkUrl(song)?.let { Uri.parse(it) }

                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(Uri.parse(audioUrl))
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
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error playing song: ${song.title}", e)
                releaseTransitionWakeLock()
            }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        startPlaybackService()
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
        updatePlaybackInfo()
    }

    fun pause() {
        player.pause()
        updatePlaybackInfo()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
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
        _playbackInfo.value = info.copy(isInstrumental = newInst)
        if (info.currentSong != null && info.currentSong.hasInstrumental) {
            playCurrentQueueIndex()
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
        if (index in queue.indices) {
            queue.removeAt(index)
            var newIndex = info.currentQueueIndex
            if (index < newIndex) {
                newIndex--
            } else if (index == newIndex && queue.isNotEmpty()) {
                newIndex = newIndex.coerceAtMost(queue.size - 1)
            }
            _playbackInfo.value = info.copy(
                queue = queue,
                currentQueueIndex = newIndex,
                currentSong = if (queue.isNotEmpty() && newIndex in queue.indices) queue[newIndex] else null
            )
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
        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            durationMs = if (player.duration > 0) player.duration else 0L
        )
    }

    private fun startProgressTracker() {
        coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                if (player.isPlaying) {
                    _playbackPositionMs.value = player.currentPosition
                    _lyricsPositionMs.value = player.currentPosition
                    updatePlaybackInfo()
                }
                kotlinx.coroutines.delay(500L)
            }
        }
    }

    fun release() {
        releaseTransitionWakeLock()
        mediaSession.release()
        player.release()
    }
}
