package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LlmClientTest {

    @Test
    fun openRouterKeyIsDetected() {
        assertThat(LlmClient.kind("sk-or-v1-abc")).isEqualTo(LlmClient.Kind.OpenRouter)
        assertThat(LlmClient.kind("  sk-or-v1-abc  ")).isEqualTo(LlmClient.Kind.OpenRouter)
    }

    @Test
    fun geminiKeyIsDetected() {
        assertThat(LlmClient.kind("AIzaSyExample")).isEqualTo(LlmClient.Kind.Gemini)
        assertThat(LlmClient.kind("")).isEqualTo(LlmClient.Kind.Gemini)
    }

    @Test
    fun extractTextReadsOpenRouterChoices() {
        val raw = """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": "{\"action\":\"ANSWER\",\"textValue\":\"Hello.\"}"
                }
              }]
            }
        """.trimIndent()
        assertThat(LlmClient.extractText(raw)).contains("ANSWER")
    }
}
