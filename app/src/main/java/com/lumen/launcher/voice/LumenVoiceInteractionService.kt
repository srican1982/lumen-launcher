package com.lumen.launcher.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class LumenVoiceInteractionService : VoiceInteractionService()

class LumenVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return LumenVoiceSession(this)
    }
}
