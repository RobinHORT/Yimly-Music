package com.example

import android.os.Bundle
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

    private val viewModel: MainViewModel by viewModels {
        val app = application as YimlyApplication
        MainViewModelFactory(
            musicRepository = app.musicRepository,
            playbackManager = app.playbackManager,
            authRepository = app.authRepository,
            sessionRepository = app.sessionRepository,
            preferencesManager = app.preferencesManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YimlyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    YimlyNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
