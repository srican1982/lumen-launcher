package com.lumen.launcher.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceCommandsTest {

    @Test
    fun setsAlarmForSevenAm() {
        val command = VoiceCommands.alarmCommand("set an alarm for 7 am")
        assertThat(command).isEqualTo(VoiceCommands.AlarmCommand.Set(7, 0, false))
    }

    @Test
    fun setsDailyAlarm() {
        val command = VoiceCommands.alarmCommand("wake me every day at 6:30 a.m.")
        assertThat(command).isEqualTo(VoiceCommands.AlarmCommand.Set(6, 30, true))
    }

    @Test
    fun listsAlarms() {
        assertThat(VoiceCommands.alarmCommand("what alarms do I have"))
            .isEqualTo(VoiceCommands.AlarmCommand.List)
    }

    @Test
    fun cancelsNamedAlarm() {
        val command = VoiceCommands.alarmCommand("cancel the 7 am alarm")
        assertThat(command).isEqualTo(VoiceCommands.AlarmCommand.Cancel(7, 0))
    }

    @Test
    fun ignoresPlainReminders() {
        assertThat(VoiceCommands.alarmCommand("remind me to call the dentist")).isNull()
    }
}
