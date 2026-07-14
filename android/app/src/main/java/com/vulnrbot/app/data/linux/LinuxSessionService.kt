package com.vulnrbot.app.data.linux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vulnrbot.app.MainActivity
import com.vulnrbot.app.R

/** Foreground service that keeps the process alive while a long-running tool (nmap, sqlmap,
 * hydra, ...) executes inside the chroot, so Android doesn't kill the app in the background. Started
 * by AgentOrchestrator around each tool execution and stopped once the run completes. */
class LinuxSessionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Running security tool…"
        startForeground(NOTIFICATION_ID, buildNotification(label))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(label: String): Notification {
        ensureChannel()
        val contentIntent = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vulnr-Bot")
            .setContentText(label)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Security tool execution", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val EXTRA_LABEL = "label"
        private const val CHANNEL_ID = "linux_session"
        private const val NOTIFICATION_ID = 42
    }
}
