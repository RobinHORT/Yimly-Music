package com.example.playback

import com.example.data.models.Song

object QueueReducer {

    /**
     * Creates a new QueueEntry and adds it to the entries list.
     * Increments revision.
     */
    fun addEntry(state: QueueState, song: Song): QueueState {
        val entry = QueueEntry(song = song)
        return state.copy(
            entries = state.entries + entry,
            revision = state.revision + 1
        )
    }

    /**
     * Removes an entry by its stable entryId.
     * Updates currentEntryId if the removed entry was active.
     * Cleans up shuffleHistory.
     * Increments revision if removal actually occurred.
     */
    fun removeEntry(state: QueueState, entryId: String): QueueState {
        val entryIndex = state.entries.indexOfFirst { it.entryId == entryId }
        if (entryIndex == -1) return state

        val newEntries = state.entries.filter { it.entryId != entryId }
        
        var nextEntryId = state.currentEntryId
        if (state.currentEntryId == entryId) {
            nextEntryId = when {
                newEntries.isNotEmpty() -> newEntries[entryIndex.coerceAtMost(newEntries.lastIndex)].entryId
                else -> null
            }
        }

        // Clean up shuffle history: remove the entryId if present
        val newShuffleHistory = state.shuffleHistory.filter { it != entryId }

        // Also update pending adds: remove any add targeting this specific absolute index
        // or shift indices of adds after this removal.
        // NOTE: In the future SSOT, pendingAdds are associated with song resolution.
        val newPendingAdds = state.pendingAdds.filter { it.targetIndex != entryIndex }
            .map { add ->
                if (add.targetIndex > entryIndex) add.copy(targetIndex = add.targetIndex - 1)
                else add
            }

        return state.copy(
            entries = newEntries,
            currentEntryId = nextEntryId,
            shuffleHistory = newShuffleHistory,
            pendingAdds = newPendingAdds,
            revision = state.revision + 1
        )
    }

    /**
     * Moves an entry from fromIndex to toIndex.
     * Preserves stable entryId identities.
     * Adjusts pendingAdds target indices.
     * Increments revision.
     */
    fun reorder(state: QueueState, fromIndex: Int, toIndex: Int): QueueState {
        if (fromIndex !in state.entries.indices || toIndex !in state.entries.indices) return state
        if (fromIndex == toIndex) return state
        
        val mutableEntries = state.entries.toMutableList()
        val entry = mutableEntries.removeAt(fromIndex)
        mutableEntries.add(toIndex, entry)

        // Adjust pending adds for the move
        val newPendingAdds = state.pendingAdds.map { add ->
            when {
                add.targetIndex == fromIndex -> add.copy(targetIndex = toIndex)
                fromIndex < toIndex && add.targetIndex in (fromIndex + 1)..toIndex -> add.copy(targetIndex = add.targetIndex - 1)
                fromIndex > toIndex && add.targetIndex in toIndex until fromIndex -> add.copy(targetIndex = add.targetIndex + 1)
                else -> add
            }
        }
        
        return state.copy(
            entries = mutableEntries.toList(),
            pendingAdds = newPendingAdds,
            revision = state.revision + 1
        )
    }

    /**
     * Updates the current entry by ID.
     * Increments revision if the ID actually changed.
     */
    fun setCurrentEntry(state: QueueState, entryId: String?): QueueState {
        if (state.currentEntryId == entryId) return state
        if (entryId != null && state.entries.none { it.entryId == entryId }) return state
        
        return state.copy(
            currentEntryId = entryId,
            revision = state.revision + 1
        )
    }

    /**
     * Clears everything and resets revision.
     */
    fun clear(state: QueueState): QueueState {
        if (state.entries.isEmpty() && state.currentEntryId == null && state.pendingAdds.isEmpty()) return state
        return state.copy(
            entries = emptyList(),
            currentEntryId = null,
            shuffleHistory = emptyList(),
            pendingAdds = emptyList(),
            revision = state.revision + 1
        )
    }

    /**
     * Registers a new pending addition.
     * Fails if token is already present.
     */
    fun registerPendingAdd(state: QueueState, add: PendingQueueAdd): QueueState {
        if (state.pendingAdds.any { it.token == add.token }) return state
        return state.copy(
            pendingAdds = state.pendingAdds + add,
            revision = state.revision + 1
        )
    }

    /**
     * Removes a pending addition (either resolved or cancelled).
     */
    fun invalidatePendingAdd(state: QueueState, token: String): QueueState {
        val exists = state.pendingAdds.any { it.token == token }
        if (!exists) return state
        return state.copy(
            pendingAdds = state.pendingAdds.filter { it.token != token },
            revision = state.revision + 1
        )
    }

    /**
     * Adds an entry at a specific index (e.g. for Play Next or pending resolution).
     */
    fun insertEntry(state: QueueState, index: Int, song: Song): QueueState {
        val safeIndex = index.coerceIn(0, state.entries.size)
        val entry = QueueEntry(song = song)
        
        val newEntries = state.entries.toMutableList()
        newEntries.add(safeIndex, entry)

        // Adjust pending adds for the insertion
        val newPendingAdds = state.pendingAdds.map { add ->
            if (add.targetIndex >= safeIndex) add.copy(targetIndex = add.targetIndex + 1)
            else add
        }

        return state.copy(
            entries = newEntries.toList(),
            pendingAdds = newPendingAdds,
            revision = state.revision + 1
        )
    }
}
