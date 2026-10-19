package com.jianzhang.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_DAILY_CHECK) return
        val request = OneTimeWorkRequestBuilder<CadenceReminderWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ReminderScheduler.CADENCE_RUN,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
