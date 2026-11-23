package com.lvupme.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lvupme.app.LvUpMeApp
import com.lvupme.app.data.remote.GeminiApiException

class PrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LvUpMeApp).container
        val settings = container.settingsRepository.getSettings()
        if (!settings.prefetchEnabled) return Result.success()

        // Do not retry infinitely when user hasn't configured an API key
        if (container.settingsRepository.getApiKey().isBlank()) return Result.success()

        return try {
            container.learningRepository.prefetchUpcomingForAllSubjects()
            Result.success()
        } catch (e: GeminiApiException) {
            when (e.httpCode) {
                400, 401, 403, 404 -> Result.failure() // Non-retryable auth/config error
                else -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
