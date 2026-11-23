package com.lvupme.app.ui.stats

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lvupme.app.AppContainer
import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.ExamType
import com.lvupme.app.ui.theme.ChunkyProgressBar
import com.lvupme.app.ui.theme.GamePillBadge
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.game

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    container: AppContainer,
    onPracticeWeakCard: (subjectId: Long, cardId: Long) -> Unit
) {
    val repo = container.learningRepository
    val g = MaterialTheme.game
    val todayCount by repo.observeTodayCount().collectAsState(initial = 0)
    val streakDays by repo.observeStreakDays().collectAsState(initial = 0)
    val dashboards by repo.observeSubjectDashboards().collectAsState(initial = emptyList())
    val weakCards by repo.observeWeakCards().collectAsState(initial = emptyList())
    val allExams by repo.observeAllExams().collectAsState(initial = emptyList())
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

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
                title = { Text("학습 통계 & 취약점", fontWeight = FontWeight.ExtraBold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GamifiedCard(
                        modifier = Modifier.weight(1f),
                        containerColor = g.skySoft,
                        borderColor = g.sky,
                        lipColor = g.skyLip
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(g.sky.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, null, tint = g.sky, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "$todayCount / ${settings.dailyGoalQuestions}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text("오늘 학습량", style = MaterialTheme.typography.labelMedium, color = g.sky, fontWeight = FontWeight.Bold)
                        }
                    }
                    GamifiedCard(
                        modifier = Modifier.weight(1f),
                        containerColor = g.goldSoft,
                        borderColor = g.gold,
                        lipColor = g.goldLip
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(g.gold.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, null, tint = g.gold, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${streakDays}일",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text("연속 학습일", style = MaterialTheme.typography.labelMedium, color = g.gold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("과목별 진도율", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }

            if (dashboards.isEmpty()) {
                item {
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "아직 등록된 과목이 없습니다.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(dashboards, key = { "subject_${it.subject.id}" }) { item ->
                    val ratio = if (item.totalUnits > 0) item.completedUnits.toFloat() / item.totalUnits else 0f
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                GamePillBadge(
                                    text = "${(ratio * 100).toInt()}% (${item.completedUnits}/${item.totalUnits})",
                                    containerColor = g.emeraldSoft,
                                    contentColor = g.emerald
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            ChunkyProgressBar(progress = ratio, fillColor = g.emerald, height = 10.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${item.subject.subTopic} · 수준 ${item.subject.userLevel} · 취약 ${item.weakCardsCount}개",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Text("취약 개념 TOP (탭하여 바로 집중 복습)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }

            if (weakCards.isEmpty()) {
                item {
                    GamifiedCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = g.emeraldSoft,
                        borderColor = g.emerald.copy(alpha = 0.5f),
                        lipColor = g.emeraldLip.copy(alpha = 0.5f)
                    ) {
                        Text(
                            "현재 취약 개념이 없어요! 완벽한 페이스예요 ✨",
                            modifier = Modifier.padding(16.dp),
                            color = g.emerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                items(weakCards.take(15), key = { "weak_${it.card.id}" }) { weak ->
                    GamifiedCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = g.coral.copy(alpha = 0.55f),
                        lipColor = g.coralLip.copy(alpha = 0.55f),
                        onClick = { onPracticeWeakCard(weak.card.subjectId, weak.card.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "[${weak.subjectName}] ${weak.card.title}",
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    weak.card.microSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            GamePillBadge(
                                text = "오답 ${weak.state.wrongCount}회",
                                containerColor = g.coralSoft,
                                contentColor = g.coral
                            )
                        }
                    }
                }
            }

            if (allExams.isNotEmpty()) {
                item {
                    Text("최근 시험 이력", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
                items(allExams.take(10), key = { "exam_${it.id}" }) { exam ->
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val typeLabel = when (exam.examType) {
                                ExamType.LEVEL_TEST -> "수준 테스트"
                                ExamType.UNIT_CHECKPOINT -> "단원 체크포인트"
                                else -> "AI 모의시험"
                            }
                            Column {
                                Text(typeLabel, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "${exam.correctCount}/${exam.totalQuestions} 문항",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            GamePillBadge(
                                text = "${exam.scorePercent}점 (${if (exam.passed) "통과" else "미달"})",
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
