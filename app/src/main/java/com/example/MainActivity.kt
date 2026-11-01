package com.example

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.navigation.YimlyNavigation
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.YimlyTheme

class MainActivity : ComponentActivity() {
    private val openNowPlayingRequest = mutableStateOf(false)
    private val navigateToSearchRequest = mutableStateOf(false)

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.example.EXTRA_OPEN_NOW_PLAYING"
        const val EXTRA_NAVIGATE_TO_SEARCH = "EXTRA_NAVIGATE_TO_SEARCH"
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openNowPlayingRequest.value = true
            intent.removeExtra(EXTRA_OPEN_NOW_PLAYING)
        }
        if (intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_SEARCH, false) == true) {
            navigateToSearchRequest.value = true
            intent.removeExtra(EXTRA_NAVIGATE_TO_SEARCH)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }


    private val viewModel: MainViewModel by viewModels {
        val app = application as YimlyApplication
        MainViewModelFactory(
            musicRepository = app.musicRepository,
            playbackManager = app.playbackManager,
            authRepository = app.authRepository,
            preferencesManager = app.preferencesManager,
            downloadNotificationManager = app.downloadNotificationManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeNotificationIntent(intent)

        setContent {
            YimlyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    YimlyNavigation(
                        viewModel = viewModel,
                        openNowPlayingRequest = openNowPlayingRequest.value,
                        onNowPlayingRequestConsumed = { openNowPlayingRequest.value = false },
                        navigateToSearchRequest = navigateToSearchRequest.value,
                        onNavigateToSearchConsumed = { navigateToSearchRequest.value = false }
                    )
                }
            }
        }
    }
}
