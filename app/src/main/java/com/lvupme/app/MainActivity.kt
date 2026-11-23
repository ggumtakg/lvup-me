package com.lvupme.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.lvupme.app.lockscreen.LockScreenService
import com.lvupme.app.notification.NotificationHelper
import com.lvupme.app.ui.navigation.LvUpNavGraph
import com.lvupme.app.ui.theme.LvUpMeTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val deepLinkSubjectId = mutableStateOf<Long?>(null)
    private val deepLinkCardId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLinkIntent(intent)

        val container = (application as LvUpMeApp).container
        setContent {
            // Wait briefly for stored themeMode before rendering to avoid cold-start theme flash
            val themeModeState by container.settingsRepository.settingsFlow
                .map<com.lvupme.app.data.local.AppSettings, String?> { it.themeMode }
                .collectAsState(initial = null)
            val themeMode = themeModeState ?: return@setContent

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }
            LvUpMeTheme(darkTheme = darkTheme) {
                LvUpNavGraph(
                    container = container,
                    deepLinkSubjectId = deepLinkSubjectId.value,
                    deepLinkCardId = deepLinkCardId.value,
                    onDeepLinkConsumed = {
                        deepLinkSubjectId.value = null
                        deepLinkCardId.value = null
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync LockScreenService safely from foreground (Android 12-15 FGS compliant)
        val container = (application as LvUpMeApp).container
        lifecycleScope.launch {
            val settings = container.settingsRepository.getSettings()
            LockScreenService.sync(this@MainActivity, settings.lockScreenOverlayEnabled)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val subId = intent?.getLongExtra(NotificationHelper.EXTRA_SUBJECT_ID, -1L) ?: -1L
        val cardId = intent?.getLongExtra(NotificationHelper.EXTRA_CARD_ID, -1L) ?: -1L
        if (subId > 0L) {
            deepLinkSubjectId.value = subId
            deepLinkCardId.value = cardId.takeIf { it > 0L }
            // Clear extras so screen rotation does not re-trigger navigation
            intent?.removeExtra(NotificationHelper.EXTRA_SUBJECT_ID)
            intent?.removeExtra(NotificationHelper.EXTRA_CARD_ID)
        }
    }
}
