package com.lumen.launcher.voice.intent

import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.FlowModule

enum class VoiceAction {
    OPEN_APP,
    OPEN_FLOW,
    OPEN_HOME,
    OPEN_DRAWER,
    OPEN_SEARCH,
    OPEN_RECENTS,
    OPEN_SETTINGS,
    OPEN_PERSONALIZE,
    OPEN_PRIVATE,
    ADD_TO_DOCK,
    REMOVE_FROM_DOCK,
    PIN_APP,
    UNPIN_APP,
    HIDE_APP,
    UNHIDE_APP,
    MOVE_TO_PRIVATE,
    REMOVE_FROM_PRIVATE,
    WHERE_APP,
    LEARN_ALIAS,
    CREATE_FOLDER,
    ADD_TO_FOLDER,
    SET_GRID,
    SET_ICON_SIZE,
    SET_LABELS,
    SET_DOCK_CAPACITY,
    SET_FLOW_MODULE,
    MOVE_FLOW_MODULE,
    SET_SPACE,
    SET_ALARM,
    CANCEL_ALARM,
    LIST_ALARMS,
    SET_REMINDER,
    SHOW_TASKS,
    CALCULATE,
    WEATHER,
    NEXT_EVENT,
    NEED_NOW,
    USED_YESTERDAY,
    PURPOSE,
    HELP,
    END_TALK,
    CLARIFY,
    UNKNOWN
}

data class VoiceIntent(
    val action: VoiceAction,
    val confidence: Float,
    val originalText: String,
    val appName: String? = null,
    val folderName: String? = null,
    val module: FlowModule? = null,
    val beforeModule: FlowModule? = null,
    val space: SpaceKind? = null,
    val enabled: Boolean? = null,
    val intValue: Int? = null,
    val floatValue: Float? = null,
    val textValue: String? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val daily: Boolean = false,
    val clarify: List<String> = emptyList(),
    val alternatives: List<VoiceIntent> = emptyList(),
    val destructive: Boolean = false
) {
    val key: String
        get() = listOf(
            action.name,
            appName,
            folderName,
            module?.name,
            beforeModule?.name,
            space?.name,
            enabled?.toString(),
            intValue?.toString(),
            floatValue?.toString(),
            textValue,
            hour?.toString(),
            minute?.toString(),
            daily.toString(),
            destructive.toString()
        ).joinToString("|")
}

object VoiceConfidence {
    const val EXECUTE = 0.95f
    const val SAFE_EXECUTE = 0.75f
    const val ASK = 0.50f

    fun shouldExecute(intent: VoiceIntent): Boolean {
        if (intent.action == VoiceAction.CLARIFY || intent.action == VoiceAction.UNKNOWN) return false
        if (intent.destructive) return false
        if (intent.confidence >= EXECUTE) return true
        if (intent.confidence >= SAFE_EXECUTE) return true
        return false
    }

    fun shouldAsk(intent: VoiceIntent): Boolean {
        if (intent.action == VoiceAction.CLARIFY) return true
        if (intent.action == VoiceAction.UNKNOWN) return false
        return intent.confidence >= ASK && !shouldExecute(intent)
    }
}
