package com.lumen.launcher.voice.intent

/** Session-only context. Explicit commands still work without a network. */
class VoiceConversation {
    private data class Turn(val text: String, val shareable: Boolean)
    private val turns = ArrayDeque<Turn>()
    private var awaitingDetail = false
    fun clear() { turns.clear(); awaitingDetail = false }
    fun history(): List<String> = turns.filter { it.shareable }.map { it.text }
    fun record(user: String, reply: String, asksForDetail: Boolean, shareable: Boolean = true) {
        if (user.isNotBlank()) turns.addLast(Turn("User: ${user.take(2000)}", shareable))
        turns.addLast(Turn("Lumen: ${reply.take(2000)}", shareable))
        while (turns.size > 16) turns.removeFirst()
        awaitingDetail = asksForDetail
    }
    fun needsContext(text: String, local: VoiceIntent): Boolean {
        if (turns.isEmpty() || local.action == VoiceAction.END_TALK) return false
        val q = text.lowercase().trim()
        if (Regex("""^(actually|instead|make (it|that)|what about|how about|why|tell me more|explain|yes|yeah|no,? but)\b""").containsMatchIn(q)) return true
        if (Regex("""\b(it|that|those|them|him|her|same)\b""").containsMatchIn(q)) return true
        return awaitingDetail && !Regex("""^(open|launch|show|set|start|stop|cancel|hide|delete|remind|call|text)\b""").containsMatchIn(q)
    }
}
