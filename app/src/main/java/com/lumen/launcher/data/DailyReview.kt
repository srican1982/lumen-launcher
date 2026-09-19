package com.lumen.launcher.data

data class DailyReview(
    val tasksLeft: Int,
    val overdue: Int,
    val nextLine: String
) {
    val spoken: String
        get() {
            val left = if (tasksLeft == 1) "1 task left" else "$tasksLeft tasks left"
            val missed = if (overdue <= 0) null else if (overdue == 1) "1 overdue" else "$overdue overdue"
            return listOfNotNull(left, missed, nextLine.takeIf { it.isNotBlank() }).joinToString(". ")
        }

    val visible: Boolean
        get() = tasksLeft > 0 || overdue > 0 || nextLine.isNotBlank()
}

object DailyReviewResolver {
    fun resolve(
        todos: List<TodoItem>,
        events: List<CalendarEvent>,
        now: Long = System.currentTimeMillis()
    ): DailyReview {
        val open = todos.filterNot { it.done }
        val overdue = open.count { item ->
            val due = item.dueAt ?: return@count false
            due < TodoTime.startOfDay(now)
        }
        val tomorrow = TodoTime.tomorrowStart(now)
        val next = events
            .filter { it.begin >= tomorrow && it.begin < tomorrow + 24 * 60 * 60 * 1000L }
            .minByOrNull { it.begin }
        val nextLine = next?.let {
            "Tomorrow's first event at ${TodoTime.timeLabel(it.begin)}"
        }.orEmpty()
        return DailyReview(open.size, overdue, nextLine)
    }
}
