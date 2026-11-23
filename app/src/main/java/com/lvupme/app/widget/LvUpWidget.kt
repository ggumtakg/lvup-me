package com.lvupme.app.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lvupme.app.LvUpMeApp
import com.lvupme.app.MainActivity
import com.lvupme.app.R
import com.lvupme.app.notification.NotificationHelper

class LvUpWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as LvUpMeApp).container
        val cardTriple = runCatching { container.learningRepository.getMainWidgetCard(advanceIndex = false) }.getOrNull()
        val settings = runCatching { container.settingsRepository.getSettings() }.getOrNull()
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (settings?.themeMode) {
            "DARK" -> true
            "LIGHT" -> false
            else -> systemDark
        }

        provideContent {
            WidgetContent(context = context, cardTriple = cardTriple, isDark = isDark)
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        cardTriple: Triple<
            com.lvupme.app.data.local.SubjectEntity,
            com.lvupme.app.data.local.CurriculumUnitEntity?,
            com.lvupme.app.data.local.LearningCardEntity
        >?,
        isDark: Boolean
    ) {
        val borderShell = ColorProvider(if (isDark) Color(0xFF365363) else Color(0xFF9AC5D4))
        val scrimColor = ColorProvider(if (isDark) Color(0x990D181E) else Color(0xB8FFFFFF))
        val chipBg = ColorProvider(if (isDark) Color(0xCC143144) else Color(0xE6DDF4FF))
        val nextBtnBg = ColorProvider(Color(0xFF58CC02))
        val skyColor = ColorProvider(if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
        val emeraldColor = ColorProvider(if (isDark) Color(0xFF7BE82E) else Color(0xFF2E7D00))
        val textPrimary = ColorProvider(if (isDark) Color(0xFFF4F9FC) else Color(0xFF0F172A))
        val textSecondary = ColorProvider(if (isDark) Color(0xFFD6E6F0) else Color(0xFF334155))
        val onEmerald = ColorProvider(Color.White)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (cardTriple != null) {
                data = Uri.parse("lvupme://study/${cardTriple.first.id}/${cardTriple.third.id}")
                putExtra(NotificationHelper.EXTRA_SUBJECT_ID, cardTriple.first.id)
                putExtra(NotificationHelper.EXTRA_CARD_ID, cardTriple.third.id)
            }
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(borderShell)
                .cornerRadius(20.dp)
                .padding(2.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
            ) {
                Image(
                    provider = ImageProvider(if (isDark) R.drawable.bg_peaks_dark else R.drawable.bg_peaks_light),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize()
                )
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .padding(14.dp)
                        .clickable(actionStartActivity(openAppIntent))
                ) {
                    if (cardTriple == null) {
                        Text(
                            text = "⚡ LvUp Me",
                            style = TextStyle(color = emeraldColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.height(6.dp))
                        Text(
                            text = "앱에서 첫 과목을 추가하면\n여기에 마이크로 레슨 카드가 표시됩니다.",
                            style = TextStyle(color = textPrimary, fontSize = 13.sp)
                        )
                    } else {
                        val (subject, unit, card) = cardTriple
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "★ ${subject.name} · ${unit?.title ?: subject.subTopic}",
                                style = TextStyle(color = skyColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .background(chipBg)
                                    .cornerRadius(10.dp)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                text = "다음 ↻",
                                style = TextStyle(color = onEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier
                                    .background(nextBtnBg)
                                    .cornerRadius(10.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .clickable(actionRunCallback<NextWidgetCardAction>())
                            )
                        }

                        Spacer(GlanceModifier.height(8.dp))
                        Text(
                            text = card.title,
                            style = TextStyle(color = textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = card.microSummary,
                            style = TextStyle(color = textSecondary, fontSize = 12.sp),
                            maxLines = 3
                        )
                        if (card.exampleText.isNotBlank()) {
                            Spacer(GlanceModifier.height(5.dp))
                            Text(
                                text = "예: ${card.exampleText}",
                                style = TextStyle(color = emeraldColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

class NextWidgetCardAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val container = (context.applicationContext as LvUpMeApp).container
        val triple = container.learningRepository.getMainWidgetCard(advanceIndex = true)
        LvUpWidget().update(context, glanceId)
        val settings = container.settingsRepository.getSettings()
        if (triple != null && settings.lockScreenNotifEnabled && settings.lockScreenOngoing) {
            val (subject, _, card) = triple
            NotificationHelper.showLockScreenCard(
                context = context,
                subjectId = subject.id,
                subjectName = subject.name,
                cardId = card.id,
                cardTitle = card.title,
                microSummary = card.microSummary,
                exampleText = card.exampleText,
                ongoing = true,
                silentUpdate = true
            )
        }
    }
}

class LvUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LvUpWidget()
}
