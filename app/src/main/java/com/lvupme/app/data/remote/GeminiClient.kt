package com.lvupme.app.data.remote

import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiApiException(
    message: String,
    val httpCode: Int? = null,
    val isRetryable: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)

class GeminiClient(
    private val settingsRepository: SettingsRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class GeminiRequest(
        val systemInstruction: ContentObj? = null,
        val contents: List<ContentObj>,
        val generationConfig: GenerationConfigObj = GenerationConfigObj()
    )

    @Serializable
    private data class ContentObj(
        val role: String? = null,
        val parts: List<PartObj>
    )

    @Serializable
    private data class PartObj(
        val text: String? = null,
        val thought: Boolean = false
    )

    @Serializable
    private data class GenerationConfigObj(
        val temperature: Double = 0.4,
        val responseMimeType: String = "application/json"
    )

    @Serializable
    private data class GeminiResponse(
        val candidates: List<CandidateObj>? = null,
        val error: GeminiErrorObj? = null
    )

    @Serializable
    private data class CandidateObj(
        val content: ContentObj? = null,
        val finishReason: String? = null
    )

    @Serializable
    private data class GeminiErrorObj(
        val code: Int? = null,
        val message: String? = null
    )

    @Serializable
    private data class PingDto(
        val status: String = "ok",
        val message: String = ""
    )

    @Serializable
    data class GeminiModelInfo(
        val id: String,
        val displayName: String,
        val description: String = ""
    )

    @Serializable
    private data class ModelsListResponse(
        val models: List<RawModelItem>? = null,
        val error: GeminiErrorObj? = null
    )

    @Serializable
    private data class RawModelItem(
        val name: String = "",
        val displayName: String = "",
        val description: String = "",
        val supportedGenerationMethods: List<String> = emptyList()
    )

    internal fun extractResponseText(responseText: String): String =
        GeminiResponseParser.extractResponseText(responseText)

    internal fun extractJsonPayload(raw: String): String =
        GeminiResponseParser.extractJsonPayload(raw)

    private suspend fun executeWithRetry(request: Request, maxRetries: Int = 2): String {
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        return text
                    }
                    val parsedErr = runCatching {
                        json.decodeFromString<GeminiResponse>(text).error?.message
                    }.getOrNull()
                    val retryable = response.code == 429 || response.code in 500..599
                    val ex = GeminiApiException(
                        message = "Gemini API 오류 (${response.code}): ${parsedErr ?: text.take(200)}",
                        httpCode = response.code,
                        isRetryable = retryable
                    )
                    if (!retryable || attempt == maxRetries) throw ex
                    lastException = ex
                }
            } catch (e: GeminiApiException) {
                if (!e.isRetryable || attempt == maxRetries) throw e
                lastException = e
            } catch (e: IOException) {
                val ex = GeminiApiException(
                    message = "네트워크 연결에 실패했습니다. 인터넷 상태를 확인해 주세요.",
                    isRetryable = true,
                    cause = e
                )
                if (attempt == maxRetries) throw ex
                lastException = ex
            }
            delay(800L * (1L shl attempt))
        }
        throw lastException ?: GeminiApiException("알 수 없는 네트워크 오류")
    }

    private suspend inline fun <reified T> requestJson(
        systemPrompt: String,
        contents: List<ContentObj>,
        temperature: Double = 0.4
    ): T = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isBlank()) {
            throw GeminiApiException("Gemini API 키가 설정되지 않았습니다. 설정 탭에서 API 키를 입력해 주세요.", httpCode = 401)
        }
        val model = AppSettings.sanitizeModel(settingsRepository.getSettings().geminiModel)
        val url = "https://generativelanguage.googleapis.com".toHttpUrl()
            .newBuilder()
            .addPathSegment("v1beta")
            .addPathSegment("models")
            .addPathSegment("$model:generateContent")
            .build()

        val bodyObj = GeminiRequest(
            systemInstruction = ContentObj(parts = listOf(PartObj(text = systemPrompt))),
            contents = contents,
            generationConfig = GenerationConfigObj(
                temperature = temperature,
                responseMimeType = "application/json"
            )
        )
        val requestBody = json.encodeToString(bodyObj)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        val responseText = executeWithRetry(request)
        val cleaned = extractResponseText(responseText)
        try {
            json.decodeFromString<T>(cleaned)
        } catch (e: Exception) {
            throw GeminiApiException("AI JSON 응답 파싱 실패: ${e.message}", cause = e)
        }
    }

    private suspend inline fun <reified T> requestJson(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.4
    ): T = requestJson(
        systemPrompt = systemPrompt,
        contents = listOf(ContentObj(role = "user", parts = listOf(PartObj(text = userPrompt)))),
        temperature = temperature
    )

    suspend fun listAvailableModels(): List<GeminiModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isBlank()) {
            throw GeminiApiException("먼저 Gemini API 키를 입력해 주세요.", httpCode = 401)
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models".toHttpUrl()
            .newBuilder()
            .addQueryParameter("pageSize", "100")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()

        val responseText = executeWithRetry(request, maxRetries = 1)
        val parsed = try {
            json.decodeFromString<ModelsListResponse>(responseText)
        } catch (e: Exception) {
            throw GeminiApiException("모델 목록 응답 파싱 실패: ${e.message}", cause = e)
        }
        parsed.models.orEmpty()
            .filter { item ->
                val id = item.name.removePrefix("models/")
                "generateContent" in item.supportedGenerationMethods &&
                    id.startsWith("gemini") &&
                    !id.contains("embedding") &&
                    !id.contains("tts") &&
                    !id.contains("image") &&
                    !id.contains("audio") &&
                    !id.contains("robotics")
            }
            .map { item ->
                val id = item.name.removePrefix("models/")
                GeminiModelInfo(
                    id = id,
                    displayName = item.displayName.ifBlank { id },
                    description = item.description
                )
            }
            .sortedWith(
                compareByDescending<GeminiModelInfo> { it.id.contains("flash") }
                    .thenByDescending { it.id }
            )
    }

    suspend fun testConnection(): String {
        val res = requestJson<PingDto>(
            systemPrompt = "Return valid JSON {\"status\":\"ok\",\"message\":\"연결 성공\"}",
            userPrompt = "Ping"
        )
        return res.message.ifBlank { "Gemini API 연결 성공!" }
    }

    suspend fun onboardingChat(history: List<ChatMessageDto>): OnboardingReplyDto {
        val system = """
            당신은 개인 맞춤 모바일 학습 앱 'LvUp Me'의 친절하고 명쾌한 학습 설계 AI 튜터입니다.
            사용자와 짧은 한국어 대화를 통해 1) 배우고 싶은 과목(예: 영어, SQL, 파이썬, 경제학 등), 2) 구체적인 상세 분야/목표(예: 비즈니스 영단어, SQL JOIN과 서브쿼리 등)를 확정하세요.
            보통 2~3턴 안에 구체화되면 readyForLevelTest=true로 설정하고 proposedSubject, proposedSubTopic, goalSummary를 채우세요.
            어학 과목(영어, 일본어, 중국어 등)이면 suggestedTtsEnabled=true 및 적절한 BCP-47 locale(예: en-US, ja-JP, zh-CN)을 제안하세요.
            반드시 아래 JSON 스키마만 출력하세요:
            {
              "assistantReply": "한국어 응답",
              "readyForLevelTest": false,
              "proposedSubject": "",
              "proposedSubTopic": "",
              "goalSummary": "",
              "suggestedTtsEnabled": false,
              "suggestedTtsLocale": "en-US"
            }
        """.trimIndent()

        // Use official multi-turn Gemini contents roles to avoid prompt injection
        val contents = history.map { msg ->
            ContentObj(
                role = if (msg.role == "user") "user" else "model",
                parts = listOf(PartObj(text = msg.content))
            )
        }
        return requestJson(systemPrompt = system, contents = contents, temperature = 0.6)
    }

    private fun normalizeQuestions(questions: List<LevelTestQuestionDto>): List<LevelTestQuestionDto> {
        return questions.mapIndexed { idx, q ->
            val cleanType = if (q.type == QuestionType.SHORT_ANSWER) QuestionType.SHORT_ANSWER else QuestionType.MULTIPLE_CHOICE
            val normalizedChoices = if (cleanType == QuestionType.MULTIPLE_CHOICE) {
                val base = q.choices.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (q.answerText.trim() in base) base
                else (base.take(3) + q.answerText.trim()).filter { it.isNotEmpty() }.distinct()
            } else emptyList()

            q.copy(
                id = if (q.id > 0) q.id else idx + 1,
                type = cleanType,
                choices = normalizedChoices,
                answerText = q.answerText.trim(),
                acceptedAnswers = (q.acceptedAnswers + q.answerText).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            )
        }
    }

    suspend fun generateLevelTest(subject: String, subTopic: String, goalSummary: String): LevelTestDto {
        val system = """
            당신은 '$subject - $subTopic' 분야의 수준 진단 출제 위원입니다.
            사용자의 현재 수준을 정확히 파악할 수 있도록 기초~중급~심화 난이도를 고르게 섞어 총 6문제를 출제하세요.
            규칙:
            - 객관식(MULTIPLE_CHOICE) 4문제 + 단답형 주관식(SHORT_ANSWER) 2문제.
            - 객관식 choices는 정확히 4개, answerText는 choices 중 하나와 완전히 일치.
            - 주관식 answerText는 반드시 3단어 이내(최대 3단어)로 제한하고, acceptedAnswers에 띄어쓰기/동의어/대소문자 변형을 포함.
            - 한국어로 설명하되 원어/코드/문법 용어는 정확히 표기.
            JSON 스키마:
            {
              "introMessage": "수준 테스트 안내 한 줄",
              "questions": [
                {
                  "id": 1,
                  "type": "MULTIPLE_CHOICE",
                  "prompt": "질문",
                  "choices": ["보기1","보기2","보기3","보기4"],
                  "answerText": "보기1",
                  "acceptedAnswers": ["보기1"],
                  "explanation": "간결한 해설"
                }
              ]
            }
        """.trimIndent()
        val raw = requestJson<LevelTestDto>(
            systemPrompt = system,
            userPrompt = "과목: $subject\n상세 분야: $subTopic\n학습 목표: $goalSummary"
        )
        return raw.copy(questions = normalizeQuestions(raw.questions))
    }

    suspend fun evaluateLevelAndBuildCurriculum(
        subject: String,
        subTopic: String,
        goalSummary: String,
        resultsSummary: String
    ): LevelEvaluationDto {
        val system = """
            당신은 '$subject - $subTopic' 전문 AI 튜터입니다.
            사용자의 수준 테스트 결과를 분석해 수준(예: 입문, 초급, 초중급, 중급, 중상급, 고급)을 판정하고,
            모바일 고반복 학습에 적합한 5~7개 단원의 체계적인 단계별 커리큘럼을 생성하세요.
            JSON 스키마:
            {
              "assessedLevel": "초중급",
              "feedback": "강점과 보완점을 포함한 2~3문장 진단 피드백",
              "units": [
                { "orderIndex": 1, "title": "단원 제목", "description": "이 단원에서 익힐 핵심 내용 한 줄" }
              ]
            }
        """.trimIndent()
        return requestJson(
            systemPrompt = system,
            userPrompt = "과목: $subject\n상세: $subTopic\n목표: $goalSummary\n테스트 결과:\n$resultsSummary"
        )
    }

    suspend fun generateUnitContentBatch(
        subject: String,
        subTopic: String,
        userLevel: String,
        unitTitle: String,
        unitDescription: String,
        count: Int,
        existingTitles: List<String> = emptyList()
    ): UnitContentBatchDto {
        val avoid = if (existingTitles.isEmpty()) "없음" else existingTitles.joinToString(", ")
        val system = """
            당신은 모바일 저용량·고반복 마이크로 러닝 콘텐츠 제작자입니다.
            주어진 단원에 대해 정확히 ${count}개의 핵심 개념(마이크로 레슨 카드)과 각 개념별 퀴즈 2종(객관식 1 + 3단어 이하 주관식 1)을 생성하세요.
            핵심 규칙:
            1. microSummary는 홈 위젯/잠금화면에서 한눈에 읽히도록 1~2줄(70자 이내)로 핵심만 압축.
            2. detailExplanation은 개념의 이유와 활용법을 명확히 2~4줄 설명.
            3. exampleText는 짧은 실전 예문 또는 짧은 코드/쿼리 예시(예: 영어 예문, SQL JOIN 쿼리).
            4. ttsText는 발음을 들려줄 원문 단어/짧은 문장 (어학 과목이 아니면 비워도 됨).
            5. multipleChoice: AI 질문 + 4개 정답 카드(choices) + 정확한 answerText + 오답 시 보여줄 간결한 wrongReason.
            6. shortAnswer: 개념 설명/해석의 빈칸 채우기 또는 설명을 보고 용어/단어 맞추기.
               **answerText는 반드시 최대 3단어 이내**로 제한! acceptedAnswers에 동의어/한영 변형 2~5개 포함.
            7. 이미 생성된 주제($avoid)와 중복되지 않게 출제.
            JSON 스키마:
            {
              "cards": [
                {
                  "title": "핵심 개념 제목 (예: resilient / LEFT JOIN)",
                  "microSummary": "위젯용 한눈에 보는 핵심 요약",
                  "detailExplanation": "자세한 설명",
                  "exampleText": "짧은 예시",
                  "ttsText": "발음용 텍스트",
                  "multipleChoice": {
                    "type": "MULTIPLE_CHOICE",
                    "prompt": "질문",
                    "choices": ["A","B","C","D"],
                    "answerText": "A",
                    "acceptedAnswers": ["A"],
                    "wrongReason": "오답 시 표시할 정답 이유"
                  },
                  "shortAnswer": {
                    "type": "SHORT_ANSWER",
                    "prompt": "3단어 이내 빈칸 또는 용어 질문",
                    "choices": [],
                    "answerText": "정답(최대 3단어)",
                    "acceptedAnswers": ["정답", "유사표현"],
                    "wrongReason": "오답 시 표시할 정답 이유"
                  }
                }
              ]
            }
        """.trimIndent()
        return requestJson(
            systemPrompt = system,
            userPrompt = "과목: $subject ($subTopic)\n학습자 수준: $userLevel\n단원: $unitTitle\n단원 설명: $unitDescription",
            temperature = 0.5
        )
    }

    suspend fun generateReinforcementCard(
        subject: String,
        subTopic: String,
        userLevel: String,
        originalTitle: String,
        originalSummary: String,
        wrongCount: Int
    ): ReinforceCardDto {
        val system = """
            사용자가 '$originalTitle' 개념을 ${wrongCount}회 틀렸습니다.
            단순 문장 암기가 아니라 진짜 이해를 돕기 위해, **완전히 새로운 예문·더 쉬운 비유·다른 각도의 질문**으로 보강 카드와 객관식/3단어 이하 주관식 문제를 1세트 생성하세요.
            shortAnswer의 answerText는 반드시 3단어 이내여야 합니다.
            JSON 스키마:
            {
              "title": "$originalTitle (약점 보강)",
              "microSummary": "더 직관적인 한 줄 요약",
              "detailExplanation": "쉬운 비유와 헷갈리기 쉬운 포인트 설명",
              "exampleText": "새로운 예문/예시",
              "ttsText": "",
              "multipleChoice": {
                "type": "MULTIPLE_CHOICE",
                "prompt": "다른 각도의 객관식 질문",
                "choices": ["A","B","C","D"],
                "answerText": "A",
                "acceptedAnswers": ["A"],
                "wrongReason": "정답 이유"
              },
              "shortAnswer": {
                "type": "SHORT_ANSWER",
                "prompt": "3단어 이내 주관식 질문",
                "choices": [],
                "answerText": "정답",
                "acceptedAnswers": ["정답"],
                "wrongReason": "정답 이유"
              }
            }
        """.trimIndent()
        return requestJson(
            systemPrompt = system,
            userPrompt = "과목: $subject ($subTopic), 수준: $userLevel\n원래 개념: $originalTitle\n원래 요약: $originalSummary",
            temperature = 0.6
        )
    }

    suspend fun gradeShortAnswer(
        subject: String,
        prompt: String,
        expectedAnswer: String,
        userAnswer: String
    ): AiGradeDto {
        val system = """
            당신은 '$subject' 채점 튜터입니다.
            사용자의 짧은 주관식 답안이 의미상 정답인지 관대하되 정확하게 판정하고, 1~2문장으로 핵심 이유를 한국어로 설명하세요.
            JSON 스키마: { "isCorrect": true, "explanation": "채점 이유" }
        """.trimIndent()
        return requestJson(
            systemPrompt = system,
            userPrompt = "문제: $prompt\n모범 답안: $expectedAnswer\n사용자 답안: $userAnswer",
            temperature = 0.2
        )
    }

    suspend fun generateMockExam(
        subject: String,
        subTopic: String,
        userLevel: String,
        coveredConcepts: List<String>,
        questionCount: Int = 10
    ): MockExamDto {
        val conceptsText = coveredConcepts.take(40).joinToString(", ")
        val mcCount = (questionCount * 7) / 10
        val saCount = questionCount - mcCount
        val system = """
            '$subject - $subTopic' ($userLevel 수준)의 종합 모의시험을 총 ${questionCount}문제 출제하세요.
            객관식(MULTIPLE_CHOICE) ${mcCount}문제 + 3단어 이내 주관식(SHORT_ANSWER) ${saCount}문제 비율을 지키세요.
            학습한 개념들을 우선 반영하되 실전 응용력을 검증하세요.
            JSON 스키마:
            {
              "title": "AI 종합 모의시험",
              "questions": [
                {
                  "id": 1,
                  "type": "MULTIPLE_CHOICE",
                  "prompt": "질문",
                  "choices": ["A","B","C","D"],
                  "answerText": "A",
                  "acceptedAnswers": ["A"],
                  "explanation": "해설"
                }
              ]
            }
        """.trimIndent()
        val raw = requestJson<MockExamDto>(
            systemPrompt = system,
            userPrompt = "다룬 핵심 개념 목록: $conceptsText",
            temperature = 0.5
        )
        return raw.copy(questions = normalizeQuestions(raw.questions))
    }

    suspend fun generateExamFeedback(
        subject: String,
        examTitle: String,
        scorePercent: Int,
        wrongItemsSummary: String
    ): ExamFeedbackDto {
        val system = """
            시험 결과를 분석해 간결한 한국어 피드백을 JSON으로 반환하세요.
            JSON 스키마:
            {
              "summary": "전반적 성취 요약 (2문장)",
              "weakTopics": ["보강이 필요한 개념1", "개념2"],
              "nextActionTip": "다음 학습 추천 한 줄"
            }
        """.trimIndent()
        return requestJson(
            systemPrompt = system,
            userPrompt = "과목: $subject\n시험: $examTitle\n점수: ${scorePercent}%\n틀린 문항:\n$wrongItemsSummary",
            temperature = 0.4
        )
    }
}
