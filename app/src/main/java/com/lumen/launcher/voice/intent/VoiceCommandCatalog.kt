package com.lumen.launcher.voice.intent

/**
 * Spoken lines Lumen should accept. This is the contract for routing tests.
 * App names stay generic: match whatever is installed, never a per-app alias.
 */
internal object VoiceCommandCatalog {

    val installedApps = listOf("Word", "WhatsApp", "PhotoLab", "FXNow", "ChatGPT", "Outlook", "YouTube")

    val openApp = listOf(
        "open ChatGPT",
        "open chat gpt",
        "go to ChatGPT",
        "open up PhotoLab",
        "launch photo lab",
        "start FXNow",
        "run WhatsApp",
        "ChatGPT"
    )

    val home = listOf(
        "hide app labels",
        "show app labels",
        "hide the names under the icons",
        "make icons bigger",
        "make icons smaller",
        "use 5 columns",
        "dock size 4",
        "add WhatsApp to the dock",
        "pin Outlook to home",
        "remove WhatsApp from home",
        "create a folder called Work",
        "add Outlook to Work folder",
        "create a Work folder and add Outlook, Teams and Chrome",
        "move WhatsApp to locked space",
        "where is Outlook",
        "remember yt as YouTube"
    )

    val spaces = listOf(
        "work",
        "go to work",
        "switch to work",
        "open work space",
        "personal",
        "open personal space",
        "go to focus",
        "switch to home space",
        "private space",
        "automatic space"
    )

    val flow = listOf(
        "open flow",
        "what's on flow",
        "hide news",
        "show news",
        "turn inbox off",
        "move weather above news"
    )

    val daily = listOf(
        "what's the weather",
        "how's the weather",
        "weather",
        "what's next",
        "need now",
        "app I used yesterday",
        "set alarm for 7",
        "show my alarms",
        "remind me to call Steve",
        "what's on my list",
        "what is 18 percent of 86",
        "help",
        "what can I say",
        "goodbye"
    )
}
