package com.lumen.launcher.widgets

import com.lumen.launcher.data.*
import com.lumen.launcher.focus.FocusSessionSnapshot

object WidgetCommandRules {
    fun completeTask(tasks: List<TodoItem>, target: String, now: Long): List<TodoItem> = tasks.map { task ->
        if (task.space != SpaceKind.Private && TodoTime.isToday(task, now) && WidgetTaskTarget.matches(task, target))
            TodoCompletion.complete(task, now) else task
    }

    fun canControlFocus(session: FocusSessionSnapshot, target: String, command: String, now: Long): Boolean {
        if (session.startedAt <= 0L || session.startedAt.toString() != target) return false
        return when (command) {
            "PauseFocus" -> session.until > now && !session.paused
            "ResumeFocus" -> session.paused
            else -> false
        }
    }
}
