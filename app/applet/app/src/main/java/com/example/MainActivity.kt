package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navigation.YimlyNavigation
import com.example.ui.MainViewModel
import com.example.ui.theme.YimlyMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YimlyMusicTheme {
                val viewModel: MainViewModel = viewModel()
                YimlyNavigation(viewModel = viewModel)
            }
        }
    }
}
