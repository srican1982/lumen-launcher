package com.lumen.launcher.widgets

import org.junit.Assert.*
import org.junit.Test

class MediaWidgetKindsTest {
    @Test fun fourMediaPlayerWidgets() {
        val media = WidgetKind.entries.filter { it.isMedia }
        assertEquals(4, media.size)
        assertEquals(
            listOf(
                WidgetKind.MediaContinue,
                WidgetKind.MediaPlayer,
                WidgetKind.MediaNowPlaying,
                WidgetKind.MediaShortcuts
            ),
            media
        )
        assertTrue(WidgetKind.MediaPlayer.wide)
        assertFalse(WidgetKind.MediaContinue.wide)
    }

    @Test fun mediaTransportCommandsAllowed() {
        listOf(WidgetKind.MediaContinue, WidgetKind.MediaPlayer, WidgetKind.MediaNowPlaying).forEach {
            assertTrue(WidgetCommandReceiver.allowed(it, "PlayPause"))
            assertTrue(WidgetCommandReceiver.allowed(it, "MediaNext"))
            assertTrue(WidgetCommandReceiver.allowed(it, "MediaPrevious"))
            assertFalse(WidgetCommandReceiver.allowed(it, "StartTrip"))
        }
        assertFalse(WidgetCommandReceiver.allowed(WidgetKind.MediaShortcuts, "PlayPause"))
    }
}
