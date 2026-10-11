package com.lumen.launcher.voice.intent

/** A parsed or AI-produced intent cannot authorize its own sensitive action. */
object VoiceRisk {
    fun requiresConfirmation(intent: VoiceIntent): Boolean = intent.destructive || intent.action in setOf(
        VoiceAction.CALL, VoiceAction.SEND_MESSAGE, VoiceAction.SET_ALARM,
        VoiceAction.CANCEL_ALARM, VoiceAction.DELETE_TASK, VoiceAction.HIDE_APP,
        VoiceAction.UNHIDE_APP, VoiceAction.MOVE_TO_PRIVATE, VoiceAction.REMOVE_FROM_PRIVATE
    )
}
