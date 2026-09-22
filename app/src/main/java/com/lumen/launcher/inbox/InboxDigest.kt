package com.lumen.launcher.inbox

import com.lumen.launcher.data.LlmClient
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
        if (LlmClient.kind(apiKey) == LlmClient.Kind.OpenRouter) {
            for (model in listOf("google/gemini-2.5-flash", "google/gemini-2.0-flash-001")) {
                val reply = LlmClient.postOpenRouter(apiKey, model, prompt, json = false, maxTokens = 256) ?: continue
                if (reply.code !in 200..299) continue
                spoken(reply.body)?.let { return it }
            }
            return null
        }
        val models = listOf("gemini-3-flash-preview", "gemini-2.5-flash")
        for (model in models) {
            val reply = LlmClient.postGemini(apiKey, model, payload(prompt).toString()) ?: continue
            if (reply.code !in 200..299) continue
            spoken(reply.body)?.let { return it }
        }
        return null
    }

    private fun spoken(raw: String): String? {
        val text = LlmClient.extractText(raw) ?: return null
        val cleaned = text.replace(Regex("```+|\\s+"), " ").trim().take(220)
        return cleaned.takeIf { it.isNotBlank() }
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
