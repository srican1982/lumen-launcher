package com.lumen.launcher.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechTurnTest {
    @Test fun oldReplyCannotFinishNewReply() {
        val turns = SpeechTurn()
        val old = turns.begin()
        val current = turns.begin()
        assertFalse(turns.complete(old))
        assertTrue(turns.complete(current))
        assertFalse(turns.complete(current))
    }

    @Test fun cancelledReplyCannotRestartListening() {
        val turns = SpeechTurn()
        val id = turns.begin()
        turns.cancel()
        assertFalse(turns.complete(id))
        assertFalse(turns.complete(null))
        assertTrue(turns.complete(turns.begin()))
    }
}
