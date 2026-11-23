package com.lvupme.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lvupme_settings")

@Serializable
data class AppSettings(
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val newToReviewRatio: Int = 5,
    val mcPercent: Int = 70,
    val dailyGoalQuestions: Int = 20,
    val weakWrongThreshold: Int = 2,
    val checkpointPassPercent: Int = 80,
    val prefetchEnabled: Boolean = true,
    val prefetchWifiOnly: Boolean = true,
    val prefetchBatchSize: Int = 8,
    val lockScreenNotifEnabled: Boolean = true,
    val lockScreenOngoing: Boolean = true,
    val lockScreenOverlayEnabled: Boolean = false,
    val lockScreenShowQuiz: Boolean = true,
    val lockScreenIntervalMinutes: Int = 60,
    val dndStartHour: Int = 23,
    val dndEndHour: Int = 7,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val ttsAutoPlayDefault: Boolean = false,
    val ttsSpeechRate: Float = 1.0f,
    val widgetCardIndex: Int = 0,
    val themeMode: String = "SYSTEM"
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        val VALID_MODEL_REGEX = Regex("^[a-zA-Z0-9._-]{2,80}$")
        val VALID_THEME_MODES = setOf("SYSTEM", "DARK", "LIGHT")

        fun sanitizeModel(raw: String): String {
            val trimmed = raw.trim()
            return if (VALID_MODEL_REGEX.matches(trimmed)) trimmed else DEFAULT_GEMINI_MODEL
        }

        fun sanitizeThemeMode(raw: String): String {
            return if (raw in VALID_THEME_MODES) raw else "SYSTEM"
        }
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY_ENC = stringPreferencesKey("gemini_api_key_enc")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val NEW_TO_REVIEW_RATIO = intPreferencesKey("new_to_review_ratio")
        val MC_PERCENT = intPreferencesKey("mc_percent")
        val DAILY_GOAL = intPreferencesKey("daily_goal_questions")
        val WEAK_THRESHOLD = intPreferencesKey("weak_wrong_threshold")
        val CHECKPOINT_PASS = intPreferencesKey("checkpoint_pass_percent")
        val PREFETCH_ENABLED = booleanPreferencesKey("prefetch_enabled")
        val PREFETCH_WIFI_ONLY = booleanPreferencesKey("prefetch_wifi_only")
        val PREFETCH_BATCH_SIZE = intPreferencesKey("prefetch_batch_size")
        val LOCKSCREEN_ENABLED = booleanPreferencesKey("lockscreen_notif_enabled")
        val LOCKSCREEN_ONGOING = booleanPreferencesKey("lockscreen_ongoing")
        val LOCKSCREEN_OVERLAY = booleanPreferencesKey("lockscreen_overlay_enabled")
        val LOCKSCREEN_SHOW_QUIZ = booleanPreferencesKey("lockscreen_show_quiz")
        val LOCKSCREEN_INTERVAL = intPreferencesKey("lockscreen_interval_min")
        val DND_START = intPreferencesKey("dnd_start_hour")
        val DND_END = intPreferencesKey("dnd_end_hour")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val TTS_AUTO_PLAY = booleanPreferencesKey("tts_auto_play_default")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val WIDGET_CARD_INDEX = intPreferencesKey("widget_card_index")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        geminiModel = AppSettings.sanitizeModel(this[Keys.GEMINI_MODEL] ?: AppSettings.DEFAULT_GEMINI_MODEL),
        newToReviewRatio = (this[Keys.NEW_TO_REVIEW_RATIO] ?: 5).coerceIn(1, 10),
        mcPercent = (this[Keys.MC_PERCENT] ?: 70).coerceIn(30, 100),
        dailyGoalQuestions = (this[Keys.DAILY_GOAL] ?: 20).coerceIn(5, 200),
        weakWrongThreshold = (this[Keys.WEAK_THRESHOLD] ?: 2).coerceIn(1, 10),
        checkpointPassPercent = (this[Keys.CHECKPOINT_PASS] ?: 80).coerceIn(50, 100),
        prefetchEnabled = this[Keys.PREFETCH_ENABLED] ?: true,
        prefetchWifiOnly = this[Keys.PREFETCH_WIFI_ONLY] ?: true,
        prefetchBatchSize = (this[Keys.PREFETCH_BATCH_SIZE] ?: 8).coerceIn(4, 20),
        lockScreenNotifEnabled = this[Keys.LOCKSCREEN_ENABLED] ?: true,
        lockScreenOngoing = this[Keys.LOCKSCREEN_ONGOING] ?: true,
        lockScreenOverlayEnabled = this[Keys.LOCKSCREEN_OVERLAY] ?: false,
        lockScreenShowQuiz = this[Keys.LOCKSCREEN_SHOW_QUIZ] ?: true,
        lockScreenIntervalMinutes = (this[Keys.LOCKSCREEN_INTERVAL] ?: 60).coerceIn(15, 360),
        dndStartHour = (this[Keys.DND_START] ?: 23).coerceIn(0, 23),
        dndEndHour = (this[Keys.DND_END] ?: 7).coerceIn(0, 23),
        reminderEnabled = this[Keys.REMINDER_ENABLED] ?: true,
        reminderHour = (this[Keys.REMINDER_HOUR] ?: 20).coerceIn(0, 23),
        reminderMinute = (this[Keys.REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
        ttsAutoPlayDefault = this[Keys.TTS_AUTO_PLAY] ?: false,
        ttsSpeechRate = (this[Keys.TTS_SPEECH_RATE] ?: 1.0f).coerceIn(0.5f, 2.0f),
        widgetCardIndex = (this[Keys.WIDGET_CARD_INDEX] ?: 0).coerceAtLeast(0),
        themeMode = AppSettings.sanitizeThemeMode(this[Keys.THEME_MODE] ?: "SYSTEM")
    )

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { it.toAppSettings() }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { p ->
        KeystoreCrypto.decrypt(p[Keys.API_KEY_ENC] ?: "")
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun getApiKey(): String = apiKeyFlow.first()

    suspend fun saveApiKey(rawKey: String) {
        val encrypted = KeystoreCrypto.encrypt(rawKey.trim())
        context.dataStore.edit { it[Keys.API_KEY_ENC] = encrypted }
    }

    /**
     * Atomically advances widgetCardIndex modulo cardCount inside DataStore.edit to prevent races.
     */
    suspend fun advanceWidgetCardIndex(cardCount: Int): Int {
        if (cardCount <= 0) return 0
        var result = 0
        context.dataStore.edit { p ->
            val current = (p[Keys.WIDGET_CARD_INDEX] ?: 0).coerceAtLeast(0)
            result = (current + 1) % cardCount
            p[Keys.WIDGET_CARD_INDEX] = result
        }
        return result
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
        var result = AppSettings()
        context.dataStore.edit { p ->
            val updated = transform(p.toAppSettings())
            val sanitized = updated.copy(
                geminiModel = AppSettings.sanitizeModel(updated.geminiModel),
                newToReviewRatio = updated.newToReviewRatio.coerceIn(1, 10),
                mcPercent = updated.mcPercent.coerceIn(30, 100),
                dailyGoalQuestions = updated.dailyGoalQuestions.coerceIn(5, 200),
                weakWrongThreshold = updated.weakWrongThreshold.coerceIn(1, 10),
                checkpointPassPercent = updated.checkpointPassPercent.coerceIn(50, 100),
                prefetchBatchSize = updated.prefetchBatchSize.coerceIn(4, 20),
                lockScreenIntervalMinutes = updated.lockScreenIntervalMinutes.coerceIn(15, 360),
                dndStartHour = updated.dndStartHour.coerceIn(0, 23),
                dndEndHour = updated.dndEndHour.coerceIn(0, 23),
                reminderHour = updated.reminderHour.coerceIn(0, 23),
                reminderMinute = updated.reminderMinute.coerceIn(0, 59),
                ttsSpeechRate = updated.ttsSpeechRate.coerceIn(0.5f, 2.0f),
                widgetCardIndex = updated.widgetCardIndex.coerceAtLeast(0),
                themeMode = AppSettings.sanitizeThemeMode(updated.themeMode)
            )
            p[Keys.GEMINI_MODEL] = sanitized.geminiModel
            p[Keys.NEW_TO_REVIEW_RATIO] = sanitized.newToReviewRatio
            p[Keys.MC_PERCENT] = sanitized.mcPercent
            p[Keys.DAILY_GOAL] = sanitized.dailyGoalQuestions
            p[Keys.WEAK_THRESHOLD] = sanitized.weakWrongThreshold
            p[Keys.CHECKPOINT_PASS] = sanitized.checkpointPassPercent
            p[Keys.PREFETCH_ENABLED] = sanitized.prefetchEnabled
            p[Keys.PREFETCH_WIFI_ONLY] = sanitized.prefetchWifiOnly
            p[Keys.PREFETCH_BATCH_SIZE] = sanitized.prefetchBatchSize
            p[Keys.LOCKSCREEN_ENABLED] = sanitized.lockScreenNotifEnabled
            p[Keys.LOCKSCREEN_ONGOING] = sanitized.lockScreenOngoing
            p[Keys.LOCKSCREEN_OVERLAY] = sanitized.lockScreenOverlayEnabled
            p[Keys.LOCKSCREEN_SHOW_QUIZ] = sanitized.lockScreenShowQuiz
            p[Keys.LOCKSCREEN_INTERVAL] = sanitized.lockScreenIntervalMinutes
            p[Keys.DND_START] = sanitized.dndStartHour
            p[Keys.DND_END] = sanitized.dndEndHour
            p[Keys.REMINDER_ENABLED] = sanitized.reminderEnabled
            p[Keys.REMINDER_HOUR] = sanitized.reminderHour
            p[Keys.REMINDER_MINUTE] = sanitized.reminderMinute
            p[Keys.TTS_AUTO_PLAY] = sanitized.ttsAutoPlayDefault
            p[Keys.TTS_SPEECH_RATE] = sanitized.ttsSpeechRate
            p[Keys.WIDGET_CARD_INDEX] = sanitized.widgetCardIndex
            p[Keys.THEME_MODE] = sanitized.themeMode
            result = sanitized
        }
        return result
    }
}
