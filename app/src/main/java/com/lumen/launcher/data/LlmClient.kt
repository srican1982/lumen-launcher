package com.lumen.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LlmClient {
    private val requests = java.util.concurrent.Executors.newFixedThreadPool(4) { task ->
        Thread(task, "lumen-http").apply { isDaemon = true }
    }
    private val deadline = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "lumen-http-deadline").apply { isDaemon = true }
    }

    enum class Kind { Gemini, OpenRouter }

    fun kind(apiKey: String): Kind {
        val key = apiKey.trim()
        return if (key.startsWith("sk-or-")) Kind.OpenRouter else Kind.Gemini
    }

    data class Reply(val code: Int, val body: String)

    fun postGemini(apiKey: String, model: String, body: String): Reply? {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        return post(url, body, mapOf("x-goog-api-key" to apiKey.trim()))
    }

    fun postOpenRouter(
        apiKey: String,
        model: String,
        prompt: String,
        json: Boolean,
        maxTokens: Int
    ): Reply? {
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
            )
        if (json) {
            payload.put("response_format", JSONObject().put("type", "json_object"))
        }
        val url = URL("https://openrouter.ai/api/v1/chat/completions")
        return post(
            url,
            payload.toString(),
            mapOf(
                "Authorization" to "Bearer ${apiKey.trim()}",
                "HTTP-Referer" to "https://github.com/srican1982/lumen-launcher",
                "X-Title" to "Lumen"
            )
        )
    }

    fun extractText(raw: String): String? {
        val root = jsonObject(raw) ?: return raw.takeIf { it.isNotBlank() }
        openRouterContent(root)?.let { return it }
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        if (parts == null) {
            return raw.takeIf { it.contains("\"action\"") || looksLikeSpeech(raw) }
        }
        val texts = (0 until parts.length()).mapNotNull { index ->
            val part = parts.optJSONObject(index) ?: return@mapNotNull null
            if (part.optBoolean("thought") || part.optBoolean("thoughts")) return@mapNotNull null
            part.optString("text").trim().takeIf { it.isNotBlank() }
        }
        texts.firstOrNull { it.contains("\"action\"") }?.let { return it }
        return texts.joinToString("\n").trim().takeIf { it.isNotBlank() }
    }

    private fun openRouterContent(root: JSONObject): String? {
        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()
        return content.takeIf { it.isNotBlank() }
    }

    private fun post(url: URL, body: String, headers: Map<String, String>): Reply? {
        val task = requests.submit<Reply?> { performPost(url, body, headers) }
        return try { task.get(10, java.util.concurrent.TimeUnit.SECONDS) }
        catch (_: Exception) { task.cancel(true); null }
    }

    private fun performPost(url: URL, body: String, headers: Map<String, String>): Reply? {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        val timeout = deadline.schedule({ connection.disconnect() }, 10, java.util.concurrent.TimeUnit.SECONDS)
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            Reply(code, stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (result.length < 524_288) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, 524_288 - result.length))
                    if (count < 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }.orEmpty())
        } catch (_: Exception) {
            null
        } finally {
            timeout.cancel(false)
            connection.disconnect()
        }
    }

    private fun jsonObject(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    private fun looksLikeSpeech(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isNotBlank() && !trimmed.startsWith("{") && trimmed.length < 400
    }
}
