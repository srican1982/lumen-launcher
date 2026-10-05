package com.lumen.launcher.focus

import android.content.Context

enum class FocusSound(
    val id: String,
    val title: String
) {
    Off("off", "Off"),
    Rain("rain", "Rain"),
    Forest("forest", "Forest"),
    Ocean("ocean", "Ocean"),
    Fireplace("fireplace", "Fireplace"),
    Flute("flute", "Native flute"),
    BrownNoise("brown", "Brown noise");

    companion object {
        val selectable = entries.filter { it != Off && it != Fireplace }
        fun fromId(raw: String?): FocusSound =
            entries.firstOrNull { it != Fireplace && it.id.equals(raw, ignoreCase = true) } ?: Off
    }
}

object FocusSoundPrefs {
    private const val PREFS = "focus_sound"
    private const val KEY_SOUND = "sound_id"
    private const val KEY_VOLUME = "volume"

    fun sound(context: Context): FocusSound =
        FocusSound.fromId(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SOUND, FocusSound.Off.id)
        )

    fun volume(context: Context): Float =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_VOLUME, 0.35f)
            .coerceIn(0.05f, 1f)

    fun setSound(context: Context, sound: FocusSound) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SOUND, sound.id).apply()
    }

    fun setVolume(context: Context, volume: Float) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_VOLUME, volume.coerceIn(0.05f, 1f)).apply()
    }
}
