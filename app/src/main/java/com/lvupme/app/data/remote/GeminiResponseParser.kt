package com.lvupme.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object GeminiResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    data class ContentObj(
        val role: String? = null,
        val parts: List<PartObj> = emptyList()
    )

    @Serializable
    data class PartObj(
        val text: String? = null,
        val thought: Boolean = false
    )

    @Serializable
    data class GeminiResponse(
        val candidates: List<CandidateObj>? = null,
        val error: GeminiErrorObj? = null
    )

    @Serializable
    data class CandidateObj(
        val content: ContentObj? = null,
        val finishReason: String? = null
    )

    @Serializable
    data class GeminiErrorObj(
        val code: Int? = null,
        val message: String? = null
    )

    fun extractResponseText(responseText: String): String {
        val parsed = try {
            json.decodeFromString<GeminiResponse>(responseText)
        } catch (e: Exception) {
            throw GeminiApiException("AI 서버 응답 형식이 올바르지 않습니다.", cause = e)
        }

        parsed.error?.let { err ->
            throw GeminiApiException(
                "Gemini API 오류 (${err.code ?: 0}): ${err.message.orEmpty()}",
                httpCode = err.code
            )
        }

        val candidate = parsed.candidates?.firstOrNull()
            ?: throw GeminiApiException("AI 응답 후보가 비어 있습니다.")

        if (candidate.finishReason in listOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT")) {
            throw GeminiApiException("안전 필터(${candidate.finishReason})로 인해 응답이 차단되었습니다. 표현을 바꿔 다시 시도해 주세요.")
        }

        val nonThoughtText = candidate.content?.parts.orEmpty()
            .filter { !it.thought && !it.text.isNullOrBlank() }
            .joinToString("\n") { it.text.orEmpty() }
            .trim()

        val rawContent = nonThoughtText.ifBlank {
            candidate.content?.parts.orEmpty()
                .mapNotNull { it.text }
                .joinToString("\n")
                .trim()
        }

        if (rawContent.isBlank()) {
            throw GeminiApiException("AI 응답 본문이 비어 있습니다.")
        }
        return extractJsonPayload(rawContent)
    }

    fun extractJsonPayload(raw: String): String {
        val trimmed = raw.trim()
        // If Gemini already returned clean JSON (typical with responseMimeType=application/json),
        // return directly so nested markdown fences inside string values (e.g. ```sql ... ```) are never sliced.
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }

        var text = trimmed
        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val fencedMatch = fenceRegex.find(text)
        if (fencedMatch != null) {
            text = fencedMatch.groupValues[1].trim()
            if (text.startsWith("{") || text.startsWith("[")) {
                return text
            }
        }

        val firstObj = text.indexOf('{')
        val lastObj = text.lastIndexOf('}')
        if (firstObj != -1 && lastObj > firstObj) {
            return text.substring(firstObj, lastObj + 1).trim()
        }
        val firstArr = text.indexOf('[')
        val lastArr = text.lastIndexOf(']')
        if (firstArr != -1 && lastArr > firstArr) {
            return text.substring(firstArr, lastArr + 1).trim()
        }
        return text
    }
}
