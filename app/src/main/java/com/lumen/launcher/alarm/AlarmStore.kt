package com.lumen.launcher.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Minimal alarm state only: available before the first unlock after boot. */
object AlarmStore {
    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("lumen_alarm_boot", Context.MODE_PRIVATE)
    fun initialized(context: Context) = prefs(context).contains("alarms")
    @Synchronized fun read(context: Context): List<LumenAlarm> = runCatching {
        val array = JSONArray(prefs(context).getString("alarms", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val a = array.getJSONObject(i)
            val h = a.optInt("hour", -1); val m = a.optInt("minute", -1)
            if (h !in 0..23 || m !in 0..59) null else LumenAlarm(
                a.getString("id"), h, m, a.optBoolean("enabled", true),
                a.optBoolean("daily"), a.optString("label"),
                a.optLong("triggerAtMs", 0L).takeIf { it > 0L })
        }
    }.getOrDefault(emptyList())
    @Synchronized fun write(context: Context, alarms: List<LumenAlarm>) {
        val array = JSONArray()
        alarms.forEach { a -> array.put(JSONObject().put("id", a.id).put("hour", a.hour)
            .put("minute", a.minute).put("enabled", a.enabled).put("daily", a.daily).put("label", a.label).put("triggerAtMs", a.triggerAtMs ?: 0L)) }
        check(prefs(context).edit().putString("alarms", array.toString()).commit())
    }
    @Synchronized fun update(context: Context, change: (List<LumenAlarm>) -> List<LumenAlarm>): List<LumenAlarm> {
        val next = change(read(context)); write(context, next); return next
    }
    fun tone(context: Context) = prefs(context).getString("tone", AlarmTones.AURA) ?: AlarmTones.AURA
    fun setTone(context: Context, tone: String) { check(prefs(context).edit().putString("tone", tone).commit()) }
}
