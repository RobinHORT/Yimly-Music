package com.example.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

class YimlyForwardingPlayer(
    player: Player,
    private val playbackManager: PlaybackManager
) : ForwardingPlayer(player) {

    override fun seekToNext() {
        playbackManager.next()
    }

    override fun seekToNextMediaItem() {
        playbackManager.next()
    }

    override fun seekToPrevious() {
        playbackManager.previous()
    }

    override fun seekToPreviousMediaItem() {
        playbackManager.previous()
    }

    override fun hasNextMediaItem(): Boolean {
        val info = playbackManager.playbackInfo.value
        return info.queue.isNotEmpty() && (info.currentQueueIndex < info.queue.size - 1 || info.repeatMode != RepeatMode.OFF)
    }

    override fun hasPreviousMediaItem(): Boolean {
        val info = playbackManager.playbackInfo.value
        return info.queue.isNotEmpty() && (info.currentQueueIndex > 0 || currentPosition > 3000L)
    }

    override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> hasNextMediaItem()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> hasPreviousMediaItem()
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        val baseCommands = super.getAvailableCommands()
        val builder = baseCommands.buildUpon()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SET_REPEAT_MODE)
            .add(Player.COMMAND_SET_SHUFFLE_MODE)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)

        if (hasNextMediaItem()) {
            builder.add(Player.COMMAND_SEEK_TO_NEXT)
            builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (hasPreviousMediaItem()) {
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }

        return builder.build()
    }

    override fun getRepeatMode(): Int {
        return when (playbackManager.playbackInfo.value.repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    override fun setRepeatMode(repeatMode: Int) {
        val target = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        playbackManager.setRepeatMode(target)
    }

    override fun getShuffleModeEnabled(): Boolean {
        return playbackManager.playbackInfo.value.isShuffle
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        playbackManager.setShuffle(shuffleModeEnabled)
    }

    override fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val info = playbackManager.playbackInfo.value
        if (mediaItemIndex != info.currentQueueIndex && mediaItemIndex in info.queue.indices) {
            playbackManager.playQueueItem(mediaItemIndex)
            if (positionMs > 0L) {
                playbackManager.seekTo(positionMs)
            }
        } else {
            playbackManager.seekTo(positionMs)
        }
    }

    override fun stop() {
        playbackManager.pause()
        super.stop()
    }
}
