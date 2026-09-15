package com.lumen.launcher.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

object HomeRole {

    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = context.getSystemService(RoleManager::class.java)
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return role.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            home,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun request(activity: Activity, roleLauncher: ActivityResultLauncher<Intent>) {
        val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        if (!samsung && Build.VERSION.SDK_INT >= 29) {
            val role = activity.getSystemService(RoleManager::class.java)
            if (role != null &&
                role.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !role.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                val launched = runCatching {
                    roleLauncher.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }.isSuccess
                if (launched) return
            }
        }
        openHomeSettings(activity)
    }

    fun openHomeSettings(activity: Activity) {
        val options = buildList {
            add(Intent(Settings.ACTION_HOME_SETTINGS))
            add(Intent("com.samsung.settings.HOME_SETTINGS"))
            add(Intent("android.settings.HOME_SETTINGS"))
            if (Build.VERSION.SDK_INT >= 24) {
                add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in options) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(activity.packageManager) != null &&
                runCatching { activity.startActivity(intent) }.isSuccess
            ) {
                return
            }
        }
    }
}
