package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.MainActivity

class DownloadNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private companion object {
        const val CHANNEL_ID = "yimly_download_channel"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for Yimly song downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDownloadNotification(notificationId: Int, title: String, progress: Int?, isFailed: Boolean = false) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NAVIGATE_TO_SEARCH", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (isFailed) "Download Failed" else "Downloading: $title")
            .setContentText(if (isFailed) "Tap to retry" else if (progress == 100) "Ready to play" else "Processing song...")
            .setSmallIcon(R.drawable.ic_yimly_mic)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!isFailed && progress != null && progress < 100)
            .setAutoCancel(isFailed || progress == 100)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (progress != null && !isFailed) {
            builder.setProgress(100, progress, progress == 0)
        } else if (isFailed) {
            builder.setProgress(0, 0, false)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }
}
