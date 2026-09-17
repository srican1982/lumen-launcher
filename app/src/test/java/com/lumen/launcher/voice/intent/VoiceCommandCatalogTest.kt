package com.lumen.launcher.voice.intent

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.search.LauncherCommand
import com.lumen.launcher.search.LauncherVoice
import com.lumen.launcher.search.VoiceMatch
import org.junit.Test

class VoiceCommandCatalogTest {

    private val apps = VoiceCommandCatalog.installedApps

    private fun route(phrase: String) = VoiceQueryRouter.route(listOf(phrase), apps)

    @Test
    fun openGoToAndBareInstalledApps() {
        val expected = mapOf(
            "open ChatGPT" to "chatgpt",
            "open chat gpt" to "chatgpt",
            "go to ChatGPT" to "chatgpt",
            "open up PhotoLab" to "photolab",
            "launch photo lab" to "photolab",
            "start FXNow" to "fxnow",
            "run WhatsApp" to "whatsapp",
            "ChatGPT" to "chatgpt"
        )
        expected.forEach { (phrase, name) ->
            val intent = route(phrase)
            assertThat(intent.action).isEqualTo(VoiceAction.OPEN_APP)
            assertThat(intent.appName).isEqualTo(name)
            assertThat(VoiceConfidence.shouldExecute(intent)).isTrue()
        }
        assertThat(route("work").action).isEqualTo(VoiceAction.SET_SPACE)
        assertThat(route("work").space).isEqualTo(SpaceKind.Work)
        assertThat(VoiceMatch.best("work", apps)).isNull()

        val bank = VoiceQueryRouter.route(
            listOf("open Bank of America"),
            appLabels = apps + "BOA"
        )
        assertThat(bank.action).isEqualTo(VoiceAction.OPEN_APP)
        assertThat(bank.appName).isEqualTo("boa")
        assertThat(VoiceConfidence.shouldExecute(bank)).isTrue()
    }

    @Test
    fun homeLayoutPhrases() {
        assertThat(route("hide app labels").action).isEqualTo(VoiceAction.SET_LABELS)
        assertThat(route("hide app labels").enabled).isFalse()
        assertThat(route("show app labels").enabled).isTrue()
        assertThat(route("hide the names under the icons").enabled).isFalse()
        assertThat(route("make icons bigger").action).isEqualTo(VoiceAction.SET_ICON_SIZE)
        assertThat(route("make icons bigger").floatValue).isGreaterThan(0f)
        assertThat(route("make icons smaller").floatValue).isLessThan(0f)
        assertThat(route("use 5 columns").action).isEqualTo(VoiceAction.SET_GRID)
        assertThat(route("use 5 columns").intValue).isEqualTo(5)
        assertThat(route("dock size 4").action).isEqualTo(VoiceAction.SET_DOCK_CAPACITY)
        assertThat(route("dock size 4").intValue).isEqualTo(4)

        val dock = route("add WhatsApp to the dock")
        assertThat(dock.action).isEqualTo(VoiceAction.ADD_TO_DOCK)
        assertThat(dock.appName).isEqualTo("whatsapp")

        val pin = route("pin Outlook to home")
        assertThat(pin.action).isEqualTo(VoiceAction.PIN_APP)
        assertThat(pin.appName).isEqualTo("outlook")

        val unpin = route("remove WhatsApp from home")
        assertThat(unpin.action).isEqualTo(VoiceAction.UNPIN_APP)
        assertThat(unpin.destructive).isTrue()
        assertThat(VoiceConfidence.shouldExecute(unpin)).isFalse()

        val folder = route("create a folder called Work")
        assertThat(folder.action).isEqualTo(VoiceAction.CREATE_FOLDER)
        assertThat(folder.folderName).isEqualTo("work")

        val heardWrong = route("create a folder cold Work")
        assertThat(heardWrong.action).isEqualTo(VoiceAction.CREATE_FOLDER)
        assertThat(heardWrong.folderName).isEqualTo("work")
        assertThat(route("high app labels").action).isEqualTo(VoiceAction.SET_LABELS)
        assertThat(route("high app labels").enabled).isFalse()
        assertThat(route("what's the whether").action).isEqualTo(VoiceAction.WEATHER)
        assertThat(route("how cold is it").action).isEqualTo(VoiceAction.WEATHER)
        assertThat(route("open sitting").action).isEqualTo(VoiceAction.OPEN_SETTINGS)
        assertThat(route("hide app tables").action).isEqualTo(VoiceAction.SET_LABELS)

        val add = route("add Outlook to Work folder")
        assertThat(add.action).isEqualTo(VoiceAction.ADD_TO_FOLDER)
        assertThat(add.appName).isEqualTo("outlook")
        assertThat(add.folderName).isEqualTo("work")

        val many = route("create a Work folder and add Outlook, Teams and Chrome")
        assertThat(many.action).isEqualTo(VoiceAction.CREATE_FOLDER)
        assertThat(many.folderName).isEqualTo("work")
        assertThat(many.appName).isEqualTo("outlook")
        assertThat(many.textValue).contains("teams")

        val locked = route("move WhatsApp to locked space")
        assertThat(locked.action).isEqualTo(VoiceAction.MOVE_TO_PRIVATE)
        assertThat(locked.appName).isEqualTo("whatsapp")
        assertThat(locked.destructive).isTrue()
        assertThat(VoiceConfidence.shouldExecute(locked)).isFalse()

        val where = route("where is Outlook")
        assertThat(where.action).isEqualTo(VoiceAction.WHERE_APP)
        assertThat(where.appName).isEqualTo("outlook")

        val alias = route("remember yt as YouTube")
        assertThat(alias.action).isEqualTo(VoiceAction.LEARN_ALIAS)
        assertThat(alias.textValue).isEqualTo("yt")
        assertThat(alias.appName).contains("youtube")
    }

