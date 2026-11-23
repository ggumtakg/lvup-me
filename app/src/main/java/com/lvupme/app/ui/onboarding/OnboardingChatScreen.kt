package com.lvupme.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lvupme.app.AppContainer
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.TactileButton
import com.lvupme.app.ui.theme.TactileStyle
import com.lvupme.app.ui.theme.game
import com.lvupme.app.ui.viewmodel.OnboardingChatViewModel
import com.lvupme.app.ui.viewmodel.lvUpViewModelFactory
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingProposal(
    val subject: String,
    val subTopic: String,
    val goalSummary: String,
    val ttsEnabled: Boolean,
    val ttsLocale: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingChatScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onProceedToLevelTest: (OnboardingProposal) -> Unit
) {
    val vm: OnboardingChatViewModel = viewModel(
        factory = lvUpViewModelFactory { OnboardingChatViewModel(container) }
    )
    val listState = rememberLazyListState()
    val g = MaterialTheme.game

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.lastIndex)
        }
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
                title = { Text("AI와 과목·상세분야 결정", fontWeight = FontWeight.ExtraBold) },
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
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.messages, key = { "msg_${it.id}" }) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        GamifiedCard(
                            containerColor = if (isUser) g.sky else g.cardBg,
                            borderColor = if (isUser) g.skyLip else g.cardBorder,
                            lipColor = if (isUser) g.skyLip else g.cardBorder,
                            cornerRadius = 18.dp,
                            modifier = Modifier.fillMaxWidth(0.86f)
                        ) {
                            Text(
                                text = msg.content,
                                modifier = Modifier.padding(14.dp),
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isUser) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (vm.messages.size == 1) {
                    item(key = "starter_chips") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "영어 실무 회화 핵심 영단어",
                                "SQL JOIN과 집계 함수 실전",
                                "Python 자료구조와 코딩 기초",
                                "생활 경제·금융 용어 정리"
                            ).forEach { chip ->
                                SuggestionChip(
                                    onClick = { vm.sendMessage(chip) },
                                    label = { Text(chip, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }
                }

                if (vm.isLoading) {
                    item(key = "loading") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp), color = g.violet)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 튜터가 생각 중...", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                vm.errorMsg?.let { err ->
                    item(key = "error") {
                        GamifiedCard(
                            containerColor = g.coralSoft,
                            borderColor = g.coral,
                            lipColor = g.coralLip
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(err, color = g.coral, fontWeight = FontWeight.Bold)
                                if (vm.failedMessage != null) {
                                    Spacer(Modifier.height(8.dp))
                                    TactileButton(
                                        onClick = { vm.retryFailedMessage() },
                                        style = TactileStyle.CORAL,
                                        height = 40.dp
                                    ) {
                                        Text("다시 전송", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (vm.readyProposal != null) {
                    item(key = "proposal_card") {
                        GamifiedCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = g.emeraldSoft,
                            borderColor = g.emerald,
                            lipColor = g.emeraldLip
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "학습 분야가 정리되었어요! 🎯",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = g.emerald
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = vm.editSubject,
                                    onValueChange = { vm.editSubject = it },
                                    label = { Text("과목명") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vm.editSubTopic,
                                    onValueChange = { vm.editSubTopic = it },
                                    label = { Text("상세 분야") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TTS 발음 듣기 사용", fontWeight = FontWeight.SemiBold)
                                    Switch(
                                        checked = vm.editTtsEnabled,
                                        onCheckedChange = { vm.editTtsEnabled = it }
                                    )
                                }
                                if (vm.editTtsEnabled) {
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = vm.editTtsLocale,
                                        onValueChange = { vm.editTtsLocale = it },
                                        label = { Text("발음 언어 코드 (예: en-US, ja-JP, zh-CN)") },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                TactileButton(
                                    onClick = {
                                        vm.buildConfirmedProposal()?.let(onProceedToLevelTest)
                                    },
                                    style = TactileStyle.EMERALD,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("수준 테스트 시작하기 →", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.inputText,
                    onValueChange = { vm.inputText = it },
                    placeholder = { Text("배우고 싶은 과목이나 목표를 입력하세요") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.sendMessage(vm.inputText) },
                    enabled = vm.inputText.isNotBlank() && !vm.isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송", tint = g.sky)
                }
            }
        }
    }
}
