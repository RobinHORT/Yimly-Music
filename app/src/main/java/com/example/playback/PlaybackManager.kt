package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.example.MainActivity
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class PlaybackManager(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val preferencesManager: PreferencesManager,
    private val coroutineScope: CoroutineScope
) {

    private val _playbackInfo = MutableStateFlow(PlaybackInfo())
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var _mediaSession: MediaSession? = null
    val mediaSession: MediaSession? get() = _mediaSession
    private var progressJob: Job? = null

    private var originalQueue: List<Song> = emptyList()
    private var currentQueue: List<Song> = emptyList()
    private var currentIndex: Int = 0

    private var currentNormalAudioUrl: String? = null
    private var currentInstrumentalAudioUrl: String? = null
    private var isInstrumentalPreferred: Boolean = false
    private var instrumentalResolutionJob: Job? = null

    private var cachedToken: String? = null
    private var cachedServerUrl: String = PreferencesManager.DEFAULT_SERVER_URL

    init {
        coroutineScope.launch {
            preferencesManager.authTokenFlow.collect { token ->
                cachedToken = token
            }
        }
        coroutineScope.launch {
            preferencesManager.serverUrlFlow.collect { url ->
                cachedServerUrl = url ?: PreferencesManager.DEFAULT_SERVER_URL
            }
        }
        initPlayer()
    }

    private fun initPlayer() {
        if (exoPlayer != null) return

        val resolver = ResolvingDataSource.Resolver { dataSpec ->
            val token = cachedToken
            val serverUrl = cachedServerUrl

            var uri = dataSpec.uri
            val uriString = uri.toString()
            if (!uriString.startsWith("http://") && !uriString.startsWith("https://")) {
                val cleanBase = serverUrl.trimEnd('/')
                val cleanPath = if (uriString.startsWith("/")) uriString else "/$uriString"
                uri = Uri.parse("$cleanBase$cleanPath")
            } else if (uriString.startsWith(PreferencesManager.DEFAULT_SERVER_URL) &&
                serverUrl != PreferencesManager.DEFAULT_SERVER_URL) {
                val cleanBase = serverUrl.trimEnd('/')
                val cleanPath = uriString.removePrefix(PreferencesManager.DEFAULT_SERVER_URL)
                uri = Uri.parse("$cleanBase$cleanPath")
            }

            val headers = dataSpec.httpRequestHeaders.toMutableMap()
            if (!token.isNullOrBlank()) {
                headers["Authorization"] = "Bearer $token"
            }
            headers["User-Agent"] = "Yimly-Android-Client/1.0"

            dataSpec.buildUpon()
                .setUri(uri)
                .setHttpRequestHeaders(headers)
                .build()
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Yimly-Android-Client/1.0")

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(httpDataSourceFactory, resolver)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(resolvingDataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        updatePlaybackState { it.copy(isBuffering = true) }
                    }
                    Player.STATE_READY -> {
                        val duration = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                        updatePlaybackState {
                            it.copy(
                                isBuffering = false,
                                durationMs = if (duration > 0) duration else (it.currentSong?.durationMs ?: 0L)
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        handleSongEnded()
                    }
                    Player.STATE_IDLE -> {
                        updatePlaybackState { it.copy(isBuffering = false) }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackManager", "ExoPlayer error occurred: ${error.message}", error)
                if (isInstrumentalPreferred) {
                    isInstrumentalPreferred = false
                    val currentSong = _playbackInfo.value.currentSong
                    if (currentSong != null) {
                        coroutineScope.launch(Dispatchers.Main) {
                            val normalUrl = currentNormalAudioUrl ?: currentSong.audioUrl
                            val newUri = Uri.parse(normalUrl)
                            val newItem = MediaItem.Builder()
                                .setMediaId("${currentSong.id}_norm")
                                .setUri(newUri)
                                .build()
                            exoPlayer?.setMediaItem(newItem)
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                            updatePlaybackState {
                                it.copy(
                                    isInstrumental = false,
                                    isBuffering = false
                                )
                            }
                        }
                        return
                    }
                }
                updatePlaybackState { it.copy(isBuffering = false, isPlaying = false) }
            }
        })

        exoPlayer = player

        try {
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            _mediaSession = MediaSession.Builder(context, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Failed to initialize MediaSession: ${e.message}")
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, YimlyPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Failed to start YimlyPlaybackService: ${e.message}")
        }
    }

    private fun updatePlaybackState(reducer: (PlaybackInfo) -> PlaybackInfo) {
        _playbackInfo.value = reducer(_playbackInfo.value)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                val pos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: (_playbackInfo.value.currentSong?.durationMs ?: 0L)
                updatePlaybackState {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playSong(song: Song, playlist: List<Song> = listOf(song), startIndex: Int = -1) {
        originalQueue = playlist
        val isShuffle = _playbackInfo.value.isShuffle

        currentQueue = if (isShuffle) {
            val remaining = playlist.filter { it.id != song.id }.shuffled()
            listOf(song) + remaining
        } else {
            playlist
        }

        val targetIdx = if (startIndex >= 0 && !isShuffle) {
            startIndex
        } else {
            currentQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        }

        currentIndex = targetIdx
        playCurrentQueueIndex()
    }

    private fun playCurrentQueueIndex() {
        if (currentQueue.isEmpty() || currentIndex !in currentQueue.indices) return

        val song = currentQueue[currentIndex]
        val player = exoPlayer ?: return
        val wantInstrumental = isInstrumentalPreferred

        instrumentalResolutionJob?.cancel()
        instrumentalResolutionJob = coroutineScope.launch {
            val resolved = musicRepository.resolvePlayableTrack(song, wantInstrumental)
            currentNormalAudioUrl = resolved.normalUrl
            currentInstrumentalAudioUrl = resolved.instrumentalUrl

            withContext(Dispatchers.Main) {
                val newUri = Uri.parse(resolved.playableUrl)
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUrl?.let { Uri.parse(it) })
                    .build()

                val requestMetadata = MediaItem.RequestMetadata.Builder()
                    .setMediaUri(newUri)
                    .build()

                val newMediaId = if (resolved.isInstrumentalActive) "${song.id}_inst" else "${song.id}_norm"

                val mediaItem = MediaItem.Builder()
                    .setMediaId(newMediaId)
                    .setUri(newUri)
                    .setRequestMetadata(requestMetadata)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                startPlaybackService()
                player.play()

                updatePlaybackState {
                    it.copy(
                        currentSong = song,
                        queue = currentQueue,
                        currentQueueIndex = currentIndex,
                        currentPositionMs = 0L,
                        durationMs = song.durationMs,
                        isPlaying = true,
                        hasInstrumental = resolved.hasInstrumental,
                        isInstrumental = resolved.isInstrumentalActive
                    )
                }
            }
        }

        coroutineScope.launch {
            musicRepository.recordPlayHistory(song.id)
        }
    }

    fun toggleInstrumental() {
        val currentSong = _playbackInfo.value.currentSong ?: return
        val player = exoPlayer ?: return
        val newIsInstrumental = !_playbackInfo.value.isInstrumental

        coroutineScope.launch {
            val resolved = musicRepository.resolvePlayableTrack(currentSong, newIsInstrumental)

            if (newIsInstrumental && !resolved.hasInstrumental) {
                isInstrumentalPreferred = false
                withContext(Dispatchers.Main) {
                    updatePlaybackState {
                        it.copy(
                            isInstrumental = false,
                            hasInstrumental = false
                        )
                    }
                }
                return@launch
            }

            currentNormalAudioUrl = resolved.normalUrl
            currentInstrumentalAudioUrl = resolved.instrumentalUrl

            isInstrumentalPreferred = newIsInstrumental

            withContext(Dispatchers.Main) {
                val currentPosition = player.currentPosition.coerceAtLeast(0L)
                val wasPlaying = player.isPlaying || player.playWhenReady

                val newUri = Uri.parse(resolved.playableUrl)
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(currentSong.title)
                    .setArtist(currentSong.artist)
                    .setAlbumTitle(currentSong.album)
                    .setArtworkUri(currentSong.artworkUrl?.let { Uri.parse(it) })
                    .build()

                val currentItem = player.currentMediaItem
                val requestMetadata = (currentItem?.requestMetadata ?: MediaItem.RequestMetadata.EMPTY)
                    .buildUpon()
                    .setMediaUri(newUri)
                    .build()

                val newMediaId = if (resolved.isInstrumentalActive) "${currentSong.id}_inst" else "${currentSong.id}_norm"

                val newItem = (currentItem?.buildUpon() ?: MediaItem.Builder())
                    .setMediaId(newMediaId)
                    .setUri(newUri)
                    .setRequestMetadata(requestMetadata)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                player.setMediaItem(newItem, currentPosition)
                player.prepare()
                if (wasPlaying) {
                    player.play()
                } else {
                    player.pause()
                }

                updatePlaybackState {
                    it.copy(
                        isInstrumental = resolved.isInstrumentalActive,
                        hasInstrumental = resolved.hasInstrumental,
                        currentPositionMs = currentPosition
                    )
                }
            }
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        updatePlaybackState { it.copy(currentPositionMs = positionMs) }
    }

    fun next() {
        if (currentQueue.isEmpty()) return

        if (currentIndex < currentQueue.size - 1) {
            currentIndex++
            playCurrentQueueIndex()
        } else if (_playbackInfo.value.repeatMode == RepeatMode.ALL) {
            currentIndex = 0
            playCurrentQueueIndex()
        }
    }

    fun previous() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000L || currentIndex <= 0) {
            player.seekTo(0L)
            updatePlaybackState { it.copy(currentPositionMs = 0L) }
        } else {
            currentIndex = (currentIndex - 1).coerceAtLeast(0)
            playCurrentQueueIndex()
        }
    }

    private fun handleSongEnded() {
        when (_playbackInfo.value.repeatMode) {
            RepeatMode.ONE -> {
                exoPlayer?.seekTo(0L)
                exoPlayer?.play()
            }
            RepeatMode.ALL -> {
                if (currentIndex >= currentQueue.size - 1) {
                    currentIndex = 0
                } else {
                    currentIndex++
                }
                playCurrentQueueIndex()
            }
            RepeatMode.OFF -> {
                if (currentIndex < currentQueue.size - 1) {
                    currentIndex++
                    playCurrentQueueIndex()
                } else {
                    updatePlaybackState { it.copy(isPlaying = false, currentPositionMs = 0L) }
                }
            }
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackInfo.value.isShuffle
        val currentSong = _playbackInfo.value.currentSong

        if (newShuffle) {
            if (currentSong != null) {
                val rest = originalQueue.filter { it.id != currentSong.id }.shuffled()
                currentQueue = listOf(currentSong) + rest
                currentIndex = 0
            }
        } else {
            currentQueue = originalQueue
            if (currentSong != null) {
                currentIndex = currentQueue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            }
        }

        updatePlaybackState {
            it.copy(
                isShuffle = newShuffle,
                queue = currentQueue,
                currentQueueIndex = currentIndex
            )
        }
    }

    fun cycleRepeatMode() {
        val nextMode = _playbackInfo.value.repeatMode.next()
        updatePlaybackState { it.copy(repeatMode = nextMode) }
    }

    fun addToQueue(song: Song) {
        currentQueue = currentQueue + song
        originalQueue = originalQueue + song
        updatePlaybackState { it.copy(queue = currentQueue) }
    }

    fun playNext(song: Song) {
        val mutable = currentQueue.toMutableList()
        val insertIndex = (currentIndex + 1).coerceAtMost(mutable.size)
        mutable.add(insertIndex, song)
        currentQueue = mutable
        updatePlaybackState { it.copy(queue = currentQueue) }
    }

    fun removeFromQueue(index: Int) {
        if (index !in currentQueue.indices || index == currentIndex) return
        val mutable = currentQueue.toMutableList()
        mutable.removeAt(index)
        if (index < currentIndex) {
            currentIndex--
        }
        currentQueue = mutable
        updatePlaybackState { it.copy(queue = currentQueue, currentQueueIndex = currentIndex) }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices) return
        val mutable = currentQueue.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)

        val currentSong = _playbackInfo.value.currentSong
        currentQueue = mutable
        if (currentSong != null) {
            currentIndex = currentQueue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
        }
        updatePlaybackState { it.copy(queue = currentQueue, currentQueueIndex = currentIndex) }
    }

    fun clearUpcomingQueue() {
        if (currentQueue.isEmpty()) return
        val currentSong = _playbackInfo.value.currentSong
        currentQueue = if (currentSong != null) listOf(currentSong) else emptyList()
        currentIndex = 0
        updatePlaybackState { it.copy(queue = currentQueue, currentQueueIndex = currentIndex) }
    }

    fun playQueueItem(index: Int) {
        if (index in currentQueue.indices) {
            currentIndex = index
            playCurrentQueueIndex()
        }
    }

    fun updateFavoriteStatus(songId: String, isFavorite: Boolean) {
        val currentSong = _playbackInfo.value.currentSong
        var updatedCurrentSong = currentSong
        if (currentSong?.id == songId) {
            updatedCurrentSong = currentSong.copy(isFavorite = isFavorite)
        }
        
        val updatedQueue = currentQueue.map { 
            if (it.id == songId) it.copy(isFavorite = isFavorite) else it 
        }
        currentQueue = updatedQueue
        
        updatePlaybackState { 
            it.copy(
                currentSong = updatedCurrentSong,
                queue = updatedQueue
            ) 
        }
    }

    fun removeDeletedSong(songId: String) {
        val currentSong = _playbackInfo.value.currentSong
        val isCurrent = currentSong?.id == songId
        val wasInQueue = currentQueue.any { it.id == songId } || originalQueue.any { it.id == songId }

        if (!isCurrent && !wasInQueue) return

        val newOriginalQueue = originalQueue.filter { it.id != songId }
        val newCurrentQueue = currentQueue.filter { it.id != songId }
        originalQueue = newOriginalQueue
        currentQueue = newCurrentQueue

        if (isCurrent) {
            instrumentalResolutionJob?.cancel()
            if (newCurrentQueue.isNotEmpty()) {
                val nextIndex = currentIndex.coerceIn(0, newCurrentQueue.lastIndex)
                currentIndex = nextIndex
                playCurrentQueueIndex()
            } else {
                stopProgressTracker()
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                currentIndex = 0
                currentNormalAudioUrl = null
                currentInstrumentalAudioUrl = null
                updatePlaybackState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        queue = emptyList(),
                        currentQueueIndex = 0,
                        currentPositionMs = 0L,
                        durationMs = 0L
                    )
                }
            }
        } else {
            val newIdx = newCurrentQueue.indexOfFirst { it.id == currentSong?.id }.coerceAtLeast(0)
            currentIndex = newIdx
            updatePlaybackState {
                it.copy(
                    queue = newCurrentQueue,
                    currentQueueIndex = newIdx
                )
            }
        }
    }

    fun release() {
        stopProgressTracker()
        try {
            _mediaSession?.run {
                player.release()
                release()
            }
            _mediaSession = null
        } catch (_: Exception) {}
        exoPlayer?.release()
        exoPlayer = null
    }
}
