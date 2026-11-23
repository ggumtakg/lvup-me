package com.lvupme.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lvupme.app.AppContainer
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
import com.lvupme.app.ui.viewmodel.LevelTestViewModel
import com.lvupme.app.ui.viewmodel.lvUpViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelTestScreen(
    container: AppContainer,
    proposal: OnboardingProposal,
    onBack: () -> Unit,
    onFinished: (Long) -> Unit
) {
    val vm: LevelTestViewModel = viewModel(
        key = "level_test_${proposal.subject}_${proposal.subTopic}",
        factory = lvUpViewModelFactory { LevelTestViewModel(container, proposal) }
    )
    val g = MaterialTheme.game

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
                        "수준 테스트 · ${proposal.subject}",
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
                        CircularProgressIndicator(color = g.emerald)
                        Spacer(Modifier.height(16.dp))
                        Text(vm.loadingMessage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                                onClick = {
                                    if (vm.answerRecords.isNotEmpty() && vm.answerRecords.size >= vm.questions.size) {
                                        vm.evaluateAnswers()
                                    } else {
                                        vm.loadTest()
                                    }
                                },
                                style = TactileStyle.CORAL,
                                height = 44.dp
                            ) { Text("다시 시도", fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }

                vm.evaluation != null -> {
                    val eval = vm.evaluation!!
                    val correctCount = vm.answerRecords.count { it.third }
                    GamifiedCard(
                        containerColor = g.emeraldSoft,
                        borderColor = g.emerald,
                        lipColor = g.emeraldLip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "진단 결과: ${eval.assessedLevel} 🏆",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.emerald
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("점수: $correctCount / ${vm.answerRecords.size}", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(eval.feedback, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text("생성된 맞춤 커리큘럼", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    eval.units.sortedBy { it.orderIndex }.forEach { u ->
                        GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${u.orderIndex}단원. ${u.title}", fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(2.dp))
                                Text(u.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    TactileButton(
                        onClick = { vm.saveCurriculumAndStart(onFinished) },
                        enabled = !vm.isSaving,
                        style = TactileStyle.EMERALD,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (vm.isSaving) "첫 단원 학습 카드 생성 중..." else "커리큘럼 저장 & 학습 시작하기 →",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                vm.questions.isNotEmpty() -> {
                    val q = vm.questions[vm.currentIndex.coerceIn(0, vm.questions.lastIndex)]
                    val letters = listOf("A", "B", "C", "D")
                    val progress = (vm.currentIndex + 1).toFloat() / vm.questions.size.toFloat()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("수준 진단 진행 중", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        GamePillBadge(
                            text = "${vm.currentIndex + 1} / ${vm.questions.size}",
                            containerColor = g.skySoft,
                            contentColor = g.sky
                        )
                    }
                    ChunkyProgressBar(progress = progress, fillColor = g.emerald, height = 12.dp)

                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            val maxWords = AnswerGrader.maxAllowedWords(q.answerText)
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
                                    onClick = { vm.submitAnswer(q, choice) }
                                )
                            }
                        }
                    } else {
                        val words = AnswerGrader.countWords(vm.shortInput)
                        val maxWords = AnswerGrader.maxAllowedWords(q.answerText)
                        GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = vm.shortInput,
                                    onValueChange = { vm.shortInput = it },
                                    label = { Text("정답 입력 (최대 ${maxWords}단어)") },
                                    supportingText = {
                                        Text(
                                            "$words / $maxWords 단어",
                                            color = if (words > maxWords) g.coral else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(10.dp))
                                TactileButton(
                                    onClick = { vm.submitAnswer(q, vm.shortInput) },
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
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("표시할 문항이 없습니다.", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            TactileButton(onClick = { vm.loadTest() }, style = TactileStyle.SKY, height = 44.dp) {
                                Text("수준 테스트 다시 불러오기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
