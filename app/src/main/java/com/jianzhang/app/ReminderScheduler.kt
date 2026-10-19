package com.jianzhang.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.ReminderKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun repository(): LedgerRepository
    fun settingsStore(): SettingsStore
}

private fun Context.reminderEntryPoint(): ReminderEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)

object ReminderScheduler {
    const val PERIODIC_CHECK = "jianzhang-reminder-check"
    const val CADENCE_RUN = "jianzhang-cadence-run"
    const val ACTION_DAILY_CHECK = "com.jianzhang.app.action.DAILY_REMINDER_CHECK"
    private const val LEGACY_CADENCE_CHECK = "jianzhang-cadence-check"
    private const val ALARM_REQUEST = 7301
    private const val TAG = "JianZhangReminder"

    /**
     * Keep the six-hour worker for near-real-time budget and bill checks, and
     * keep exactly one wall-clock alarm for the user-selected daily check.
     * AlarmManager is used instead of a WorkManager initial delay because the
     * latter is best-effort and can drift by hours under Doze.
     */
    fun sync(context: Context, settings: AppSettings) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<ReminderCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_CHECK, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        workManager.cancelUniqueWork(LEGACY_CADENCE_CHECK)
        scheduleNextDailyAlarm(appContext, settings)
    }

    fun hasAnyReminder(settings: AppSettings): Boolean =
        settings.budgetAlert || settings.billAlert || settings.dailyReminder || settings.weeklyReminder

    fun nextDailyCheck(settings: AppSettings): LocalDateTime {
        val now = LocalDateTime.now()
        val time = LocalTime.of(
            settings.reminderHour.coerceIn(0, 23),
            settings.reminderMinute.coerceIn(0, 59),
        )
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next
    }

    fun scheduleNextDailyAlarm(context: Context, settings: AppSettings) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST,
            Intent(appContext, ReminderAlarmReceiver::class.java).setAction(ACTION_DAILY_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        if (!settings.systemNotify || !hasAnyReminder(settings)) return

        val triggerAt = nextDailyCheck(settings)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }.onFailure {
                Log.w(TAG, "Exact alarm rejected; falling back to inexact", it)
                runCatching {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }.onFailure { error ->
                    Log.w(TAG, "Unable to schedule daily reminder", error)
                }
            }
        } else {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }.onFailure { error ->
                Log.w(TAG, "Unable to schedule daily reminder", error)
            }
        }
    }
}

class ReminderCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = applicationContext.reminderEntryPoint()
        return runCatching {
            val settings = entry.settingsStore().settings.first()
            entry.repository().runRecurring(autoOnly = true)
            if (settings.systemNotify) {
                val items = ReminderEngine.build(entry.repository(), settings)
                    .filter { it.kind == ReminderKind.BUDGET || it.kind == ReminderKind.BILL }
                NotificationCenter.notifyNew(applicationContext, items)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

class CadenceReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = applicationContext.reminderEntryPoint()
        return runCatching {
            val settings = entry.settingsStore().settings.first()
            // Schedule before doing any notification work so one failure can
            // never break the next day's alarm chain.
            ReminderScheduler.scheduleNextDailyAlarm(applicationContext, settings)
            entry.repository().runRecurring(autoOnly = true)
            if (settings.systemNotify) {
                val items = ReminderEngine.build(entry.repository(), settings)
                NotificationCenter.notifyNew(applicationContext, items)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
