package com.lumen.launcher.voice.intent

import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.search.LauncherCommand

object VoiceIntentMapper {

    fun toCommand(intent: VoiceIntent): LauncherCommand? {
        if (!VoiceConfidence.shouldExecute(intent)) return null
        return when (intent.action) {
            VoiceAction.OPEN_APP -> intent.appName?.let { LauncherCommand.OpenApp(it) }
            VoiceAction.OPEN_FLOW -> LauncherCommand.OpenFlow
            VoiceAction.OPEN_HOME -> LauncherCommand.OpenHome
            VoiceAction.OPEN_DRAWER -> LauncherCommand.OpenDrawer
            VoiceAction.OPEN_SEARCH -> LauncherCommand.OpenSearch
            VoiceAction.OPEN_RECENTS -> LauncherCommand.OpenRecents
            VoiceAction.OPEN_SETTINGS -> LauncherCommand.OpenSettings
            VoiceAction.OPEN_PERSONALIZE -> LauncherCommand.OpenPersonalize
            VoiceAction.OPEN_PRIVATE -> LauncherCommand.OpenPrivate
            VoiceAction.ADD_TO_DOCK -> intent.appName?.let { LauncherCommand.Dock(it) }
            VoiceAction.REMOVE_FROM_DOCK -> intent.appName?.let { LauncherCommand.Undock(it) }
            VoiceAction.PIN_APP -> intent.appName?.let { LauncherCommand.Pin(it) }
            VoiceAction.UNPIN_APP -> intent.appName?.let { LauncherCommand.Unpin(it) }
            VoiceAction.HIDE_APP -> intent.appName?.let { LauncherCommand.HideApp(it) }
            VoiceAction.UNHIDE_APP -> intent.appName?.let { LauncherCommand.UnhideApp(it) }
            VoiceAction.MOVE_TO_PRIVATE -> intent.appName?.let { LauncherCommand.MoveToPrivate(it) }
            VoiceAction.REMOVE_FROM_PRIVATE -> intent.appName?.let { LauncherCommand.RemoveFromPrivate(it) }
            VoiceAction.WHERE_APP -> intent.appName?.let { LauncherCommand.WhereApp(it) }
            VoiceAction.LEARN_ALIAS -> {
                val alias = intent.textValue ?: return null
                val app = intent.appName ?: return null
                LauncherCommand.LearnAlias(alias, app)
            }
            VoiceAction.CREATE_FOLDER -> LauncherCommand.CreateFolder(
                name = intent.folderName,
                apps = extraApps(intent)
            )
            VoiceAction.ADD_TO_FOLDER -> {
                val app = intent.appName ?: return null
                val folder = intent.folderName ?: return null
                LauncherCommand.AddToFolder(app, folder, extraApps(intent))
            }
            VoiceAction.SET_GRID -> intent.intValue?.let { LauncherCommand.Grid(it) }
            VoiceAction.SET_ICON_SIZE -> {
                val value = intent.floatValue ?: return null
                if (intent.textValue == "absolute") LauncherCommand.IconSize(sizeDp = value)
                else LauncherCommand.IconSize(delta = value)
            }
            VoiceAction.SET_LABELS -> intent.enabled?.let { LauncherCommand.Labels(it) }
            VoiceAction.SET_DOCK_CAPACITY -> intent.intValue?.let { LauncherCommand.DockCapacity(it) }
            VoiceAction.SET_FLOW_MODULE -> {
                val module = intent.module ?: return null
                val on = intent.enabled ?: return null
                if (module == FlowModule.News) {
                    if (on) LauncherCommand.ShowNews else LauncherCommand.HideNews
                } else {
                    LauncherCommand.SetFlowCard(module, on)
                }
            }
            VoiceAction.MOVE_FLOW_MODULE -> {
                val module = intent.module ?: return null
                LauncherCommand.MoveFlowCard(module, intent.beforeModule)
            }
            VoiceAction.SET_SPACE -> LauncherCommand.SwitchSpace(intent.space)
            VoiceAction.WEATHER -> LauncherCommand.Weather
            VoiceAction.NEXT_EVENT -> LauncherCommand.NextEvent
            VoiceAction.NEED_NOW -> LauncherCommand.NeedNow
            VoiceAction.USED_YESTERDAY -> LauncherCommand.UsedYesterday
            VoiceAction.PURPOSE -> LauncherCommand.Purpose(intent.textValue ?: intent.originalText)
            VoiceAction.HELP -> LauncherCommand.Help
            VoiceAction.END_TALK -> LauncherCommand.EndTalk
            VoiceAction.SET_ALARM,
            VoiceAction.CANCEL_ALARM,
            VoiceAction.LIST_ALARMS,
            VoiceAction.SET_REMINDER,
            VoiceAction.SHOW_TASKS,
            VoiceAction.CALCULATE,
            VoiceAction.CLARIFY,
            VoiceAction.UNKNOWN -> null
        }
    }

    private fun extraApps(intent: VoiceIntent): List<String> {
        return intent.textValue
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }
}
