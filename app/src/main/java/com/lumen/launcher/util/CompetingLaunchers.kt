package com.lumen.launcher.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class CompetingLauncher(
    val label: String,
    val packageName: String,
    val overlay: Boolean,
    val accessibility: Boolean
)

object CompetingLaunchers {

    private val oemHomes = setOf(
        "com.sec.android.app.launcher",
        "com.samsung.android.app.launcher",
        "com.sec.android.app.easylauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    )

    fun find(context: Context): List<CompetingLauncher> {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = pm.queryIntentActivities(home, PackageManager.MATCH_ALL)
        val enabledA11y = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return resolved
            .map { it.activityInfo }
            .distinctBy { it.packageName }
            .filterNot { info ->
                val pkg = info.packageName
                pkg == context.packageName ||
                    pkg in oemHomes ||
                    pkg.startsWith("com.sec.") ||
                    pkg.startsWith("com.samsung.") ||
                    pkg.startsWith("com.android.") ||
                    info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            }
            .mapNotNull { info ->
                val label = info.loadLabel(pm).toString().trim()
                if (label.isEmpty()) return@mapNotNull null
                val overlay = hasOverlay(appOps, info.applicationInfo.uid, info.packageName)
                val accessibility = hasAccessibility(enabledA11y, info.packageName)
                if (!overlay && !accessibility) return@mapNotNull null
                CompetingLauncher(
                    label = label,
                    packageName = info.packageName,
                    overlay = overlay,
                    accessibility = accessibility
                )
            }
    }

    private fun hasAccessibility(enabled: String, packageName: String): Boolean {
        if (enabled.isBlank()) return false
        return enabled.split(':').any { entry ->
            entry == packageName || entry.startsWith("$packageName/")
        }
    }

    private fun hasOverlay(appOps: AppOpsManager?, uid: Int, packageName: String): Boolean {
        if (appOps == null) return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
