package com.example.playback

import com.example.data.datastore.PreferencesManager

class PlaybackSnapshotRepository(
    private val preferencesManager: PreferencesManager
) {
    suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
        preferencesManager.savePlaybackSnapshot(snapshot)
    }

    suspend fun loadSnapshot(): PlaybackSnapshot? {
        return preferencesManager.loadPlaybackSnapshot()
    }

    suspend fun clearSnapshot() {
        preferencesManager.clearPlaybackSnapshot()
    }
}
