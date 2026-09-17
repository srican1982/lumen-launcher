package com.lumen.launcher.search

import com.lumen.launcher.voice.intent.VoiceIntentMapper
import com.lumen.launcher.voice.intent.VoiceQueryRouter

object LauncherVoice {

    fun parse(text: String): LauncherCommand? {
        val intent = VoiceQueryRouter.route(listOf(text))
        return VoiceIntentMapper.toCommand(intent)
    }
}
