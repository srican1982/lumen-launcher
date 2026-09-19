package com.lumen.launcher.search

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.data.TodoRepeat
import com.lumen.launcher.data.TodoTime
import org.junit.Test
import java.util.Calendar

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
    fun addsFromListPhrase() {
        assertThat(VoiceCommands.task("add milk to my list")).isEqualTo("milk")
        assertThat(VoiceCommands.task("add WhatsApp to the dock")).isNull()
    }

    @Test
    fun voiceTaskDefaultsToAnytime() {
        val now = 1_700_000_000_000L
        assertThat(VoiceCommands.task("remind me to call Steve")).isEqualTo("call Steve")
        assertThat(TodoTime.hasClock(VoiceCommands.taskDue("remind me to call Steve", now)))
            .isFalse()
        assertThat(VoiceCommands.task("remind me to buy milk anytime")).isEqualTo("buy milk")
        assertThat(TodoTime.hasClock(VoiceCommands.taskDue("remind me to buy milk anytime", now)))
            .isFalse()
    }

    @Test
    fun voiceTaskCanBeHighPriority() {
        assertThat(VoiceCommands.task("remind me to review proposal high priority")).isEqualTo("review proposal")
        assertThat(VoiceCommands.taskPriority("remind me to review proposal high priority")).isTrue()
        assertThat(VoiceCommands.taskPriority("remind me to buy milk")).isFalse()
    }

    @Test
    fun voiceTaskCanRepeatWeekly() {
        assertThat(VoiceCommands.task("remind me to take trash out every Tuesday")).isEqualTo("take trash out")
        assertThat(VoiceCommands.taskRepeat("remind me to take trash out every Tuesday")).isEqualTo(TodoRepeat.Weekly)
        val monday = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 14, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val due = VoiceCommands.taskDue("remind me to take trash out every Tuesday", monday)
        val cal = Calendar.getInstance().apply { timeInMillis = due }
        assertThat(cal.get(Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.TUESDAY)
    }

    @Test
    fun voiceTaskKeepsSpokenTime() {
        val now = 1_700_000_000_000L
        assertThat(VoiceCommands.task("remind me to call Steve at 3 pm")).isEqualTo("call Steve")
        val due = VoiceCommands.taskDue("remind me to call Steve at 3 pm", now)
        assertThat(TodoTime.hasClock(due)).isTrue()
        assertThat(TodoTime.hourOf(due)).isEqualTo(15)
    }

    @Test
    fun completesAndDeletesTasks() {
        assertThat(VoiceCommands.completeTask("I'm done with call Steve")).isEqualTo("call Steve")
        assertThat(VoiceCommands.completeTask("im done with call Steve")).isEqualTo("call Steve")
        assertThat(VoiceCommands.completeTask("I am done with this task")).isEqualTo("this task")
        assertThat(VoiceCommands.deleteTask("delete the task call Steve")).isEqualTo("call Steve")
        assertThat(VoiceCommands.deleteTask("remove WhatsApp from home")).isNull()
    }

    @Test
    fun capturesNotePhrase() {
        assertThat(VoiceCommands.note("note that parking is level B")).isEqualTo("parking is level B")
        assertThat(VoiceCommands.note("jot down call back later")).isEqualTo("call back later")
        assertThat(VoiceCommands.note("open maps")).isNull()
    }

    @Test
    fun pinsForLaterAndReviewsDay() {
        assertThat(VoiceCommands.saveLater("save this for later")).isTrue()
        assertThat(VoiceCommands.dailyReview("what's left today")).isTrue()
        assertThat(VoiceCommands.dailyReview("open maps")).isFalse()
    }

    @Test
    fun startsAndEndsFocus() {
        assertThat(VoiceCommands.focusMinutes("focus 30 minutes")).isEqualTo(30)
        assertThat(VoiceCommands.focusMinutes("start focus")).isEqualTo(30)
        assertThat(VoiceCommands.focusMinutes("end focus")).isEqualTo(0)
        assertThat(VoiceCommands.focusMinutes("open maps")).isNull()
    }
}
