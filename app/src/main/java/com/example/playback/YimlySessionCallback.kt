package com.example.playback

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class YimlySessionCallback(
    private val playbackManager: PlaybackManager
) : MediaLibrarySession.Callback {

    companion object {
        const val ACTION_TOGGLE_INSTRUMENTAL = "com.example.ACTION_TOGGLE_INSTRUMENTAL"
        const val ACTION_SHUFFLE_PLAYLIST = "com.example.ACTION_SHUFFLE_PLAYLIST"
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        android.util.Log.d("AA_DIAG", "YimlySessionCallback.onConnect() package=${controller.packageName}, uid=${controller.uid}, isTrusted=${controller.isTrusted}")

        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand(ACTION_TOGGLE_INSTRUMENTAL, Bundle.EMPTY))
            .add(SessionCommand(ACTION_SHUFFLE_PLAYLIST, Bundle.EMPTY))
            .build()

        val playerCommands = session.player.availableCommands.buildUpon()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SET_REPEAT_MODE)
            .add(Player.COMMAND_SET_SHUFFLE_MODE)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            ACTION_TOGGLE_INSTRUMENTAL -> playbackManager.toggleInstrumental()
            ACTION_SHUFFLE_PLAYLIST -> playbackManager.shuffleCurrentQueue()
            else -> return super.onCustomCommand(session, controller, customCommand, args)
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return super.onMediaButtonEvent(session, controller, intent)

        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    playbackManager.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    playbackManager.pause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    playbackManager.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                    playbackManager.next()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                    playbackManager.previous()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    playbackManager.pause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    val current = playbackManager.playbackInfo.value.currentPositionMs
                    playbackManager.seekTo(current + 10000L)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    val current = playbackManager.playbackInfo.value.currentPositionMs
                    playbackManager.seekTo((current - 10000L).coerceAtLeast(0L))
                    return true
                }
            }
        } else if (keyEvent.action == KeyEvent.ACTION_UP) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> return true
            }
        }

        return super.onMediaButtonEvent(session, controller, intent)
    }

    override fun onPlaybackResumption(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        val fallback = super.onPlaybackResumption(session, controller)

        // DataStore restoration starts asynchronously during PlaybackManager creation.
        // Wait for it before deciding whether a saved queue exists; otherwise a cold
        // Android Auto launch can incorrectly report that there is nothing to resume.
        val job = playbackManager.coroutineScope.launch {
            try {
                playbackManager.awaitPlaybackSnapshotRestoration()
                withContext(Dispatchers.Main) {
                    val currentSong = playbackManager.playbackInfo.value.currentSong
                    val player = session.player
                    if (currentSong != null && player.mediaItemCount > 0) {
                        // Preserve the complete Media3 timeline (not just the active item).
                        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                        val startIndex = player.currentMediaItemIndex.coerceIn(items.indices)
                        val position = playbackManager.playbackInfo.value.currentPositionMs
                            .takeIf { it > 0L } ?: player.currentPosition
                        result.set(
                            MediaSession.MediaItemsWithStartPosition(items, startIndex, position)
                        )
                    } else {
                        fallback.addListener(
                            {
                                try {
                                    result.set(fallback.get())
                                } catch (e: Throwable) {
                                    result.setException(e)
                                }
                            },
                            MoreExecutors.directExecutor()
                        )
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                result.cancel(false)
                throw cancelled
            } catch (e: Throwable) {
                result.setException(e)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !result.isDone) result.setException(cause)
        }
        return result
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetLibraryRoot() START package=${browser.packageName}, uid=${browser.uid}, params=$params")
        val rootMetadata = MediaMetadata.Builder()
            .setTitle("Yimly Music")
            .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build()
        val rootItem = MediaItem.Builder()
            .setMediaId("root")
            .setMediaMetadata(rootMetadata)
            .build()
        val result = LibraryResult.ofItem(rootItem, params)
        android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetLibraryRoot() SUCCESS rootId=${rootItem.mediaId}")
        return Futures.immediateFuture(result)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetChildren() START package=${browser.packageName}, parentId=$parentId, page=$page, pageSize=$pageSize")
        val future = SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
        val job = playbackManager.coroutineScope.launch(Dispatchers.IO) {
            try {
                val mediaItems = mutableListOf<MediaItem>()

                when (parentId) {
                    "root", "", "/" -> {
                        val categories = listOf(
                            Triple("recently_played", "Recently Played", MediaMetadata.FOLDER_TYPE_TITLES),
                            Triple("songs", "All Songs", MediaMetadata.FOLDER_TYPE_TITLES),
                            Triple("playlists", "Playlists", MediaMetadata.FOLDER_TYPE_PLAYLISTS),
                            Triple("favourites", "Favourites", MediaMetadata.FOLDER_TYPE_TITLES)
                        )
                        for ((id, title, folderType) in categories) {
                            val metadata = MediaMetadata.Builder()
                                .setTitle(title)
                                .setFolderType(folderType)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                            mediaItems.add(
                                MediaItem.Builder()
                                    .setMediaId(id)
                                    .setMediaMetadata(metadata)
                                    .build()
                            )
                        }
                    }
                    "recently_played" -> {
                        val songs = playbackManager.musicRepository.recentlyPlayedSongs.firstOrNull() ?: emptyList()
                        for (song in songs) {
                            val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                            val metadata = MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                                .build()
                            mediaItems.add(
                                MediaItem.Builder()
                                    .setMediaId("recent_song|${song.id}")
                                    .setMediaMetadata(metadata)
                                    .build()
                            )
                        }
                    }
                    "songs" -> {
                        val songs = playbackManager.musicRepository.allSongs.firstOrNull() ?: emptyList()
                        for (song in songs) {
                            val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                            val metadata = MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                                .build()
                            mediaItems.add(
                                MediaItem.Builder()
                                    .setMediaId("all_song|${song.id}")
                                    .setMediaMetadata(metadata)
                                    .build()
                            )
                        }
                    }
                    "playlists" -> {
                        val playlists = playbackManager.musicRepository.allPlaylists.firstOrNull() ?: emptyList()
                        for (playlist in playlists) {
                            val metadata = MediaMetadata.Builder()
                                .setTitle(playlist.name)
                                .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                            mediaItems.add(
                                MediaItem.Builder()
                                    .setMediaId("playlist|${playlist.id}")
                                    .setMediaMetadata(metadata)
                                    .build()
                            )
                        }
                    }
                    "favourites" -> {
                        val songs = playbackManager.musicRepository.favoriteSongs.firstOrNull() ?: emptyList()
                        for (song in songs) {
                            val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                            val metadata = MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                                .build()
                            mediaItems.add(
                                MediaItem.Builder()
                                    .setMediaId("favorite_song|${song.id}")
                                    .setMediaMetadata(metadata)
                                    .build()
                            )
                        }
                    }
                    else -> {
                        if (parentId.startsWith("playlist|")) {
                            val playlistId = parentId.removePrefix("playlist|")
                            val songs = playbackManager.musicRepository.getSongsForPlaylist(playlistId).firstOrNull() ?: emptyList()
                            for (song in songs) {
                                val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                                val metadata = MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setAlbumTitle(song.album)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                                    .build()
                                mediaItems.add(
                                    MediaItem.Builder()
                                        .setMediaId("playlist_song|${playlistId}|${song.id}")
                                        .setMediaMetadata(metadata)
                                        .build()
                                )
                            }
                        }
                    }
                }

                val fromIndexLong = page.toLong() * pageSize.toLong()
                val pagedList = if (pageSize > 0 && page >= 0) {
                    if (fromIndexLong >= mediaItems.size) {
                        emptyList()
                    } else {
                        val fromIndex = fromIndexLong.toInt()
                        val toIndex = (fromIndexLong + pageSize).coerceAtMost(mediaItems.size.toLong()).toInt()
                        mediaItems.subList(fromIndex, toIndex)
                    }
                } else {
                    mediaItems
                }

                val immutableList = com.google.common.collect.ImmutableList.copyOf(pagedList)
                android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetChildren() SUCCESS parentId=$parentId, totalFound=${mediaItems.size}, returnedCount=${immutableList.size}")
                future.set(LibraryResult.ofItemList(immutableList, params))
            } catch (e: Throwable) {
                android.util.Log.e("AA_DIAG", "YimlySessionCallback.onGetChildren() ERROR parentId=$parentId: ${e.message}", e)
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !future.isDone) {
                android.util.Log.e("AA_DIAG", "YimlySessionCallback.onGetChildren() Job cancelled/failed for parentId=$parentId: ${cause.message}")
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetItem() START package=${browser.packageName}, mediaId=$mediaId")
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        val job = playbackManager.coroutineScope.launch(Dispatchers.IO) {
            try {
                val parts = mediaId.split("|")
                val actualSongId = when {
                    mediaId.startsWith("playlist_song|") && parts.size >= 3 -> parts[2]
                    mediaId.startsWith("favorite_song|") && parts.size >= 2 -> parts[1]
                    mediaId.startsWith("recent_song|") && parts.size >= 2 -> parts[1]
                    mediaId.startsWith("all_song|") && parts.size >= 2 -> parts[1]
                    else -> mediaId
                }
                val song = playbackManager.musicRepository.getSongById(actualSongId)
                if (song != null) {
                    val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                        .build()
                    val item = MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setMediaMetadata(metadata)
                        .build()
                    android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetItem() SUCCESS mediaId=$mediaId, title=${song.title}")
                    future.set(LibraryResult.ofItem(item, null))
                } else {
                    android.util.Log.w("AA_DIAG", "YimlySessionCallback.onGetItem() NOT FOUND mediaId=$mediaId")
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                }
            } catch (e: Throwable) {
                android.util.Log.e("AA_DIAG", "YimlySessionCallback.onGetItem() ERROR mediaId=$mediaId: ${e.message}", e)
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !future.isDone) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        android.util.Log.d("AA_DIAG", "YimlySessionCallback.onSearch() package=${browser.packageName}, query=$query")
        val future = SettableFuture.create<LibraryResult<Void>>()
        val job = playbackManager.coroutineScope.launch(Dispatchers.IO) {
            try {
                val resultCount = playbackManager.musicRepository.searchSongs(query).size
                withContext(Dispatchers.Main) {
                    session.notifySearchResultChanged(browser, query, resultCount, params)
                    future.set(LibraryResult.ofVoid())
                }
            } catch (e: Throwable) {
                android.util.Log.e("AA_DIAG", "YimlySessionCallback.onSearch() ERROR query=$query: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    session.notifySearchResultChanged(browser, query, 0, params)
                    future.set(LibraryResult.ofVoid())
                }
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !future.isDone) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetSearchResult() START package=${browser.packageName}, query=$query, page=$page, pageSize=$pageSize")
        val future = SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
        val job = playbackManager.coroutineScope.launch(Dispatchers.IO) {
            try {
                val songs = playbackManager.musicRepository.searchSongs(query)
                val mediaItems = songs.map { song ->
                    val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                        .build()
                    MediaItem.Builder()
                        .setMediaId("all_song|${song.id}")
                        .setMediaMetadata(metadata)
                        .build()
                }
                val fromIndexLong = page.toLong() * pageSize.toLong()
                val pagedList = if (pageSize > 0 && page >= 0) {
                    if (fromIndexLong >= mediaItems.size) {
                        emptyList()
                    } else {
                        val fromIndex = fromIndexLong.toInt()
                        val toIndex = (fromIndexLong + pageSize).coerceAtMost(mediaItems.size.toLong()).toInt()
                        mediaItems.subList(fromIndex, toIndex)
                    }
                } else {
                    mediaItems
                }
                val immutableList = com.google.common.collect.ImmutableList.copyOf(pagedList)
                android.util.Log.d("AA_DIAG", "YimlySessionCallback.onGetSearchResult() SUCCESS query=$query, count=${immutableList.size}")
                future.set(LibraryResult.ofItemList(immutableList, params))
            } catch (e: Throwable) {
                android.util.Log.e("AA_DIAG", "YimlySessionCallback.onGetSearchResult() ERROR query=$query: ${e.message}", e)
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !future.isDone) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        val job = playbackManager.coroutineScope.launch(Dispatchers.IO) {
            try {
                val resolvedItems = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    val mediaId = item.mediaId
                    val parts = mediaId.split("|")
                    val songId = when {
                        mediaId.startsWith("playlist_song|") && parts.size >= 3 -> parts[2]
                        mediaId.startsWith("favorite_song|") && parts.size >= 2 -> parts[1]
                        mediaId.startsWith("recent_song|") && parts.size >= 2 -> parts[1]
                        mediaId.startsWith("all_song|") && parts.size >= 2 -> parts[1]
                        else -> null
                    }
                    val song = if (songId != null) {
                        playbackManager.musicRepository.getSongById(songId)
                    } else {
                        null
                    }

                    if (song != null) {
                        val resolvedTrack = playbackManager.musicRepository.resolvePlayableTrack(song, false)
                        val audioUrl = resolvedTrack.playableUrl
                        val artworkUri = playbackManager.getResolvedArtworkUrl(song)?.let { Uri.parse(it) }
                        val parsedAudioUri = Uri.parse(audioUrl)

                        val resolvedItem = MediaItem.Builder()
                            .setMediaId(mediaId)
                            .setUri(parsedAudioUri)
                            .setRequestMetadata(
                                MediaItem.RequestMetadata.Builder()
                                    .setMediaUri(parsedAudioUri)
                                    .build()
                            )
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setAlbumTitle(song.album)
                                    .setArtworkUri(artworkUri)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .setIsPlayable(true)
                                    .build()
                            )
                            .build()
                        resolvedItems.add(resolvedItem)
                    } else {
                        // Preserve only items that already have a real playable URI.
                        // A non-empty mediaId alone is not enough: returning unresolved
                        // library IDs here can leave Media3 with an item it cannot play.
                        val fallbackUri = item.localConfiguration?.uri
                            ?: item.requestMetadata.mediaUri
                        if (fallbackUri != null) {
                            resolvedItems.add(
                                item.buildUpon()
                                    .setUri(fallbackUri)
                                    .build()
                            )
                        } else {
                            android.util.Log.w(
                                "YimlySessionCallback",
                                "Skipping unresolved media item: mediaId=$mediaId"
                            )
                        }
                    }
                }
                future.set(resolvedItems)
            } catch (e: kotlinx.coroutines.CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                android.util.Log.e("YimlySessionCallback", "Error in onAddMediaItems: ${e.message}", e)
                // Do not return unresolved items as if they were playable.
                future.set(mediaItems.filter { it.localConfiguration?.uri != null || it.requestMetadata.mediaUri != null })
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !future.isDone) {
                if (cause is kotlinx.coroutines.CancellationException) {
                    future.cancel(false)
                } else {
                    android.util.Log.e("YimlySessionCallback", "onAddMediaItems job failed", cause)
                    future.set(mediaItems.filter { it.localConfiguration?.uri != null || it.requestMetadata.mediaUri != null })
                }
            }
        }
        return future
    }
}
