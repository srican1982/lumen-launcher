package com.lumen.launcher.data

object TodoCompletion {
    fun complete(item: TodoItem, now: Long): TodoItem = when {
        item.done -> item
        item.repeat.isNotBlank() -> item.copy(done = false, completedAt = null,
            dueAt = TodoRepeat.nextDue(item.dueAt ?: now, item.repeat, now))
        else -> item.copy(done = true, completedAt = now)
    }
}
