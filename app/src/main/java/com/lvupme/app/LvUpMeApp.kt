package com.lvupme.app

import android.app.Application
import com.lvupme.app.data.local.BackupManager
import com.lvupme.app.data.local.LvUpDatabase
import com.lvupme.app.data.local.SettingsRepository
import com.lvupme.app.data.remote.GeminiClient
import com.lvupme.app.data.repository.LearningRepository
import com.lvupme.app.domain.TtsHelper
import com.lvupme.app.notification.NotificationHelper
import com.lvupme.app.worker.WorkScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    val database: LvUpDatabase = LvUpDatabase.getInstance(app)
    val settingsRepository: SettingsRepository = SettingsRepository(app)
    val geminiClient: GeminiClient = GeminiClient(settingsRepository)
    val learningRepository: LearningRepository = LearningRepository(
        context = app,
        db = database,
        settingsRepository = settingsRepository,
        geminiClient = geminiClient
    )
    val backupManager: BackupManager = BackupManager(database, settingsRepository)
    val ttsHelper: TtsHelper by lazy { TtsHelper(app) }
}

class LvUpMeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        // Use non-destructive KEEP policy on process startup so running workers/timers are never reset
        container.appScope.launch {
            val settings = container.settingsRepository.getSettings()
            WorkScheduler.ensureScheduled(this@LvUpMeApp, settings)
        }
    }
}
