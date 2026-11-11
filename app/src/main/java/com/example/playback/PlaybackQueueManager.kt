package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

interface QueueMediaSyncListener {
    fun onMediaItemAdded(index: Int, mediaItem: MediaItem)
    fun onMediaItemRemoved(index: Int)
    fun onMediaItemMoved(fromIndex: Int, toIndex: Int)
    fun onQueueCleared()
}

class PlaybackQueueManager(
    private val coroutineScope: CoroutineScope,
    private val mediaItemBuilder: suspend (Song, Boolean) -> MediaItem,
    private val syncListener: QueueMediaSyncListener
) {
    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    val queue: StateFlow<List<Song>> = _queueState
        .map { state -> state.entries.map { it.song } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, _queueState.value.entries.map { it.song })

    val currentQueueIndex: StateFlow<Int> = _queueState
        .map { state ->
            state.entries.indexOfFirst { it.entryId == state.currentEntryId }
        }
        .stateIn(coroutineScope, SharingStarted.Eagerly, -1)

    /**
     * Internal helper to update the authoritative QueueState.
     * Projections (queue, currentQueueIndex) will update reactively.
     */
    private fun updateInternalState(newState: QueueState) {
        _queueState.value = newState
    }

    private fun getResolvedEntries(state: QueueState): List<QueueEntry> {
        val pendingEntryIds = state.pendingAdds.map { it.entryId }.toSet()
        return state.entries.filter { it.entryId !in pendingEntryIds }
    }

    private fun getMedia3Index(state: QueueState, entryId: String): Int {
        val pendingEntryIds = state.pendingAdds.map { it.entryId }.toSet()
        var media3Index = 0
        for (entry in state.entries) {
            if (entry.entryId == entryId) return media3Index
            if (entry.entryId !in pendingEntryIds) media3Index++
        }
        return -1
    }

    /**
     * Replaces the entire queue while attempting to preserve stable entry identities.
     * Matching is performed by Song ID, preferring existing entries to maintain 
     * cross-mutation identity for shuffle history and UI stability.
     */
    fun updateQueueState(queue: List<Song>, currentIndex: Int) {
        val oldState = _queueState.value
        val oldEntriesBySongId = oldState.entries.groupBy { it.song.id }.mapValues { it.value.toMutableList() }
        
        val newEntries = queue.map { song ->
            val matchingOldEntries = oldEntriesBySongId[song.id]
            if (matchingOldEntries != null && matchingOldEntries.isNotEmpty()) {
                matchingOldEntries.removeAt(0).copy(song = song)
            } else {
                QueueEntry(song = song)
            }
        }
        
        val newCurrentEntryId = newEntries.getOrNull(currentIndex)?.entryId
        
        if (newEntries == oldState.entries && newCurrentEntryId == oldState.currentEntryId && oldState.pendingAdds.isEmpty()) {
            return // No-op: do not increment revision
        }

        val newState = oldState.copy(
            entries = newEntries,
            currentEntryId = newCurrentEntryId,
            pendingAdds = emptyList(), // Invalidate all pending additions from the previous queue
            revision = oldState.revision + 1
        )
        updateInternalState(newState)
    }

    fun clearShuffleHistory() {
        val state = _queueState.value
        if (state.shuffleHistory.isEmpty()) return
        updateInternalState(state.copy(
            shuffleHistory = emptyList(),
            revision = state.revision + 1
        ))
    }

    fun addShuffleHistory(index: Int) {
        val state = _queueState.value
        val entryId = state.entries.getOrNull(index)?.entryId ?: return
        if (state.shuffleHistory.contains(entryId)) return
        
        updateInternalState(state.copy(
            shuffleHistory = state.shuffleHistory + entryId,
            revision = state.revision + 1
        ))
    }

    fun removeLastShuffleHistory(): Int? {
        val state = _queueState.value
        if (state.shuffleHistory.isEmpty()) return null
        
        val lastEntryId = state.shuffleHistory.last()
        val index = state.entries.indexOfFirst { it.entryId == lastEntryId }
        
        updateInternalState(state.copy(
            shuffleHistory = state.shuffleHistory.dropLast(1),
            revision = state.revision + 1
        ))
        
        return if (index >= 0) index else null
    }

    fun addToQueue(song: Song, isCurrentSongNull: Boolean, onPlaySong: (Song, List<Song>, Int) -> Unit) {
        if (isCurrentSongNull) {
            val newState = QueueReducer.addEntry(_queueState.value, song)
            updateInternalState(newState)
            onPlaySong(song, newState.entries.map { it.song }, 0)
        } else {
            val (newState, pendingAdd) = QueueReducer.addEntryWithPending(_queueState.value, song)
            updateInternalState(newState)

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val mediaItem = mediaItemBuilder(song, false)
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        val result = QueueReducer.consumePendingAdd(_queueState.value, pendingAdd.token)
                        if (result.media3InsertionIndex != null) {
                            updateInternalState(result.newState)
                            syncListener.onMediaItemAdded(
                                result.media3InsertionIndex.coerceIn(0, result.newState.entries.size),
                                mediaItem
                            )
                        }
                    }
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        val currentEntry = _queueState.value.entries.find { it.entryId == pendingAdd.entryId }
                        if (currentEntry != null) {
                            updateInternalState(QueueReducer.removeEntry(_queueState.value, pendingAdd.entryId))
                        } else {
                            updateInternalState(QueueReducer.invalidatePendingAdd(_queueState.value, pendingAdd.token))
                        }
                    }
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
        }
    }

    fun removeSongFromQueue(songId: String, currentQueue: List<Song>, oldIndex: Int): Pair<List<Song>, Int> {
        var currentState = _queueState.value
        val matchingEntries = currentState.entries.filter { it.song.id == songId }
        if (matchingEntries.isEmpty()) return currentQueue to oldIndex

        // Process matching entries in descending order of their current positions in currentState
        for (entry in matchingEntries.reversed()) {
            val currentIdx = currentState.entries.indexOfFirst { it.entryId == entry.entryId }
            if (currentIdx == -1) continue

            val isPending = currentState.pendingAdds.any { it.entryId == entry.entryId }
            val media3Index = if (!isPending) getMedia3Index(currentState, entry.entryId) else -1

            currentState = QueueReducer.removeEntry(currentState, entry.entryId)

            if (!isPending && media3Index >= 0) {
                syncListener.onMediaItemRemoved(media3Index)
            }
        }

        updateInternalState(currentState)

        if (currentState.entries.isEmpty()) {
            syncListener.onQueueCleared()
        }

        val newSongs = currentState.entries.map { it.song }
        val newIndex = currentQueueIndex.value

        return newSongs to newIndex
    }

    fun playNext(song: Song, isCurrentSongNull: Boolean, currentQueueIndex: Int, onPlaySong: (Song, List<Song>, Int) -> Unit) {
        val state = _queueState.value
        val insertIndex = if (currentQueueIndex in state.entries.indices) currentQueueIndex + 1 else state.entries.size

        if (isCurrentSongNull) {
            val newState = QueueReducer.insertEntry(state, insertIndex, song)
            updateInternalState(newState)
            onPlaySong(song, newState.entries.map { it.song }, insertIndex)
        } else {
            val (newState, pendingAdd) = QueueReducer.insertEntryWithPending(state, insertIndex, song)
            updateInternalState(newState)

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val mediaItem = mediaItemBuilder(song, false)
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        val result = QueueReducer.consumePendingAdd(_queueState.value, pendingAdd.token)
                        if (result.media3InsertionIndex != null) {
                            updateInternalState(result.newState)
                            syncListener.onMediaItemAdded(
                                result.media3InsertionIndex.coerceIn(0, result.newState.entries.size),
                                mediaItem
                            )
                        }
                    }
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        val currentEntry = _queueState.value.entries.find { it.entryId == pendingAdd.entryId }
                        if (currentEntry != null) {
                            updateInternalState(QueueReducer.removeEntry(_queueState.value, pendingAdd.entryId))
                        } else {
                            updateInternalState(QueueReducer.invalidatePendingAdd(_queueState.value, pendingAdd.token))
                        }
                    }
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _queueState.value
        if (index !in state.entries.indices) return
        
        val targetEntry = state.entries[index]
        val isPending = state.pendingAdds.any { it.entryId == targetEntry.entryId }
        val media3Index = if (!isPending) getMedia3Index(state, targetEntry.entryId) else -1

        val newState = QueueReducer.removeEntry(state, targetEntry.entryId)
        updateInternalState(newState)

        if (newState.entries.isEmpty()) {
            syncListener.onQueueCleared()
        } else if (!isPending && media3Index >= 0) {
            syncListener.onMediaItemRemoved(media3Index)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val state = _queueState.value
        if (fromIndex !in state.entries.indices || toIndex !in state.entries.indices) return
        if (fromIndex == toIndex) return

        val fromEntry = state.entries[fromIndex]
        val resolvedBefore = getResolvedEntries(state)
        val newState = QueueReducer.reorder(state, fromIndex, toIndex)
        val resolvedAfter = getResolvedEntries(newState)

        updateInternalState(newState)

        if (resolvedBefore != resolvedAfter) {
            val media3From = resolvedBefore.indexOfFirst { it.entryId == fromEntry.entryId }
            val media3To = resolvedAfter.indexOfFirst { it.entryId == fromEntry.entryId }
            if (media3From >= 0 && media3To >= 0 && media3From != media3To) {
                syncListener.onMediaItemMoved(media3From, media3To)
            }
        }
    }

    fun clearQueue() {
        updateInternalState(QueueReducer.clear(_queueState.value))
        syncListener.onQueueCleared()
    }

    /**
     * Clears everything from the queue except the currently playing song.
     */
    fun keepOnlyCurrent() {
        val state = _queueState.value
        val currentEntry = state.entries.find { it.entryId == state.currentEntryId }
        val resolvedEntries = getResolvedEntries(state)
        val media3CurrentIndex = if (currentEntry != null) resolvedEntries.indexOfFirst { it.entryId == currentEntry.entryId } else -1

        val newState = QueueReducer.keepOnlyCurrent(state)
        updateInternalState(newState)

        if (currentEntry == null) {
            syncListener.onQueueCleared()
        } else if (media3CurrentIndex >= 0) {
            // Remove resolved items after current index in descending order
            for (i in (resolvedEntries.size - 1) downTo (media3CurrentIndex + 1)) {
                syncListener.onMediaItemRemoved(i)
            }
            // Remove resolved items before current index in descending order
            for (i in (media3CurrentIndex - 1) downTo 0) {
                syncListener.onMediaItemRemoved(i)
            }
        }
    }

    fun updateSong(songId: String, transform: (Song) -> Song) {
        val state = _queueState.value
        val newEntries = state.entries.map { entry ->
            if (entry.song.id == songId) {
                entry.copy(song = transform(entry.song))
            } else {
                entry
            }
        }
        if (newEntries != state.entries) {
            updateInternalState(state.copy(
                entries = newEntries,
                revision = state.revision + 1
            ))
        }
    }

    /**
     * Returns a consistent snapshot of the current queue and active index.
     * Use this instead of reading separate flows to avoid revision divergence.
     */
    fun getQueueSnapshot(): Pair<List<Song>, Int> {
        val state = _queueState.value
        val songs = state.entries.map { it.song }
        val index = state.entries.indexOfFirst { it.entryId == state.currentEntryId }
        return songs to index
    }

    fun setCurrentIndex(index: Int) {
        val state = _queueState.value
        val entryId = state.entries.getOrNull(index)?.entryId
        updateInternalState(QueueReducer.setCurrentEntry(state, entryId))
    }

    /**
     * Restores shuffle history from a list of indices.
     * Used during snapshot restoration.
     */
    fun restoreShuffleHistory(indices: List<Int>) {
        val state = _queueState.value
        val history = indices.mapNotNull { idx ->
            state.entries.getOrNull(idx)?.entryId
        }
        updateInternalState(state.copy(
            shuffleHistory = history,
            revision = state.revision + 1
        ))
    }
}
