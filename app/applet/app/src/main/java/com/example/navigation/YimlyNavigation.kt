package com.example.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.player.FullPlayerScreen
import com.example.ui.queue.QueueScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Player : Screen("player")
    object Queue : Screen("queue")
}

@Composable
fun YimlyNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToQueue = { navController.navigate(Screen.Queue.route) }
            )
        }
        composable(Screen.Player.route) {
            FullPlayerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Queue.route) {
            QueueScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
