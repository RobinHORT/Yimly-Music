package com.example.playback

import com.example.data.models.Song
import java.util.UUID

data class ConsumeResult(
    val newState: QueueState,
    val media3InsertionIndex: Int?
)

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
     * Atomically creates one QueueEntry and its corresponding PendingQueueAdd at the end of the queue.
     * Increments revision exactly once.
     */
    fun addEntryWithPending(
        state: QueueState,
        song: Song,
        token: String = UUID.randomUUID().toString()
    ): Pair<QueueState, PendingQueueAdd> {
        val entry = QueueEntry(song = song)
        val targetIndex = state.entries.size
        val pendingAdd = PendingQueueAdd(
            token = token,
            entryId = entry.entryId,
            song = song,
            targetIndex = targetIndex
        )
        val newState = state.copy(
            entries = state.entries + entry,
            pendingAdds = state.pendingAdds + pendingAdd,
            revision = state.revision + 1
        )
        return newState to pendingAdd
    }

    /**
     * Atomically inserts one QueueEntry at index, correctly adjusts existing pending additions,
     * and registers the new PendingQueueAdd referencing the newly created entryId.
     * The new pending addition's targetIndex is not shifted by its own insertion.
     * Increments revision exactly once.
     */
    fun insertEntryWithPending(
        state: QueueState,
        index: Int,
        song: Song,
        token: String = UUID.randomUUID().toString()
    ): Pair<QueueState, PendingQueueAdd> {
        val safeIndex = index.coerceIn(0, state.entries.size)
        val entry = QueueEntry(song = song)
        
        val newEntries = state.entries.toMutableList()
        newEntries.add(safeIndex, entry)

        // Adjust existing pending adds for the insertion
        val shiftedExistingPending = state.pendingAdds.map { add ->
            if (add.targetIndex >= safeIndex) add.copy(targetIndex = add.targetIndex + 1)
            else add
        }

        val newPendingAdd = PendingQueueAdd(
            token = token,
            entryId = entry.entryId,
            song = song,
            targetIndex = safeIndex
        )

        val newState = state.copy(
            entries = newEntries.toList(),
            pendingAdds = shiftedExistingPending + newPendingAdd,
            revision = state.revision + 1
        )
        return newState to newPendingAdd
    }

    /**
     * Atomically consumes a pending addition:
     * - Locates the pending operation by token.
     * - Verifies its associated entry still exists.
     * - Calculates its correct Media3 timeline insertion index:
     *   media3InsertionIndex = queueEntryIndex - unresolvedPrecedingPendingCount
     * - Removes the pending operation.
     * - Returns the updated QueueState and insertion index.
     * - Returns no insertion index if the operation was cancelled, already consumed, or its entry no longer exists.
     */
    fun consumePendingAdd(state: QueueState, token: String): ConsumeResult {
        val pending = state.pendingAdds.find { it.token == token }
        if (pending == null) {
            return ConsumeResult(state, null)
        }

        val queueEntryIndex = state.entries.indexOfFirst { it.entryId == pending.entryId }
        if (queueEntryIndex == -1) {
            val updatedState = state.copy(
                pendingAdds = state.pendingAdds.filter { it.token != token },
                revision = state.revision + 1
            )
            return ConsumeResult(updatedState, null)
        }

        val otherPendingEntryIds = state.pendingAdds
            .filter { it.token != token }
            .map { it.entryId }
            .toSet()

        var unresolvedPrecedingPendingCount = 0
        for (i in 0 until queueEntryIndex) {
            if (state.entries[i].entryId in otherPendingEntryIds) {
                unresolvedPrecedingPendingCount++
            }
        }

        val media3InsertionIndex = (queueEntryIndex - unresolvedPrecedingPendingCount).coerceAtLeast(0)

        val updatedState = state.copy(
            pendingAdds = state.pendingAdds.filter { it.token != token },
            revision = state.revision + 1
        )
        return ConsumeResult(updatedState, media3InsertionIndex)
    }

    /**
     * Removes an entry by its stable entryId.
     * Updates currentEntryId if the removed entry was active.
     * Cleans up shuffleHistory.
     * Cancels any pending addition associated with the removed entryId.
     * Shifts remaining pending target indices.
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

        // Cancel pending additions associated with the removed entryId or index,
        // and shift remaining pending adds targeting indices after this removal.
        val newPendingAdds = state.pendingAdds
            .filter { it.entryId != entryId && it.targetIndex != entryIndex }
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
     * Retains only the active entry and clears all pending additions associated with the upcoming queue.
     */
    fun keepOnlyCurrent(state: QueueState): QueueState {
        val currentEntry = state.entries.find { it.entryId == state.currentEntryId }
        if (currentEntry == null) {
            return clear(state)
        }
        if (state.entries.size == 1 && state.entries[0].entryId == currentEntry.entryId && state.pendingAdds.isEmpty()) {
            return state // No-op
        }
        return state.copy(
            entries = listOf(currentEntry),
            shuffleHistory = state.shuffleHistory.filter { it == currentEntry.entryId },
            pendingAdds = emptyList(),
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
     * Clears everything and increments revision if not already empty.
     */
    fun clear(state: QueueState): QueueState {
        if (state.entries.isEmpty() && state.currentEntryId == null && state.pendingAdds.isEmpty() && state.shuffleHistory.isEmpty()) return state
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
