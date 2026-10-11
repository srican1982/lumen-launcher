package com.lumen.launcher.inbox

import com.lumen.launcher.data.LlmClient
import com.lumen.launcher.data.AiModels
import org.json.JSONArray
import org.json.JSONObject

object InboxDigest {

    fun fingerprint(items: List<InboxItem>): String =
        readable(items).joinToString("|") { "${it.key}:${it.postedAt}" }

    fun local(items: List<InboxItem>): String {
        val fresh = readable(items)
        if (fresh.isEmpty()) return ""
        val bits = fresh.take(4).map { item ->
            val who = item.title.trim()
            val bit = item.preview.trim().replace(Regex("\\s+"), " ").take(42)
            if (bit.isBlank()) who else "$who: $bit"
        }
        val lead = if (fresh.size == 1) "1 new." else "${fresh.size} new."
        return "$lead ${bits.joinToString("; ")}"
    }

    fun summarize(apiKey: String, items: List<InboxItem>): String {
        val fallback = local(items)
        if (apiKey.isBlank() || fallback.isBlank()) return fallback
        return generate(apiKey, readable(items)) ?: fallback
    }

    private fun readable(items: List<InboxItem>): List<InboxItem> {
        val fresh = items.filterNot { it.isDigest }
        return (if (fresh.isNotEmpty()) fresh else items).take(8)
    }

    private fun generate(apiKey: String, items: List<InboxItem>): String? {
        val lines = items.joinToString("\n") { item ->
            "- ${item.source.title} | ${item.title} | ${item.preview.take(80)}"
        }
        val prompt = """
            Summarize these unread notifications for a phone launcher.
            One or two short spoken sentences. No bullets, no markdown, no greeting.
            Mention who and what matters. Skip promo and login codes if there is real mail.

            $lines
        """.trimIndent()
        val reply = if (LlmClient.kind(apiKey) == LlmClient.Kind.OpenRouter)
            LlmClient.postOpenRouter(apiKey, AiModels.openRouter, prompt, false, 256)
        else LlmClient.postGemini(apiKey, AiModels.gemini, payload(prompt).toString())
        if (reply != null && reply.code in 200..299) {
            val text = LlmClient.extractText(reply.body)
            val cleaned = text?.replace(Regex("```+|\\s+"), " ")?.trim()?.take(220)
            if (!cleaned.isNullOrBlank()) return cleaned
        }
        return null
    }

    private fun payload(prompt: String): JSONObject {
        val payload = JSONObject()
        payload.put(
            "contents",
            JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
        )
        payload.put(
            "generationConfig",
            JSONObject()
                .put("temperature", 0.2)
                .put("maxOutputTokens", 256)
                .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        )
        return payload
    }

}
