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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FormatListNumbered
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lvupme.app.AppContainer
import com.lvupme.app.data.local.AppSettings
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
import com.lvupme.app.ui.viewmodel.StudyViewModel
import com.lvupme.app.ui.viewmodel.lvUpViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    container: AppContainer,
    subjectId: Long,
    initialPinnedCardId: Long? = null,
    initialFocusUnitId: Long? = null,
    onBack: () -> Unit,
    onOpenExam: (subjectId: Long, unitId: Long?) -> Unit
) {
    val vm: StudyViewModel = viewModel(
        key = "study_$subjectId",
        factory = lvUpViewModelFactory {
            StudyViewModel(container, subjectId, initialPinnedCardId, initialFocusUnitId)
        }
    )
    val g = MaterialTheme.game

    val subject by container.learningRepository.observeSubject(subjectId).collectAsState(initial = null)
    val unitProgressList by remember(subjectId) {
        container.learningRepository.observeUnitProgressList(subjectId)
    }.collectAsState(initial = null)
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

    var showUnitsSheet by remember { mutableStateOf(false) }

    val openExamSafely: (Long?) -> Unit = { targetUnitId ->
        vm.flushPendingCommit()
        onOpenExam(subjectId, targetUnitId)
    }

    LaunchedEffect(initialPinnedCardId) {
        vm.applyPinnedCard(initialPinnedCardId)
    }

    LaunchedEffect(initialFocusUnitId) {
        if (initialPinnedCardId == null) {
            vm.applyFocusUnitArg(initialFocusUnitId)
        }
    }

    val reviewRoundTotal = vm.effectiveReviewRoundTotal
    val headerProgress = when {
        vm.isPastUnitReviewMode && reviewRoundTotal > 0 -> {
            (vm.reviewRoundCompletedCount.toFloat() / reviewRoundTotal.toFloat()).coerceIn(0f, 1f)
        }
        else -> vm.unitReadiness
    }
    val headerProgressColor = if (vm.isPastUnitReviewMode) g.violet else g.emerald
    val headerProgressLabel = when {
        vm.isPastUnitReviewMode -> "${vm.reviewRoundCompletedCount}/$reviewRoundTotal"
        vm.allUnitsCompleted -> "완료"
        else -> "${(vm.unitReadiness * 100).toInt()}%"
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
                                text = headerProgressLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = headerProgressColor
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
                            progress = headerProgress,
                            fillColor = headerProgressColor,
                            height = 10.dp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showUnitsSheet = true },
                        enabled = !vm.isAiRechecking
                    ) {
                        Icon(
                            Icons.Default.FormatListNumbered,
                            contentDescription = "단원 목록",
                            tint = g.violet
                        )
                    }
                    IconButton(
                        onClick = {
                            openExamSafely(if (vm.isPastUnitReviewMode) vm.activeUnit?.id else null)
                        },
                        enabled = !vm.isAiRechecking
                    ) {
                        Icon(Icons.Default.Quiz, contentDescription = "시험", tint = g.sky)
                    }
                    IconButton(
                        onClick = { vm.loadNextQuestion(prefetchIfMissing = true) },
                        enabled = !vm.isAiRechecking
                    ) {
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
            // Contextual top banners
            when {
                vm.isPastUnitReviewMode && !vm.showPastReviewCompleteGate -> {
                    GamifiedCard(
                        containerColor = g.violetSoft,
                        borderColor = g.violet,
                        lipColor = g.violetLip,
                        onClick = if (!vm.isAiRechecking) ({ showUnitsSheet = true }) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🔁 ${vm.activeUnit?.orderIndex ?: ""}단원 과거 복습 중 (진도 유지)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.violet,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "단원 변경 →",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.violet
                            )
                        }
                    }
                }

                !vm.isPastUnitReviewMode && vm.allUnitsCompleted -> {
                    GamifiedCard(
                        containerColor = g.emeraldSoft,
                        borderColor = g.emerald,
                        lipColor = g.emeraldLip,
                        onClick = if (!vm.isAiRechecking) ({ showUnitsSheet = true }) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🎉 모든 단원 완료! 전 단원 균형 복습 중",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.emerald,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "단원 선택 →",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.emerald
                            )
                        }
                    }
                }

                !vm.isPastUnitReviewMode && !vm.allUnitsCompleted && !vm.showUnitCompleteGate && vm.unitReadiness >= 0.75f -> {
                    val isFull = vm.unitReadiness >= 1f
                    GamifiedCard(
                        containerColor = if (isFull) g.goldSoft else g.skySoft,
                        borderColor = if (isFull) g.gold else g.sky,
                        lipColor = if (isFull) g.goldLip else g.skyLip,
                        onClick = if (!vm.isAiRechecking) ({ openExamSafely(vm.activeUnit?.id) }) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isFull) "🏆 단원 100% 완료 · 추가 복습 중" else "🎯 단원 체크포인트 시험 준비 완료!",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isFull) g.gold else g.sky,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "응시하기 →",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isFull) g.gold else g.sky
                            )
                        }
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

                vm.showUnitCompleteGate -> {
                    val unit = vm.activeUnit
                    GamifiedCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = g.cardBg,
                        borderColor = g.emerald,
                        lipColor = g.emeraldLip
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(g.emeraldSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = g.emerald,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            GamePillBadge(
                                text = "진행도 100% 달성 (${vm.unitPracticedCount}/${vm.unitTotalCards} 카드)",
                                containerColor = g.emeraldSoft,
                                contentColor = g.emerald
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "${unit?.orderIndex ?: ""}단원 학습 완료! 🎉",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            if (unit != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = unit.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = g.sky,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "이 단원의 모든 핵심 카드를 익혔어요.\n체크포인트 시험을 통과하면 다음 단원이 해금됩니다!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            TactileButton(
                                onClick = { openExamSafely(unit?.id) },
                                style = TactileStyle.EMERALD,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🎯 체크포인트 시험 보러 가기", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            TactileButton(
                                onClick = { vm.continueExtraPracticeAfterUnitComplete() },
                                style = TactileStyle.SKY,
                                height = 46.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("이 단원 추가 복습 계속하기", fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.height(10.dp))
                            TactileButton(
                                onClick = { showUnitsSheet = true },
                                style = TactileStyle.VIOLET,
                                height = 46.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("다른 단원 선택하기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                vm.showPastReviewCompleteGate -> {
                    val unit = vm.activeUnit
                    GamifiedCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = g.cardBg,
                        borderColor = g.violet,
                        lipColor = g.violetLip
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(g.violetSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = g.violet,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            GamePillBadge(
                                text = "정답 ${vm.reviewRoundCorrectCount} / $reviewRoundTotal",
                                containerColor = g.violetSoft,
                                contentColor = g.violet
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "${unit?.orderIndex ?: ""}단원 집중 복습 완료! 🎉",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            if (unit != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = unit.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = g.violet,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "선택한 과거 단원의 카드 ${reviewRoundTotal}개를 한 바퀴 모두 복습했어요.\n기존 커리큘럼 진도는 그대로 유지됩니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            TactileButton(
                                onClick = { vm.restartPastReviewRound() },
                                style = TactileStyle.VIOLET,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("한 번 더 복습하기", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            TactileButton(
                                onClick = { openExamSafely(unit?.id) },
                                style = TactileStyle.SKY,
                                height = 46.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("이 단원 시험 재응시하기", fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.height(10.dp))
                            TactileButton(
                                onClick = {
                                    if (vm.allUnitsCompleted) {
                                        showUnitsSheet = true
                                    } else {
                                        vm.switchToUnit(null)
                                    }
                                },
                                style = TactileStyle.EMERALD,
                                height = 46.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (vm.allUnitsCompleted) "다른 단원 선택하기" else "현재 진도로 돌아가기",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
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
                            when {
                                vm.isPastUnitReviewMode -> {
                                    GamePillBadge(
                                        text = "${vm.activeUnit?.orderIndex ?: ""}단원 집중 복습",
                                        containerColor = g.violetSoft,
                                        contentColor = g.violet,
                                        icon = Icons.Default.Replay
                                    )
                                }
                                vm.allUnitsCompleted -> {
                                    GamePillBadge(
                                        text = "전 단원 균형 복습",
                                        containerColor = g.emeraldSoft,
                                        contentColor = g.emerald,
                                        icon = Icons.Default.AutoAwesome
                                    )
                                }
                                scheduled.isFromPastReview -> {
                                    GamePillBadge(
                                        text = "과거 복습 ${settings.newToReviewRatio}:1",
                                        containerColor = g.goldSoft,
                                        contentColor = g.gold,
                                        icon = Icons.Default.Replay
                                    )
                                }
                                else -> {
                                    GamePillBadge(
                                        text = "현재 진도",
                                        containerColor = g.skySoft,
                                        contentColor = g.sky,
                                        icon = Icons.Default.Bolt
                                    )
                                }
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
                                text = when {
                                    vm.isPastUnitReviewMode -> "${vm.activeUnit?.orderIndex ?: ""}단원 · ${vm.activeUnit?.title ?: ""}"
                                    vm.allUnitsCompleted -> "${card.unitOrderIndex}단원 복습 · 전체 커리큘럼 완료 🎉"
                                    else -> "${vm.activeUnit?.orderIndex ?: ""}단원 · ${vm.activeUnit?.title ?: ""}"
                                },
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

                        val nextBtnLabel = when {
                            vm.isReadyToFinishPastReviewRound -> "${vm.activeUnit?.orderIndex ?: ""}단원 복습 완료! 결과 보기 →"
                            vm.isReadyToFinishActiveUnit -> "단원 100% 달성! 다음 단계 보기 →"
                            else -> "계속하기 →"
                        }
                        val nextBtnStyle = when {
                            vm.isReadyToFinishPastReviewRound -> TactileStyle.VIOLET
                            vm.isReadyToFinishActiveUnit -> TactileStyle.GOLD
                            vm.isCorrect -> TactileStyle.EMERALD
                            else -> TactileStyle.SKY
                        }

                        TactileButton(
                            onClick = { vm.loadNextQuestion(prefetchIfMissing = false) },
                            enabled = !vm.isAiRechecking,
                            style = nextBtnStyle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(nextBtnLabel, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
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

    if (showUnitsSheet) {
        CurriculumUnitsBottomSheet(
            subjectName = subject?.name ?: "학습",
            units = unitProgressList,
            currentFocusUnitId = if (vm.isPastUnitReviewMode) vm.focusUnitId else null,
            onDismiss = { showUnitsSheet = false },
            onStudyUnit = { unitId, isPastCompleted ->
                vm.switchToUnit(if (isPastCompleted) unitId else null)
            },
            onExamUnit = { unitId ->
                openExamSafely(unitId)
            },
            onReturnToActiveProgress = if (vm.isPastUnitReviewMode) {
                { vm.switchToUnit(null) }
            } else null
        )
    }
}
