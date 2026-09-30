package com.lumen.launcher.voice

/** Rejects late TTS completions after a new reply, interruption, or dismissal. */
class SpeechTurn {
    private var sequence = 0L
    private var active: String? = null

    fun begin(): String = "lumen-speak-${++sequence}".also { active = it }

    fun cancel() { active = null }

    fun complete(id: String?): Boolean {
        if (id == null || id != active) return false
        active = null
        return true
    }
}
