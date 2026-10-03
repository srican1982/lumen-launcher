package com.lumen.launcher.focus

import org.junit.Assert.*
import org.junit.Test

class FocusReachToggleTest {
    @Test fun togglingOneChannelPreservesTheOther() {
        FocusReach.entries.forEach { original ->
            val calls = original.toggleCalls()
            assertEquals(!original.allowsCalls, calls.allowsCalls)
            assertEquals(original.allowsMessages, calls.allowsMessages)
            assertEquals(original, calls.toggleCalls())
            val messages = original.toggleMessages()
            assertEquals(original.allowsCalls, messages.allowsCalls)
            assertEquals(!original.allowsMessages, messages.allowsMessages)
            assertEquals(original, messages.toggleMessages())
        }
    }
    @Test fun bothChannelsCanBeDisabled() {
        val off = FocusReach.CallsAndMessages.toggleCalls().toggleMessages()
        assertEquals(FocusReach.Neither, off)
        assertFalse(off.allowsCalls)
        assertFalse(off.allowsMessages)
    }
}
