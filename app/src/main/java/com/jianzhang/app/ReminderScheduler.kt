package com.jianzhang.app

import android.content.Context
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
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
    const val CADENCE_CHECK = "jianzhang-cadence-check"

    fun sync(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<ReminderCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_CHECK, ExistingPeriodicWorkPolicy.UPDATE, periodic)

        workManager.cancelUniqueWork(CADENCE_CHECK)
        if (!settings.systemNotify) return
        val next = nextCadence(settings) ?: return
        val delay = Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<CadenceReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(CADENCE_CHECK, ExistingWorkPolicy.REPLACE, request)
    }

    fun nextCadence(settings: AppSettings): LocalDateTime? {
        if (!settings.dailyReminder && !settings.weeklyReminder) return null
        val now = LocalDateTime.now()
        val time = LocalTime.of(
            settings.reminderHour.coerceIn(0, 23),
            settings.reminderMinute.coerceIn(0, 59),
        )
        val candidates = mutableListOf<LocalDateTime>()
        if (settings.dailyReminder) {
            var daily = LocalDateTime.of(LocalDate.now(), time)
            if (!daily.isAfter(now)) daily = daily.plusDays(1)
            candidates += daily
        }
        if (settings.weeklyReminder) {
            val target = if (settings.weeklyReminderWeekday == 0) DayOfWeek.SUNDAY
            else DayOfWeek.of(settings.weeklyReminderWeekday.coerceIn(1, 7))
            val date = LocalDate.now().with(TemporalAdjusters.nextOrSame(target))
            var weekly = LocalDateTime.of(date, time)
            if (!weekly.isAfter(now)) weekly = weekly.plusWeeks(1)
            candidates += weekly
        }
        return candidates.minOrNull()
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
            if (settings.systemNotify) {
                val items = ReminderEngine.build(entry.repository(), settings)
                    .filter { it.kind == ReminderKind.DAILY || it.kind == ReminderKind.WEEKLY }
                NotificationCenter.notifyNew(applicationContext, items)
            }
            ReminderScheduler.sync(applicationContext, settings)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}