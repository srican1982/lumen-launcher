package com.lumen.launcher.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.util.Log

object StatusBarController {

    private const val TAG = "LumenShade"

    fun expandNotifications(context: Context) {
        expand(context, settings = false)
    }

    fun expandQuickSettings(context: Context) {
        expand(context, settings = true)
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expand(context: Context, settings: Boolean) {
        HiddenApis.unseal()
        if (expandViaManager(context, settings)) return
        if (expandViaBinder(settings)) return
        Log.w(TAG, "Could not expand ${if (settings) "quick settings" else "notifications"}")
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandViaManager(context: Context, settings: Boolean): Boolean {
        val service = runCatching { context.getSystemService("statusbar") }.getOrNull() ?: return false
        val cls = runCatching { Class.forName("android.app.StatusBarManager") }.getOrNull() ?: return false
        return if (settings) {
            invoke(cls, service, "expandSettingsPanel") ||
                invoke(cls, service, "expandSettingsPanel", arrayOf(String::class.java), arrayOf(null)) ||
                invoke(cls, service, "expandSettingsPanel", arrayOf(String::class.java), arrayOf(""))
        } else {
            val name = if (Build.VERSION.SDK_INT >= 17) "expandNotificationsPanel" else "expand"
            invoke(cls, service, name) || invoke(cls, service, "expand")
        }
    }

    @SuppressLint("PrivateApi")
    private fun expandViaBinder(settings: Boolean): Boolean {
        return runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "statusbar") as? IBinder
                ?: return false
            val stub = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                ?: return false
            if (settings) {
                runCatching {
                    service.javaClass.getMethod("expandSettingsPanel", String::class.java).invoke(service, null as String?)
                }.recoverCatching {
                    service.javaClass.getMethod("expandSettingsPanel").invoke(service)
                }.getOrThrow()
            } else {
                service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
            }
            true
        }.onFailure { Log.w(TAG, "Binder expand failed", it) }.getOrDefault(false)
    }

    private fun invoke(
        cls: Class<*>,
        service: Any,
        method: String,
        types: Array<Class<*>> = emptyArray(),
        args: Array<Any?> = emptyArray()
    ): Boolean {
        return runCatching {
            val declared = if (types.isEmpty()) cls.getMethod(method) else cls.getMethod(method, *types)
            declared.isAccessible = true
            declared.invoke(service, *args)
            true
        }.onFailure { Log.w(TAG, "StatusBarManager.$method failed", it) }.getOrDefault(false)
    }
}

internal object HiddenApis {
    @SuppressLint("PrivateApi")
    fun unseal() {
        runCatching {
            val vmRuntime = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntime.getDeclaredMethod("getRuntime")
            getRuntime.isAccessible = true
            val runtime = getRuntime.invoke(null)
            val setHiddenApiExemptions = vmRuntime.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            setHiddenApiExemptions.isAccessible = true
            setHiddenApiExemptions.invoke(runtime, arrayOf("L") as Any)
        }
    }
}
