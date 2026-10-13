package com.lumen.launcher.widgets

import com.lumen.launcher.data.*
import java.util.Calendar

object TaskWidgetRules {
    val filters = setOf("Today", "Week", "All", "Inbox", "Important")
    fun visible(tasks: List<TodoItem>, filter: String, now: Long): List<TodoItem> {
        val public = tasks.filter { it.space != SpaceKind.Private }
        val end = Calendar.getInstance().apply { timeInMillis = TodoTime.startOfDay(now); add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
        return when (filter) {
            "Today" -> TaskAgenda.today(public, now) + TaskAgenda.completedToday(public, now)
            "Week" -> public.filter { it.dueAt?.let { due -> due < end && due >= TodoTime.startOfDay(now) } == true || (!it.done && it.dueAt == null) }
            "Inbox" -> public.filter { !it.done && it.dueAt == null }
            "Important" -> public.filter { !it.done && it.priority }
            else -> public
        }.sortedWith(compareBy<TodoItem> { it.done }.thenByDescending { TaskAgenda.overdue(it, now) }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.createdAt })
    }
}
