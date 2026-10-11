package com.lumen.launcher.data

object TaskAgenda {
    fun overdue(item: TodoItem, now: Long): Boolean {
        val due = item.dueAt ?: return false
        return !item.done && if (TodoTime.hasClock(due)) due < now else TodoTime.startOfDay(due) < TodoTime.startOfDay(now)
    }
    fun today(items: List<TodoItem>, now: Long): List<TodoItem> = items.filter { !it.done && !TodoTime.isUpcoming(it, now) }
        .sortedWith(compareByDescending<TodoItem> { overdue(it, now) }
            .thenBy { if (it.dueAt?.let(TodoTime::hasClock) == true) 0 else 1 }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.createdAt })
    fun completedToday(items: List<TodoItem>, now: Long): List<TodoItem> = items.filter {
        it.done && TodoTime.startOfDay(it.completedAt ?: it.dueAt ?: 0L) == TodoTime.startOfDay(now)
    }
    fun tomorrow(items: List<TodoItem>, now: Long): List<TodoItem> = items.filter {
        !it.done && it.dueAt?.let { due -> TodoTime.startOfDay(due) == TodoTime.tomorrowStart(now) } == true
    }.sortedWith(compareBy<TodoItem> { if (it.dueAt?.let(TodoTime::hasClock) == true) 0 else 1 }.thenBy { it.dueAt })
}
