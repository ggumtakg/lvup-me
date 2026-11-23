package com.lvupme.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lvupme.app.LvUpMeApp
import com.lvupme.app.notification.NotificationHelper
import kotlinx.coroutines.flow.first

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val container = (applicationContext as LvUpMeApp).container
        val settings = container.settingsRepository.getSettings()
        if (!settings.reminderEnabled) return Result.success()

        val todayCount = container.learningRepository.observeTodayCount().first()
        val streakDays = container.learningRepository.observeStreakDays().first()

        if (todayCount < settings.dailyGoalQuestions) {
            NotificationHelper.showDailyReminder(
                context = applicationContext,
                todayCount = todayCount,
                dailyGoal = settings.dailyGoalQuestions,
                streakDays = streakDays
            )
        }

        // Chain exact next day's reminder to prevent 24h drift
        WorkScheduler.syncDailyReminder(applicationContext, settings)
        Result.success()
    }.getOrElse {
        Result.success()
    }
}
