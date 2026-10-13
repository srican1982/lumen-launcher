package com.lumen.launcher.vm

import android.content.Intent
import com.lumen.launcher.inbox.InboxItem
import com.lumen.launcher.data.MissedCall

/** One-time commands consumed by the current resumed Activity; never stores an Activity. */
sealed interface LauncherEvent {
    data class Authenticate(val success: () -> Unit, val failure: () -> Unit) : LauncherEvent
    enum class Permission : LauncherEvent { Contacts, MediaImages, Calendar, CallLog, Notifications, Mic, MicQuiet, Assistant }
    data class Open(val intent: Intent) : LauncherEvent
    data class Conversation(val item: InboxItem) : LauncherEvent
    data class Missed(val call: MissedCall, val fallback: () -> Unit) : LauncherEvent
    data class Speak(val text: String, val listen: Boolean) : LauncherEvent
    data object Listen : LauncherEvent
    data object StopVoice : LauncherEvent
    data class Confirm(val title: String, val message: String, val action: () -> Unit) : LauncherEvent
}
