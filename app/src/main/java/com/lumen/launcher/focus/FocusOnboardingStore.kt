package com.lumen.launcher.focus

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

/** The in-progress marker prevents our own defaults from looking like a legacy restore. */
internal fun needsFocusGuide(completed: Boolean, inProgress: Boolean, hasSettings: Boolean): Boolean =
    !completed && (inProgress || !hasSettings)

class FocusOnboardingStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus_onboarding", Context.MODE_PRIVATE)

    fun prepare(repo: FocusAllowedPeopleRepository): Boolean {
        val people = context.getSharedPreferences("focus_people", Context.MODE_PRIVATE)
        val existing = people.all.keys.any { it != "selected_groups" } ||
            people.getStringSet("selected_groups", emptySet()).orEmpty().isNotEmpty() ||
            context.getSharedPreferences("focus_apps", Context.MODE_PRIVATE).contains("selected") ||
            context.getSharedPreferences("focus_sound", Context.MODE_PRIVATE).contains("sound_id")
        val show = needsFocusGuide(prefs.getBoolean("completed", false), prefs.getBoolean("in_progress", false), existing)
        if (!show) { complete(); return false }
        if (!prefs.getBoolean("in_progress", false)) {
            prefs.edit().putBoolean("in_progress", true).apply()
            repo.setSettings(repo.settingsNow().copy(lastDurationMinutes = 30, allowRepeatedCallers = false))
            val phone = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
                ?: context.packageManager.resolveActivity(Intent(Intent.ACTION_DIAL), 0)?.activityInfo?.packageName
            FocusAppAccess.save(context, setOfNotNull(phone?.takeUnless { it == "android" || it == context.packageName }))
            FocusSoundPrefs.setSound(context, FocusSound.Off)
        }
        return true
    }

    fun step(): Int = prefs.getInt("step", 0).coerceIn(0, 3)
    fun setStep(step: Int) { prefs.edit().putInt("step", step.coerceIn(0, 3)).apply() }
    fun complete() { prefs.edit().putBoolean("completed", true).putBoolean("in_progress", false).putInt("step", 0).apply() }
}
