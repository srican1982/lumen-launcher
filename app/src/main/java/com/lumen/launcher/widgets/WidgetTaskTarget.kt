package com.lumen.launcher.widgets

import com.lumen.launcher.data.TodoItem
import org.json.JSONObject

/** An old widget tap must not complete the next occurrence of a repeating task. */
object WidgetTaskTarget {
    fun encode(task: TodoItem): String = JSONObject().put("id", task.id)
        .put("due", task.dueAt ?: JSONObject.NULL).toString()

    fun matches(task: TodoItem, target: String): Boolean = runCatching {
        val json = JSONObject(target)
        if (!json.has("id") || !json.has("due")) return false
        val due = if (json.isNull("due")) null else json.getLong("due")
        !task.done && task.id == json.getString("id") && task.dueAt == due
    }.getOrDefault(false)
}
