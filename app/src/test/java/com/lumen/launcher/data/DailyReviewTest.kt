package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class DailyReviewTest {

    @Test
    fun countsOpenAndOverdue() {
        val now = day(2026, Calendar.SEPTEMBER, 19, 10, 0)
        val todos = listOf(
            TodoItem("1", "Open today", dueAt = now, space = SpaceKind.Work),
            TodoItem("2", "Late", dueAt = now - 2 * 24 * 60 * 60 * 1000L, space = SpaceKind.Work),
            TodoItem("3", "Done", done = true, dueAt = now, space = SpaceKind.Work)
        )
        val review = DailyReviewResolver.resolve(todos, emptyList(), now)
        assertThat(review.tasksLeft).isEqualTo(2)
        assertThat(review.overdue).isEqualTo(1)
        assertThat(review.visible).isTrue()
    }

    private fun day(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
