package com.lvupme.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lvupme.app.AppContainer
import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.SubjectEntity
import com.lvupme.app.data.repository.SubjectDashboardItem
import com.lvupme.app.notification.NotificationHelper
import com.lvupme.app.ui.theme.ChunkyProgressBar
import com.lvupme.app.ui.theme.GamePillBadge
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.TactileButton
import com.lvupme.app.ui.theme.TactileStyle
import com.lvupme.app.ui.theme.game
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onAddSubject: () -> Unit,
    onOpenStudy: (Long) -> Unit,
    onOpenExam: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val repo = container.learningRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = MaterialTheme.game

    val dashboards by repo.observeSubjectDashboards().collectAsState(initial = emptyList())
    val todayCount by repo.observeTodayCount().collectAsState(initial = 0)
    val streakDays by repo.observeStreakDays().collectAsState(initial = 0)
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    val apiKey by container.settingsRepository.apiKeyFlow.collectAsState(initial = "")

    var hasNotifPerm by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPerm = granted }

    var deleteTarget by remember { mutableStateOf<SubjectEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(g.emerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("LvUp Me", fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    GamePillBadge(
                        text = "${streakDays}일",
                        containerColor = g.goldSoft,
                        contentColor = g.gold,
                        icon = Icons.Default.LocalFireDepartment
                    )
                    Spacer(Modifier.width(8.dp))
                    GamePillBadge(
                        text = "$todayCount/${settings.dailyGoalQuestions}",
                        containerColor = g.emeraldSoft,
                        contentColor = g.emerald,
                        icon = Icons.Default.Bolt
                    )
                    Spacer(Modifier.width(12.dp))
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddSubject,
                containerColor = g.violet,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "과목 추가") },
                text = { Text("새 과목 추가", fontWeight = FontWeight.ExtraBold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (apiKey.isBlank()) {
                item {
                    GamifiedCard(
                        containerColor = g.coralSoft,
                        borderColor = g.coral,
                        lipColor = g.coralLip
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Gemini API 키 설정이 필요해요!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.coral
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "AI 대화형 커리큘럼 설계, 수준 테스트, 문제 생성을 위해 설정에서 개인 Gemini API 키를 입력해 주세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            TactileButton(
                                onClick = onOpenSettings,
                                style = TactileStyle.CORAL,
                                height = 44.dp
                            ) {
                                Text("설정에서 API 키 입력하기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotifPerm &&
                (settings.lockScreenNotifEnabled || settings.reminderEnabled)
            ) {
                item(key = "notif_perm_banner") {
                    GamifiedCard(
                        containerColor = g.goldSoft,
                        borderColor = g.gold,
                        lipColor = g.goldLip
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "알림 권한을 허용해 주세요",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.gold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "잠금화면 지식 카드와 매일 학습 리마인더를 받으려면 알림 권한이 필요합니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(10.dp))
                            TactileButton(
                                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                style = TactileStyle.GOLD,
                                height = 42.dp
                            ) {
                                Text("알림 권한 허용하기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            item {
                DailyQuestHeroCard(
                    todayCount = todayCount,
                    dailyGoal = settings.dailyGoalQuestions,
                    streakDays = streakDays
                )
            }

            if (dashboards.isEmpty()) {
                item {
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(g.violetSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = g.violet,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "첫 학습 여정을 시작해 볼까요?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "AI와 짧은 대화를 나눠 과목과 상세 분야를 정하고,\n수준 테스트로 나만의 커리큘럼을 만들어 보세요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            TactileButton(
                                onClick = onAddSubject,
                                style = TactileStyle.EMERALD,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("AI와 첫 과목 설계하기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "내 학습 과목",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "★ 위젯·잠금화면 메인",
                            style = MaterialTheme.typography.labelMedium,
                            color = g.gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(dashboards, key = { "subject_${it.subject.id}" }) { item ->
                    SubjectGameCard(
                        item = item,
                        onSetMain = {
                            scope.launch { repo.setMainWidgetSubject(item.subject.id) }
                        },
                        onToggleTts = {
                            scope.launch {
                                repo.updateSubjectTts(
                                    item.subject.id,
                                    !item.subject.ttsEnabled,
                                    item.subject.ttsLocale
                                )
                            }
                        },
                        onStudy = { onOpenStudy(item.subject.id) },
                        onExam = { onOpenExam(item.subject.id) },
                        onDelete = { deleteTarget = item.subject }
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("과목 삭제", fontWeight = FontWeight.ExtraBold) },
            text = { Text("'${target.name} (${target.subTopic})' 과목과 모든 학습 기록을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteSubject(target) }
                    deleteTarget = null
                }) {
                    Text("삭제", color = g.coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun DailyQuestHeroCard(
    todayCount: Int,
    dailyGoal: Int,
    streakDays: Int
) {
    val g = MaterialTheme.game
    val progress = if (dailyGoal > 0) (todayCount.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
    val remaining = (dailyGoal - todayCount).coerceAtLeast(0)
    val goalReached = todayCount >= dailyGoal && dailyGoal > 0

    GamifiedCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (goalReached) g.emeraldSoft else g.cardBg,
        borderColor = if (goalReached) g.emerald else g.cardBorder,
        lipColor = if (goalReached) g.emeraldLip else g.cardBorder
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (goalReached) "오늘의 퀘스트 완료! 🎉" else "오늘의 일일 퀘스트",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (goalReached) g.emerald else g.sky
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$todayCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            " / $dailyGoal 문제",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (goalReached) g.emerald else g.goldSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (goalReached) Icons.Default.EmojiEvents else Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (goalReached) Color.White else g.gold,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ChunkyProgressBar(
                progress = progress,
                fillColor = if (goalReached) g.emerald else g.gold,
                height = 14.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    goalReached -> "대단해요! ${streakDays.coerceAtLeast(1)}일 연속 학습 스트릭을 이어가고 있어요."
                    todayCount == 0 -> "가볍게 첫 문제를 풀어 오늘의 스트릭을 시작해 보세요!"
                    else -> "목표까지 딱 ${remaining}문제 남았어요. 조금만 더 힘내볼까요?"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubjectGameCard(
    item: SubjectDashboardItem,
    onSetMain: () -> Unit,
    onToggleTts: () -> Unit,
    onStudy: () -> Unit,
    onExam: () -> Unit,
    onDelete: () -> Unit
) {
    val g = MaterialTheme.game
    val subject = item.subject
    val unitProgress = if (item.totalUnits > 0) {
        item.completedUnits.toFloat() / item.totalUnits.toFloat()
    } else 0f

    var menuExpanded by remember { mutableStateOf(false) }

    val accentColors = listOf(
        g.sky to g.skySoft,
        g.violet to g.violetSoft,
        g.emerald to g.emeraldSoft,
        g.gold to g.goldSoft
    )
    val (accent, accentSoft) = accentColors[(subject.id.toInt().coerceAtLeast(0)) % accentColors.size]

    GamifiedCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (subject.isMainForWidget) g.gold.copy(alpha = 0.75f) else g.cardBorder,
        lipColor = if (subject.isMainForWidget) g.goldLip.copy(alpha = 0.75f) else g.cardBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subject Emblem
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = subject.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            subject.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        GamePillBadge(
                            text = subject.userLevel,
                            containerColor = accentSoft,
                            contentColor = accent
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subject.subTopic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSetMain) {
                    Icon(
                        imageVector = if (subject.isMainForWidget) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "메인 위젯 과목 지정",
                        tint = if (subject.isMainForWidget) g.gold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "과목 옵션",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (subject.ttsEnabled) "TTS 발음 끄기" else "TTS 발음 켜기") },
                            leadingIcon = {
                                Icon(
                                    if (subject.ttsEnabled) Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleTts()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("과목 삭제", color = g.coral) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = g.coral) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "현재 단원: ${item.activeUnitTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (item.weakCardsCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    GamePillBadge(
                        text = "취약 ${item.weakCardsCount}",
                        containerColor = g.coralSoft,
                        contentColor = g.coral
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ChunkyProgressBar(
                progress = unitProgress,
                fillColor = accent,
                height = 10.dp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "단원 ${item.completedUnits}/${item.totalUnits} 완료 · 학습 카드 ${item.totalCards}개",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (subject.ttsEnabled) {
                    Text(
                        "🔊 TTS ON",
                        style = MaterialTheme.typography.labelSmall,
                        color = g.emerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TactileButton(
                    onClick = onStudy,
                    style = TactileStyle.EMERALD,
                    height = 46.dp,
                    modifier = Modifier.weight(1.25f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("학습 시작", fontWeight = FontWeight.ExtraBold)
                }
                TactileButton(
                    onClick = onExam,
                    style = TactileStyle.SKY,
                    height = 46.dp,
                    modifier = Modifier.weight(0.95f)
                ) {
                    Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("시험", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
