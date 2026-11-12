package com.example.playback

import com.example.data.datastore.PreferencesManager

open class PlaybackSnapshotRepository(
    private val preferencesManager: PreferencesManager
) {
    open suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
        preferencesManager.savePlaybackSnapshot(snapshot)
    }

    open suspend fun loadSnapshot(): PlaybackSnapshot? {
        return preferencesManager.loadPlaybackSnapshot()
    }

    open suspend fun clearSnapshot() {
        preferencesManager.clearPlaybackSnapshot()
    }
}
