package com.lumen.launcher.voice.intent

import org.junit.Assert.*
import org.junit.Test
import com.lumen.launcher.search.LauncherCommand

class VoiceIntentMapperTest {
    @Test fun mapsTrustedAppWithoutCreatingArbitraryIntent() {
        assertEquals(LauncherCommand.OpenApp("Chrome"), VoiceIntentMapper.toCommand(VoiceIntent(VoiceAction.OPEN_APP, 1f, "open Chrome", appName="Chrome")))
    }
    @Test fun refusesDestructiveCommandWithoutConfirmation() {
        assertNull(VoiceIntentMapper.toCommand(VoiceIntent(VoiceAction.MOVE_TO_PRIVATE, 1f, "hide Chrome", appName="Chrome", destructive=true)))
    }
    @Test fun rejectsUncertainAndIncompleteRequests() {
        assertNull(VoiceIntentMapper.toCommand(VoiceIntent(VoiceAction.OPEN_APP, .3f, "maybe Chrome", appName="Chrome")))
        assertNull(VoiceIntentMapper.toCommand(VoiceIntent(VoiceAction.OPEN_APP, 1f, "open")))
    }
}
