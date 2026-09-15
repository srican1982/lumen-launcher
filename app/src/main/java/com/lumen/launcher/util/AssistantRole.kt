package com.lumen.launcher.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

object AssistantRole {

    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = context.getSystemService(RoleManager::class.java)
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return role.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            }
        }
        return false
    }

    fun request(activity: Activity, roleLauncher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = activity.getSystemService(RoleManager::class.java)
            if (role != null &&
                role.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                !role.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            ) {
                val launched = runCatching {
                    roleLauncher.launch(role.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                }.isSuccess
                if (launched) return
            }
        }
        openSettings(activity)
    }

    fun openSettings(activity: Activity) {
        val options = buildList {
            add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
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
