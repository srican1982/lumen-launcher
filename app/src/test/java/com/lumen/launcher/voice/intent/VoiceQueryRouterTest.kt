package com.lumen.launcher.voice.intent

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.search.LauncherCommand
import com.lumen.launcher.search.LauncherVoice
import org.junit.Test

class VoiceQueryRouterTest {

    @Test
    fun installedYesterdayIsNotUsedYesterday() {
        val intent = VoiceQueryRouter.route(listOf("open the app I installed yesterday"))
        assertThat(intent.action).isNotEqualTo(VoiceAction.USED_YESTERDAY)
    }

    @Test
    fun usedYesterdayStillWorks() {
        val intent = VoiceQueryRouter.route(listOf("app I used yesterday"))
        assertThat(intent.action).isEqualTo(VoiceAction.USED_YESTERDAY)
        assertThat(intent.confidence).isAtLeast(0.95f)
    }

    @Test
    fun takeMeHomeIsNotOpenHome() {
        val intent = VoiceQueryRouter.route(listOf("take me home"))
        assertThat(intent.action).isEqualTo(VoiceAction.UNKNOWN)
        assertThat(LauncherVoice.parse("take me home")).isNull()
    }

    @Test
    fun namesUnderIconsHidesLabels() {
        val intent = VoiceQueryRouter.route(listOf("get rid of the names under the icons"))
        assertThat(intent.action).isEqualTo(VoiceAction.SET_LABELS)
        assertThat(intent.enabled).isFalse()
    }

    @Test
    fun crowdedHomeAsksInsteadOfGuessing() {
        val intent = VoiceQueryRouter.route(listOf("make my home screen less crowded"))
        assertThat(intent.action).isEqualTo(VoiceAction.CLARIFY)
        assertThat(intent.clarify).isNotEmpty()
    }

    @Test
    fun workIsSpaceNotAnAppGuess() {
        val intent = VoiceQueryRouter.route(listOf("work"), appLabels = listOf("Word", "WhatsApp"))
        assertThat(intent.action).isEqualTo(VoiceAction.SET_SPACE)
        assertThat(intent.space).isEqualTo(SpaceKind.Work)
    }

    @Test
    fun personalAndFocusSpaceAreNotApps() {
        assertThat(VoiceQueryRouter.route(listOf("open personal space")).action)
            .isEqualTo(VoiceAction.SET_SPACE)
        assertThat(VoiceQueryRouter.route(listOf("open focus space")).action)
            .isEqualTo(VoiceAction.SET_SPACE)
        assertThat(VoiceQueryRouter.route(listOf("launch the personal space")).action)
            .isEqualTo(VoiceAction.SET_SPACE)
    }

    @Test
    fun destructiveCommandsAlwaysAsk() {
        val intent = VoiceQueryRouter.route(listOf("remove WhatsApp from home"))
        assertThat(intent.action).isEqualTo(VoiceAction.UNPIN_APP)
        assertThat(intent.destructive).isTrue()
        assertThat(VoiceConfidence.shouldExecute(intent)).isFalse()
        assertThat(VoiceConfidence.shouldAsk(intent)).isTrue()
        assertThat(VoiceIntentMapper.toCommand(intent)).isNull()
    }

    @Test
    fun transcriptOrderBreaksEqualConfidenceTie() {
        val intent = VoiceQueryRouter.route(listOf("show app labels", "hide app labels"))
        assertThat(intent.action).isEqualTo(VoiceAction.SET_LABELS)
        assertThat(intent.enabled).isTrue()
    }

    @Test
    fun clarificationCanSelectAndCancel() {
        val pending = VoiceQueryRouter.route(listOf("make my home screen less crowded"))
        val selected = ClarificationResolver.resolve(listOf("second one"), pending)
        assertThat(selected).isInstanceOf(ClarificationResolution.Execute::class.java)
        assertThat((selected as ClarificationResolution.Execute).intent.action)
            .isEqualTo(VoiceAction.SET_GRID)
        assertThat(ClarificationResolver.resolve(listOf("no"), pending))
            .isEqualTo(ClarificationResolution.Cancel)
        assertThat(ClarificationResolver.resolve(listOf("thanks"), pending))
            .isEqualTo(ClarificationResolution.End)
    }

