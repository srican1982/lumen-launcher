package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class TodoRepeatTest {

    @Test
    fun dailyMovesToNextDay() {
        val monday = day(2026, Calendar.SEPTEMBER, 14, 9, 30)
        val next = TodoRepeat.nextDue(monday, TodoRepeat.Daily, monday)
        assertThat(TodoTime.startOfDay(next)).isEqualTo(TodoTime.startOfDay(monday) + 24 * 60 * 60 * 1000L)
        assertThat(TodoTime.hourOf(next)).isEqualTo(9)
        assertThat(TodoTime.minuteOf(next)).isEqualTo(30)
    }

    @Test
    fun weeklyKeepsWeekday() {
        val friday = day(2026, Calendar.SEPTEMBER, 18, 15, 0)
        val next = TodoRepeat.nextDue(friday, TodoRepeat.Weekly, friday)
        val cal = Calendar.getInstance().apply { timeInMillis = next }
        assertThat(cal.get(Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.FRIDAY)
        assertThat(TodoTime.hourOf(next)).isEqualTo(15)
    }

    @Test
    fun monthlyAdvancesMonth() {
        val first = day(2026, Calendar.SEPTEMBER, 1, 8, 0)
        val next = TodoRepeat.nextDue(first, TodoRepeat.Monthly, first)
        val cal = Calendar.getInstance().apply { timeInMillis = next }
        assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.OCTOBER)
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(1)
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
