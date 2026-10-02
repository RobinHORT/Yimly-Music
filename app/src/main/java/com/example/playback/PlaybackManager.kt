package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import java.io.ByteArrayOutputStream
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
    internal val context: Context,
    internal val musicRepository: MusicRepository,
    internal val preferencesManager: PreferencesManager,
    internal val coroutineScope: CoroutineScope
) {

    private val _playbackInfo = MutableStateFlow(PlaybackInfo())
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    val player: Player? get() = exoPlayer
    private var _mediaSession: androidx.media3.session.MediaLibraryService.MediaLibrarySession? = null
    val mediaSession: androidx.media3.session.MediaLibraryService.MediaLibrarySession?
        get() {
            if (_mediaSession == null) {
                ensurePlayerInitialized()
            }
            return _mediaSession
        }
    private var progressJob: Job? = null

    val isPlayerInitialized: Boolean
        get() = exoPlayer != null

    @Synchronized
    internal fun ensurePlayerInitialized(): ExoPlayer {
        exoPlayer?.let { return it }
        initPlayer()
        return exoPlayer ?: throw IllegalStateException("Failed to initialize ExoPlayer")
    }

    // High-frequency position tracking for active lyrics screen
    private val _lyricsPositionMs = MutableStateFlow(0L)
    val lyricsPositionMs: StateFlow<Long> = _lyricsPositionMs.asStateFlow()
    private var lyricsTrackerJob: Job? = null
    private var isLyricsActive: Boolean = false

    private var originalQueue: List<Song> = emptyList()
    private var currentQueue: List<Song> = emptyList()
    private var currentIndex: Int = 0

    private var currentNormalAudioUrl: String? = null
    private var currentInstrumentalAudioUrl: String? = null
    private var isInstrumentalPreferred: Boolean = false
    private var instrumentalResolutionJob: Job? = null

    private var cachedToken: String? = null
    private var cachedServerUrl: String = PreferencesManager.DEFAULT_SERVER_URL

    private var isWaitingForNetworkRecovery = false
    private var savedPlaybackPositionMs = 0L
    private var isNetworkMonitoringRegistered = false
    private var connectivityManager: android.net.ConnectivityManager? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var recoveryAttemptCount = 0
    private var recoveryJob: Job? = null

    init {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
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

            Log.d("INSTRUMENTAL_DEBUG", "ResolvingDataSource: original=${dataSpec.uri} -> resolved=$uri (hasAuth=${!token.isNullOrBlank()})")

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

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 60000,
                /* maxBufferMs = */ 120000,
                /* bufferForPlaybackMs = */ 3000,
                /* bufferForPlaybackAfterRebufferMs = */ 5000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLooper(context.mainLooper)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setUsePlatformDiagnostics(false)
            .build()

        player.shuffleModeEnabled = _playbackInfo.value.isShuffle
        player.repeatMode = when (_playbackInfo.value.repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }

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
                handlePlayerError(error)
            }
        })

        exoPlayer = player

        try {
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val forwardingPlayer = YimlyForwardingPlayer(player, this)
            val sessionCallback = YimlySessionCallback(this)
            val sessionId = "yimly_media_session_${System.identityHashCode(this)}_${System.currentTimeMillis()}"
            _mediaSession = androidx.media3.session.MediaLibraryService.MediaLibrarySession.Builder(context, forwardingPlayer, sessionCallback)
                .setId(sessionId)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Failed to initialize MediaSession: ${e.message}", e)
            e.printStackTrace()
        }
        registerNetworkMonitoring()
    }

    internal fun handlePlayerError(error: PlaybackException) {
        Log.e("PlaybackManager", "ExoPlayer error occurred: ${error.message}", error)
        
        val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _playbackInfo.value.currentPositionMs
        savedPlaybackPositionMs = currentPos

        val isInstrumentalMissingError = _playbackInfo.value.isInstrumental && (
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.message?.contains("404") == true ||
                error.message?.contains("Not Found") == true
        )

        val isNetworkError = !isInstrumentalMissingError && (
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.message?.contains("Unable to connect") == true ||
                error.message?.contains("404") == true ||
                error.message?.contains("500") == true
        )

        if (isNetworkError) {
            Log.i("PlaybackManager", "Network/Server error detected. Initiating background recovery. Saved position: $savedPlaybackPositionMs")
            isWaitingForNetworkRecovery = true
            updatePlaybackState { it.copy(isBuffering = true, isPlaying = true) }
            performRecoveryAttempt()
            return
        }

        if (_playbackInfo.value.isInstrumental) {
            val currentSong = _playbackInfo.value.currentSong
            if (currentSong != null) {
                coroutineScope.launch(Dispatchers.Main) {
                    val savedPosition = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _playbackInfo.value.currentPositionMs
                    val normalUrl = currentNormalAudioUrl ?: currentSong.audioUrl
                    val newUri = Uri.parse(normalUrl)
                    val newItem = MediaItem.Builder()
                        .setMediaId("${currentSong.id}_norm")
                        .setUri(newUri)
                        .build()
                    isInstrumentalPreferred = false
                    exoPlayer?.setMediaItem(newItem, savedPosition)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    updatePlaybackState {
                        it.copy(
                            isInstrumental = false,
                            hasInstrumental = false,
                            isBuffering = false
                        )
                    }
                }
                return
            }
        }
        updatePlaybackState { it.copy(isBuffering = false, isPlaying = false) }
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
                val pos = if (isWaitingForNetworkRecovery) {
                    savedPlaybackPositionMs
                } else {
                    exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                }
                val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: (_playbackInfo.value.currentSong?.durationMs ?: 0L)
                _playbackPositionMs.value = pos
                if (_playbackInfo.value.durationMs != dur && dur > 0) {
                    updatePlaybackState { it.copy(durationMs = dur) }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
        val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _playbackPositionMs.value
        _playbackPositionMs.value = currentPos
        updatePlaybackState { it.copy(currentPositionMs = currentPos) }
    }

    fun setLyricsActive(active: Boolean) {
        if (isLyricsActive == active) return
        isLyricsActive = active
        if (active) {
            val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _playbackPositionMs.value
            _lyricsPositionMs.value = currentPos
            startLyricsTracker()
        } else {
            stopLyricsTracker()
        }
    }

    private fun startLyricsTracker() {
        lyricsTrackerJob?.cancel()
        lyricsTrackerJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                val pos = if (isWaitingForNetworkRecovery) {
                    savedPlaybackPositionMs
                } else {
                    exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _playbackPositionMs.value
                }
                _lyricsPositionMs.value = pos
                if (_playbackInfo.value.isPlaying) {
                    delay(16) // ~60 updates per second for fluid 60 FPS Progressive Sweeping
                } else {
                    delay(200) // Lower frequency while paused to save CPU/battery
                }
            }
        }
    }

    fun getCurrentContinuousPositionMs(): Long {
        return exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _lyricsPositionMs.value
    }

    private fun stopLyricsTracker() {
        lyricsTrackerJob?.cancel()
        lyricsTrackerJob = null
    }

    fun playSong(song: Song, playlist: List<Song> = listOf(song), startIndex: Int = -1) {
        isWaitingForNetworkRecovery = false
        recoveryJob?.cancel()
        recoveryAttemptCount = 0
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
        updatePlaybackState {
            it.copy(
                currentSong = song,
                queue = currentQueue,
                currentQueueIndex = currentIndex,
                currentPositionMs = 0L,
                durationMs = song.durationMs,
                hasInstrumental = true,
                isInstrumental = isInstrumentalPreferred
            )
        }
        playCurrentQueueIndex()
    }

    internal fun getResolvedArtworkUrl(song: Song): String? {
        val direct = song.explicitArtworkUrl ?: song.artworkPath ?: song.albumArtworkPath
        if (!direct.isNullOrBlank()) {
            return if (direct.startsWith("http")) direct else "${cachedServerUrl.trimEnd('/')}${if (direct.startsWith("/")) "" else "/"}$direct"
        }
        val defaultArt = song.artworkUrl
        if (!defaultArt.isNullOrBlank()) {
            return if (defaultArt.startsWith(PreferencesManager.DEFAULT_SERVER_URL) && cachedServerUrl != PreferencesManager.DEFAULT_SERVER_URL) {
                val cleanBase = cachedServerUrl.trimEnd('/')
                val path = defaultArt.removePrefix(PreferencesManager.DEFAULT_SERVER_URL)
                "$cleanBase$path"
            } else {
                defaultArt
            }
        }
        return null
    }

    private suspend fun loadArtworkBytes(artworkUrl: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (artworkUrl.isNullOrBlank()) return@withContext null
        try {
            kotlinx.coroutines.withTimeoutOrNull(600L) {
                val loader = (context.applicationContext as? ImageLoaderFactory)?.newImageLoader()
                    ?: ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(512, 512)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        stream.toByteArray()
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Failed to load artwork bytes for $artworkUrl: ${e.message}")
            null
        }
    }

    private fun playCurrentQueueIndex() {
        if (currentQueue.isEmpty() || currentIndex !in currentQueue.indices) return

        isWaitingForNetworkRecovery = false
        recoveryJob?.cancel()
        recoveryAttemptCount = 0

        val song = currentQueue[currentIndex]
        val player = ensurePlayerInitialized()
        val wantInstrumental = isInstrumentalPreferred

        instrumentalResolutionJob?.cancel()
        instrumentalResolutionJob = coroutineScope.launch {
            val resolved = musicRepository.resolvePlayableTrack(song, wantInstrumental)
            currentNormalAudioUrl = resolved.normalUrl
            currentInstrumentalAudioUrl = resolved.instrumentalUrl

            val resolvedArtworkUrl = getResolvedArtworkUrl(song)

            withContext(Dispatchers.Main) {
                val newUri = Uri.parse(resolved.playableUrl)
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(resolvedArtworkUrl?.let { Uri.parse(it) })
                    .setTrackNumber(currentIndex + 1)
                    .setTotalTrackCount(currentQueue.size)
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

    fun playSongFromAuto(songId: String, type: String, playlistId: String?) {
        coroutineScope.launch(Dispatchers.IO) {
            val song = musicRepository.getSongById(songId) ?: return@launch
            
            val playlistSongs = when (type) {
                "playlist_song" -> {
                    if (playlistId != null) {
                        musicRepository.getSongsForPlaylist(playlistId).firstOrNull() ?: listOf(song)
                    } else {
                        listOf(song)
                    }
                }
                "favorite_song" -> {
                    musicRepository.favoriteSongs.firstOrNull() ?: listOf(song)
                }
                "recent_song" -> {
                    musicRepository.recentlyPlayedSongs.firstOrNull() ?: listOf(song)
                }
                "all_song" -> {
                    musicRepository.allSongs.firstOrNull() ?: listOf(song)
                }
                else -> {
                    listOf(song)
                }
            }
            
            val startIndex = playlistSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            
            withContext(Dispatchers.Main) {
                playSong(song, playlistSongs, startIndex)
            }
        }
    }

    fun toggleInstrumental() {
        val currentSong = _playbackInfo.value.currentSong ?: run {
            Log.w("INSTRUMENTAL_DEBUG", "INSTRUMENTAL_TOGGLE_CALLED - failed: currentSong is null")
            return
        }
        val player = ensurePlayerInitialized()

        val beforeSongId = currentSong.id
        val beforeTitle = currentSong.title
        val beforePref = isInstrumentalPreferred
        val beforeUri = player.currentMediaItem?.requestMetadata?.mediaUri
            ?: player.currentMediaItem?.localConfiguration?.uri
            ?: currentNormalAudioUrl
            ?: currentSong.audioUrl
        val beforePosition = player.currentPosition

        Log.d("INSTRUMENTAL_DEBUG", "INSTRUMENTAL_TOGGLE_CALLED")
        Log.d("INSTRUMENTAL_DEBUG", "=== BEFORE TAP ===")
        Log.d("INSTRUMENTAL_DEBUG", "song ID: $beforeSongId")
        Log.d("INSTRUMENTAL_DEBUG", "title: $beforeTitle")
        Log.d("INSTRUMENTAL_DEBUG", "isInstrumentalPreferred: $beforePref")
        Log.d("INSTRUMENTAL_DEBUG", "current ExoPlayer MediaItem URI: $beforeUri")
        Log.d("INSTRUMENTAL_DEBUG", "current playback position: $beforePosition")

        val newIsInstrumental = !isInstrumentalPreferred
        isInstrumentalPreferred = newIsInstrumental
        Log.d("INSTRUMENTAL_DEBUG", "INSTRUMENTAL_NEW_STATE isInstrumentalPreferred: $newIsInstrumental")

        instrumentalResolutionJob?.cancel()
        instrumentalResolutionJob = coroutineScope.launch {
            val resolved = musicRepository.resolvePlayableTrack(currentSong, newIsInstrumental)
            Log.d("INSTRUMENTAL_DEBUG", "INSTRUMENTAL_RESOLVED_URL = ${resolved.playableUrl}")

            currentNormalAudioUrl = resolved.normalUrl
            currentInstrumentalAudioUrl = resolved.instrumentalUrl

            val resolvedArtworkUrl = getResolvedArtworkUrl(currentSong)

            withContext(Dispatchers.Main) {
                val currentPosition = player.currentPosition.coerceAtLeast(0L)
                val wasPlaying = player.isPlaying || player.playWhenReady

                val newUri = Uri.parse(resolved.playableUrl)
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(currentSong.title)
                    .setArtist(currentSong.artist)
                    .setAlbumTitle(currentSong.album)
                    .setArtworkUri(resolvedArtworkUrl?.let { Uri.parse(it) })
                    .setTrackNumber(currentIndex + 1)
                    .setTotalTrackCount(currentQueue.size)
                    .build()

                val requestMetadata = MediaItem.RequestMetadata.Builder()
                    .setMediaUri(newUri)
                    .build()

                val newMediaId = if (resolved.isInstrumentalActive) "${currentSong.id}_inst" else "${currentSong.id}_norm"

                val newItem = MediaItem.Builder()
                    .setMediaId(newMediaId)
                    .setUri(newUri)
                    .setRequestMetadata(requestMetadata)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                Log.d("INSTRUMENTAL_DEBUG", "INSTRUMENTAL_SET_MEDIA_ITEM: mediaId=$newMediaId, uri=$newUri, pos=$currentPosition")
                player.setMediaItem(newItem, currentPosition)
                player.prepare()
                if (wasPlaying) {
                    player.play()
                } else {
                    player.pause()
                }

                val afterUri = player.currentMediaItem?.requestMetadata?.mediaUri
                    ?: player.currentMediaItem?.localConfiguration?.uri
                    ?: newUri
                Log.d("INSTRUMENTAL_DEBUG", "INSTRUMENTAL_PLAYER_URI = $afterUri")
                Log.d("INSTRUMENTAL_DEBUG", "=== AFTER TAP ===")
                Log.d("INSTRUMENTAL_DEBUG", "song ID: ${currentSong.id}")
                Log.d("INSTRUMENTAL_DEBUG", "isInstrumentalPreferred: $newIsInstrumental")
                Log.d("INSTRUMENTAL_DEBUG", "resolved playable URL: ${resolved.playableUrl}")
                Log.d("INSTRUMENTAL_DEBUG", "new MediaItem URI: $newUri")
                Log.d("INSTRUMENTAL_DEBUG", "ExoPlayer currentMediaItem URI: $afterUri")
                Log.d("INSTRUMENTAL_DEBUG", "playback position: ${player.currentPosition}")
                Log.d("INSTRUMENTAL_DEBUG", "player playback state: ${player.playbackState}")

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
        startPlaybackService()
        val player = ensurePlayerInitialized()
        if (player.currentMediaItem == null && currentQueue.isNotEmpty()) {
            playCurrentQueueIndex()
        } else {
            player.play()
        }
    }

    fun pause() {
        isWaitingForNetworkRecovery = false
        recoveryJob?.cancel()
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val player = exoPlayer
        if (player?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (exoPlayer != null || currentQueue.isNotEmpty()) {
            ensurePlayerInitialized().seekTo(positionMs)
        }
        _playbackPositionMs.value = positionMs
        _lyricsPositionMs.value = positionMs
        updatePlaybackState { it.copy(currentPositionMs = positionMs) }
    }

    fun next() {
        if (currentQueue.isEmpty()) return

        if (currentIndex < currentQueue.size - 1) {
            currentIndex++
            val nextSong = currentQueue[currentIndex]
            updatePlaybackState {
                it.copy(
                    currentSong = nextSong,
                    currentQueueIndex = currentIndex,
                    currentPositionMs = 0L,
                    durationMs = nextSong.durationMs
                )
            }
            playCurrentQueueIndex()
        } else if (_playbackInfo.value.repeatMode != RepeatMode.OFF) {
            currentIndex = 0
            val nextSong = currentQueue[currentIndex]
            updatePlaybackState {
                it.copy(
                    currentSong = nextSong,
                    currentQueueIndex = currentIndex,
                    currentPositionMs = 0L,
                    durationMs = nextSong.durationMs
                )
            }
            playCurrentQueueIndex()
        }
    }

    fun previous() {
        if (currentQueue.isEmpty()) return
        val player = ensurePlayerInitialized()
        if (player.currentPosition > 3000L || currentIndex <= 0) {
            player.seekTo(0L)
            updatePlaybackState { it.copy(currentPositionMs = 0L) }
        } else {
            currentIndex = (currentIndex - 1).coerceAtLeast(0)
            val prevSong = currentQueue[currentIndex]
            updatePlaybackState {
                it.copy(
                    currentSong = prevSong,
                    currentQueueIndex = currentIndex,
                    currentPositionMs = 0L,
                    durationMs = prevSong.durationMs
                )
            }
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
                val nextSong = currentQueue.getOrNull(currentIndex)
                if (nextSong != null) {
                    updatePlaybackState {
                        it.copy(
                            currentSong = nextSong,
                            currentQueueIndex = currentIndex,
                            currentPositionMs = 0L,
                            durationMs = nextSong.durationMs
                        )
                    }
                }
                playCurrentQueueIndex()
            }
            RepeatMode.OFF -> {
                if (currentIndex < currentQueue.size - 1) {
                    currentIndex++
                    val nextSong = currentQueue.getOrNull(currentIndex)
                    if (nextSong != null) {
                        updatePlaybackState {
                            it.copy(
                                currentSong = nextSong,
                                currentQueueIndex = currentIndex,
                                currentPositionMs = 0L,
                                durationMs = nextSong.durationMs
                            )
                        }
                    }
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

        exoPlayer?.shuffleModeEnabled = newShuffle

        updatePlaybackState {
            it.copy(
                isShuffle = newShuffle,
                queue = currentQueue,
                currentQueueIndex = currentIndex
            )
        }
    }

    fun setShuffle(enabled: Boolean) {
        if (_playbackInfo.value.isShuffle != enabled) {
            toggleShuffle()
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        exoPlayer?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        updatePlaybackState { it.copy(repeatMode = mode) }
    }

    fun cycleRepeatMode() {
        val nextMode = _playbackInfo.value.repeatMode.next()
        setRepeatMode(nextMode)
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
            val targetSong = currentQueue[currentIndex]
            updatePlaybackState {
                it.copy(
                    currentSong = targetSong,
                    currentQueueIndex = currentIndex,
                    currentPositionMs = 0L,
                    durationMs = targetSong.durationMs
                )
            }
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

    fun release() {
        unregisterNetworkMonitoring()
        recoveryJob?.cancel()
        stopProgressTracker()
        stopLyricsTracker()
        _playbackPositionMs.value = 0L
        _lyricsPositionMs.value = 0L
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

    private fun registerNetworkMonitoring() {
        if (isNetworkMonitoringRegistered) return
        val cm = connectivityManager ?: return
        try {
            val builder = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    Log.i("PlaybackManager", "Network connection returned. Triggering recovery if needed.")
                    coroutineScope.launch(Dispatchers.Main) {
                        handleNetworkReturned()
                    }
                }
            }
            cm.registerNetworkCallback(builder.build(), callback)
            networkCallback = callback
            isNetworkMonitoringRegistered = true
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkMonitoring() {
        if (!isNetworkMonitoringRegistered) return
        val cm = connectivityManager ?: return
        val callback = networkCallback ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
        networkCallback = null
        isNetworkMonitoringRegistered = false
    }

    private fun handleNetworkReturned() {
        if (isWaitingForNetworkRecovery) {
            Log.i("PlaybackManager", "Network connection restored. Forcing immediate recovery attempt.")
            recoveryJob?.cancel()
            recoveryAttemptCount = 0
            performRecoveryAttemptImmediate()
        }
    }

    private fun performRecoveryAttemptImmediate() {
        coroutineScope.launch(Dispatchers.Main) {
            val player = exoPlayer ?: return@launch
            val currentMediaItem = player.currentMediaItem
            val song = _playbackInfo.value.currentSong
            if (song == null || currentMediaItem == null || !isWaitingForNetworkRecovery) return@launch

            try {
                Log.i("PlaybackManager", "Immediate recovery triggered at position $savedPlaybackPositionMs")
                player.setMediaItem(currentMediaItem, savedPlaybackPositionMs)
                player.prepare()
                player.play()
                isWaitingForNetworkRecovery = false
                recoveryAttemptCount = 0
                updatePlaybackState { it.copy(isBuffering = false) }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Immediate recovery failed, falling back to backoff: ${e.message}")
                recoveryAttemptCount++
                performRecoveryAttempt()
            }
        }
    }

    private fun performRecoveryAttempt() {
        recoveryJob?.cancel()
        recoveryJob = coroutineScope.launch(Dispatchers.Main) {
            val currentMediaItem = exoPlayer?.currentMediaItem
            val song = _playbackInfo.value.currentSong
            if (song == null || currentMediaItem == null) {
                isWaitingForNetworkRecovery = false
                return@launch
            }

            // Exponential backoff: 2s, 4s, 8s, up to 16s
            val backoffMs = (Math.pow(2.0, recoveryAttemptCount.toDouble()) * 1000).toLong().coerceAtMost(16000L)
            Log.i("PlaybackManager", "Retrying playback recovery (attempt #${recoveryAttemptCount + 1}) in ${backoffMs}ms")
            delay(backoffMs)

            if (!isWaitingForNetworkRecovery) return@launch

            val cm = connectivityManager
            val isNetworkUp = if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                true
            }

            if (!isNetworkUp) {
                Log.d("PlaybackManager", "Network is still down. Rescheduling recovery.")
                recoveryAttemptCount++
                performRecoveryAttempt()
                return@launch
            }

            try {
                Log.i("PlaybackManager", "Network is up. Re-preparing ExoPlayer at position $savedPlaybackPositionMs")
                exoPlayer?.let { player ->
                    player.setMediaItem(currentMediaItem, savedPlaybackPositionMs)
                    player.prepare()
                    player.play()
                }
                recoveryAttemptCount = 0
                isWaitingForNetworkRecovery = false
                updatePlaybackState { it.copy(isBuffering = false) }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Recovery prepare failed: ${e.message}")
                recoveryAttemptCount++
                performRecoveryAttempt()
            }
        }
    }
}
