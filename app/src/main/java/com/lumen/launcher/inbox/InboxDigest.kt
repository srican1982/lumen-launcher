package com.lumen.launcher.inbox

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        val models = listOf("gemini-3-flash-preview", "gemini-2.5-flash")
        for (model in models) {
            val body = payload(prompt).toString()
            val reply = post(apiKey, body, model) ?: continue
            if (reply.code !in 200..299) continue
            extractText(reply.body)?.let { text ->
                val cleaned = text.replace(Regex("```+|\\s+"), " ").trim().take(220)
                if (cleaned.isNotBlank()) return cleaned
            }
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

    private data class Reply(val code: Int, val body: String)

    private fun post(apiKey: String, body: String, model: String): Reply? {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 14_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            Reply(code, stream?.bufferedReader()?.readText().orEmpty())
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun extractText(raw: String): String? {
        val root = runCatching {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) null else JSONObject(raw.substring(start, end + 1))
        }.getOrNull() ?: return raw.takeIf { it.isNotBlank() && !it.trim().startsWith("{") }
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: return null
        val texts = (0 until parts.length()).mapNotNull { index ->
            val part = parts.optJSONObject(index) ?: return@mapNotNull null
            if (part.optBoolean("thought") || part.optBoolean("thoughts")) return@mapNotNull null
            part.optString("text").trim().takeIf { it.isNotBlank() }
        }
        return texts.joinToString(" ").trim().takeIf { it.isNotBlank() }
    }
}
