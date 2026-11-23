package com.lvupme.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lvupme.app.LvUpMeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NEXT_LOCKSCREEN_CARD = "com.lvupme.app.ACTION_NEXT_LOCKSCREEN_CARD"
        const val ACTION_DISMISS_LOCKSCREEN_CARD = "com.lvupme.app.ACTION_DISMISS_LOCKSCREEN_CARD"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_DISMISS_LOCKSCREEN_CARD -> {
                NotificationHelper.cancelLockScreenCard(context)
            }

            ACTION_NEXT_LOCKSCREEN_CARD -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withTimeout(8_000L) {
                            val container = (context.applicationContext as LvUpMeApp).container
                            val settings = container.settingsRepository.getSettings()
                            val cardTriple = container.learningRepository.getMainWidgetCard(advanceIndex = true)
                            if (cardTriple != null) {
                                val (subject, _, card) = cardTriple
                                NotificationHelper.showLockScreenCard(
                                    context = context,
                                    subjectId = subject.id,
                                    subjectName = subject.name,
                                    cardId = card.id,
                                    cardTitle = card.title,
                                    microSummary = card.microSummary,
                                    exampleText = card.exampleText,
                                    ongoing = settings.lockScreenOngoing,
                                    silentUpdate = true
                                )
                                container.learningRepository.refreshWidget()
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore timeout or transient errors safely
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
