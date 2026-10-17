package com.jianzhang.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jianzhang.core.model.ReminderItem
import com.jianzhang.core.model.ReminderKind

object NotificationCenter {
    private const val CHANNEL_ALERTS = "jianzhang-alerts"
    private const val CHANNEL_CADENCE = "jianzhang-cadence"
    private const val GROUP = "jianzhang-reminders"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "预算与账单提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "预算接近上限、分类超支和周期账目到期"
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CADENCE,
                "每日与每周提醒",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "每日记账和每周账本小结"
            },
        )
    }

    fun notifyNew(context: Context, items: List<ReminderItem>): Int {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return 0
        }
        var count = 0
        items.forEach { item ->
            val id = item.key.hashCode() and 0x7FFFFFFF
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = "jianzhang://open?target=${android.net.Uri.encode(item.route)}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, channel(item.kind))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(item.title)
                .setContentText(item.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
                .setPriority(
                    if (item.kind == ReminderKind.DAILY || item.kind == ReminderKind.WEEKLY) {
                        NotificationCompat.PRIORITY_LOW
                    } else {
                        NotificationCompat.PRIORITY_DEFAULT
                    },
                )
                .setGroup(GROUP)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
                .onSuccess { count++ }
        }
        return count
    }

    private fun channel(kind: ReminderKind): String = when (kind) {
        ReminderKind.DAILY, ReminderKind.WEEKLY -> CHANNEL_CADENCE
        else -> CHANNEL_ALERTS
    }
}