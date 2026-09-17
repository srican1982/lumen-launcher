package com.lumen.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

data class DockApp(
    val id: String,
    val key: String,
    val label: String,
    val packageName: String,
    val activityName: String,
    val fallbackIntent: Intent
)

object DockResolver {
    const val MIN = 3
    const val DEFAULT = 4
    const val MAX = 6

    fun resolve(context: Context, apps: List<AppInfo>, keys: List<String>?, capacity: Int = DEFAULT): List<DockApp> {
        val resolved = (keys ?: defaultKeys(context, apps)).distinct().take(capacity.coerceIn(MIN, MAX))
        return resolved.mapNotNull { key -> fromKey(context, apps, key) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun defaultKeys(context: Context, apps: List<AppInfo>): List<String> {
        return listOf("phone", "messages", "browser", "camera")
    }

    fun fromApp(app: AppInfo): DockApp {
        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        return DockApp(app.key, app.key, app.label, app.packageName, app.activityName, launch)
    }

    private fun fromKey(context: Context, apps: List<AppInfo>, key: String): DockApp? {
        defaultIntents(context).find { it.id == key }?.let { return it }
        apps.find { it.key == key }?.let { return fromApp(it) }
        return defaultIntents(context).find { it.key == key }
    }

    private fun defaultIntents(context: Context): List<DockApp> {
        return listOf(
            resolveIntent(context, "phone", "Phone", Intent(Intent.ACTION_DIAL)),
            resolveIntent(
                context,
                "messages",
                "Messages",
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            ),
            resolveIntent(
                context,
                "browser",
                "Browser",
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
            ),
            resolveIntent(
                context,
                "camera",
                "Camera",
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            )
        )
    }

    private fun resolveIntent(context: Context, id: String, label: String, intent: Intent): DockApp {
        val launch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = context.packageManager.resolveActivity(launch, PackageManager.MATCH_DEFAULT_ONLY)
        val activity = resolved?.activityInfo
        val packageName = activity?.packageName.orEmpty()
        val activityName = activity?.name.orEmpty()
        val key = if (packageName.isBlank()) id else "$packageName/$activityName"
        return DockApp(
            id = id,
            key = key,
            label = resolved?.loadLabel(context.packageManager)?.toString() ?: label,
            packageName = packageName,
            activityName = activityName,
            fallbackIntent = launch
        )
    }
}
