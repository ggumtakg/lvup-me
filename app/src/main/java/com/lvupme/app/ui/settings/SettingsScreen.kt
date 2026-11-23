package com.lvupme.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lvupme.app.AppContainer
import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.BackupPayload
import com.lvupme.app.data.remote.GeminiClient
import com.lvupme.app.lockscreen.LockScreenService
import com.lvupme.app.notification.NotificationHelper
import com.lvupme.app.ui.lockscreen.LockScreenActivity
import com.lvupme.app.ui.theme.GamifiedCard
import com.lvupme.app.ui.theme.TactileButton
import com.lvupme.app.ui.theme.TactileStyle
import com.lvupme.app.ui.theme.game
import com.lvupme.app.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = MaterialTheme.game

    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    val storedApiKey by container.settingsRepository.apiKeyFlow.collectAsState(initial = "")

    var apiKeyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(settings.geminiModel) }
    var availableModels by remember { mutableStateOf<List<GeminiClient.GeminiModelInfo>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(storedApiKey) {
        if (apiKeyInput.isBlank() && storedApiKey.isNotBlank()) {
            apiKeyInput = storedApiKey
        }
    }
    LaunchedEffect(settings.geminiModel) {
        modelInput = settings.geminiModel
    }

    fun applySettings(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            val previous = container.settingsRepository.getSettings()
            val updated = container.settingsRepository.updateSettings(transform)
            WorkScheduler.syncDiff(context, previous, updated)
        }
    }

    fun openOverlayPermissionSettings() {
        val overlayIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(overlayIntent) }
            .onFailure {
                val appInfoIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                runCatching { context.startActivity(appInfoIntent) }
                    .onFailure { statusMessage = "설정 화면을 열 수 없습니다. 기기 설정에서 직접 허용해 주세요." }
            }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "알림 권한이 허용되었습니다." else "알림 권한이 거부되었습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val jsonText = container.backupManager.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                    }
                }
            }.onSuccess {
                statusMessage = "백업 파일 저장 완료!"
            }.onFailure {
                statusMessage = "백업 실패: ${it.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val jsonText = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            total += read
                            if (total > BackupPayload.MAX_BACKUP_BYTES) {
                                error("백업 파일 크기가 제한(10MB)을 초과했습니다.")
                            }
                            out.write(buf, 0, read)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: error("파일을 읽을 수 없습니다.")
                }
                val restored = container.backupManager.importJson(jsonText)
                container.learningRepository.refreshWidget()
                WorkScheduler.syncAll(context, restored)
                LockScreenService.sync(context, restored.lockScreenOverlayEnabled)
                if (!restored.lockScreenNotifEnabled) {
                    NotificationHelper.cancelLockScreenCard(context)
                }
            }.onSuccess {
                statusMessage = "백업 복원이 완료되었습니다!"
            }.onFailure {
                statusMessage = "복원 실패: ${it.message}"
            }
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
                title = { Text("설정", fontWeight = FontWeight.ExtraBold) }
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
            statusMessage?.let { msg ->
                GamifiedCard(
                    containerColor = g.skySoft,
                    borderColor = g.sky,
                    lipColor = g.skyLip
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = g.sky
                    )
                }
            }

            // 0. Theme Mode
            SettingsSection("화면 테마") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SYSTEM" to "시스템", "DARK" to "다크", "LIGHT" to "라이트").forEach { (key, label) ->
                        val selected = settings.themeMode == key
                        FilterChip(
                            selected = selected,
                            onClick = {
                                scope.launch {
                                    val prev = container.settingsRepository.getSettings()
                                    val updated = container.settingsRepository.updateSettings { it.copy(themeMode = key) }
                                    WorkScheduler.syncDiff(context, prev, updated)
                                    container.learningRepository.refreshWidget()
                                }
                            },
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = g.skySoft,
                                selectedLabelColor = g.sky
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 1. Gemini API
            SettingsSection("Gemini AI 설정") {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Gemini API 키 (암호화 로컬 저장)") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Gemini 모델명") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TactileButton(
                        onClick = {
                            scope.launch {
                                container.settingsRepository.saveApiKey(apiKeyInput)
                                val sanitized = AppSettings.sanitizeModel(modelInput)
                                modelInput = sanitized
                                applySettings { it.copy(geminiModel = sanitized) }
                                statusMessage = "API 키와 모델($sanitized)이 안전하게 저장되었습니다."
                            }
                        },
                        style = TactileStyle.EMERALD,
                        height = 44.dp,
                        modifier = Modifier.weight(1f)
                    ) { Text("저장", fontWeight = FontWeight.ExtraBold) }

                    TactileButton(
                        onClick = {
                            if (isBusy) return@TactileButton
                            isBusy = true
                            scope.launch {
                                container.settingsRepository.saveApiKey(apiKeyInput)
                                val sanitized = AppSettings.sanitizeModel(modelInput)
                                modelInput = sanitized
                                container.settingsRepository.updateSettings { it.copy(geminiModel = sanitized) }
                                statusMessage = runCatching { container.geminiClient.testConnection() }
                                    .getOrElse { "연결 실패: ${it.message}" }
                                isBusy = false
                            }
                        },
                        enabled = !isBusy,
                        style = TactileStyle.SKY,
                        height = 44.dp,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isBusy) "확인 중..." else "연결 테스트", fontWeight = FontWeight.ExtraBold) }
                }

                Spacer(Modifier.height(10.dp))
                TactileButton(
                    onClick = {
                        if (isBusy) return@TactileButton
                        isBusy = true
                        scope.launch {
                            container.settingsRepository.saveApiKey(apiKeyInput)
                            runCatching { container.geminiClient.listAvailableModels() }
                                .onSuccess { list ->
                                    availableModels = list
                                    statusMessage = if (list.isEmpty()) {
                                        "사용 가능한 텍스트 생성 Gemini 모델을 찾지 못했습니다."
                                    } else {
                                        "사용 가능한 모델 ${list.size}개를 불러왔습니다. 아래에서 선택하세요."
                                    }
                                }
                                .onFailure { e ->
                                    statusMessage = e.message ?: "모델 목록 조회 실패"
                                }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                    style = TactileStyle.NEUTRAL,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isBusy) "조회 중..." else "내 API 키로 사용 가능한 모델 불러오기",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (availableModels.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "사용 가능한 모델 (탭하여 선택)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableModels.forEach { m ->
                            val selected = modelInput == m.id
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    modelInput = m.id
                                    applySettings { it.copy(geminiModel = m.id) }
                                    statusMessage = "모델이 '${m.id}'로 변경되었습니다."
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = g.emeraldSoft,
                                    selectedLabelColor = g.emerald
                                ),
                                label = { Text(m.id, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }

            // 2. Learning rules
            SettingsSection("학습·반복학습(SRS)·시험 규칙") {
                SliderSettingRow(
                    title = "현재 진도 : 과거 복습 비율",
                    formatLabel = { "${it.roundToInt()} : 1" },
                    value = settings.newToReviewRatio.toFloat(),
                    range = 1f..10f,
                    steps = 8,
                    onCommit = { v -> applySettings { it.copy(newToReviewRatio = v.roundToInt()) } }
                )
                SliderSettingRow(
                    title = "객관식 : 주관식(≤3단어) 비율",
                    formatLabel = {
                        val mc = (it.roundToInt() / 5) * 5
                        "$mc% : ${100 - mc}%"
                    },
                    value = settings.mcPercent.toFloat(),
                    range = 50f..90f,
                    steps = 7,
                    onCommit = { v -> applySettings { it.copy(mcPercent = (v.roundToInt() / 5) * 5) } }
                )
                SliderSettingRow(
                    title = "일일 학습 목표",
                    formatLabel = { "${(it.roundToInt() / 5) * 5}문제" },
                    value = settings.dailyGoalQuestions.toFloat(),
                    range = 5f..60f,
                    steps = 10,
                    onCommit = { v -> applySettings { it.copy(dailyGoalQuestions = (v.roundToInt() / 5) * 5) } }
                )
                SliderSettingRow(
                    title = "AI 약점 보강 발동 오답 횟수",
                    formatLabel = { "${it.roundToInt()}회 오답 시" },
                    value = settings.weakWrongThreshold.toFloat(),
                    range = 1f..5f,
                    steps = 3,
                    onCommit = { v -> applySettings { it.copy(weakWrongThreshold = v.roundToInt()) } }
                )
                SliderSettingRow(
                    title = "단원 체크포인트 합격 기준",
                    formatLabel = { "${(it.roundToInt() / 5) * 5}%" },
                    value = settings.checkpointPassPercent.toFloat(),
                    range = 60f..100f,
                    steps = 7,
                    onCommit = { v -> applySettings { it.copy(checkpointPassPercent = (v.roundToInt() / 5) * 5) } }
                )
            }

            // 3. Prefetch Caching
            SettingsSection("사전 생성 캐싱 (오프라인 & 0초 대기)") {
                ToggleSettingRow(
                    title = "백그라운드 자동 사전 생성",
                    checked = settings.prefetchEnabled,
                    onCheckedChange = { c -> applySettings { it.copy(prefetchEnabled = c) } }
                )
                ToggleSettingRow(
                    title = "Wi-Fi 연결 시에만 사전 생성",
                    checked = settings.prefetchWifiOnly,
                    onCheckedChange = { c -> applySettings { it.copy(prefetchWifiOnly = c) } }
                )
                SliderSettingRow(
                    title = "단원당 사전 생성 카드 수",
                    formatLabel = { "${it.roundToInt()}개" },
                    value = settings.prefetchBatchSize.toFloat(),
                    range = 4f..16f,
                    steps = 5,
                    onCommit = { v -> applySettings { it.copy(prefetchBatchSize = v.roundToInt()) } }
                )
                TactileButton(
                    onClick = {
                        if (isBusy) return@TactileButton
                        isBusy = true
                        scope.launch {
                            statusMessage = runCatching {
                                val count = container.learningRepository.prefetchUpcomingForAllSubjects()
                                "사전 생성 완료: 새 개념 카드 ${count}개 추가"
                            }.getOrElse { "사전 생성 실패: ${it.message}" }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                    style = TactileStyle.NEUTRAL,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isBusy) "생성 중..." else "지금 모든 과목 다음 단원 미리 생성", fontWeight = FontWeight.Bold)
                }
            }

            // 4. Lock Screen Dedicated Card Screen (Widget-style Overlay)
            SettingsSection("잠금화면 전용 학습 카드 (위젯 스타일)") {
                var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    canOverlay = Settings.canDrawOverlays(context)
                }

                if (!canOverlay) {
                    Text(
                        "화면을 켤 때마다 잠금화면에 학습 카드를 직접 표시하려면 '다른 앱 위에 표시' 권한을 한 번 허용해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TactileButton(
                        onClick = { openOverlayPermissionSettings() },
                        style = TactileStyle.GOLD,
                        height = 44.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("다른 앱 위에 표시 권한 허용하기", fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ToggleSettingRow(
                    title = "화면 켤 때 잠금화면 학습 카드 표시",
                    checked = settings.lockScreenOverlayEnabled && canOverlay,
                    onCheckedChange = { enabled ->
                        canOverlay = Settings.canDrawOverlays(context)
                        if (enabled && !canOverlay) {
                            statusMessage = "먼저 위의 '다른 앱 위에 표시 권한 허용하기'를 켜주세요."
                            openOverlayPermissionSettings()
                        } else {
                            scope.launch {
                                val prev = container.settingsRepository.getSettings()
                                val updated = container.settingsRepository.updateSettings {
                                    it.copy(lockScreenOverlayEnabled = enabled)
                                }
                                WorkScheduler.syncDiff(context, prev, updated)
                                val ok = LockScreenService.sync(context, enabled)
                                statusMessage = when {
                                    enabled && ok -> "잠금화면 학습 카드가 활성화되었습니다! 화면을 껐다 켜보세요."
                                    enabled && !ok -> "잠금화면 서비스 시작이 제한되었습니다. 앱을 다시 연 뒤 시도해 주세요."
                                    else -> "잠금화면 학습 카드가 비활성화되었습니다."
                                }
                            }
                        }
                    }
                )
                ToggleSettingRow(
                    title = "잠금화면 카드에서 바로 미니 퀴즈 풀기",
                    checked = settings.lockScreenShowQuiz,
                    onCheckedChange = { c -> applySettings { it.copy(lockScreenShowQuiz = c) } }
                )
                Spacer(Modifier.height(4.dp))
                TactileButton(
                    onClick = {
                        context.startActivity(Intent(context, LockScreenActivity::class.java))
                    },
                    style = TactileStyle.SKY,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("잠금화면 학습 카드 지금 미리보기", fontWeight = FontWeight.ExtraBold)
                }
            }

            // 5. Lock Screen Notification Card & Reminder Notifications
            SettingsSection("잠금화면 지식 알림 & 학습 리마인더") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !NotificationHelper.hasNotificationPermission(context)
                ) {
                    TactileButton(
                        onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        style = TactileStyle.GOLD,
                        height = 44.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("알림 권한 허용하기 (잠금화면 카드·리마인더 필수)", fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ToggleSettingRow(
                    title = "잠금화면 지식 알림 카드 활성화",
                    checked = settings.lockScreenNotifEnabled,
                    onCheckedChange = { c ->
                        applySettings { it.copy(lockScreenNotifEnabled = c) }
                        if (!c) NotificationHelper.cancelLockScreenCard(context)
                    }
                )
                ToggleSettingRow(
                    title = "잠금화면 카드 상시 고정 (스와이프 방지)",
                    checked = settings.lockScreenOngoing,
                    onCheckedChange = { c -> applySettings { it.copy(lockScreenOngoing = c) } }
                )
                SliderSettingRow(
                    title = "잠금화면 카드 노출 주기",
                    formatLabel = { "${it.roundToInt()}분마다" },
                    value = settings.lockScreenIntervalMinutes.toFloat(),
                    range = 15f..180f,
                    steps = 10,
                    onCommit = { v -> applySettings { it.copy(lockScreenIntervalMinutes = v.roundToInt()) } }
                )
                SliderSettingRow(
                    title = "방해금지 시작 시각",
                    formatLabel = { "${it.roundToInt()}시" },
                    value = settings.dndStartHour.toFloat(),
                    range = 0f..23f,
                    steps = 22,
                    onCommit = { v -> applySettings { it.copy(dndStartHour = v.roundToInt()) } }
                )
                SliderSettingRow(
                    title = "방해금지 종료 시각",
                    formatLabel = { "${it.roundToInt()}시" },
                    value = settings.dndEndHour.toFloat(),
                    range = 0f..23f,
                    steps = 22,
                    onCommit = { v -> applySettings { it.copy(dndEndHour = v.roundToInt()) } }
                )
                TactileButton(
                    onClick = {
                        scope.launch {
                            val cardTriple = container.learningRepository.getMainWidgetCard(advanceIndex = false)
                            if (cardTriple == null) {
                                statusMessage = "표시할 메인 과목 카드가 없습니다. 과목을 먼저 추가해 주세요."
                            } else {
                                val (sub, _, card) = cardTriple
                                NotificationHelper.showLockScreenCard(
                                    context = context,
                                    subjectId = sub.id,
                                    subjectName = sub.name,
                                    cardId = card.id,
                                    cardTitle = card.title,
                                    microSummary = card.microSummary,
                                    exampleText = card.exampleText,
                                    ongoing = settings.lockScreenOngoing,
                                    silentUpdate = false
                                )
                                statusMessage = "잠금화면 알림 카드를 지금 발송했습니다!"
                            }
                        }
                    },
                    style = TactileStyle.NEUTRAL,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("지금 잠금화면 알림 테스트 발송", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = g.cardBorder
                )

                ToggleSettingRow(
                    title = "매일 학습 리마인더 알림",
                    checked = settings.reminderEnabled,
                    onCheckedChange = { c -> applySettings { it.copy(reminderEnabled = c) } }
                )
                SliderSettingRow(
                    title = "리마인더 알림 시(Hour)",
                    formatLabel = { String.format("%02d시", it.roundToInt()) },
                    value = settings.reminderHour.toFloat(),
                    range = 6f..23f,
                    steps = 16,
                    onCommit = { v -> applySettings { it.copy(reminderHour = v.roundToInt()) } }
                )
                SliderSettingRow(
                    title = "리마인더 알림 분(Minute)",
                    formatLabel = { String.format("%02d분", (it.roundToInt() / 15) * 15) },
                    value = settings.reminderMinute.toFloat(),
                    range = 0f..45f,
                    steps = 2,
                    onCommit = { v -> applySettings { it.copy(reminderMinute = (v.roundToInt() / 15) * 15) } }
                )
            }

            // 6. TTS Settings
            SettingsSection("TTS 발음 읽기 설정") {
                ToggleSettingRow(
                    title = "카드 표시 시 자동 발음 재생",
                    checked = settings.ttsAutoPlayDefault,
                    onCheckedChange = { c -> applySettings { it.copy(ttsAutoPlayDefault = c) } }
                )
                SliderSettingRow(
                    title = "TTS 읽기 속도",
                    formatLabel = { String.format("%.1fx", it) },
                    value = settings.ttsSpeechRate,
                    range = 0.5f..1.8f,
                    steps = 12,
                    onCommit = { v -> applySettings { it.copy(ttsSpeechRate = v) } }
                )
                TactileButton(
                    onClick = {
                        container.ttsHelper.speak("Consistency beats intensity.", "en-US", settings.ttsSpeechRate)
                    },
                    style = TactileStyle.NEUTRAL,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔊 영어 TTS 발음 테스트", fontWeight = FontWeight.Bold)
                }
            }

            // 7. Local SQLite JSON Backup / Restore
            SettingsSection("로컬 데이터 백업 및 복원 (JSON)") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TactileButton(
                        onClick = { exportLauncher.launch("lvupme_backup.json") },
                        style = TactileStyle.SKY,
                        height = 44.dp,
                        modifier = Modifier.weight(1f)
                    ) { Text("백업 내보내기", fontWeight = FontWeight.ExtraBold) }

                    TactileButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        style = TactileStyle.NEUTRAL,
                        height = 44.dp,
                        modifier = Modifier.weight(1f)
                    ) { Text("백업 복원하기", fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    GamifiedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val g = MaterialTheme.game
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = g.emerald,
                uncheckedTrackColor = g.trackBg
            )
        )
    }
}

/**
 * Smooth slider that holds local drag state and only persists to DataStore/WorkManager on drag finish.
 */
@Composable
private fun SliderSettingRow(
    title: String,
    formatLabel: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit
) {
    val g = MaterialTheme.game
    var localValue by remember(value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(formatLabel(localValue), fontWeight = FontWeight.ExtraBold, color = g.sky)
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onCommit(localValue) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = g.sky,
                activeTrackColor = g.sky,
                inactiveTrackColor = g.trackBg,
                activeTickColor = Color.White.copy(alpha = 0.5f),
                inactiveTickColor = g.cardBorder
            )
        )
    }
}
