package com.lumen.launcher

import android.app.Application
import com.lumen.launcher.util.HiddenApis

class LumenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HiddenApis.unseal()
    }
}
