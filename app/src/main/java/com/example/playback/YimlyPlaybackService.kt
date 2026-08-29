package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.R
import com.example.YimlyApplication

@UnstableApi
class YimlyPlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "yimly_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
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
        val app = application as? YimlyApplication
        val session = app?.playbackManager?.mediaSession
        if (session != null && !sessions.contains(session)) {
            addSession(session)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val app = application as? YimlyApplication
        return app?.playbackManager?.mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? YimlyApplication
        val player = app?.playbackManager?.mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_IDLE || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            stopSelf()
        }
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
