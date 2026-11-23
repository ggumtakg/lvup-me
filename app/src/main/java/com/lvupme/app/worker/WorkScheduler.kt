package com.lvupme.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lvupme.app.data.local.AppSettings
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_PREFETCH = "lvup_prefetch_work"
    private const val WORK_LOCKSCREEN = "lvup_lockscreen_card_work"
    private const val WORK_REMINDER = "lvup_daily_reminder_work"

    /**
     * Non-destructive startup sync: uses KEEP policy so an already-running ReminderWorker
     * is never cancelled by Application.onCreate() and LockScreenCardWorker's timer is not reset.
     */
    fun ensureScheduled(context: Context, settings: AppSettings) {
        syncPrefetch(context, settings, replaceExisting = false)
        syncLockScreenNotification(context, settings, replaceExisting = false)
        syncDailyReminder(context, settings, replaceExisting = false)
    }

    fun syncAll(context: Context, settings: AppSettings) {
        syncPrefetch(context, settings, replaceExisting = true)
        syncLockScreenNotification(context, settings, replaceExisting = true)
        syncDailyReminder(context, settings, replaceExisting = true)
    }

    /**
     * Granular sync: only reschedules workers whose relevant settings actually changed,
     * preventing unrelated changes (theme, TTS, ratios) from resetting timers.
     */
    fun syncDiff(context: Context, previous: AppSettings, updated: AppSettings) {
        if (previous.prefetchEnabled != updated.prefetchEnabled ||
            previous.prefetchWifiOnly != updated.prefetchWifiOnly
        ) {
            syncPrefetch(context, updated, replaceExisting = true)
        }
        if (previous.lockScreenNotifEnabled != updated.lockScreenNotifEnabled ||
            previous.lockScreenIntervalMinutes != updated.lockScreenIntervalMinutes
        ) {
            syncLockScreenNotification(context, updated, replaceExisting = true)
        }
        if (previous.reminderEnabled != updated.reminderEnabled ||
            previous.reminderHour != updated.reminderHour ||
            previous.reminderMinute != updated.reminderMinute
        ) {
            syncDailyReminder(context, updated, replaceExisting = true)
        }
    }

    fun syncPrefetch(context: Context, settings: AppSettings, replaceExisting: Boolean = true) {
        val wm = WorkManager.getInstance(context)
        if (settings.prefetchEnabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (settings.prefetchWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<PrefetchWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            val policy = if (replaceExisting) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            wm.enqueueUniquePeriodicWork(WORK_PREFETCH, policy, req)
        } else {
            wm.cancelUniqueWork(WORK_PREFETCH)
        }
    }

    fun syncLockScreenNotification(context: Context, settings: AppSettings, replaceExisting: Boolean = true) {
        val wm = WorkManager.getInstance(context)
        if (settings.lockScreenNotifEnabled) {
            val interval = settings.lockScreenIntervalMinutes.coerceAtLeast(15).toLong()
            val req = PeriodicWorkRequestBuilder<LockScreenCardWorker>(interval, TimeUnit.MINUTES)
                .setInitialDelay(interval, TimeUnit.MINUTES)
                .build()
            val policy = if (replaceExisting) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            wm.enqueueUniquePeriodicWork(WORK_LOCKSCREEN, policy, req)
        } else {
            wm.cancelUniqueWork(WORK_LOCKSCREEN)
        }
    }

    /**
     * Uses exact OneTimeWorkRequest chaining so daily reminder time never drifts over days.
     */
    fun syncDailyReminder(context: Context, settings: AppSettings, replaceExisting: Boolean = true) {
        val wm = WorkManager.getInstance(context)
        if (settings.reminderEnabled) {
            val delayMin = computeDelayMinutesUntil(settings.reminderHour, settings.reminderMinute)
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .build()
            val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            wm.enqueueUniqueWork(WORK_REMINDER, policy, req)
        } else {
            wm.cancelUniqueWork(WORK_REMINDER)
        }
    }

    internal fun computeDelayMinutesUntil(
        hour: Int,
        minute: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): Long {
        var target = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMinutes().coerceAtLeast(1L)
    }
}
