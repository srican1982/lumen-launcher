package com.lumen.launcher

import android.app.Application

class LumenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
            com.lumen.launcher.data.AiModels.load(this)
            com.lumen.launcher.widgets.WidgetLiveUpdates.start(this)
        }
    }
}
