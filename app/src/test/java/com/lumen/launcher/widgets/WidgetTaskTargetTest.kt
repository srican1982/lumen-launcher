package com.lumen.launcher.widgets

import com.lumen.launcher.data.TodoItem
import org.junit.Assert.*
import org.junit.Test

class WidgetTaskTargetTest {
    private val task = TodoItem("call-steve", "Call Steve", dueAt = 1000L, repeat = "daily")
    @Test fun oldTapCannotCompleteNextOccurrence() {
        val target = WidgetTaskTarget.encode(task)
        assertTrue(WidgetTaskTarget.matches(task, target))
        assertFalse(WidgetTaskTarget.matches(task.copy(dueAt = 2000L), target))
    }
    @Test fun completedOrDifferentTaskCannotBeReplayed() {
        val target = WidgetTaskTarget.encode(task)
        assertFalse(WidgetTaskTarget.matches(task.copy(done = true), target))
        assertFalse(WidgetTaskTarget.matches(task.copy(id = "other"), target))
    }
    @Test fun anytimeTasksRequireTheSameSchedule() {
        val anytime = task.copy(dueAt = null)
        val target = WidgetTaskTarget.encode(anytime)
        assertTrue(WidgetTaskTarget.matches(anytime, target))
        assertFalse(WidgetTaskTarget.matches(task, target))
    }
    @Test fun malformedTargetsDoNothing() {
        listOf("", "call-steve", "{}", "{\"id\":\"call-steve\"}", "{\"id\":\"call-steve\",\"due\":\"later\"}")
            .forEach { assertFalse(WidgetTaskTarget.matches(task, it)) }
    }
}
