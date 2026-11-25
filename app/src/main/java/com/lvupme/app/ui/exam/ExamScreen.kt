package com.lvupme.app.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lvupme.app.AppContainer
import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.ExamType
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.domain.AnswerGrader
import com.lvupme.app.ui.components.CurriculumUnitsBottomSheet
import com.lvupme.app.ui.theme.ChoiceTileCard
import com.lvupme.app.ui.theme.ChoiceVisualState
import com.lvupme.app.ui.theme.ChunkyProgressBar
import com.lvupme.app.ui.theme.GamePillBadge
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.TactileButton
import com.lvupme.app.ui.theme.TactileStyle
import com.lvupme.app.ui.theme.game
import com.lvupme.app.ui.viewmodel.ExamViewModel
import com.lvupme.app.ui.viewmodel.lvUpViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    container: AppContainer,
    subjectId: Long,
    initialUnitId: Long? = null,
    onBack: () -> Unit,
    onGoStudy: (subjectId: Long, unitId: Long?) -> Unit
) {
    val vm: ExamViewModel = viewModel(
        key = "exam_${subjectId}_${initialUnitId ?: 0L}",
        factory = lvUpViewModelFactory { ExamViewModel(container, subjectId, initialUnitId) }
    )
    val g = MaterialTheme.game

    val subject by container.learningRepository.observeSubject(subjectId).collectAsState(initial = null)
    val unitProgressList by remember(subjectId) {
        container.learningRepository.observeUnitProgressList(subjectId)
    }.collectAsState(initial = null)
    val pastExams by container.learningRepository.observeExams(subjectId).collectAsState(initial = emptyList())
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    var showUnitsSheet by remember { mutableStateOf(false) }

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
                    Text(
                        "시험 · ${subject?.name ?: ""}",
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                vm.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = g.sky)
                        Spacer(Modifier.height(14.dp))
                        Text("시험을 준비·채점 중이에요...", fontWeight = FontWeight.Bold)
                    }
                }

                vm.activeExamType != null && vm.questions.isNotEmpty() -> {
                    val q = vm.questions[vm.currentIndex.coerceIn(0, vm.questions.lastIndex)]
                    val letters = listOf("A", "B", "C", "D")
                    val maxWords = AnswerGrader.maxAllowedWords(q.answerText)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(vm.activeExamTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        GamePillBadge(
                            text = "${vm.currentIndex + 1} / ${vm.questions.size}",
                            containerColor = g.skySoft,
                            contentColor = g.sky
                        )
                    }
                    ChunkyProgressBar(
                        progress = (vm.currentIndex + 1).toFloat() / vm.questions.size.toFloat(),
                        fillColor = g.sky,
                        height = 12.dp
                    )

                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            GamePillBadge(
                                text = if (q.type == QuestionType.SHORT_ANSWER) "주관식 (최대 ${maxWords}단어)" else "객관식",
                                containerColor = if (q.type == QuestionType.SHORT_ANSWER) g.violetSoft else g.skySoft,
                                contentColor = if (q.type == QuestionType.SHORT_ANSWER) g.violet else g.sky
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(q.prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (q.type == QuestionType.MULTIPLE_CHOICE && q.choices.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            q.choices.forEachIndexed { idx, choice ->
                                ChoiceTileCard(
                                    letter = letters.getOrElse(idx) { "${idx + 1}" },
                                    text = choice,
                                    state = ChoiceVisualState.IDLE,
                                    enabled = true,
                                    onClick = { vm.submitExamAnswer(q, choice) }
                                )
                            }
                        }
                    } else {
                        val words = AnswerGrader.countWords(vm.shortInput)
                        GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = vm.shortInput,
                                    onValueChange = { vm.shortInput = it },
                                    label = { Text("정답 입력 (최대 ${maxWords}단어)") },
                                    supportingText = { Text("$words / $maxWords 단어") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(10.dp))
                                TactileButton(
                                    onClick = { vm.submitExamAnswer(q, vm.shortInput) },
                                    enabled = vm.shortInput.isNotBlank() && words <= maxWords,
                                    style = TactileStyle.EMERALD,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("제출", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }

                else -> {
                    vm.errorMsg?.let {
                        GamifiedCard(
                            containerColor = g.coralSoft,
                            borderColor = g.coral,
                            lipColor = g.coralLip
                        ) {
                            Text(it, modifier = Modifier.padding(14.dp), color = g.coral, fontWeight = FontWeight.Bold)
                        }
                    }

                    vm.finishedResult?.let { res ->
                        GamifiedCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (res.passed) g.emeraldSoft else g.coralSoft,
                            borderColor = if (res.passed) g.emerald else g.coral,
                            lipColor = if (res.passed) g.emeraldLip else g.coralLip
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = if (res.passed) g.emerald else g.coral,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (res.passed) "시험 합격! (${res.scorePercent}점)" else "재도전 필요 (${res.scorePercent}점)",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (res.passed) g.emerald else g.coral
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("정답 ${res.correctCount} / ${res.totalQuestions}", fontWeight = FontWeight.Bold)
                                if (res.aiFeedback.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(res.aiFeedback, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(14.dp))
                                TactileButton(
                                    onClick = {
                                        val targetUnitForStudy = if (vm.lastExamWasRetake) vm.activeUnit?.id else null
                                        onGoStudy(subjectId, targetUnitForStudy)
                                    },
                                    style = if (res.passed) TactileStyle.EMERALD else TactileStyle.SKY,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val label = when {
                                        vm.lastExamWasRetake && res.passed -> "이 단원 다시 복습하기 →"
                                        vm.lastExamWasRetake -> "이 단원 취약 개념 복습하기 →"
                                        res.passed && vm.allUnitsCompleted -> "전체 커리큘럼 완료! 복습 계속하기 →"
                                        res.passed -> "다음 단원 학습하기 →"
                                        else -> "취약 개념 복습하러 가기 →"
                                    }
                                    Text(label, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    GamifiedCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (vm.isRetakeCompletedUnit) g.violet.copy(alpha = 0.65f) else g.emerald.copy(alpha = 0.65f),
                        lipColor = if (vm.isRetakeCompletedUnit) g.violetLip.copy(alpha = 0.65f) else g.emeraldLip.copy(alpha = 0.65f)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (vm.isRetakeCompletedUnit) g.violetSoft else g.emeraldSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = if (vm.isRetakeCompletedUnit) g.violet else g.emerald
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (vm.isRetakeCompletedUnit) "과거 단원 복습 시험" else "단원 체크포인트 시험",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        when {
                                            vm.allUnitsCompleted -> "모든 단원 완료 🎉 원하는 단원 재응시 가능"
                                            vm.isRetakeCompletedUnit -> "완료된 단원 재응시 (기존 진도 유지)"
                                            else -> "합격 기준 ${settings.checkpointPassPercent}% 통과 시 다음 단원 해금"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (vm.isRetakeCompletedUnit) g.violet else g.emerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                GamePillBadge(
                                    text = "단원 변경",
                                    containerColor = g.skySoft,
                                    contentColor = g.sky,
                                    icon = Icons.Default.FormatListNumbered,
                                    onClick = { showUnitsSheet = true }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = vm.activeUnit?.let { "대상 단원: ${it.orderIndex}단원 · ${it.title}" } ?: "대상 단원: 없음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            TactileButton(
                                onClick = { vm.startCheckpointExam() },
                                enabled = vm.activeUnit != null,
                                style = if (vm.isRetakeCompletedUnit) TactileStyle.VIOLET else TactileStyle.EMERALD,
                                height = 46.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (vm.isRetakeCompletedUnit) "선택 단원 시험 재응시" else "단원 시험 응시하기",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    GamifiedCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = g.violet.copy(alpha = 0.65f),
                        lipColor = g.violetLip.copy(alpha = 0.65f)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(g.violetSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = g.violet)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("AI 종합 모의시험", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text(
                                        "언제든 실전 10문제 즉시 출제",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = g.violet,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "지금까지 학습한 내용을 바탕으로 AI가 실전 모의시험 10문제를 생성하고 취약 영역 리포트를 제공합니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            TactileButton(
                                onClick = { vm.startMockExam() },
                                style = TactileStyle.VIOLET,
                                height = 46.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("AI 종합 모의시험 시작", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    if (pastExams.isNotEmpty()) {
                        Text("최근 시험 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        pastExams.take(10).forEach { exam ->
                            GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val label = when (exam.examType) {
                                            ExamType.LEVEL_TEST -> "수준 테스트"
                                            ExamType.UNIT_CHECKPOINT -> "단원 체크포인트"
                                            else -> "AI 모의시험"
                                        }
                                        Text(label, fontWeight = FontWeight.ExtraBold)
                                        Text(
                                            "${exam.correctCount}/${exam.totalQuestions} 문항 (${exam.scorePercent}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    GamePillBadge(
                                        text = if (exam.passed) "통과" else "미달",
                                        containerColor = if (exam.passed) g.emeraldSoft else g.coralSoft,
                                        contentColor = if (exam.passed) g.emerald else g.coral
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnitsSheet) {
        CurriculumUnitsBottomSheet(
            subjectName = subject?.name ?: "시험",
            units = unitProgressList,
            currentFocusUnitId = vm.activeUnit?.id,
            focusedBadgeLabel = "선택됨",
            onDismiss = { showUnitsSheet = false },
            onStudyUnit = { unitId, isPastCompleted ->
                onGoStudy(subjectId, if (isPastCompleted) unitId else null)
            },
            onExamUnit = { unitId ->
                vm.selectExamUnit(unitId)
            },
            onReturnToActiveProgress = if (vm.isRetakeCompletedUnit && !vm.allUnitsCompleted) {
                { vm.selectExamUnit(null) }
            } else null
        )
    }
}
