package com.lvupme.app.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsHelper(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile
    private var isReady = false

    @Volatile
    private var isInitializing = false

    private data class PendingSpeech(val text: String, val localeTag: String, val speechRate: Float)

    @Volatile
    private var pendingSpeech: PendingSpeech? = null

    @Synchronized
    private fun ensureInitialized() {
        if (tts != null || isInitializing) return
        isInitializing = true
        tts = TextToSpeech(appContext) { status ->
            isReady = (status == TextToSpeech.SUCCESS)
            isInitializing = false
            val pending = pendingSpeech
            pendingSpeech = null
            if (isReady && pending != null) {
                speakInternal(pending.text, pending.localeTag, pending.speechRate)
            }
        }
    }

    fun speak(text: String, localeTag: String = "en-US", speechRate: Float = 1.0f) {
        if (text.isBlank()) return
        if (!isReady) {
            pendingSpeech = PendingSpeech(text, localeTag, speechRate)
            ensureInitialized()
            return
        }
        speakInternal(text, localeTag, speechRate)
    }

    private fun speakInternal(text: String, localeTag: String, speechRate: Float) {
        val engine = tts ?: return
        val requestedLocale = Locale.forLanguageTag(localeTag.ifBlank { "en-US" })
        val langResult = engine.setLanguage(requestedLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = engine.setLanguage(Locale.US)
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
        }
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lvup_tts_${System.currentTimeMillis()}")
    }

    fun stop() {
        pendingSpeech = null
        tts?.stop()
    }

    @Synchronized
    fun shutdown() {
        pendingSpeech = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        isInitializing = false
    }
}
