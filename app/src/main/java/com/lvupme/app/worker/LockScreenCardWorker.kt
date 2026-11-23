package com.lvupme.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lvupme.app.LvUpMeApp
import com.lvupme.app.notification.NotificationHelper
import java.time.LocalTime

class LockScreenCardWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val container = (applicationContext as LvUpMeApp).container
        val settings = container.settingsRepository.getSettings()
        if (!settings.lockScreenNotifEnabled) return Result.success()

        val hour = LocalTime.now().hour
        if (isWithinDnd(hour, settings.dndStartHour, settings.dndEndHour)) {
            return Result.success()
        }

        val cardTriple = container.learningRepository.getMainWidgetCard(advanceIndex = true)
            ?: return Result.success()
        val (subject, _, card) = cardTriple

        NotificationHelper.showLockScreenCard(
            context = applicationContext,
            subjectId = subject.id,
            subjectName = subject.name,
            cardId = card.id,
            cardTitle = card.title,
            microSummary = card.microSummary,
            exampleText = card.exampleText,
            ongoing = settings.lockScreenOngoing,
            silentUpdate = false
        )
        container.learningRepository.refreshWidget()
        Result.success()
    }.getOrElse {
        Result.success()
    }

    companion object {
        internal fun isWithinDnd(currentHour: Int, startHour: Int, endHour: Int): Boolean {
            return if (startHour == endHour) {
                false
            } else if (startHour < endHour) {
                currentHour in startHour until endHour
            } else {
                currentHour >= startHour || currentHour < endHour
            }
        }
    }
}
