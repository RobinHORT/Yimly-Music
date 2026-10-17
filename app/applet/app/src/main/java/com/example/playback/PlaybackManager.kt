package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val durationSeconds: Long = 0L
)

data class PlaybackInfo(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1
)

class PlaybackManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

    private val _playbackInfo = MutableStateFlow(PlaybackInfo())
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _lyricsPositionMs = MutableStateFlow(0L)
    val lyricsPositionMs: StateFlow<Long> = _lyricsPositionMs.asStateFlow()

    init {
        startProgressTracker()
    }

    private fun acquireTransitionWakeLock() {
        try {
            if (transitionWakeLock == null) {
                transitionWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "YimlyMusic::TransitionWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (transitionWakeLock?.isHeld == false) {
                transitionWakeLock?.acquire(5000L) // 5 second safety net
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

    fun getCoroutineScope(): CoroutineScope = scope

    fun playSong(song: Song, queue: List<Song> = listOf(song), startIndex: Int = 0) {
        val finalQueue = if (queue.isEmpty()) listOf(song) else queue
        val validIndex = if (startIndex in finalQueue.indices) startIndex else 0
        
        _playbackInfo.value = _playbackInfo.value.copy(
            queue = finalQueue,
            currentIndex = validIndex
        )
        playCurrentQueueIndex()
    }

    fun next() {
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) return
        val nextIndex = (info.currentIndex + 1) % info.queue.size
        _playbackInfo.value = info.copy(currentIndex = nextIndex)
        playCurrentQueueIndex()
    }

    fun previous() {
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) return
        val prevIndex = if (info.currentIndex - 1 >= 0) info.currentIndex - 1 else info.queue.size - 1
        _playbackInfo.value = info.copy(currentIndex = prevIndex)
        playCurrentQueueIndex()
    }

    private fun handleSongEnded() {
        acquireTransitionWakeLock()
        val info = _playbackInfo.value
        if (info.queue.isEmpty()) {
            releaseTransitionWakeLock()
            return
        }
        val nextIndex = info.currentIndex + 1
        if (nextIndex < info.queue.size) {
            _playbackInfo.value = info.copy(currentIndex = nextIndex)
            playCurrentQueueIndex()
        } else {
            // Loop or stop
            _playbackInfo.value = info.copy(currentIndex = 0)
            playCurrentQueueIndex()
        }
    }

    private fun playCurrentQueueIndex() {
        val info = _playbackInfo.value
        if (info.currentIndex !in info.queue.indices) {
            releaseTransitionWakeLock()
            return
        }
        val song = info.queue[info.currentIndex]
        _playbackInfo.value = info.copy(currentSong = song)

        scope.launch(Dispatchers.IO) {
            try {
                acquireTransitionWakeLock()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(Uri.parse(song.audioUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()

                launch(Dispatchers.Main) {
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
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
        updatePlaybackInfo()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    private fun updatePlaybackInfo() {
        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            durationMs = if (player.duration > 0) player.duration else 0L
        )
    }

    private fun startProgressTracker() {
        scope.launch(Dispatchers.Main) {
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
        player.release()
    }
}
