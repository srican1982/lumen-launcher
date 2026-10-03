package com.lumen.launcher.focus

import android.content.Context
import android.content.Intent

/** Launcher-level restriction; does not suspend packages or control Android Recents. */
object FocusAppAccess {
    private fun prefs(context: Context) = context.getSharedPreferences("focus_apps", Context.MODE_PRIVATE)
    fun selected(context: Context): Set<String> = prefs(context).getStringSet("selected", emptySet()).orEmpty().toSet()
    fun save(context: Context, packages: Set<String>) { prefs(context).edit().putStringSet("selected", packages).apply() }
    internal fun packageAllowed(running: Boolean, pkg: String, ownPackage: String, selected: Set<String>) =
        !running || pkg == ownPackage || pkg in selected

    fun allows(context: Context, intent: Intent): Boolean {
        if (!FocusSessionManager.get(context).snapshotNow().running) return true
        if (intent.action == Intent.ACTION_DIAL || intent.action == "android.intent.action.CALL_EMERGENCY") return true
        if (intent.action?.startsWith("android.settings.") == true) return true
        val pkg = intent.component?.packageName ?: intent.`package`
            ?: intent.resolveActivity(context.packageManager)?.packageName ?: return false
        return packageAllowed(true, pkg, context.packageName, selected(context))
    }
}
