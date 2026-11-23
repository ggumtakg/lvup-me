package com.lvupme.app.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lvupme.app.LvUpMeApp
import com.lvupme.app.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    val appCtx = context.applicationContext
                    val container = (appCtx as LvUpMeApp).container
                    val settings = container.settingsRepository.getSettings()
                    WorkScheduler.ensureScheduled(appCtx, settings)
                    if (settings.lockScreenOverlayEnabled) {
                        LockScreenService.sync(appCtx, true)
                    }
                }
            } catch (_: Exception) {
                // Swallow exceptions to prevent broadcast crash/ANR on Android 14-15 restrictions
            } finally {
                pending.finish()
            }
        }
    }
}