    @Test
    fun clarificationCanCollectMissingApp() {
        val pending = VoiceQueryRouter.route(
            listOf("put it down at the bottom with my other main apps"),
            appLabels = listOf("WhatsApp", "Word")
        )
        assertThat(pending.action).isEqualTo(VoiceAction.CLARIFY)
        val answer = ClarificationResolver.resolve(
            listOf("WhatsApp"),
            pending,
            appLabels = listOf("WhatsApp", "Word")
        )
        assertThat(answer).isInstanceOf(ClarificationResolution.Execute::class.java)
        val intent = (answer as ClarificationResolution.Execute).intent
        assertThat(intent.action).isEqualTo(VoiceAction.ADD_TO_DOCK)
        assertThat(intent.appName).isEqualTo("whatsapp")
    }

    @Test
    fun clarificationCannotSwapToDifferentApp() {
        val pending = VoiceIntent(
            VoiceAction.CLARIFY,
            0.97f,
            "remove WhatsApp from home",
            alternatives = listOf(
                VoiceIntent(
                    VoiceAction.UNPIN_APP,
                    0.97f,
                    "remove WhatsApp from home",
                    appName = "whatsapp",
                    destructive = true
                )
            )
        )
        val result = ClarificationResolver.resolve(
            listOf("remove Telegram from home"),
            pending,
            appLabels = listOf("WhatsApp", "Telegram")
        )
        assertThat(result).isEqualTo(ClarificationResolution.NoMatch)
    }

    @Test
    fun fuzzyBareAppRequiresConfirmation() {
        val intent = VoiceQueryRouter.route(listOf("outloo"), appLabels = listOf("Outlook"))
        assertThat(intent.action).isEqualTo(VoiceAction.OPEN_APP)
        assertThat(VoiceConfidence.shouldExecute(intent)).isFalse()
        assertThat(VoiceConfidence.shouldAsk(intent)).isTrue()
    }

    @Test
    fun newlyInstalledAppsAreMatchedFromTheLiveCatalog() {
        val installed = listOf("Word", "WhatsApp", "PhotoLab", "FXNow", "ChatGPT")
        listOf(
            "open PhotoLab" to "photolab",
            "open photo lab" to "photolab",
            "open FXNow" to "fxnow",
            "open fx now" to "fxnow",
            "open f x now" to "fxnow",
            "open ChatGPT" to "chatgpt",
            "open chat gpt" to "chatgpt",
            "open chat g p t" to "chatgpt"
        ).forEach { (phrase, expected) ->
            val intent = VoiceQueryRouter.route(
                candidates = listOf(phrase, "show app labels"),
                appLabels = installed
            )
            assertThat(intent.action).isEqualTo(VoiceAction.OPEN_APP)
            assertThat(intent.appName).isEqualTo(expected)
            assertThat(VoiceConfidence.shouldExecute(intent)).isTrue()
        }
    }

    @Test
    fun spokenMathAndConversionsStayLocal() {
        val math = VoiceQueryRouter.route(listOf("what is 18 percent of 86"))
        assertThat(math.action).isEqualTo(VoiceAction.CALCULATE)
        assertThat(math.textValue).contains("15.48")

        val conversion = VoiceQueryRouter.route(listOf("convert 10 miles to kilometers"))
        assertThat(conversion.action).isEqualTo(VoiceAction.CALCULATE)
        assertThat(conversion.textValue).contains("16.09 kilometers")
    }

    @Test
    fun existingCommandsStillParse() {
        assertThat(LauncherVoice.parse("open Flow")).isEqualTo(LauncherCommand.OpenFlow)
        assertThat(LauncherVoice.parse("show recents panel")).isEqualTo(LauncherCommand.OpenRecents)
        assertThat(LauncherVoice.parse("open WhatsApp")).isEqualTo(LauncherCommand.OpenApp("whatsapp"))
        assertThat(LauncherVoice.parse("turn inbox off"))
            .isEqualTo(LauncherCommand.SetFlowCard(FlowModule.Inbox, false))
        assertThat(LauncherVoice.parse("hide app labels")).isEqualTo(LauncherCommand.Labels(false))
        assertThat(LauncherVoice.parse("add Outlook to Work folder"))
            .isEqualTo(LauncherCommand.AddToFolder("outlook", "work"))
    }
}
