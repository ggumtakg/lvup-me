package com.lvupme.app.ui.lockscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lvupme.app.AppContainer
import com.lvupme.app.LvUpMeApp
import com.lvupme.app.MainActivity
import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.CurriculumUnitEntity
import com.lvupme.app.data.local.LearningCardEntity
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.data.local.QuizQuestionEntity
import com.lvupme.app.data.local.SubjectEntity
import com.lvupme.app.domain.AnswerGrader
import com.lvupme.app.domain.ScheduledQuestion
import com.lvupme.app.domain.SrsScheduler
import com.lvupme.app.notification.NotificationHelper
import com.lvupme.app.ui.theme.ChoiceTileCard
import com.lvupme.app.ui.theme.ChoiceVisualState
import com.lvupme.app.ui.theme.GamePillBadge
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.LvUpBackground
import com.lvupme.app.ui.theme.LvUpMeTheme
import com.lvupme.app.ui.theme.TactileButton
import com.lvupme.app.ui.theme.TactileStyle
import com.lvupme.app.ui.theme.game
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class LockScreenActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isShowing: Boolean = false
    }

    private val reloadSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenFlags()

        val container = (application as LvUpMeApp).container
        setContent {
            val themeModeState by container.settingsRepository.settingsFlow
                .map<AppSettings, String?> { it.themeMode }
                .collectAsState(initial = null)
            val themeMode = themeModeState ?: return@setContent

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }
            LvUpMeTheme(darkTheme = darkTheme) {
                LockScreenCardContent(
                    container = container,
                    reloadSignal = reloadSignal.intValue,
                    onOpenInApp = { subjectId, cardId ->
                        openMainApp(subjectId, cardId)
                    },
                    onDismiss = {
                        finishWithNoAnim()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reloadSignal.intValue++
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        super.onPause()
        isShowing = false
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    private fun openMainApp(subjectId: Long, cardId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_SUBJECT_ID, subjectId)
            putExtra(NotificationHelper.EXTRA_CARD_ID, cardId)
        }
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    startActivity(intent)
                    finishWithNoAnim()
                }
                override fun onDismissCancelled() {
                    // Stay on lock screen card
                }
            })
        } else {
            startActivity(intent)
            finishWithNoAnim()
        }
    }

    private fun finishWithNoAnim() {
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

@Composable
private fun LockScreenCardContent(
    container: AppContainer,
    reloadSignal: Int,
    onOpenInApp: (subjectId: Long, cardId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val g = MaterialTheme.game
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            now = current
            val millisToNextMinute = ((60 - current.second) * 1000L - (current.nano / 1_000_000L)).coerceIn(500L, 60_000L)
            delay(millisToNextMinute)
        }
    }

    var subject by remember { mutableStateOf<SubjectEntity?>(null) }
    var unit by remember { mutableStateOf<CurriculumUnitEntity?>(null) }
    var card by remember { mutableStateOf<LearningCardEntity?>(null) }
    var mcQuestion by remember { mutableStateOf<QuizQuestionEntity?>(null) }
    var choices by remember { mutableStateOf<List<String>>(emptyList()) }

    var selectedChoice by remember { mutableStateOf<String?>(null) }
    var quizAnswered by remember { mutableStateOf(false) }
    var quizCorrect by remember { mutableStateOf(false) }
    var showQuizPanel by remember { mutableStateOf(false) }

    fun loadCard(advance: Boolean) {
        scope.launch {
            val triple = container.learningRepository.getMainWidgetCard(advanceIndex = advance)
            if (triple != null) {
                subject = triple.first
                unit = triple.second
                card = triple.third
                val qs = container.database.quizDao().getByCard(triple.third.id)
                val mc = qs.firstOrNull { it.type == QuestionType.MULTIPLE_CHOICE }
                mcQuestion = mc
                choices = mc?.let {
                    runCatching { json.decodeFromString<List<String>>(it.choicesJson) }
                        .getOrDefault(emptyList())
                        .shuffled()
                } ?: emptyList()
                selectedChoice = null
                quizAnswered = false
                quizCorrect = false
                showQuizPanel = false
                if (advance) {
                    container.learningRepository.refreshWidget()
                }
            }
        }
    }

    LaunchedEffect(reloadSignal) { loadCard(advance = false) }

    val timeText = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val dateText = remember(now) { now.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)) }
    val letters = listOf("A", "B", "C", "D")

    Box(modifier = Modifier.fillMaxSize()) {
        LvUpBackground(scrimAlphaOverride = if (g.isDark) 0.14f else 0.06f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Clock Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = timeText,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Center Gamified Learning Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                val c = card
                val s = subject
                if (c == null || s == null) {
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp)) {
                            Text("⚡ LvUp Me", color = g.emerald, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "표시할 학습 카드가 아직 없습니다.\n앱에서 첫 과목을 추가해 주세요.",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    GamifiedCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        borderColor = g.sky.copy(alpha = 0.6f),
                        lipColor = g.skyLip.copy(alpha = 0.6f)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GamePillBadge(
                                    text = "${s.name} · ${unit?.title ?: s.subTopic}",
                                    containerColor = g.skySoft,
                                    contentColor = g.sky,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val ttsText = c.ttsText.ifBlank { c.title }
                                    if (s.ttsEnabled && ttsText.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(g.emeraldSoft),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    container.ttsHelper.speak(ttsText, s.ttsLocale, settings.ttsSpeechRate)
                                                }
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "발음 듣기",
                                                    tint = g.emerald,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    GamePillBadge(
                                        text = "다음",
                                        containerColor = g.goldSoft,
                                        contentColor = g.gold,
                                        icon = Icons.Default.Refresh,
                                        onClick = { loadCard(advance = true) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = c.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = c.microSummary,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (c.exampleText.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    color = g.emeraldSoft,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "예: ${c.exampleText}",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = g.emerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val q = mcQuestion
                            if (settings.lockScreenShowQuiz && q != null && choices.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = g.cardBorder)
                                Spacer(Modifier.height(14.dp))

                                if (!showQuizPanel) {
                                    TactileButton(
                                        onClick = { showQuizPanel = true },
                                        style = TactileStyle.SKY,
                                        height = 46.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Bolt, null, Modifier.size(20.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("바로 미니 퀴즈 풀기", fontWeight = FontWeight.ExtraBold)
                                    }
                                } else {
                                    Text(
                                        text = q.prompt,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        choices.forEachIndexed { idx, ch ->
                                            val isThisAnswer = AnswerGrader.normalize(ch) == AnswerGrader.normalize(q.answerText)
                                            val visualState = when {
                                                !quizAnswered -> ChoiceVisualState.IDLE
                                                isThisAnswer -> ChoiceVisualState.CORRECT
                                                ch == selectedChoice -> ChoiceVisualState.WRONG
                                                else -> ChoiceVisualState.DIMMED
                                            }
                                            ChoiceTileCard(
                                                letter = letters.getOrElse(idx) { "${idx + 1}" },
                                                text = ch,
                                                state = visualState,
                                                enabled = !quizAnswered,
                                                onClick = {
                                                    selectedChoice = ch
                                                    quizCorrect = isThisAnswer
                                                    quizAnswered = true
                                                    container.appScope.launch {
                                                        val existing = container.database.reviewDao().getByCard(c.id)
                                                            ?: SrsScheduler().defaultReviewState(c)
                                                        container.learningRepository.recordStudyAnswer(
                                                            scheduled = ScheduledQuestion(
                                                                card = c,
                                                                question = q,
                                                                reviewState = existing,
                                                                isFromPastReview = false
                                                            ),
                                                            isCorrect = isThisAnswer,
                                                            userInput = ch
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    if (quizAnswered) {
                                        Spacer(Modifier.height(10.dp))
                                        Surface(
                                            color = if (quizCorrect) g.emeraldSoft else g.coralSoft,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text(
                                                    if (quizCorrect) "정답이에요! 🎉" else "오답 · 정답: ${q.answerText}",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (quizCorrect) g.emerald else g.coral
                                                )
                                                if (q.wrongReason.isNotBlank()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        q.wrongReason,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        TactileButton(
                                            onClick = { loadCard(advance = true) },
                                            style = TactileStyle.EMERALD,
                                            height = 46.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("다음 카드로 넘어가기 →", fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val c = card
                val s = subject
                if (c != null && s != null) {
                    TactileButton(
                        onClick = { onOpenInApp(s.id, c.id) },
                        style = TactileStyle.EMERALD,
                        height = 46.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("앱에서 학습", fontWeight = FontWeight.ExtraBold)
                    }
                }
                TactileButton(
                    onClick = onDismiss,
                    style = TactileStyle.NEUTRAL,
                    height = 46.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LockOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("닫기", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
