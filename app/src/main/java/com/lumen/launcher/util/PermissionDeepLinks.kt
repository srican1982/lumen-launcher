package com.lumen.launcher.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.lumen.launcher.inbox.LumenNotificationListener

/**
 * Opens the most specific Settings page for a permission so the user
 * does not have to scroll a long list hunting for "Lumen".
 */
object PermissionDeepLinks {

    fun openDndAccess(context: Context): Boolean {
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= 30) {
            // Constant may be missing from some SDK stubs — use the public action string.
            val detail = Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS").apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (start(context, detail)) return true
        }
        return start(
            context,
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openNotificationListener(context: Context): Boolean {
        val component = ComponentName(context, LumenNotificationListener::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (start(context, detail)) return true
        }
        return start(
            context,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openExactAlarm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (start(context, intent)) return true
        }
        return start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess
}
