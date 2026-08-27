package com.example.data.repository

import com.example.data.api.YimlyApiService
import com.example.data.datastore.PreferencesManager
import com.example.data.models.AuthState
import com.example.data.models.LoginRequest
import com.example.data.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

data class AuthSession(
    val user: UserProfile,
    val token: String,
    val isPersistent: Boolean
)

class AuthRepository(
    private val apiService: YimlyApiService,
    private val preferencesManager: PreferencesManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val _inMemorySession = MutableStateFlow<AuthSession?>(null)
    private val _authError = MutableStateFlow<String?>(null)

    @Suppress("UNCHECKED_CAST")
    val authStateFlow: Flow<AuthState> = combine(
        _inMemorySession,
        preferencesManager.authTokenFlow,
        preferencesManager.usernameFlow,
        preferencesManager.userRoleFlow,
        preferencesManager.isAdminFlow,
        _authError
    ) { flows ->
        val inMem = flows[0] as? AuthSession
        val token = flows[1] as? String
        val username = flows[2] as? String
        val role = flows[3] as? String
        val isAdmin = flows[4] as? Boolean ?: false
        val error = flows[5] as? String

        if (inMem != null) {
            AuthState.Authenticated(
                user = inMem.user,
                token = inMem.token
            )
        } else if (!token.isNullOrBlank() && !username.isNullOrBlank()) {
            AuthState.Authenticated(
                user = UserProfile(
                    id = "usr_active",
                    username = username,
                    email = null,
                    displayName = username.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    avatarUrl = null,
                    rawIsAdmin = isAdmin,
                    role = role
                ),
                token = token
            )
        } else {
            AuthState.Unauthenticated(errorMessage = error)
        }
    }

    init {
        // Validate persisted session asynchronously on startup
        coroutineScope.launch {
            val token = preferencesManager.authTokenFlow.firstOrNull()
            if (!token.isNullOrBlank()) {
                validatePersistedSession(token)
            }
        }
    }

    suspend fun getActiveToken(): String? {
        val inMemToken = _inMemorySession.value?.token
        if (!inMemToken.isNullOrBlank()) return inMemToken
        return preferencesManager.authTokenFlow.firstOrNull()
    }

    private suspend fun validatePersistedSession(token: String) {
        try {
            val profile = apiService.getProfile()
            preferencesManager.saveAuthSession(
                token = token,
                userId = profile.id,
                username = profile.username,
                email = profile.email,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
                role = profile.role,
                isAdmin = profile.isAdmin,
                rememberMe = true
            )
            _inMemorySession.value = AuthSession(
                user = profile,
                token = token,
                isPersistent = true
            )
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                // Token expired on server
                logout()
                _authError.value = "Your session has expired. Please sign in again."
            }
        } catch (_: Exception) {
            // Keep offline / cached credentials active if network is temporarily unreachable
        }
    }

    suspend fun login(username: String, password: String, rememberMe: Boolean = true): Result<UserProfile> = withContext(Dispatchers.IO) {
        _authError.value = null
        try {
            val response = apiService.login(LoginRequest(username = username.trim(), password = password))
            val user = response.user
            val token = response.token

            if (rememberMe) {
                preferencesManager.saveAuthSession(
                    token = token,
                    userId = user.id,
                    username = user.username,
                    email = user.email,
                    displayName = user.displayName,
                    avatarUrl = user.avatarUrl,
                    role = user.role,
                    isAdmin = user.isAdmin,
                    rememberMe = true
                )
            } else {
                // Clear any stored credentials from disk so they do not persist after app close
                preferencesManager.saveAuthSession(
                    token = "",
                    userId = "",
                    username = "",
                    email = null,
                    rememberMe = false
                )
            }

            _inMemorySession.value = AuthSession(
                user = user,
                token = token,
                isPersistent = rememberMe
            )

            Result.success(user)
        } catch (e: HttpException) {
            val msg = when (e.code()) {
                401, 403 -> "Invalid username or password. Please try again."
                404 -> "Authentication endpoint not found on server."
                500, 502, 503 -> "Yimly server is temporarily unavailable (Error ${e.code()})."
                else -> "Sign in failed: ${e.message()}"
            }
            _authError.value = msg
            Result.failure(Exception(msg, e))
        } catch (e: IOException) {
            val msg = "Unable to connect to Yimly server. Please check your network."
            _authError.value = msg
            Result.failure(Exception(msg, e))
        } catch (e: Exception) {
            val msg = e.message ?: "Authentication failed. Please check your credentials."
            _authError.value = msg
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        _inMemorySession.value = null
        try {
            apiService.logout()
        } catch (_: Exception) {
            // Ignore network errors during logout
        }
        preferencesManager.clearAuthSession()
    }
}
