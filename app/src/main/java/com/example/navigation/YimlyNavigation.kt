package com.example.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.models.AuthState
import com.example.data.models.Song
import com.example.ui.MainViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.home.HomeScreen
import com.example.ui.music.AlbumDetailScreen
import com.example.ui.music.ArtistDetailScreen
import com.example.ui.music.MusicLibraryScreen
import com.example.ui.music.PlaylistDetailScreen
import com.example.ui.player.FullPlayerScreen
import com.example.ui.player.MiniPlayer
import com.example.ui.search.SearchScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.components.YimlyMicIcon
import com.example.ui.components.YimlyTopBar
import kotlinx.coroutines.launch

@Composable
fun YimlyNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    openNowPlayingRequest: Boolean = false,
    onNowPlayingRequestConsumed: () -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val currentLyricsData by viewModel.currentLyricsData.collectAsState()
    val currentSongOffset by viewModel.currentSongOffset.collectAsState()
    val lyricsConfig by viewModel.lyricsConfig.collectAsState()
    val lyricsPositionMs by viewModel.lyricsPositionMs.collectAsState()

    val allSongs by viewModel.allSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val albums by viewModel.allAlbums.collectAsState()
    val artists by viewModel.allArtists.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    LaunchedEffect(openNowPlayingRequest, authState, playbackInfo.currentSong) {
        if (openNowPlayingRequest && authState is AuthState.Authenticated && playbackInfo.currentSong != null) {
            isPlayerExpanded = true
            onNowPlayingRequestConsumed()
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated && currentRoute != Screen.Login.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        } else if (authState is AuthState.Authenticated && currentRoute == Screen.Login.route) {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BackgroundDark)) {
        val isWideScreen = maxWidth > 720.dp
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val coroutineScope = rememberCoroutineScope()

        BackHandler(enabled = drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isWideScreen && currentRoute != Screen.Login.route,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = SurfaceDark,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            YimlyMicIcon(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Yimly Music",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = YimlyPink
                            )
                        }
                        userProfile?.let { user ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = user.username,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            Text(
                                text = if (isAdmin) "Administrator" else (user.role?.ifBlank { "User" } ?: "User"),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        }
                    }
                    HorizontalDivider(color = SurfaceBorderDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationDrawerItem(
                            label = { Text(item.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    tint = if (selected) YimlyPink else TextSecondaryDark
                                )
                            },
                            selected = selected,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = YimlyPink.copy(alpha = 0.15f),
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = YimlyPink,
                                unselectedTextColor = TextSecondaryDark
                            ),
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag("drawer_item_${item.route}")
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = SurfaceBorderDark)
                    NavigationDrawerItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Now Playing Queue")
                                if (playbackInfo.queue.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = YimlyPink.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "${playbackInfo.queue.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = YimlyPink,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Queue",
                                tint = if (isPlayerExpanded) YimlyPink else TextSecondaryDark
                            )
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            if (playbackInfo.currentSong != null) {
                                isPlayerExpanded = true
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = YimlyPink.copy(alpha = 0.15f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = YimlyPink,
                            unselectedTextColor = TextSecondaryDark
                        ),
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_item_queue")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
                topBar = {
                    if (!isWideScreen && currentRoute != Screen.Login.route && currentRoute in listOf(Screen.Home.route, Screen.Music.route, Screen.Search.route, Screen.Settings.route)) {
                        YimlyTopBar(
                            title = when (currentRoute) {
                                Screen.Home.route -> "Yimly"
                                Screen.Music.route -> "Library"
                                Screen.Search.route -> "Search"
                                Screen.Settings.route -> "Settings"
                                else -> "Yimly"
                            },
                            onOpenDrawer = {
                                coroutineScope.launch { drawerState.open() }
                            },
                        )
                    }
                },
            bottomBar = {
                if (!isWideScreen && currentRoute != Screen.Login.route) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                        // Mini Player
                        if (playbackInfo.currentSong != null) {
                            MiniPlayer(
                                playbackInfo = playbackInfo,
                                playbackPositionFlow = viewModel.playbackPositionMs,
                                onMiniPlayerClick = { isPlayerExpanded = true },
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onNext = { viewModel.next() },
                                onPrevious = { viewModel.previous() },
                                onToggleFavorite = {
                                    playbackInfo.currentSong?.let { viewModel.toggleFavorite(it.id) }
                                }
                            )
                        }

                        // Bottom Navigation
                        NavigationBar(
                            containerColor = SurfaceDark,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (currentRoute != item.route) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.title
                                        )
                                    },
                                    label = { Text(item.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = YimlyPink,
                                        selectedTextColor = YimlyPink,
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = TextSecondaryDark,
                                        unselectedTextColor = TextSecondaryDark
                                    ),
                                    modifier = Modifier.testTag("nav_item_${item.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Wide Screen Navigation Rail
                if (isWideScreen && currentRoute != Screen.Login.route) {
                    NavigationRail(
                        containerColor = SurfaceDark,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("wide_nav_rail")
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = YimlyPink,
                                    selectedTextColor = YimlyPink,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark
                                )
                            )
                        }
                    }
                }

                // NavHost Routes
                val startDestination = remember {
                    if (viewModel.authState.value is AuthState.Authenticated) Screen.Home.route else Screen.Login.route
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.weight(1f).padding(innerPadding)
                ) {
                    // 1. Home
                    composable(Screen.Home.route) {
                        HomeScreen(
                            userProfile = userProfile,
                            allSongs = allSongs,
                            recentlyPlayed = recentlyPlayed,
                            recentlyAdded = recentlyAdded,
                            favoriteSongs = favoriteSongs,
                            albums = albums,
                            artists = artists,
                            playlists = playlists,
                            currentSong = playbackInfo.currentSong,
                            isPlaying = playbackInfo.isPlaying,
                            onSongClick = { song, list -> viewModel.playSong(song, list, list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)) },
                            onAlbumClick = { album -> navController.navigate(Screen.AlbumDetail.createRoute(album.id)) },
                            onArtistClick = { artist -> navController.navigate(Screen.ArtistDetail.createRoute(artist.id)) },
                            onPlaylistClick = { playlist -> navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                            onFavoritesClick = {
                                navController.navigate(Screen.Music.route)
                            },
                            onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                            onPlayNext = { song -> viewModel.playNext(song) },
                            onAddToQueue = { song -> viewModel.addToQueue(song) },
                            onAddToNowPlaying = { song -> viewModel.addToQueue(song) },
                            onAddToPlaylist = { song -> songToAddToPlaylist = song }
                        )
                    }

                    // 2. Music Library
                    composable(Screen.Music.route) {
                        MusicLibraryScreen(
                            allSongs = allSongs,
                            albums = albums,
                            artists = artists,
                            playlists = playlists,
                            favoriteSongs = favoriteSongs,
                            currentSong = playbackInfo.currentSong,
                            isPlaying = playbackInfo.isPlaying,
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onAlbumClick = { album -> navController.navigate(Screen.AlbumDetail.createRoute(album.id)) },
                            onArtistClick = { artist -> navController.navigate(Screen.ArtistDetail.createRoute(artist.id)) },
                            onPlaylistClick = { playlist -> navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                            onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                            onPlayNext = { song -> viewModel.playNext(song) },
                            onAddToQueue = { song -> viewModel.addToQueue(song) },
                            onAddToNowPlaying = { song -> viewModel.addToQueue(song) },
                            onAddToPlaylist = { song -> songToAddToPlaylist = song },
                            onCreatePlaylist = { name, desc, isPublic -> viewModel.createPlaylist(name, desc, isPublic) }
                        )
                    }

                    // 3. Search
                    composable(Screen.Search.route) {
                        SearchScreen(
                            searchQuery = searchQuery,
                            onQueryChange = { viewModel.searchQuery.value = it },
                            searchResult = searchResult,
                            currentSong = playbackInfo.currentSong,
                            isPlaying = playbackInfo.isPlaying,
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onAlbumClick = { album -> navController.navigate(Screen.AlbumDetail.createRoute(album.id)) },
                            onArtistClick = { artist -> navController.navigate(Screen.ArtistDetail.createRoute(artist.id)) },
                            onPlaylistClick = { playlist -> navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                            onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                            onPlayNext = { song -> viewModel.playNext(song) },
                            onAddToQueue = { song -> viewModel.addToQueue(song) },
                            onAddToNowPlaying = { song -> viewModel.addToQueue(song) },
                            onAddToPlaylist = { song -> songToAddToPlaylist = song }
                        )
                    }

                    // 4. Settings
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            userProfile = userProfile,
                            serverUrl = serverUrl,
                            lyricsConfig = lyricsConfig,
                            onUpdateServerUrl = { viewModel.updateServerUrl(it) },
                            onSaveLyricsConfig = { viewModel.saveLyricsConfig(it) },
                            onLogout = { viewModel.logout() },
                            onLoginClick = { navController.navigate(Screen.Login.route) },
                            onSyncLibrary = { viewModel.syncLibrary() }
                        )
                    }

                    // 6. Login
                    composable(Screen.Login.route) {
                        LoginScreen(
                            isLoading = isLoading,
                            errorMessage = authError,
                            onLogin = { user, pass, rememberMe ->
                                viewModel.login(user, pass, rememberMe)
                            }
                        )
                    }

                    // 7. Album Detail
                    composable(
                        route = Screen.AlbumDetail.route,
                        arguments = listOf(navArgument("albumId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val albumId = backStackEntry.arguments?.getString("albumId")
                        val album = albums.firstOrNull { it.id == albumId }
                        if (album != null) {
                            val albumSongs by viewModel.getSongsForAlbum(album.title).collectAsState(initial = emptyList())
                            AlbumDetailScreen(
                                album = album,
                                songs = albumSongs,
                                currentSong = playbackInfo.currentSong,
                                isPlaying = playbackInfo.isPlaying,
                                onBack = { navController.popBackStack() },
                                onSongClick = { song, list -> viewModel.playSong(song, list) },
                                onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                                onPlayNext = { song -> viewModel.playNext(song) },
                                onAddToQueue = { song -> viewModel.addToQueue(song) },
                                onAddToNowPlaying = { song -> viewModel.addToQueue(song) },
                                onAddToPlaylist = { song -> songToAddToPlaylist = song }
                            )
                        }
                    }

                    // 8. Artist Detail
                    composable(
                        route = Screen.ArtistDetail.route,
                        arguments = listOf(navArgument("artistId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val artistId = backStackEntry.arguments?.getString("artistId")
                        val artist = artists.firstOrNull { it.id == artistId }
                        if (artist != null) {
                            val artistSongs by viewModel.getSongsForArtist(artist.name).collectAsState(initial = emptyList())
                            ArtistDetailScreen(
                                artist = artist,
                                songs = artistSongs,
                                currentSong = playbackInfo.currentSong,
                                isPlaying = playbackInfo.isPlaying,
                                onBack = { navController.popBackStack() },
                                onSongClick = { song, list -> viewModel.playSong(song, list) },
                                onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                                onPlayNext = { song -> viewModel.playNext(song) },
                                onAddToQueue = { song -> viewModel.addToQueue(song) },
                                onAddToNowPlaying = { song -> viewModel.addToQueue(song) },
                                onAddToPlaylist = { song -> songToAddToPlaylist = song }
                            )
                        }
                    }

                    // 9. Playlist Detail
                    composable(
                        route = Screen.PlaylistDetail.route,
                        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId")
                        LaunchedEffect(playlistId) {
                            if (playlistId != null) {
                                viewModel.refreshPlaylistDetail(playlistId)
                            }
                        }
                        val playlist = playlists.firstOrNull { it.id == playlistId }
                        if (playlist != null) {
                            val playlistSongs by viewModel.getSongsForPlaylist(playlist.id).collectAsState(initial = emptyList())
                            val isUserAdmin = isAdmin || userProfile?.isAdmin == true || userProfile?.role.equals("administrator", ignoreCase = true) || userProfile?.role.equals("admin", ignoreCase = true)
                            PlaylistDetailScreen(
                                playlist = playlist,
                                songs = playlistSongs,
                                currentSong = playbackInfo.currentSong,
                                isPlaying = playbackInfo.isPlaying,
                                isAdmin = isUserAdmin,
                                onBack = { navController.popBackStack() },
                                onSongClick = { song, list -> viewModel.playSong(song, list) },
                                onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                                onPlayNext = { song -> viewModel.playNext(song) },
                                onAddToQueue = { song -> viewModel.addToQueue(song) },
                                onAddToNowPlaying = { song -> viewModel.addToQueue(song) },
                                onAddToPlaylist = { song -> songToAddToPlaylist = song },
                                onDeletePlaylist = if (playlist.isOwner || playlist.permission == "owner" || isUserAdmin) {
                                    {
                                        viewModel.deletePlaylist(playlist.id) {
                                            navController.popBackStack()
                                        }
                                    }
                                } else null,
                                onUpdatePlaylist = if (playlist.isOwner || playlist.permission == "owner") {
                                    { name, desc, isPublic ->
                                        viewModel.updatePlaylist(playlist.id, name, desc, isPublic)
                                    }
                                } else null,
                                onRemoveSongFromPlaylist = if (playlist.canEdit) {
                                    { songId -> viewModel.removeSongFromPlaylist(playlist.id, songId) }
                                } else null,
                                onSharePlaylist = if (playlist.isOwner || playlist.permission == "owner") {
                                    { username, perm ->
                                        viewModel.sharePlaylist(playlist.id, username, perm)
                                    }
                                } else null,
                                onGetCollaborators = { callback ->
                                    viewModel.getCollaborators(playlist.id, callback)
                                },
                                onRevokeShare = if (playlist.isOwner || playlist.permission == "owner") {
                                    { userId -> viewModel.revokeShare(playlist.id, userId) }
                                } else null,
                                onUpdateArtwork = if (playlist.canEdit) {
                                    { uri -> viewModel.updateArtwork(playlist.id, uri) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

        // Full Screen Player Modal / Slide-up
        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = isPlayerExpanded && playbackInfo.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            FullPlayerScreen(
                playbackInfo = playbackInfo,
                lyricsData = currentLyricsData,
                songOffsetMs = currentSongOffset,
                lyricsConfig = lyricsConfig,
                lyricsPositionMs = lyricsPositionMs,
                playbackPositionFlow = viewModel.playbackPositionMs,
                onSetLyricsActive = { active -> viewModel.setLyricsActive(active) },
                onCollapse = { isPlayerExpanded = false },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeekTo = { pos -> viewModel.seekTo(pos) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onToggleFavorite = {
                    playbackInfo.currentSong?.let { viewModel.toggleFavorite(it.id) }
                },
                onToggleInstrumental = { viewModel.toggleInstrumental() },
                onSaveLyricsConfig = { config -> viewModel.saveLyricsConfig(config) },
                onLyricsOffsetChange = { offset -> viewModel.setLyricsOffset(offset) },
                onRemoveFromQueue = { idx -> viewModel.removeFromQueue(idx) },
                onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
                onClearQueue = { viewModel.clearQueue() },
                onPlayQueueItem = { idx -> viewModel.playQueueItem(idx) },
                onAddToPlaylist = { song -> songToAddToPlaylist = song }
            )
        }

        // Sync Indicator
        if (isLoading && currentRoute != Screen.Login.route) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                color = YimlyPink,
                trackColor = Color.Transparent
            )
        }

        // Add to Playlist Dialog
        songToAddToPlaylist?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = playlists,
                onDismiss = { songToAddToPlaylist = null },
                onSelectPlaylist = { playlist ->
                    viewModel.addSongToPlaylist(playlist.id, song.id)
                    songToAddToPlaylist = null
                },
                onCreateAndAddToPlaylist = { name ->
                    viewModel.createPlaylistAndAddSong(name, song.id)
                    songToAddToPlaylist = null
                }
            )
        }
    }
}
