package com.example.data.repository

import com.example.data.api.YimlyApiService
import com.example.data.models.GuestSongRequest
import com.example.data.models.Song
import com.example.data.models.YimlySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SessionRepository(
    private val apiService: YimlyApiService,
    private val musicRepository: MusicRepository
) {

    private val _activeSession = MutableStateFlow<YimlySession?>(null)
    val activeSession: StateFlow<YimlySession?> = _activeSession.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    suspend fun hostSession(hostName: String): Result<YimlySession> = withContext(Dispatchers.IO) {
        try {
            val session = apiService.createSession()
            _activeSession.value = session
            _isHost.value = true
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinSession(code: String, guestName: String): Result<YimlySession> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        try {
            val remoteSession = apiService.joinSession(cleanCode)
            _activeSession.value = remoteSession
            _isHost.value = false
            Result.success(_activeSession.value!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestSong(song: Song, requestedBy: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = _activeSession.value ?: return@withContext Result.failure(IllegalStateException("No active session"))
        try {
            apiService.requestSongInSession(session.roomCode, song)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Note: the following methods would ideally be API calls as well, but for now we leave local state management empty or report failure if unsupported.
    fun approveRequest(request: GuestSongRequest) {
        // Requires backend endpoint implementation
    }

    fun dismissRequest(requestId: String) {
        // Requires backend endpoint implementation
    }

    fun toggleAllowGuestRequests() {
        // Requires backend endpoint implementation
    }

    fun leaveSession() {
        _activeSession.value = null
        _isHost.value = false
    }
}
