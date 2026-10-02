package com.lumen.launcher.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                FocusSessionManager.get(context.applicationContext).let { manager ->
                    if (intent.action == "com.lumen.launcher.FOCUS_FINISHED") {
                        manager.onAlarm(intent.getLongExtra("until", -1))
                    } else {
                        manager.recover()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
