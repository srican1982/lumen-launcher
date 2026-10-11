package com.lumen.launcher.widgets

import com.lumen.launcher.data.*
import com.lumen.launcher.focus.FocusSessionSnapshot
import org.junit.Assert.*
import org.junit.Test

class WidgetCommandRulesTest {
    private val now = 1_800_000_000_000L
    @Test fun completingOneRowPreservesOtherTasks() {
        val row = TodoItem("call", "Call Steve")
        val other = TodoItem("invoice", "Invoice", priority = true)
        val result = WidgetCommandRules.completeTask(listOf(row, other), WidgetTaskTarget.encode(row), now)
        assertTrue(result[0].done)
        assertEquals(now, result[0].completedAt)
        assertEquals(other, result[1])
    }
    @Test fun privateFutureAndAlreadyCompletedRowsCannotBeCompletedFromAWidget() {
        listOf(TodoItem("private", "Private", space = SpaceKind.Private),
            TodoItem("future", "Tomorrow", dueAt = TodoTime.tomorrowStart(now) + 36_000_000),
            TodoItem("done", "Done", done = true)).forEach { row ->
            assertEquals(listOf(row), WidgetCommandRules.completeTask(listOf(row), WidgetTaskTarget.encode(row), now))
        }
    }
    @Test fun repeatAdvancesOnceAndAReplayedTapDoesNothing() {
        val row = TodoItem("repeat", "Water plants", dueAt = now - 86_400_000, repeat = TodoRepeat.Daily)
        val target = WidgetTaskTarget.encode(row)
        val advanced = WidgetCommandRules.completeTask(listOf(row), target, now)
        assertFalse(advanced.single().done)
        assertTrue(advanced.single().dueAt!! >= TodoTime.startOfDay(now))
        assertTrue(advanced.single().dueAt!! > row.dueAt!!)
        assertEquals(advanced, WidgetCommandRules.completeTask(advanced, target, now))
    }
    @Test fun focusControlsApplyOnlyToTheDisplayedSessionAndState() {
        val running = FocusSessionSnapshot(startedAt = now - 1000, until = now + 30_000)
        val target = running.startedAt.toString()
        assertTrue(WidgetCommandRules.canControlFocus(running, target, "PauseFocus", now))
        assertFalse(WidgetCommandRules.canControlFocus(running, target, "ResumeFocus", now))
        assertFalse(WidgetCommandRules.canControlFocus(running.copy(startedAt = now), target, "PauseFocus", now))
        assertFalse(WidgetCommandRules.canControlFocus(running, target, "PauseFocus", now + 60_000))
        val paused = running.copy(until = 0, pausedRemainingMs = 30_000)
        assertTrue(WidgetCommandRules.canControlFocus(paused, target, "ResumeFocus", now))
        assertFalse(WidgetCommandRules.canControlFocus(paused, target, "PauseFocus", now))
    }
}
