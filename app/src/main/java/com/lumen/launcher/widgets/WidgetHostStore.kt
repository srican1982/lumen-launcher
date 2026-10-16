package com.lumen.launcher.widgets

import android.content.Context

/** Persist actual AppWidget IDs, never screenshots or imitation widgets. */
object WidgetHostStore {
    const val HOST_ID = 1026
    fun prefs(context: Context) = context.getSharedPreferences("lumen_widget_home", Context.MODE_PRIVATE)
    fun ids(context: Context): List<Int> = prefs(context).getString("ids", "").orEmpty()
        .split(',').mapNotNull(String::toIntOrNull).distinct()
    fun transparent(context: Context, id: Int) = prefs(context).getBoolean("transparent_$id", false)
    fun setTransparent(context: Context, id: Int, value: Boolean) {
        prefs(context).edit().putBoolean("transparent_$id", value).apply()
    }
    fun idsForMode(context: Context, mode: String): List<Int> = ids(context).filter {
        prefs(context).getString("space_$it", "Home") == mode
    }
    fun assignMode(context: Context, id: Int, mode: String) {
        prefs(context).edit().putString("space_$id", mode).apply()
    }
    @Synchronized fun add(context: Context, id: Int) {
        prefs(context).edit().putString("ids", (ids(context) + id).distinct().joinToString(",")).apply()
    }
    @Synchronized fun remove(context: Context, removed: Set<Int>) {
        val prefs = prefs(context)
        val existing = ids(context)
        val affectedModes = existing.filter { it in removed }.map { prefs.getString("space_$it", "Home") ?: "Home" }.toSet()
        val edit = prefs.edit().putString("ids", existing.filterNot { it in removed }.joinToString(","))
        affectedModes.forEach { mode ->
            edit.putInt("compaction_$mode", prefs.getInt("compaction_$mode", 0) + 1)
        }
        removed.forEach { edit.remove("external_$it").remove("minimum_$it").remove("transparent_$it").remove("space_$it").remove("columns_$it").remove("height_$it").remove("x_$it").remove("y_$it") }; edit.apply()
    }
    fun external(context: Context, id: Int) = prefs(context).getBoolean("external_$id", false)
    fun registerProvider(context: Context, id: Int, info: android.appwidget.AppWidgetProviderInfo) {
        if (info.provider.packageName == context.packageName) return
        val density = context.resources.displayMetrics.density
        val minimum = (info.minResizeHeight / density).toInt().coerceIn(48, 600)
        prefs(context).edit().putBoolean("external_$id", true)
            .putInt("minimum_$id", minimum)
            .putInt("height_$id", (info.minHeight / density).toInt().coerceIn(minimum, 600))
            .putInt("columns_$id", if (info.minWidth / density > 180) 2 else 1).apply()
    }
    fun minimum(context: Context, id: Int, kind: WidgetKind) = if (external(context, id)) prefs(context).getInt("minimum_$id", 96) else WidgetContentRules.minHeight(kind)
    fun maximum(context: Context, id: Int) = if (external(context, id)) 600 else 300
    fun constrainHeight(context: Context, id: Int, value: Int, kind: WidgetKind) = value.coerceIn(minimum(context, id, kind), maximum(context, id))
    fun columns(context: Context, id: Int, kind: WidgetKind): Int = WidgetContentRules.columns(prefs(context).getInt("columns_$id", if (kind.wide) 2 else 1))
        .coerceAtLeast(if (kind.wide && !external(context, id)) 2 else 1)
    fun height(context: Context, id: Int, kind: WidgetKind): Int = constrainHeight(context, id, prefs(context).getInt("height_$id", kind.heightDp), kind)
    fun resize(context: Context, id: Int, columns: Int, height: Int, kind: WidgetKind) {
        prefs(context).edit().putInt("columns_$id", WidgetContentRules.columns(columns).coerceAtLeast(if (kind.wide && !external(context, id)) 2 else 1))
            .putInt("height_$id", constrainHeight(context, id, height, kind)).apply()
    }
    fun position(context: Context, id: Int): WidgetPosition? {
        val prefs = prefs(context)
        if (!prefs.contains("x_$id") || !prefs.contains("y_$id")) return null
        val x = prefs.getFloat("x_$id", 0f)
        val y = prefs.getFloat("y_$id", 0f)
        if (!x.isFinite() || !y.isFinite()) return null
        return WidgetPosition(x.coerceIn(0f, 1f), y.coerceAtLeast(0f))
    }
    fun setPositions(context: Context, positions: Map<Int, WidgetPosition>) {
        val edit = prefs(context).edit()
        positions.forEach { (id, p) -> edit.putFloat("x_$id", p.xFraction).putFloat("y_$id", p.yDp) }
        edit.apply()
    }
    fun setPosition(context: Context, id: Int, position: WidgetPosition) {
        if (!position.xFraction.isFinite() || !position.yDp.isFinite()) return
        prefs(context).edit().putFloat("x_$id", position.xFraction.coerceIn(0f, 1f))
            .putFloat("y_$id", position.yDp.coerceAtLeast(0f)).apply()
    }
    @Synchronized fun move(context: Context, id: Int, delta: Int) {
        val ids = ids(context).toMutableList()
        val index = ids.indexOf(id)
        if (index < 0) return
        val destination = (index + delta).coerceIn(0, ids.lastIndex)
        ids.removeAt(index); ids.add(destination, id)
        prefs(context).edit().putString("ids", ids.joinToString(",")).apply()
    }

    /** Absolute reorder used by drag-and-drop on the widgets page. */
    @Synchronized fun moveTo(context: Context, id: Int, toIndex: Int) {
        val ids = ids(context).toMutableList()
        val from = ids.indexOf(id)
        if (from < 0 || ids.isEmpty()) return
        val destination = toIndex.coerceIn(0, ids.lastIndex)
        if (from == destination) return
        ids.removeAt(from)
        // After removal, indices above `from` shift down by one.
        val dest = if (from < destination) destination - 1 else destination
        ids.add(dest.coerceIn(0, ids.size), id)
        prefs(context).edit().putString("ids", ids.joinToString(",")).apply()
    }

    /** Replace the full order after a gesture drag finishes. */
    @Synchronized fun setOrder(context: Context, ordered: List<Int>) {
        val current = ids(context).toSet()
        val next = ordered.filter { it in current }.distinct()
        if (next.isEmpty() || next.toSet() != current) return
        prefs(context).edit().putString("ids", next.joinToString(",")).apply()
    }
}
