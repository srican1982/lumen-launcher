package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class TaskAgendaTest {
    private val now = date(8, 17)

    @Test fun overdueSurvivesDayChangeAndComesFirst() {
        val overdue = TodoItem("old", "Yesterday", dueAt = date(7, 9))
        val scheduled = TodoItem("later", "Tonight", dueAt = date(8, 18))
        val anytime = TodoItem("any", "Anytime")
        assertThat(TaskAgenda.today(listOf(anytime, scheduled, overdue), now))
            .containsExactly(overdue, scheduled, anytime).inOrder()
        assertThat(TaskAgenda.overdue(overdue, now)).isTrue()
        assertThat(TaskAgenda.overdue(scheduled, now)).isFalse()
    }

    @Test fun scheduledTasksSortByTimeBeforeAnytime() {
        val early = TodoItem("early", "Early", dueAt = date(8, 18))
        val late = TodoItem("late", "Late", dueAt = date(8, 20))
        val anytime = TodoItem("any", "Anytime", dueAt = date(8, 0))
        assertThat(TaskAgenda.today(listOf(late, anytime, early), now))
            .containsExactly(early, late, anytime).inOrder()
    }

    @Test fun tomorrowOnlyContainsRealUnfinishedTasksForTomorrow() {
        val tomorrow = TodoItem("tom", "Tomorrow", dueAt = date(9, 9))
        assertThat(TaskAgenda.tomorrow(listOf(tomorrow, tomorrow.copy(id = "done", done = true),
            TodoItem("today", "Today", dueAt = date(8, 19)),
            TodoItem("later", "Later", dueAt = date(10, 9))), now)).containsExactly(tomorrow)
    }

    @Test fun completionDateCountsOldTaskFinishedToday() {
        val completed = TodoItem("old", "Old task", done = true, dueAt = date(7, 9), completedAt = now)
        assertThat(TaskAgenda.completedToday(listOf(completed), now)).containsExactly(completed)
        assertThat(TaskAgenda.today(listOf(completed), now)).isEmpty()
    }

    @Test fun legacyCompletedTasksFallBackToDueDate() {
        val legacy = TodoItem("legacy", "Legacy", done = true, dueAt = date(8, 9))
        assertThat(TaskAgenda.completedToday(listOf(legacy), now)).containsExactly(legacy)
    }

    private fun date(day: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, 0)
    }.timeInMillis
}
