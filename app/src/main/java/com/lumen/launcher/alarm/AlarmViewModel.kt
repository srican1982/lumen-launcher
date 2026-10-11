package com.lumen.launcher.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.launcher.data.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlarmUiState(val alarms: List<LumenAlarm> = emptyList(), val tone: String = AlarmTones.AURA)

/** Alarm persistence, scheduling and state are independent of Home/Flow/voice UI. */
class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = LauncherPreferences(application)
    private val operations = kotlinx.coroutines.channels.Channel<suspend () -> Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val pendingWrites = java.util.concurrent.atomic.AtomicInteger(0)
    private val _state = MutableStateFlow(AlarmUiState(AlarmStore.read(application), AlarmStore.tone(application)))
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (operation in operations) {
                try { operation() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) {
                    _state.value = AlarmUiState(AlarmStore.read(application), AlarmStore.tone(application))
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(application, "The alarm could not be saved. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                    }
                } finally {
                    if (pendingWrites.decrementAndGet() == 0) _state.value = AlarmUiState(AlarmStore.read(application), AlarmStore.tone(application))
                }
            }
        }
        viewModelScope.launch {
            preferences.state.map { AlarmUiState(it.alarms, it.alarmTone) }.distinctUntilChanged().collect { stored ->
                if (pendingWrites.get() == 0) {
                    _state.value = stored
                    viewModelScope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(application) }
                }
            }
        }
    }
    private fun save(next: List<LumenAlarm>) {
        _state.update { it.copy(alarms = next) }
        pendingWrites.incrementAndGet()
        operations.trySend {
            val removed = AlarmStore.read(getApplication()).filterNot { alarm -> next.any { it.id == alarm.id } }
            preferences.setAlarms(next)
            removed.forEach { AlarmScheduler.cancel(getApplication(), it.id) }
            AlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun add(hour: Int, minute: Int, daily: Boolean, label: String): LumenAlarm {
        require(hour in 0..23 && minute in 0..59)
        val existing = _state.value.alarms.find { it.hour == hour && it.minute == minute && it.triggerAtMs == null }
        val alarm = (existing ?: LumenAlarm(hour = hour, minute = minute)).copy(
            enabled = true, daily = daily || existing?.daily == true, label = label.ifBlank { existing?.label.orEmpty() })
        save(_state.value.alarms.filterNot { it.id == alarm.id } + alarm)
        return alarm
    }
    fun addAt(hour: Int, minute: Int, deadline: Long): LumenAlarm {
        require(hour in 0..23 && minute in 0..59)
        val alarm = LumenAlarm(hour = hour, minute = minute, triggerAtMs = deadline)
        save(_state.value.alarms + alarm)
        return alarm
    }
    fun addTimer(minutes: Int, now: Long = System.currentTimeMillis()): LumenAlarm {
        require(minutes in 1..1440)
        return addTimerSeconds(minutes * 60, now)
    }
    fun addTimerSeconds(seconds: Int, now: Long = System.currentTimeMillis()): LumenAlarm {
        val deadline = AlarmDeadline.timer(seconds, now)
        val at = java.time.Instant.ofEpochMilli(deadline).atZone(java.time.ZoneId.systemDefault())
        val alarm = LumenAlarm(hour = at.hour, minute = at.minute, label = "Timer", triggerAtMs = deadline)
        save(_state.value.alarms + alarm)
        return alarm
    }
    fun toggle(id: String) { save(_state.value.alarms.map { if (it.id == id) it.copy(enabled = !it.enabled, triggerAtMs = if (!it.enabled) null else it.triggerAtMs) else it }) }
    fun delete(id: String) {
        AlarmScheduler.cancel(getApplication(), id)
        save(_state.value.alarms.filterNot { it.id == id })
    }
    fun cancel(hour: Int?, minute: Int?): Int {
        val victims = _state.value.alarms.filter { if (hour == null) it.enabled else it.hour == hour && (minute == null || it.minute == minute) }
        victims.forEach { AlarmScheduler.cancel(getApplication(), it.id) }
        save(_state.value.alarms.filterNot { a -> victims.any { it.id == a.id } })
        return victims.size
    }
    fun cycleTone() {
        val tone = AlarmTones.next(_state.value.tone)
        _state.update { it.copy(tone = tone) }
        pendingWrites.incrementAndGet()
        operations.trySend { preferences.setAlarmTone(tone); AlarmScheduler.rescheduleAll(getApplication()) }
        AlarmTonePlayer.preview(getApplication(), tone)
    }
    override fun onCleared() { operations.close(); super.onCleared() }
    fun upcoming() = _state.value.alarms.filter { it.enabled }.sortedBy { it.nextTriggerMs() }
}
