package com.lvupme.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lvupme.app.data.local.UnitStatus
import com.lvupme.app.data.repository.UnitProgressItem
import com.lvupme.app.ui.theme.ChunkyProgressBar
import com.lvupme.app.ui.theme.GamePillBadge
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.TactileButton
import com.lvupme.app.ui.theme.TactileStyle
import com.lvupme.app.ui.theme.game

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumUnitsBottomSheet(
    subjectName: String,
    units: List<UnitProgressItem>?,
    currentFocusUnitId: Long? = null,
    focusedBadgeLabel: String = "복습 중",
    onDismiss: () -> Unit,
    onStudyUnit: (unitId: Long, isPastCompleted: Boolean) -> Unit,
    onExamUnit: (unitId: Long) -> Unit,
    onReturnToActiveProgress: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val g = MaterialTheme.game
    val unitList = units.orEmpty()
    val completedCount = unitList.count { it.unit.status == UnitStatus.COMPLETED }
    val activeProgressUnit = unitList.firstOrNull { it.unit.status == UnitStatus.ACTIVE }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$subjectName 커리큘럼",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "완료한 과거 단원을 골라 다시 복습하거나 시험을 재응시할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (units != null) {
                    Spacer(Modifier.width(8.dp))
                    GamePillBadge(
                        text = "$completedCount / ${unitList.size} 완료",
                        containerColor = g.emeraldSoft,
                        contentColor = g.emerald
                    )
                }
            }

            if (currentFocusUnitId != null && onReturnToActiveProgress != null && units != null) {
                Spacer(Modifier.height(12.dp))
                val returnLabel = if (activeProgressUnit != null) {
                    "현재 진도 (${activeProgressUnit.unit.orderIndex}단원)로 돌아가기"
                } else {
                    "전 단원 균형 복습 모드로 돌아가기"
                }
                GamifiedCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = g.skySoft,
                    borderColor = g.sky,
                    lipColor = g.skyLip,
                    onClick = {
                        onReturnToActiveProgress()
                        onDismiss()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = g.sky, modifier = Modifier.size(20.dp))
                            Text(
                                text = returnLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = g.sky,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "이동 →",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = g.sky
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                units == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = g.violet)
                    }
                }

                units.isEmpty() -> {
                    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "등록된 커리큘럼 단원이 없습니다.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(units, key = { "unit_${it.unit.id}" }) { item ->
                            UnitPickerCard(
                                item = item,
                                isCurrentlyFocused = currentFocusUnitId == item.unit.id,
                                focusedBadgeLabel = focusedBadgeLabel,
                                onStudy = {
                                    onStudyUnit(item.unit.id, item.unit.status == UnitStatus.COMPLETED)
                                    onDismiss()
                                },
                                onExam = {
                                    onExamUnit(item.unit.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPickerCard(
    item: UnitProgressItem,
    isCurrentlyFocused: Boolean,
    focusedBadgeLabel: String,
    onStudy: () -> Unit,
    onExam: () -> Unit
) {
    val g = MaterialTheme.game
    val unit = item.unit
    val isCompleted = unit.status == UnitStatus.COMPLETED
    val isActive = unit.status == UnitStatus.ACTIVE
    val isLocked = unit.status == UnitStatus.LOCKED
    val pct = (item.readiness * 100).toInt()

    val borderColor = when {
        isCurrentlyFocused -> g.violet
        isActive -> g.sky
        isCompleted -> g.emerald.copy(alpha = 0.65f)
        else -> g.cardBorder
    }
    val lipColor = when {
        isCurrentlyFocused -> g.violetLip
        isActive -> g.skyLip
        isCompleted -> g.emeraldLip.copy(alpha = 0.65f)
        else -> g.cardBorder
    }
    val badgeBg = when {
        isCompleted -> g.emeraldSoft
        isActive -> g.skySoft
        else -> g.subtleSurface
    }
    val badgeFg = when {
        isCompleted -> g.emerald
        isActive -> g.sky
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    GamifiedCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isLocked) g.subtleSurface else g.cardBg,
        borderColor = borderColor,
        lipColor = lipColor
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isCompleted -> Icons.Default.CheckCircle
                            isActive -> Icons.Default.Bolt
                            else -> Icons.Default.Lock
                        },
                        contentDescription = null,
                        tint = badgeFg,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${unit.orderIndex}단원 · ${unit.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (unit.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = unit.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                GamePillBadge(
                    text = when {
                        isCurrentlyFocused -> focusedBadgeLabel
                        isCompleted -> "완료"
                        isActive && item.totalCards > 0 && item.readiness >= 1f -> "시험 준비"
                        isActive -> "진행 중"
                        else -> "잠금"
                    },
                    containerColor = if (isCurrentlyFocused) g.violetSoft else badgeBg,
                    contentColor = if (isCurrentlyFocused) g.violet else badgeFg
                )
            }

            if (!isLocked) {
                Spacer(Modifier.height(10.dp))
                if (item.totalCards > 0) {
                    ChunkyProgressBar(
                        progress = item.readiness,
                        fillColor = if (isCompleted) g.emerald else g.sky,
                        height = 8.dp
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.totalCards > 0) {
                            "숙달 ${item.practicedCards}/${item.totalCards} 카드 ($pct%)"
                        } else {
                            "학습 카드 준비 전 (시작 시 자동 생성)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.weakCards > 0) {
                        GamePillBadge(
                            text = "취약 ${item.weakCards}",
                            containerColor = g.coralSoft,
                            contentColor = g.coral
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TactileButton(
                        onClick = onStudy,
                        style = if (isCompleted) TactileStyle.VIOLET else TactileStyle.EMERALD,
                        height = 44.dp,
                        modifier = Modifier.weight(1.15f)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.Replay else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isCompleted) "다시 학습" else "이어서 학습",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TactileButton(
                        onClick = onExam,
                        style = TactileStyle.SKY,
                        height = 44.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isCompleted) "시험 재응시" else "단원 시험",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "이전 단원의 체크포인트 시험을 통과하면 해금됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
