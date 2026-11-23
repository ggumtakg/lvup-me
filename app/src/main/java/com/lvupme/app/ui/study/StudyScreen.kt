package com.lvupme.app.ui.study

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lvupme.app.AppContainer
import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.domain.AnswerGrader
import com.lvupme.app.ui.theme.ChoiceTileCard
import com.lvupme.app.ui.theme.ChoiceVisualState
import com.lvupme.app.ui.theme.ChunkyProgressBar
import com.lvupme.app.ui.theme.GamePillBadge
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.TactileButton
import com.lvupme.app.ui.theme.TactileStyle
import com.lvupme.app.ui.theme.game
import com.lvupme.app.ui.viewmodel.StudyViewModel
import com.lvupme.app.ui.viewmodel.lvUpViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    container: AppContainer,
    subjectId: Long,
    initialPinnedCardId: Long? = null,
    onBack: () -> Unit,
    onOpenExam: (Long) -> Unit
) {
    val vm: StudyViewModel = viewModel(
        key = "study_$subjectId",
        factory = lvUpViewModelFactory { StudyViewModel(container, subjectId, initialPinnedCardId) }
    )
    val g = MaterialTheme.game

    val subject by container.learningRepository.observeSubject(subjectId).collectAsState(initial = null)
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

    LaunchedEffect(initialPinnedCardId) {
        vm.applyPinnedCard(initialPinnedCardId)
    }

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = subject?.name ?: "학습",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = if (vm.allUnitsCompleted) "완료" else "${(vm.unitReadiness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.emerald
                            )
                            if (vm.comboStreak >= 2) {
                                GamePillBadge(
                                    text = "${vm.comboStreak}콤보!",
                                    containerColor = g.goldSoft,
                                    contentColor = g.gold
                                )
                            }
                        }
                        ChunkyProgressBar(
                            progress = vm.unitReadiness,
                            fillColor = g.emerald,
                            height = 10.dp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenExam(subjectId) }) {
                        Icon(Icons.Default.Quiz, contentDescription = "시험", tint = g.sky)
                    }
                    IconButton(onClick = { vm.loadNextQuestion(prefetchIfMissing = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!vm.allUnitsCompleted && vm.unitReadiness >= 0.75f) {
                GamifiedCard(
                    containerColor = g.skySoft,
                    borderColor = g.sky,
                    lipColor = g.skyLip,
                    onClick = { onOpenExam(subjectId) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🎯 단원 체크포인트 시험 준비 완료!",
                            fontWeight = FontWeight.ExtraBold,
                            color = g.sky
                        )
                        Text(
                            "응시하기 →",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = g.sky
                        )
                    }
                }
            }

            when {
                vm.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = g.emerald)
                        Spacer(Modifier.height(14.dp))
                        Text("학습 카드를 준비하고 있어요...", fontWeight = FontWeight.SemiBold)
                    }
                }

                vm.errorMsg != null -> {
                    GamifiedCard(
                        containerColor = g.coralSoft,
                        borderColor = g.coral,
                        lipColor = g.coralLip
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(vm.errorMsg!!, color = g.coral, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            TactileButton(
                                onClick = { vm.loadNextQuestion(prefetchIfMissing = true) },
                                style = TactileStyle.CORAL,
                                height = 44.dp
                            ) {
                                Text("다시 불러오기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                vm.currentScheduled != null -> {
                    val scheduled = vm.currentScheduled!!
                    val card = scheduled.card
                    val q = scheduled.question
                    val letters = listOf("A", "B", "C", "D")
                    val maxWords = AnswerGrader.maxAllowedWords(q.answerText)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (scheduled.isFromPastReview) {
                                GamePillBadge(
                                    text = "과거 복습 ${settings.newToReviewRatio}:1",
                                    containerColor = g.goldSoft,
                                    contentColor = g.gold,
                                    icon = Icons.Default.Replay
                                )
                            } else {
                                GamePillBadge(
                                    text = "현재 진도",
                                    containerColor = g.skySoft,
                                    contentColor = g.sky,
                                    icon = Icons.Default.Bolt
                                )
                            }
                            GamePillBadge(
                                text = if (q.type == QuestionType.SHORT_ANSWER) "주관식 ≤${maxWords}단어" else "객관식",
                                containerColor = g.subtleSurface,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (card.isAiReinforced) {
                                GamePillBadge(
                                    text = "AI 보강",
                                    containerColor = g.violetSoft,
                                    contentColor = g.violet,
                                    icon = Icons.Default.AutoAwesome
                                )
                            }
                        }

                        val ttsText = card.ttsText.ifBlank { card.title }
                        if ((subject?.ttsEnabled == true) && ttsText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(g.skySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        container.ttsHelper.speak(
                                            ttsText,
                                            subject?.ttsLocale ?: "en-US",
                                            settings.ttsSpeechRate
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "발음 듣기",
                                        tint = g.sky,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = if (vm.allUnitsCompleted) "전체 커리큘럼 복습 모드 🎉" else (vm.activeUnit?.title ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = q.prompt,
                                style = MaterialTheme.typography.titleLarge.copy(lineHeight = 30.sp),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(12.dp))
                            GamePillBadge(
                                text = if (vm.showLessonCard) "개념 카드 닫기" else "개념 카드 미리 보기",
                                containerColor = g.violetSoft,
                                contentColor = g.violet,
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                onClick = { vm.toggleLessonCard() }
                            )
                        }
                    }

                    if (vm.showLessonCard) {
                        GamifiedCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = g.subtleSurface,
                            borderColor = g.violet.copy(alpha = 0.6f),
                            lipColor = g.violetLip.copy(alpha = 0.6f)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    card.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = g.violet
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    card.microSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (card.detailExplanation.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        card.detailExplanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (card.exampleText.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        color = g.emeraldSoft,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "예시: ${card.exampleText}",
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = g.emerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (q.type == QuestionType.MULTIPLE_CHOICE && vm.shuffledChoices.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            vm.shuffledChoices.forEachIndexed { idx, choice ->
                                val isThisAnswer = AnswerGrader.normalize(choice) == AnswerGrader.normalize(q.answerText)
                                val visualState = when {
                                    !vm.answered -> ChoiceVisualState.IDLE
                                    isThisAnswer -> ChoiceVisualState.CORRECT
                                    choice == vm.selectedChoice -> ChoiceVisualState.WRONG
                                    else -> ChoiceVisualState.DIMMED
                                }
                                ChoiceTileCard(
                                    letter = letters.getOrElse(idx) { "${idx + 1}" },
                                    text = choice,
                                    state = visualState,
                                    enabled = !vm.answered,
                                    onClick = { vm.submitMultipleChoice(choice) }
                                )
                            }
                        }
                    } else if (!vm.answered) {
                        val wordCount = AnswerGrader.countWords(vm.shortInput)
                        GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "정답 입력 (최대 ${maxWords}단어)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    GamePillBadge(
                                        text = "$wordCount / $maxWords 단어",
                                        containerColor = if (wordCount > maxWords) g.coralSoft else g.skySoft,
                                        contentColor = if (wordCount > maxWords) g.coral else g.sky
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = vm.shortInput,
                                    onValueChange = { vm.shortInput = it },
                                    placeholder = { Text("핵심 단어나 표현을 입력하세요") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(12.dp))
                                TactileButton(
                                    onClick = { vm.submitShortAnswer() },
                                    enabled = vm.shortInput.isNotBlank() && wordCount <= maxWords,
                                    style = TactileStyle.EMERALD,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("정답 확인", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    if (vm.answered) {
                        GamifiedCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (vm.isCorrect) g.emeraldSoft else g.coralSoft,
                            borderColor = if (vm.isCorrect) g.emerald else g.coral,
                            lipColor = if (vm.isCorrect) g.emeraldLip else g.coralLip
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    if (vm.isCorrect) "정답이에요! 🎉" else "아쉬워요! 정답: ${q.answerText}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (vm.isCorrect) g.emerald else g.coral
                                )
                                if (vm.feedbackReason.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(vm.feedbackReason, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (vm.reinforcementCreated) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        color = g.violetSoft,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "✨ 취약 개념 감지! AI가 새 예문·질문의 보강 카드를 자동 생성했어요.",
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = g.violet,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (vm.offerAiRecheck && !vm.isCorrect) {
                            TactileButton(
                                onClick = { vm.requestAiRecheck(subject?.name ?: "") },
                                enabled = !vm.isAiRechecking,
                                style = TactileStyle.VIOLET,
                                height = 46.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (vm.isAiRechecking) "AI 재채점 중..." else "AI에게 의미 재채점 요청하기",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        TactileButton(
                            onClick = { vm.loadNextQuestion(prefetchIfMissing = false) },
                            enabled = !vm.isAiRechecking,
                            style = if (vm.isCorrect) TactileStyle.EMERALD else TactileStyle.SKY,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("계속하기 →", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                else -> {
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("학습할 카드가 없습니다.", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            TactileButton(
                                onClick = { vm.loadNextQuestion(prefetchIfMissing = true) },
                                style = TactileStyle.EMERALD,
                                height = 44.dp
                            ) {
                                Text("학습 카드 생성하기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