    @Test
    fun spacesAndFlowPhrases() {
        assertThat(route("go to work").space).isEqualTo(SpaceKind.Work)
        assertThat(route("switch to work").space).isEqualTo(SpaceKind.Work)
        assertThat(route("open work space").space).isEqualTo(SpaceKind.Work)
        assertThat(route("open personal space").space).isEqualTo(SpaceKind.Personal)
        assertThat(route("go to focus").space).isEqualTo(SpaceKind.Focus)
        assertThat(route("switch to home space").space).isEqualTo(SpaceKind.Home)
        assertThat(route("private space").action).isEqualTo(VoiceAction.OPEN_PRIVATE)
        assertThat(route("automatic space").action).isEqualTo(VoiceAction.SET_SPACE)
        assertThat(route("automatic space").space).isNull()

        assertThat(route("open flow").action).isEqualTo(VoiceAction.OPEN_FLOW)
        assertThat(route("what's on flow").action).isEqualTo(VoiceAction.OPEN_FLOW)
        assertThat(route("hide news").module).isEqualTo(FlowModule.News)
        assertThat(route("hide news").enabled).isFalse()
        assertThat(route("show news").enabled).isTrue()
        assertThat(route("turn inbox off").module).isEqualTo(FlowModule.Inbox)
        val moved = route("move weather above news")
        assertThat(moved.action).isEqualTo(VoiceAction.MOVE_FLOW_MODULE)
        assertThat(moved.module).isEqualTo(FlowModule.Weather)
        assertThat(moved.beforeModule).isEqualTo(FlowModule.News)
    }

    @Test
    fun dailyPhrases() {
        assertThat(route("what's the weather").action).isEqualTo(VoiceAction.WEATHER)
        assertThat(route("how's the weather").action).isEqualTo(VoiceAction.WEATHER)
        assertThat(route("weather").action).isEqualTo(VoiceAction.WEATHER)
        assertThat(route("what's next").action).isEqualTo(VoiceAction.NEXT_EVENT)
        assertThat(route("need now").action).isEqualTo(VoiceAction.NEED_NOW)
        assertThat(route("app I used yesterday").action).isEqualTo(VoiceAction.USED_YESTERDAY)

        val alarm = route("set alarm for 7")
        assertThat(alarm.action).isEqualTo(VoiceAction.SET_ALARM)
        assertThat(alarm.hour).isNotNull()
        assertThat(route("show my alarms").action).isEqualTo(VoiceAction.LIST_ALARMS)

        val reminder = route("remind me to call Steve")
        assertThat(reminder.action).isEqualTo(VoiceAction.SET_REMINDER)
        assertThat(reminder.textValue).contains("call")
        assertThat(route("what's on my list").action).isEqualTo(VoiceAction.SHOW_TASKS)

        val math = route("what is 18 percent of 86")
        assertThat(math.action).isEqualTo(VoiceAction.CALCULATE)
        assertThat(math.textValue).contains("15.48")

        assertThat(route("help").action).isEqualTo(VoiceAction.HELP)
        assertThat(route("what can I say").action).isEqualTo(VoiceAction.HELP)
        assertThat(route("goodbye").action).isEqualTo(VoiceAction.END_TALK)
    }

    @Test
    fun catalogLinesAllResolve() {
        val unknown = (
            VoiceCommandCatalog.openApp +
                VoiceCommandCatalog.home +
                VoiceCommandCatalog.spaces +
                VoiceCommandCatalog.flow +
                VoiceCommandCatalog.daily
            ).filter { route(it).action == VoiceAction.UNKNOWN }
        assertThat(unknown).isEmpty()
    }

    @Test
    fun mapperStillProducesLauncherCommands() {
        assertThat(LauncherVoice.parse("open Flow")).isEqualTo(LauncherCommand.OpenFlow)
        assertThat(LauncherVoice.parse("hide app labels")).isEqualTo(LauncherCommand.Labels(false))
        assertThat(LauncherVoice.parse("go to ChatGPT")).isEqualTo(LauncherCommand.OpenApp("chatgpt"))
    }
}
