package com.lumen.launcher.search

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.data.SpaceKind
import org.junit.Test

class LauncherVoiceTest {

    @Test
    fun opensFlow() {
        assertThat(LauncherVoice.parse("open Flow")).isEqualTo(LauncherCommand.OpenFlow)
        assertThat(LauncherVoice.parse("what's on flow")).isEqualTo(LauncherCommand.OpenFlow)
    }

    @Test
    fun opensRecents() {
        assertThat(LauncherVoice.parse("show recents panel")).isEqualTo(LauncherCommand.OpenRecents)
    }

    @Test
    fun doesNotStealNavigateHome() {
        assertThat(LauncherVoice.parse("take me home")).isNull()
    }

    @Test
    fun switchesWorkLayout() {
        assertThat(LauncherVoice.parse("switch to work layout"))
            .isEqualTo(LauncherCommand.SwitchSpace(SpaceKind.Work))
    }

    @Test
    fun hidesNewsFromFlow() {
        assertThat(LauncherVoice.parse("hide news from Flow")).isEqualTo(LauncherCommand.HideNews)
    }

    @Test
    fun pinsAppToHome() {
        assertThat(LauncherVoice.parse("pin WhatsApp")).isEqualTo(LauncherCommand.Pin("whatsapp"))
    }

    @Test
    fun asksWhatIUseNow() {
        assertThat(LauncherVoice.parse("what do I normally use now")).isEqualTo(LauncherCommand.NeedNow)
    }

    @Test
    fun yesterdayApp() {
        assertThat(LauncherVoice.parse("app I used yesterday")).isEqualTo(LauncherCommand.UsedYesterday)
    }

    @Test
    fun bankingApps() {
        assertThat(LauncherVoice.parse("banking apps")).isInstanceOf(LauncherCommand.Purpose::class.java)
    }

    @Test
    fun opensWhatsApp() {
        assertThat(LauncherVoice.parse("open WhatsApp"))
            .isEqualTo(LauncherCommand.OpenApp("whatsapp"))
        assertThat(LauncherVoice.parse("launch whatsapp"))
            .isEqualTo(LauncherCommand.OpenApp("whatsapp"))
    }

    @Test
    fun noEndsTheConversation() {
        assertThat(LauncherVoice.parse("no")).isEqualTo(LauncherCommand.EndTalk)
        assertThat(LauncherVoice.parse("no thanks")).isEqualTo(LauncherCommand.EndTalk)
    }
}
