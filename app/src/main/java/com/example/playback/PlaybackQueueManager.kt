package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

fun interface QueueChangeListener {
    fun onQueueChanged(state: QueueState)
}

class PlaybackQueueManager(
    private val coroutineScope: CoroutineScope,
    private val mediaItemBuilder: suspend (Song, Boolean) -> MediaItem,
    private val syncListener: QueueMediaSyncListener,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val queueChangeListener: QueueChangeListener? = null
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
     * Invokes queueChangeListener only when a committed state change occurs (revision increments).
     */
    private fun updateInternalState(newState: QueueState) {
        val oldState = _queueState.value
        _queueState.value = newState
        if (oldState.revision != newState.revision) {
            queueChangeListener?.onQueueChanged(newState)
        }
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

    /** Cleanup is allowed to outlive cancellation, but may only remove the exact operation it owns. */
    private suspend fun cleanupPendingAdd(pendingAdd: PendingQueueAdd) {
        withContext(NonCancellable + mainDispatcher) {
            val state = _queueState.value
            val tokenRegistration = state.pendingAdds.firstOrNull { it.token == pendingAdd.token }
            when {
                tokenRegistration?.entryId == pendingAdd.entryId -> {
                    updateInternalState(QueueReducer.removeEntry(state, pendingAdd.entryId))
                }
                tokenRegistration != null -> {
                    // The token belongs to this operation, but its entry identity no longer matches.
                    updateInternalState(QueueReducer.invalidatePendingAdd(state, pendingAdd.token))
                }
                else -> {
                    // The operation was already invalidated (for example, by queue replacement).
                    // Never remove an entry merely because a replacement reused its entryId.
                }
            }
        }
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

    /**
     * Selects a random candidate index and song from the queue (excluding the active index)
     * for shuffle activation or track advancement.
     */
    fun selectShuffleCandidate(): Pair<Int, Song>? {
        val state = _queueState.value
        val queue = state.entries.map { it.song }
        if (queue.isEmpty()) return null
        val currentQueueIndex = state.entries.indexOfFirst { it.entryId == state.currentEntryId }
        if (queue.size <= 1) return 0 to queue[0]
        val candidates = queue.indices.filter { it != currentQueueIndex }
        val nextIndex = candidates.randomOrNull() ?: currentQueueIndex
        return nextIndex to queue[nextIndex]
    }

    fun addToQueue(song: Song, isCurrentSongNull: Boolean, onPlaySong: (Song, List<Song>, Int) -> Unit) {
        if (isCurrentSongNull) {
            val newState = QueueReducer.addEntry(_queueState.value, song)
            updateInternalState(newState)
            onPlaySong(song, newState.entries.map { it.song }, 0)
        } else {
            val (newState, pendingAdd) = QueueReducer.addEntryWithPending(_queueState.value, song)
            updateInternalState(newState)

            coroutineScope.launch(ioDispatcher) {
                try {
                    val mediaItem = mediaItemBuilder(song, false)
                    withContext(mainDispatcher) {
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
                    cleanupPendingAdd(pendingAdd)
                    if (e is CancellationException) throw e
                    // Builder failures are reconciled before being contained by this async operation.
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

            coroutineScope.launch(ioDispatcher) {
                try {
                    val mediaItem = mediaItemBuilder(song, false)
                    withContext(mainDispatcher) {
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
                    cleanupPendingAdd(pendingAdd)
                    if (e is CancellationException) throw e
                    // Builder failures are reconciled before being contained by this async operation.
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
     * Calculates and applies the next queue index based on playback policy.
     * Returns the new index, or null if the queue has reached its end.
     */
    fun advanceNext(manual: Boolean, repeatMode: RepeatMode, isShuffle: Boolean): Int? {
        val state = _queueState.value
        if (state.entries.isEmpty()) return null
        
        val currentIndex = state.entries.indexOfFirst { it.entryId == state.currentEntryId }
        val nextIndex = when {
            !manual && repeatMode == RepeatMode.ONE -> currentIndex
            isShuffle && state.entries.size > 1 -> {
                state.entries.indices.filter { it != currentIndex }.randomOrNull() ?: currentIndex
            }
            isShuffle -> currentIndex
            repeatMode == RepeatMode.ALL -> (currentIndex + 1) % state.entries.size
            currentIndex + 1 < state.entries.size -> currentIndex + 1
            else -> return null
        }
        
        var nextState = state
        if (isShuffle && nextIndex != currentIndex && currentIndex in state.entries.indices) {
            val currentEntryId = state.entries[currentIndex].entryId
            if (!state.shuffleHistory.contains(currentEntryId)) {
                nextState = nextState.copy(
                    shuffleHistory = nextState.shuffleHistory + currentEntryId
                )
            }
        }
        
        val nextEntryId = nextState.entries.getOrNull(nextIndex)?.entryId
        nextState = QueueReducer.setCurrentEntry(nextState, nextEntryId)
        
        updateInternalState(nextState)
        return nextIndex
    }

    /**
     * Calculates and applies the previous queue index based on playback policy.
     * Returns the new index, or null if the queue is empty.
     */
    fun advancePrevious(isShuffle: Boolean): Int? {
        val state = _queueState.value
        if (state.entries.isEmpty()) return null
        
        val currentIndex = state.entries.indexOfFirst { it.entryId == state.currentEntryId }
        
        val prevIndex = if (isShuffle && state.shuffleHistory.isNotEmpty()) {
            val lastEntryId = state.shuffleHistory.last()
            state.entries.indexOfFirst { it.entryId == lastEntryId }.takeIf { it >= 0 } 
                ?: (currentIndex - 1).coerceAtLeast(0)
        } else if (currentIndex - 1 >= 0) {
            currentIndex - 1
        } else {
            state.entries.size - 1
        }
        
        var nextState = state
        if (isShuffle && state.shuffleHistory.isNotEmpty()) {
            nextState = nextState.copy(
                shuffleHistory = nextState.shuffleHistory.dropLast(1)
            )
        }
        
        val prevEntryId = nextState.entries.getOrNull(prevIndex)?.entryId
        nextState = QueueReducer.setCurrentEntry(nextState, prevEntryId)
        
        updateInternalState(nextState)
        return prevIndex
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
     * Synchronises the current entry ID with the actual active item in ExoPlayer.
     * This is used for natural transitions where ExoPlayer has already moved to the next item.
     * Returns true if the current entry actually changed.
     */
    fun syncCurrentEntryFromPlayer(index: Int, mediaId: String?): Boolean {
        if (mediaId == null) return false
        val state = _queueState.value
        val resolvedEntries = getResolvedEntries(state)
        
        // Find the entry at the reported index in the resolved entries list.
        val targetEntry = resolvedEntries.getOrNull(index) ?: return false
        
        // Safety check: verify mediaId matches.
        // In Yimly, mediaId is either songId, songId + "_inst", or an Android Auto formatted ID.
        val expectedSongId = targetEntry.song.id
        val actualSongId = when {
            mediaId.startsWith("playlist_song|") -> mediaId.substringAfterLast("|").removeSuffix("_inst")
            mediaId.startsWith("favorite_song|") ||
                mediaId.startsWith("recent_song|") ||
                mediaId.startsWith("all_song|") -> mediaId.substringAfter("|").removeSuffix("_inst")
            else -> mediaId.removeSuffix("_inst")
        }
        if (expectedSongId != actualSongId) {
            // Stale or mismatched transition: index does not match mediaId.
            return false
        }
        
        if (state.currentEntryId == targetEntry.entryId) return false
        
        updateInternalState(QueueReducer.setCurrentEntry(state, targetEntry.entryId))
        return true
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
