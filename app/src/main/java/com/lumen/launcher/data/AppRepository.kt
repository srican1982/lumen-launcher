package com.lumen.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    suspend fun loadLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        resolved.mapNotNull { info -> toAppInfo(pm, info) }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.key }
    }

    private fun toAppInfo(pm: PackageManager, info: ResolveInfo): AppInfo? {
        val activity = info.activityInfo ?: return null
        val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
        if (label.isEmpty()) return null
        val applicationInfo = activity.applicationInfo
        return AppInfo(
            label = label,
            packageName = activity.packageName,
            activityName = activity.name,
            lastUpdateTime = 0L,
            category = AppCategorizer.categorize(
                packageName = activity.packageName,
                label = label,
                category = applicationInfo?.category ?: -1
            )
        )
    }
}
