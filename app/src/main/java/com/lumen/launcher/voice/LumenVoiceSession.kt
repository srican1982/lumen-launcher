package com.lumen.launcher.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.lumen.launcher.LauncherActivity

class LumenVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, LauncherActivity::class.java).apply {
            action = ACTION_HEY_LUMEN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(intent) }
        hide()
    }

    companion object {
        const val ACTION_HEY_LUMEN = "com.lumen.launcher.action.HEY_LUMEN"
    }
}
