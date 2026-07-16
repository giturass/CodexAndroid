package com.termuxcodex.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

class CodexConnectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Codex 后台连接",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持 Codex Android 与本地 App Server 的任务连接"
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val busy = intent?.getBooleanExtra(EXTRA_BUSY, false) == true
        startForeground(NOTIFICATION_ID, notification(busy))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(busy: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (busy) "Codex 正在执行任务" else "Codex 已连接")
            .setContentText("连接到 Termux 本地 App Server")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "codex_connection"
        private const val NOTIFICATION_ID = 4100
        private const val ACTION_START = "com.termuxcodex.client.START_CONNECTION"
        private const val ACTION_STOP = "com.termuxcodex.client.STOP_CONNECTION"
        private const val EXTRA_BUSY = "busy"

        fun start(context: Context, busy: Boolean) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CodexConnectionService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_BUSY, busy)
                },
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CodexConnectionService::class.java))
        }
    }
}
