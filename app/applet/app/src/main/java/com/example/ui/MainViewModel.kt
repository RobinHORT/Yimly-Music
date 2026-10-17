package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.playback.PlaybackInfo
import com.example.playback.PlaybackManager
import com.example.playback.Song
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val playbackManager = PlaybackManager(application)
    val playbackInfo: StateFlow<PlaybackInfo> = playbackManager.playbackInfo

    private val _sampleSongs = MutableStateFlow(
        listOf(
            Song(
                id = "1",
                title = "Midnight Serenade",
                artist = "Luna Eclipse",
                album = "Starlight",
                audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                durationSeconds = 372
            ),
            Song(
                id = "2",
                title = "Electric Horizon",
                artist = "Neon Pulse",
                album = "Synthwave City",
                audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                durationSeconds = 423
            ),
            Song(
                id = "3",
                title = "Acoustic Breeze",
                artist = "River Stone",
                album = "Unplugged",
                audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                durationSeconds = 310
            )
        )
    )
    val sampleSongs: StateFlow<List<Song>> = _sampleSongs.asStateFlow()

    fun playSong(song: Song) {
        playbackManager.playSong(song, _sampleSongs.value, _sampleSongs.value.indexOf(song))
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun next() {
        playbackManager.next()
    }

    fun previous() {
        playbackManager.previous()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    override fun onCleared() {
        playbackManager.release()
        super.onCleared()
    }
}
