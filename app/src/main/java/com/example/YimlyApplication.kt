package com.example

import android.app.Application
import com.example.data.api.NetworkModule
import com.example.data.datastore.PreferencesManager
import com.example.data.db.YimlyDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.MusicRepository
import com.example.data.repository.SessionRepository
import com.example.playback.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class YimlyApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferencesManager by lazy {
        PreferencesManager(this)
    }

    val database by lazy {
        YimlyDatabase.getDatabase(this)
    }

    val networkModule by lazy {
        NetworkModule(preferencesManager)
    }

    val musicRepository by lazy {
        MusicRepository(
            musicDao = database.musicDao(),
            apiService = networkModule.apiService,
            coroutineScope = applicationScope
        )
    }

    val authRepository by lazy {
        AuthRepository(
            apiService = networkModule.apiService,
            preferencesManager = preferencesManager
        )
    }

    val sessionRepository by lazy {
        SessionRepository(
            apiService = networkModule.apiService,
            musicRepository = musicRepository
        )
    }

    val playbackManager by lazy {
        PlaybackManager(
            context = this,
            musicRepository = musicRepository,
            preferencesManager = preferencesManager,
            coroutineScope = applicationScope
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}
