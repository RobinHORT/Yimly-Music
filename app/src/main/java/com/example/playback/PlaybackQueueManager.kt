package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    // Preserve existing runtime pending-add mechanism for now (Fix 1.3.2)
    private val pendingQueueAdds = mutableListOf<PendingQueueAdd>()

    private data class PendingQueueAdd(
        val token: String = UUID.randomUUID().toString(),
        val song: Song,
        var targetIndex: Int
    )

    /**
     * Internal helper to update the authoritative QueueState.
     * Projections (queue, currentQueueIndex) will update reactively.
     */
    private fun updateInternalState(newState: QueueState) {
        _queueState.value = newState
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
        
        if (newEntries == oldState.entries && newCurrentEntryId == oldState.currentEntryId) {
            return // No-op: do not increment revision
        }

        val newState = oldState.copy(
            entries = newEntries,
            currentEntryId = newCurrentEntryId,
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
        val newState = QueueReducer.addEntry(_queueState.value, song)
        updateInternalState(newState)

        if (isCurrentSongNull) {
            onPlaySong(song, newState.entries.map { it.song }, 0)
        } else {
            val pendingIndex = newState.entries.lastIndex
            val pendingAdd = PendingQueueAdd(song = song, targetIndex = pendingIndex)
            pendingQueueAdds.add(pendingAdd)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val mediaItem = mediaItemBuilder(song, false)
                    withContext(Dispatchers.Main.immediate) {
                        if (pendingAdd in pendingQueueAdds) {
                            pendingQueueAdds.remove(pendingAdd)
                            syncListener.onMediaItemAdded(pendingAdd.targetIndex.coerceIn(0, _queueState.value.entries.size), mediaItem)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main.immediate) {
                        pendingQueueAdds.remove(pendingAdd)
                    }
                }
            }
        }
    }

    fun removeSongFromQueue(songId: String, currentQueue: List<Song>, oldIndex: Int): Pair<List<Song>, Int> {
        val state = _queueState.value
        val indicesToRemove = state.entries.indices.filter { state.entries[it].song.id == songId }
        if (indicesToRemove.isEmpty()) return currentQueue to oldIndex

        var currentState = state
        // Process removals in descending order to preserve index validity for callbacks
        for (idx in indicesToRemove.sortedDescending()) {
            val entryId = currentState.entries[idx].entryId
            
            // Adjust runtime pending list (Fix 1.3.2 preservation)
            val iterator = pendingQueueAdds.iterator()
            while (iterator.hasNext()) {
                val add = iterator.next()
                if (add.targetIndex == idx) {
                    iterator.remove()
                } else if (add.targetIndex > idx) {
                    add.targetIndex--
                }
            }
            
            currentState = QueueReducer.removeEntry(currentState, entryId)
        }
        
        updateInternalState(currentState)
        
        // Notify listener for each removal
        for (idx in indicesToRemove.sortedDescending()) {
            syncListener.onMediaItemRemoved(idx)
        }
        
        val newSongs = currentState.entries.map { it.song }
        val newIndex = currentQueueIndex.value
        
        return newSongs to newIndex
    }

    fun playNext(song: Song, isCurrentSongNull: Boolean, currentQueueIndex: Int, onPlaySong: (Song, List<Song>, Int) -> Unit) {
        val state = _queueState.value
        val insertIndex = if (currentQueueIndex in state.entries.indices) currentQueueIndex + 1 else state.entries.size
        
        // Adjust runtime pending list (Fix 1.3.2 preservation)
        for (add in pendingQueueAdds) {
            if (add.targetIndex >= insertIndex) {
                add.targetIndex++
            }
        }

        val newState = QueueReducer.insertEntry(state, insertIndex, song)
        updateInternalState(newState)

        if (isCurrentSongNull) {
            onPlaySong(song, newState.entries.map { it.song }, insertIndex)
        } else {
            val pendingAdd = PendingQueueAdd(song = song, targetIndex = insertIndex)
            pendingQueueAdds.add(pendingAdd)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val mediaItem = mediaItemBuilder(song, false)
                    withContext(Dispatchers.Main.immediate) {
                        if (pendingAdd in pendingQueueAdds) {
                            pendingQueueAdds.remove(pendingAdd)
                            syncListener.onMediaItemAdded(pendingAdd.targetIndex.coerceIn(0, _queueState.value.entries.size), mediaItem)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main.immediate) {
                        pendingQueueAdds.remove(pendingAdd)
                    }
                }
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _queueState.value
        if (index !in state.entries.indices) return
        
        val entryId = state.entries[index].entryId

        // Adjust runtime pending list (Fix 1.3.2 preservation)
        val iterator = pendingQueueAdds.iterator()
        while (iterator.hasNext()) {
            val add = iterator.next()
            if (add.targetIndex == index) {
                iterator.remove()
            } else if (add.targetIndex > index) {
                add.targetIndex--
            }
        }

        val newState = QueueReducer.removeEntry(state, entryId)
        updateInternalState(newState)

        if (newState.entries.isEmpty()) {
            syncListener.onQueueCleared()
        } else {
            syncListener.onMediaItemRemoved(index)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val state = _queueState.value
        if (fromIndex !in state.entries.indices || toIndex !in state.entries.indices) return
        if (fromIndex == toIndex) return

        // Adjust runtime pending list (Fix 1.3.2 preservation)
        for (add in pendingQueueAdds) {
            if (add.targetIndex == fromIndex) {
                add.targetIndex = toIndex
            } else if (fromIndex < toIndex && add.targetIndex in (fromIndex + 1)..toIndex) {
                add.targetIndex--
            } else if (fromIndex > toIndex && add.targetIndex in toIndex until fromIndex) {
                add.targetIndex++
            }
        }

        val newState = QueueReducer.reorder(state, fromIndex, toIndex)
        updateInternalState(newState)
        
        syncListener.onMediaItemMoved(fromIndex, toIndex)
    }

    fun clearQueue() {
        pendingQueueAdds.clear()
        updateInternalState(QueueReducer.clear(_queueState.value))
        syncListener.onQueueCleared()
    }

    /**
     * Clears everything from the queue except the currently playing song.
     */
    fun keepOnlyCurrent() {
        val state = _queueState.value
        val currentEntry = state.entries.find { it.entryId == state.currentEntryId }
        if (currentEntry != null) {
            updateInternalState(state.copy(
                entries = listOf(currentEntry),
                revision = state.revision + 1
            ))
            // Current index becomes 0
        } else {
            clearQueue()
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
