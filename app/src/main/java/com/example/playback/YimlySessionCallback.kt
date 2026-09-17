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
import com.example.YimlyApplication
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
class YimlySessionCallback(
    private val playbackManager: PlaybackManager
) : MediaLibrarySession.Callback {

    companion object {
        const val ACTION_TOGGLE_INSTRUMENTAL = "com.example.ACTION_TOGGLE_INSTRUMENTAL"
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand(ACTION_TOGGLE_INSTRUMENTAL, Bundle.EMPTY))
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
        if (customCommand.customAction == ACTION_TOGGLE_INSTRUMENTAL) {
            playbackManager.toggleInstrumental()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return super.onCustomCommand(session, controller, customCommand, args)
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
        val currentSong = playbackManager.playbackInfo.value.currentSong
        if (currentSong != null) {
            val item = session.player.currentMediaItem
            if (item != null) {
                val pos = playbackManager.playbackInfo.value.currentPositionMs
                val itemsWithStart = MediaSession.MediaItemsWithStartPosition(
                    listOf(item),
                    0,
                    pos
                )
                return Futures.immediateFuture(itemsWithStart)
            }
        }
        return super.onPlaybackResumption(session, controller)
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
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
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        val mediaItems = mutableListOf<MediaItem>()

        when (parentId) {
            "root" -> {
                val categories = listOf(
                    Triple("recently_played", "Recently Played", MediaMetadata.FOLDER_TYPE_MIXED),
                    Triple("songs", "All Songs", MediaMetadata.FOLDER_TYPE_MIXED),
                    Triple("playlists", "Playlists", MediaMetadata.FOLDER_TYPE_PLAYLISTS),
                    Triple("favourites", "Favourites", MediaMetadata.FOLDER_TYPE_MIXED)
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
                val songs = runBlocking { playbackManager.musicRepository.recentlyPlayedSongs.firstOrNull() ?: emptyList() }
                for (song in songs) {
                    val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
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
                val songs = runBlocking { playbackManager.musicRepository.allSongs.firstOrNull() ?: emptyList() }
                for (song in songs) {
                    val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
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
                val playlists = runBlocking { playbackManager.musicRepository.allPlaylists.firstOrNull() ?: emptyList() }
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
                val songs = runBlocking { playbackManager.musicRepository.favoriteSongs.firstOrNull() ?: emptyList() }
                for (song in songs) {
                    val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
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
                    val songs = runBlocking { playbackManager.musicRepository.getSongsForPlaylist(playlistId).firstOrNull() ?: emptyList() }
                    for (song in songs) {
                        val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
                        val metadata = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
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

        val immutableList = com.google.common.collect.ImmutableList.copyOf(mediaItems)
        return Futures.immediateFuture(LibraryResult.ofItemList(immutableList, params))
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val song = runBlocking {
            val parts = mediaId.split("|")
            val actualSongId = when {
                mediaId.startsWith("playlist_song|") && parts.size >= 3 -> parts[2]
                mediaId.startsWith("favorite_song|") && parts.size >= 2 -> parts[1]
                mediaId.startsWith("recent_song|") && parts.size >= 2 -> parts[1]
                mediaId.startsWith("all_song|") && parts.size >= 2 -> parts[1]
                else -> mediaId
            }
            playbackManager.musicRepository.getSongById(actualSongId)
        }
        if (song != null) {
            val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                .build()
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(metadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }
        return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 1, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        val app = playbackManager.context.applicationContext as YimlyApplication
        val entities = runBlocking { app.database.musicDao().searchSongs(query) }
        val mediaItems = entities.map { entity ->
            val song = entity.toSong()
            val resolvedArt = playbackManager.getResolvedArtworkUrl(song)
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setArtworkUri(resolvedArt?.let { Uri.parse(it) })
                .build()
            MediaItem.Builder()
                .setMediaId("all_song|${song.id}")
                .setMediaMetadata(metadata)
                .build()
        }
        return Futures.immediateFuture(LibraryResult.ofItemList(com.google.common.collect.ImmutableList.copyOf(mediaItems), params))
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val item = mediaItems.firstOrNull() ?: return Futures.immediateFuture(mediaItems)
        val mediaId = item.mediaId

        val parts = mediaId.split("|")
        val (type, songId, playlistId) = when {
            mediaId.startsWith("playlist_song|") && parts.size >= 3 -> {
                Triple("playlist_song", parts[2], parts[1])
            }
            mediaId.startsWith("favorite_song|") && parts.size >= 2 -> {
                Triple("favorite_song", parts[1], null)
            }
            mediaId.startsWith("recent_song|") && parts.size >= 2 -> {
                Triple("recent_song", parts[1], null)
            }
            mediaId.startsWith("all_song|") && parts.size >= 2 -> {
                Triple("all_song", parts[1], null)
            }
            else -> {
                Triple("raw", mediaId, null)
            }
        }

        if (songId != null) {
            playbackManager.playSongFromAuto(songId, type, playlistId)
        }

        return Futures.immediateFuture(emptyList())
    }
}
