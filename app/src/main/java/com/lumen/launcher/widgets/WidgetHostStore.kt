package com.lumen.launcher.widgets

import android.content.Context

/** Persist actual AppWidget IDs, never screenshots or imitation widgets. */
object WidgetHostStore {
    const val HOST_ID = 1026
    fun prefs(context: Context) = context.getSharedPreferences("lumen_widget_home", Context.MODE_PRIVATE)
    fun ids(context: Context): List<Int> = prefs(context).getString("ids", "").orEmpty()
        .split(',').mapNotNull(String::toIntOrNull).distinct()
    @Synchronized fun add(context: Context, id: Int) {
        prefs(context).edit().putString("ids", (ids(context) + id).distinct().joinToString(",")).apply()
    }
    @Synchronized fun remove(context: Context, removed: Set<Int>) {
        val edit = prefs(context).edit().putString("ids", ids(context).filterNot { it in removed }.joinToString(","))
        removed.forEach { edit.remove("columns_$it").remove("height_$it") }; edit.apply()
    }
    fun columns(context: Context, id: Int, kind: WidgetKind): Int = WidgetContentRules.columns(prefs(context).getInt("columns_$id", if (kind.wide) 2 else 1))
    fun height(context: Context, id: Int, kind: WidgetKind): Int = WidgetContentRules.height(prefs(context).getInt("height_$id", kind.heightDp), kind)
    fun resize(context: Context, id: Int, columns: Int, height: Int, kind: WidgetKind) {
        prefs(context).edit().putInt("columns_$id", WidgetContentRules.columns(columns))
            .putInt("height_$id", WidgetContentRules.height(height, kind)).apply()
    }
    @Synchronized fun move(context: Context, id: Int, delta: Int) {
        val ids = ids(context).toMutableList()
        val index = ids.indexOf(id)
        if (index < 0) return
        val destination = (index + delta).coerceIn(0, ids.lastIndex)
        ids.removeAt(index); ids.add(destination, id)
        prefs(context).edit().putString("ids", ids.joinToString(",")).apply()
    }
}
