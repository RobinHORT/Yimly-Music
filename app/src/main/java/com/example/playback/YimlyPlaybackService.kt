package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.example.R
import com.example.YimlyApplication

@UnstableApi
class YimlyPlaybackService : MediaLibraryService() {

    companion object {
        const val CHANNEL_ID = "yimly_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("AA_DIAG", "YimlyPlaybackService.onCreate() called")
        createNotificationChannel()

        val provider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_notification_channel_name)
            .build()
        setMediaNotificationProvider(provider)

        val app = application as? YimlyApplication
        val session = app?.playbackManager?.mediaSession
        if (session != null && !sessions.contains(session)) {
            addSession(session)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("AA_DIAG", "YimlyPlaybackService.onStartCommand() intent=$intent")
        val app = application as? YimlyApplication
        val session = app?.playbackManager?.mediaSession
        if (session != null && !sessions.contains(session)) {
            addSession(session)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        android.util.Log.d("AA_DIAG", "YimlyPlaybackService.onGetSession() package=${controllerInfo.packageName}, uid=${controllerInfo.uid}")
        val app = application as? YimlyApplication
        val session = app?.playbackManager?.mediaSession
        if (session != null && !sessions.contains(session)) {
            addSession(session)
        }
        return session
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        val app = application as? YimlyApplication
        val manager = app?.playbackManager
        val info = manager?.playbackInfo?.value
        val hasActiveQueue = info != null && info.queue.isNotEmpty() && info.currentQueueIndex in info.queue.indices
        val player = session.player
        val shouldStayInForeground = startInForegroundRequired || (hasActiveQueue && (player.playWhenReady || manager?.isTransitioning == true))
        super.onUpdateNotification(session, shouldStayInForeground)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val manager = (application as? YimlyApplication)?.playbackManager
        val player = manager?.mediaSession?.player

        // Do not tear down the media service while a track is being resolved or
        // prepared. During this window ExoPlayer can still report STATE_IDLE even
        // though playWhenReady is true and the queue transition is in progress.
        val hasQueuedTrack = manager?.playbackInfo?.value?.let { info ->
            info.currentSong != null && info.currentQueueIndex in info.queue.indices
        } == true
        val playbackRequested = player?.playWhenReady == true
        val transitioning = manager?.isTransitioning == true

        if (hasQueuedTrack && (playbackRequested || transitioning)) {
            android.util.Log.d(
                "YimlyPlaybackService",
                "Task removed; keeping playback service alive (playWhenReady=$playbackRequested, transitioning=$transitioning, state=${player?.playbackState})"
            )
            return
        }

        android.util.Log.d(
            "YimlyPlaybackService",
            "Task removed with no active playback request; stopping service"
        )
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.playback_notification_channel_name)
            val descriptionText = getString(R.string.playback_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        val app = application as? YimlyApplication
        val session = app?.playbackManager?.mediaSession
        if (session != null && sessions.contains(session)) {
            removeSession(session)
        }
        super.onDestroy()
    }
}
