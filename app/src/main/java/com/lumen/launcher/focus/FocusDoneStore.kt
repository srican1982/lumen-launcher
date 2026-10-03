package com.lumen.launcher.focus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FocusDoneEntry(
    val id: String,
    val text: String,
    val at: Long
)

/** Recent tasks completed by finishing a Focus session. */
object FocusDoneStore {
    private const val PREFS = "focus_done"
    private const val KEY = "entries"
    private const val MAX = 20

    fun record(context: Context, id: String, text: String) {
        if (id.isBlank() || text.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = (listOf(FocusDoneEntry(id, text.trim(), System.currentTimeMillis())) + load(context))
            .distinctBy { it.id + "|" + it.text.lowercase() }
            .take(MAX)
        val arr = JSONArray()
        next.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("text", e.text).put("at", e.at))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): List<FocusDoneEntry> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        FocusDoneEntry(
                            id = o.optString("id"),
                            text = o.optString("text"),
                            at = o.optLong("at")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
