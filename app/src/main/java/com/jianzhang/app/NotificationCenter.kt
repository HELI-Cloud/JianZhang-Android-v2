package com.jianzhang.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jianzhang.core.model.ReminderItem
import com.jianzhang.core.model.ReminderKind
import java.time.LocalDate

object NotificationCenter {
    private const val TAG = "JianZhangNotify"
    private const val CHANNEL_ALERTS = "jianzhang-alerts"
    private const val CHANNEL_CADENCE = "jianzhang-cadence-v2"
    private const val LEGACY_CHANNEL_CADENCE = "jianzhang-cadence"
    private const val GROUP = "jianzhang-reminders"
    private const val PREFS = "jianzhang-notification-state"
    private const val LAST_PREFIX = "last:"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(LEGACY_CHANNEL_CADENCE)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "预算与账单提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "预算接近上限、分类超支和周期账目到期"
                enableVibration(true)
                setShowBadge(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CADENCE,
                "每日与每周提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "每日记账、每周账本小结和每日检查"
                enableVibration(false)
                setShowBadge(true)
            },
        )
    }

    /**
     * Notification permission alone is not enough: the user can also turn the
     * app's notification switch off in system settings. Both paths must be open
     * before WorkManager or an alarm may claim that a reminder was delivered.
     */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun notifyNew(context: Context, items: List<ReminderItem>): Int {
        ensureChannels(context)
        if (items.isEmpty()) return 0
        if (!canNotify(context)) {
            Log.w(TAG, "Notification permission or app notification switch is disabled")
            return 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return 0
        }

        val manager = NotificationManagerCompat.from(context)
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val editor = preferences.edit()
        var preferencesChanged = false
        val handled = mutableSetOf<String>()
        var count = 0

        // Keep this tiny state file bounded. A key only needs to survive for
        // the current day; older entries can never match again.
        preferences.all.forEach { (key, value) ->
            if (key.startsWith(LAST_PREFIX) && value != today) {
                editor.remove(key)
                preferencesChanged = true
            }
        }

        items.forEach { item ->
            if (!handled.add(item.key)) return@forEach
            val stateKey = LAST_PREFIX + item.key
            if (preferences.getString(stateKey, null) == today) return@forEach

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
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setGroup(GROUP)
                .setSortKey(item.key)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()

            runCatching { manager.notify(id, notification) }
                .onSuccess {
                    editor.putString(stateKey, today)
                    preferencesChanged = true
                    count++
                }
                .onFailure { Log.w(TAG, "Unable to post reminder ${item.key}", it) }
        }

        if (preferencesChanged) editor.apply()
        return count
    }

    private fun channel(kind: ReminderKind): String = when (kind) {
        ReminderKind.DAILY, ReminderKind.WEEKLY -> CHANNEL_CADENCE
        else -> CHANNEL_ALERTS
    }
}
