package com.jianzhang.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in supportedActions()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val entry = EntryPointAccessors.fromApplication(
                    appContext,
                    ReminderEntryPoint::class.java,
                )
                val settings = entry.settingsStore().settings.first()
                ReminderScheduler.sync(appContext, settings)
            }.onFailure {
                Log.w("JianZhangReminder", "Unable to restore reminder schedule", it)
            }
            pendingResult.finish()
        }
    }

    private fun supportedActions(): Set<String> = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        ACTION_EXACT_ALARM_PERMISSION_CHANGED,
    )

    companion object {
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
