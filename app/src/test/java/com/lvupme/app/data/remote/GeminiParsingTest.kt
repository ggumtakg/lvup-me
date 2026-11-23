package com.lvupme.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiParsingTest {

    @Test
    fun ignoresThinkingPartsAndExtractsJsonCleanly() {
        val responseJson = """
            {
              "candidates": [
                {
                  "finishReason": "STOP",
                  "content": {
                    "role": "model",
                    "parts": [
                      { "thought": true, "text": "Let me think step by step about the quiz..." },
                      { "thought": false, "text": "```json\n{\"status\":\"ok\",\"message\":\"연결 성공\"}\n```" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val extracted = GeminiResponseParser.extractResponseText(responseJson)
        assertEquals("""{"status":"ok","message":"연결 성공"}""", extracted)
    }

    @Test
    fun extractsJsonWrappedInExplanatoryProse() {
        val raw = "Here is the requested JSON:\n{\"a\":1,\"b\":\"two\"}\nHope this helps!"
        assertEquals("""{"a":1,"b":"two"}""", GeminiResponseParser.extractJsonPayload(raw))
    }

    @Test
    fun preservesValidJsonContainingNestedMarkdownCodeFences() {
        val rawJson = """{"title":"LEFT JOIN","exampleText":"```sql\nSELECT * FROM a LEFT JOIN b ON a.id = b.id;\n```"}"""
        assertEquals(rawJson, GeminiResponseParser.extractJsonPayload(rawJson))
    }

    @Test
    fun throwsDescriptiveExceptionOnSafetyFinishReason() {
        val responseJson = """
            {
              "candidates": [
                { "finishReason": "SAFETY" }
              ]
            }
        """.trimIndent()

        try {
            GeminiResponseParser.extractResponseText(responseJson)
            fail("Expected GeminiApiException for SAFETY finishReason")
        } catch (e: GeminiApiException) {
            assertTrue(e.message.orEmpty().contains("SAFETY"))
        }
    }

    @Test
    fun throwsGeminiApiExceptionOnMalformedHtmlBody() {
        try {
            GeminiResponseParser.extractResponseText("<html><body>Captive Portal</body></html>")
            fail("Expected GeminiApiException for HTML response")
        } catch (e: GeminiApiException) {
            assertTrue(e.message.orEmpty().contains("응답 형식"))
        }
    }
}
