package com.lumen.launcher.voice

object SpeechContext {
    /** App/folder labels only: contact names must not leak into a provider's cloud recognizer. */
    fun hints(labels: List<String>, commands: List<String> = emptyList()): List<String> =
        (labels + commands).map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }.take(100)
}
