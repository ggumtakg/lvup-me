package com.lvupme.app.lockscreen

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lvupme.app.LvUpMeApp
import com.lvupme.app.MainActivity
import com.lvupme.app.R
import com.lvupme.app.ui.lockscreen.LockScreenActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime

class LockScreenService : Service() {

    companion object {
        private const val CHANNEL_SERVICE = "lvup_lockscreen_service"
        private const val NOTIF_ID_SERVICE = 1099

        /**
         * Safely starts or stops LockScreenService, catching Android 12-15 background FGS restrictions.
         * Returns true if the requested sync succeeded.
         */
        fun sync(context: Context, enabled: Boolean): Boolean {
            val canOverlay = Settings.canDrawOverlays(context)
            val intent = Intent(context, LockScreenService::class.java)
            return if (enabled && canOverlay) {
                runCatching {
                    ContextCompat.startForegroundService(context, intent)
                    true
                }.getOrDefault(false)
            } else {
                runCatching { context.stopService(intent) }
                true
            }
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_SERVICE,
            "잠금화면 학습 카드 대기",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "화면을 켤 때 LvUp Me 잠금화면 학습 카드를 표시하기 위한 백그라운드 서비스입니다."
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIF_ID_SERVICE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_lvup)
            .setContentTitle("LvUp Me 잠금화면 카드 활성화됨")
            .setContentText("화면을 켤 때 메인 과목 학습 카드를 표시합니다.")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID_SERVICE, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID_SERVICE, notif)
            }
        }.onFailure {
            stopSelf()
        }
    }

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_ON) return
                handleScreenOn()
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = receiver
    }

    private fun handleScreenOn() {
        if (LockScreenActivity.isShowing) return
        if (!Settings.canDrawOverlays(this)) return

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) return

        serviceScope.launch {
            val container = (applicationContext as LvUpMeApp).container
            val settings = container.settingsRepository.getSettings()
            if (!settings.lockScreenOverlayEnabled) {
                stopSelf()
                return@launch
            }
            val hour = LocalTime.now().hour
            if (isWithinDnd(hour, settings.dndStartHour, settings.dndEndHour)) {
                return@launch
            }
            val hasCard = container.learningRepository.getMainWidgetCard(advanceIndex = false) != null
            if (!hasCard) return@launch

            val lockIntent = Intent(this@LockScreenService, LockScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            runCatching { startActivity(lockIntent) }
        }
    }

    private fun isWithinDnd(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        return if (startHour == endHour) {
            false
        } else if (startHour < endHour) {
            currentHour in startHour until endHour
        } else {
            currentHour >= startHour || currentHour < endHour
        }
    }
}
