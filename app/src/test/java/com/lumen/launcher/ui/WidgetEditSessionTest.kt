package com.lumen.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class WidgetEditSessionTest {
    @Test fun pagingRestoresOnlyAfterLastSelectionCloses() {
        val session = WidgetEditSession()
        val first = Any()
        val second = Any()
        assertFalse(session.active)
        session.acquire(first)
        session.acquire(second)
        assertTrue(session.active)
        session.release(first)
        assertTrue(session.active)
        session.release(second)
        assertFalse(session.active)
    }
    @Test fun duplicateCleanupDoesNotKeepPagerLocked() {
        val session = WidgetEditSession()
        val owner = Any()
        session.acquire(owner)
        session.acquire(owner)
        session.release(owner)
        session.release(owner)
        assertFalse(session.active)
    }
    @Test fun exclusiveSelectionAllowsOnlyWidgetAndResizeMargin() {
        val session = WidgetEditSession()
        val owner = Any()
        session.acquire(owner)
        session.updateBounds(owner, androidx.compose.ui.geometry.Rect(100f, 100f, 300f, 300f))
        assertTrue(session.allows(androidx.compose.ui.geometry.Offset(90f, 200f)))
        assertFalse(session.allows(androidx.compose.ui.geometry.Offset(20f, 20f)))
        session.release(owner)
        assertTrue(session.allows(androidx.compose.ui.geometry.Offset.Zero))
    }
    @Test fun arrangementPageKeepsItsOwnToolbarAvailable() {
        val session = WidgetEditSession()
        session.acquire(Any(), exclusive = false)
        assertTrue(session.active)
        assertFalse(session.exclusive)
        assertTrue(session.allows(androidx.compose.ui.geometry.Offset.Zero))
    }

}
