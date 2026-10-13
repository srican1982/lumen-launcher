package com.lumen.launcher.widgets

import com.lumen.launcher.data.*
import org.junit.Assert.*
import org.junit.Test

class TaskWidgetRulesTest {
    private val now = 1700000000000L
    private val task = TodoItem("one", "Call Steve", dueAt = now - 1000)
    @Test fun everyFilterExcludesPrivateTasks() {
        val hidden = task.copy(id = "secret", space = SpaceKind.Private, priority = true)
        TaskWidgetRules.filters.forEach { filter -> assertFalse(TaskWidgetRules.visible(listOf(task, hidden), filter, now).any { it.id == "secret" }) }
    }
    @Test fun inboxAndImportantAreDistinctWorkingFilters() {
        val inbox = task.copy(id = "inbox", dueAt = null)
        val important = task.copy(id = "priority", priority = true)
        val tasks = listOf(task, inbox, important)
        assertEquals(listOf(inbox), TaskWidgetRules.visible(tasks, "Inbox", now))
        assertEquals(listOf(important), TaskWidgetRules.visible(tasks, "Important", now))
    }
    @Test fun allSupportsCompletingFutureTasksAndRejectsStaleTargets() {
        val future = task.copy(dueAt = now + 86400000L)
        val completed = WidgetCommandRules.completeListedTask(listOf(future), WidgetTaskTarget.encode(future), now)
        assertTrue(completed.single().done)
        assertFalse(WidgetCommandRules.completeListedTask(listOf(future), WidgetTaskTarget.encode(task), now).single().done)
    }
    @Test fun fiveTaskStylesAreRegistered() {
        assertEquals(5, WidgetKind.entries.count { it.isTask })
        WidgetKind.entries.filter { it.isTask }.forEach { assertEquals(it, WidgetKind.parse(it.name)) }
    }
}
