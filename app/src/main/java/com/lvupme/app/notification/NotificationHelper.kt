package com.lvupme.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lvupme.app.MainActivity
import com.lvupme.app.R

object NotificationHelper {
    const val CHANNEL_LOCKSCREEN_CARD = "lvup_lockscreen_card"
    const val CHANNEL_REMINDER = "lvup_daily_reminder"

    const val NOTIF_ID_LOCKSCREEN = 1001
    const val NOTIF_ID_REMINDER = 1002

    const val EXTRA_SUBJECT_ID = "extra_subject_id"
    const val EXTRA_CARD_ID = "extra_card_id"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val lockChannel = NotificationChannel(
            CHANNEL_LOCKSCREEN_CARD,
            "잠금화면 지식 카드",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "메인 과목의 짧은 마이크로 레슨 카드를 잠금화면 알림으로 표시합니다."
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "일일 학습 리마인더",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "매일 설정한 시간에 학습 목표와 연속 학습일 알림을 보냅니다."
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannel(lockChannel)
        nm.createNotificationChannel(reminderChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun cancelLockScreenCard(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID_LOCKSCREEN)
        }
    }

    fun showLockScreenCard(
        context: Context,
        subjectId: Long,
        subjectName: String,
        cardId: Long,
        cardTitle: String,
        microSummary: String,
        exampleText: String,
        ongoing: Boolean = true,
        silentUpdate: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        createChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("lvupme://study/$subjectId/$cardId")
            putExtra(EXTRA_SUBJECT_ID, subjectId)
            putExtra(EXTRA_CARD_ID, cardId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_LOCKSCREEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NEXT_LOCKSCREEN_CARD
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_LOCKSCREEN_CARD
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            2002,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val expandedBody = buildString {
            append(microSummary)
            if (exampleText.isNotBlank()) {
                append("\n예시: ")
                append(exampleText)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_LOCKSCREEN_CARD)
            .setSmallIcon(R.drawable.ic_stat_lvup)
            .setContentTitle("[$subjectName] $cardTitle")
            .setContentText(microSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(silentUpdate)
            .setSilent(silentUpdate)
            .setContentIntent(openPendingIntent)
            .addAction(0, "다음 카드 ↻", nextPendingIntent)
            .addAction(0, "퀴즈 풀기", openPendingIntent)

        if (ongoing) {
            builder.addAction(0, "닫기", dismissPendingIntent)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_LOCKSCREEN, builder.build())
        }
    }

    fun showDailyReminder(
        context: Context,
        todayCount: Int,
        dailyGoal: Int,
        streakDays: Int
    ) {
        if (!hasNotificationPermission(context)) return
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remaining = (dailyGoal - todayCount).coerceAtLeast(0)
        val title = if (streakDays > 0) {
            "연속 ${streakDays}일 학습 중! 오늘 목표까지 ${remaining}문제"
        } else {
            "오늘의 5분 LvUp 학습 시간입니다"
        }
        val body = "지금까지 ${todayCount}/${dailyGoal}문제 완료했어요. 가볍게 몇 문제만 풀어볼까요?"

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_lvup)
            .setContentTitle(title)
            .setContentText(body)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, notification)
        }
    }
}
