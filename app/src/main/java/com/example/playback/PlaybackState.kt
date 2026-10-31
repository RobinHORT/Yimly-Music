package com.example.playback

import com.example.data.models.Song

enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

data class PlaybackSnapshot(
    val queue: List<Song>,
    val currentQueueIndex: Int,
    val currentPositionMs: Long,
    val isShuffle: Boolean,
    val repeatMode: RepeatMode,
    val isInstrumental: Boolean,
    val shuffleHistory: List<Int> = emptyList()
)

data class PlaybackInfo(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = 0,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isBuffering: Boolean = false,
    val hasInstrumental: Boolean = false,
    val isInstrumental: Boolean = false
)
