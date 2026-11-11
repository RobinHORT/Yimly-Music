package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.models.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class PlaybackClient(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    @Volatile
    private var isReleased = false

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var playerListener: Player.Listener? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState: StateFlow<ProgressState> = _progressState.asStateFlow()

    private val moshi by lazy { Moshi.Builder().build() }
    private val songAdapter by lazy { moshi.adapter(Song::class.java) }
    private val songListAdapter by lazy {
        moshi.adapter<List<Song>>(Types.newParameterizedType(List::class.java, Song::class.java))
    }

    init {
        connect()
    }

    fun connect() {
        if (isReleased) return
        if (mediaController != null || controllerFuture != null) return

        try {
            val sessionToken = SessionToken(context, ComponentName(context, YimlyPlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future

            future.addListener({
                try {
                    if (isReleased) {
                        if (future.isDone && !future.isCancelled) {
                            try {
                                future.get().release()
                            } catch (_: Exception) {}
                        }
                        return@addListener
                    }

                    val controller = future.get()
                    mediaController = controller

                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (!isReleased) updateState(player)
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (!isReleased) updateState(controller)
                        }
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (!isReleased) updateState(controller)
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (!isReleased) updateState(controller)
                        }
                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            if (!isReleased) updateState(controller)
                        }
                    }
                    playerListener = listener
                    controller.addListener(listener)
                    updateState(controller)
                } catch (e: Exception) {
                    if (!isReleased) {
                        android.util.Log.e("PlaybackClient", "Failed to connect MediaController", e)
                    }
                } finally {
                    controllerFuture = null
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            android.util.Log.e("PlaybackClient", "Error initializing MediaController buildAsync", e)
            controllerFuture = null
        }
    }

    private fun updateState(player: Player) {
        val queue = (0 until player.mediaItemCount).map { i ->
            player.getMediaItemAt(i).toSong()
        }
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        val currentSong = queue.getOrNull(currentIndex)
        val isInstrumental = player.currentMediaItem?.mediaId?.endsWith("_inst") == true

        _playbackState.value = PlaybackState(
            currentSong = currentSong,
            isPlaying = player.isPlaying,
            queue = queue,
            currentQueueIndex = currentIndex,
            isShuffle = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            hasInstrumental = currentSong?.hasInstrumental ?: false,
            isInstrumental = isInstrumental,
            playbackSpeed = player.playbackParameters.speed
        )

        _progressState.value = ProgressState(
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L)
        )
    }

    private fun MediaItem.toSong(): Song {
        val meta = mediaMetadata
        return Song(
            id = mediaId.removeSuffix("_inst"),
            title = meta.title?.toString() ?: "",
            artist = meta.artist?.toString() ?: "",
            album = meta.albumTitle?.toString() ?: "",
            explicitArtworkUrl = meta.artworkUri?.toString()
        )
    }

    // Standard Commands
    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun next() {
        mediaController?.seekToNextMediaItem()
    }

    fun previous() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun setRepeatMode(mode: RepeatMode) {
        val pRepeat = when (mode) {
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = pRepeat
    }

    fun cycleRepeatMode() {
        val current = _playbackState.value.repeatMode
        setRepeatMode(current.next())
    }

    fun setShuffle(enabled: Boolean) {
        mediaController?.shuffleModeEnabled = enabled
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    // Custom Queue Commands
    fun playSong(song: Song, playlist: List<Song> = listOf(song), startIndex: Int = -1) {
        val bundle = Bundle().apply {
            putString("songJson", songAdapter.toJson(song))
            putString("playlistJson", songListAdapter.toJson(playlist))
            putInt("startIndex", startIndex)
        }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_PLAY_SONG, Bundle.EMPTY),
            bundle
        )
    }

    fun playQueueItem(index: Int) {
        val bundle = Bundle().apply { putInt("index", index) }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_PLAY_QUEUE_ITEM, Bundle.EMPTY),
            bundle
        )
    }

    fun addToQueue(song: Song) {
        val bundle = Bundle().apply { putString("songJson", songAdapter.toJson(song)) }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_ADD_TO_QUEUE, Bundle.EMPTY),
            bundle
        )
    }

    fun playNext(song: Song) {
        val bundle = Bundle().apply { putString("songJson", songAdapter.toJson(song)) }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_PLAY_NEXT, Bundle.EMPTY),
            bundle
        )
    }

    fun removeFromQueue(index: Int) {
        val bundle = Bundle().apply { putInt("index", index) }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_REMOVE_FROM_QUEUE, Bundle.EMPTY),
            bundle
        )
    }

    fun removeSongFromQueue(songId: String) {
        val bundle = Bundle().apply { putString("songId", songId) }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_REMOVE_SONG_FROM_QUEUE, Bundle.EMPTY),
            bundle
        )
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val bundle = Bundle().apply {
            putInt("fromIndex", fromIndex)
            putInt("toIndex", toIndex)
        }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_REORDER_QUEUE, Bundle.EMPTY),
            bundle
        )
    }

    fun clearUpcomingQueue() {
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_CLEAR_UPCOMING_QUEUE, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    fun clearQueueAndStop() {
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_CLEAR_QUEUE_AND_STOP, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    fun updateFavoriteStatus(songId: String, isFavorite: Boolean) {
        val bundle = Bundle().apply {
            putString("songId", songId)
            putBoolean("isFavorite", isFavorite)
        }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_UPDATE_FAVORITE_STATUS, Bundle.EMPTY),
            bundle
        )
    }

    fun setLyricsActive(active: Boolean) {
        val bundle = Bundle().apply { putBoolean("active", active) }
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_SET_LYRICS_ACTIVE, Bundle.EMPTY),
            bundle
        )
    }

    fun toggleInstrumental() {
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_TOGGLE_INSTRUMENTAL, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    fun shuffleCurrentQueue() {
        mediaController?.sendCustomCommand(
            SessionCommand(YimlySessionCallback.ACTION_SHUFFLE_PLAYLIST, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    fun release() {
        isReleased = true
        controllerFuture?.cancel(true)
        controllerFuture = null

        val listener = playerListener
        val controller = mediaController
        if (controller != null && listener != null) {
            controller.removeListener(listener)
        }
        playerListener = null
        mediaController = null
    }
}
