package com.termuxcodex.client

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class ApprovalNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Codex 审批与提问",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "任务在后台等待审批、权限或用户输入时提醒"
                enableVibration(true)
            }
        )
    }

    fun showPendingAction(pending: PendingAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val detail = pending.detail.ifBlank { "打开应用完成操作后，Codex 将继续执行任务。" }
        val openApp = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            Intent(appContext, MainActivity::class.java).apply {
                action = ApprovalNotificationIntent.ACTION_OPEN_PENDING
                putExtra(ApprovalNotificationIntent.EXTRA_THREAD_ID, pending.threadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(pending.title)
            .setContentText(detail.lineSequence().firstOrNull()?.take(120))
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(openApp)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelPendingAction() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private companion object {
        const val CHANNEL_ID = "codex_pending_actions"
        const val NOTIFICATION_ID = 4101
    }
}

object ApprovalNotificationIntent {
    const val ACTION_OPEN_PENDING = "com.termuxcodex.client.OPEN_PENDING_APPROVAL"
    const val EXTRA_THREAD_ID = "pending_thread_id"
}
