package com.lumen.launcher.vm

import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.data.SpaceKind

/** Feature snapshots prevent unrelated weather/voice changes from invalidating the task list. */
data class TasksUiState(val todos: List<TodoItem> = emptyList(), val activeSpace: SpaceKind = SpaceKind.Home, val duePickerTodoId: String = "")
data class VoiceUiState(val voiceHeard: String = "", val voiceHint: String = "", val voiceLevel: Float = 0f, val voiceListening: Boolean = false, val voiceCanRetry: Boolean = false, val voiceAlternatives: List<String> = emptyList(), val voicePending: com.lumen.launcher.voice.intent.VoiceIntent? = null, val questionToCopy: String = "", val openedApp: String = "", val returnVisible: Boolean = false)
