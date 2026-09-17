package com.lumen.launcher.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FuzzySearchTest {

    @Test
    fun prefixBeatsLaterSubstring() {
        val whatsapp = FuzzySearch.score("wh", "WhatsApp")
        val youtube = FuzzySearch.score("wh", "YouTube")
        assertThat(whatsapp).isGreaterThan(youtube)
        assertThat(whatsapp).isAtLeast(800)
    }

    @Test
    fun typoStillFindsWhatsApp() {
        val score = FuzzySearch.score("watsap", "WhatsApp")
        assertThat(score).isAtLeast(300)
        assertThat(FuzzySearch.score("watsap", "Settings")).isLessThan(score)
    }

    @Test
    fun bankFindsBankingApps() {
        assertThat(FuzzySearch.score("bank", "Chase Bank")).isAtLeast(700)
        assertThat(FuzzySearch.score("you", "YouTube")).isAtLeast(800)
    }
}

class SearchInterpreterTest {

    private val apps = listOf(
        app("WhatsApp", "com.whatsapp", com.lumen.launcher.data.AppCategory.Social),
        app("YouTube", "com.google.android.youtube", com.lumen.launcher.data.AppCategory.Entertainment),
        app("Gmail", "com.google.android.gm", com.lumen.launcher.data.AppCategory.Work),
        app("Chrome", "com.android.chrome", com.lumen.launcher.data.AppCategory.Utilities),
        app("Messages", "com.google.android.apps.messaging", com.lumen.launcher.data.AppCategory.Social)
    )

    @Test
    fun whatsappRanksAsAppFirst() {
        val hits = SearchInterpreter.interpret("WhatsApp", apps)
        val first = hits.first()
        assertThat(first).isInstanceOf(SearchHit.App::class.java)
        val appHit = first as SearchHit.App
        assertThat(appHit.app.label).isEqualTo("WhatsApp")
        assertThat(appHit.score).isAtLeast(900)
    }

    @Test
    fun openWhatsAppStillOpensWhatsApp() {
        val hits = SearchInterpreter.interpret("open WhatsApp", apps)
        val appHit = hits.filterIsInstance<SearchHit.App>().first()
        assertThat(appHit.app.label).isEqualTo("WhatsApp")
        assertThat(appHit.score).isAtLeast(900)
        assertThat(hits.filterIsInstance<SearchHit.Action>().none { it.id == "open" }).isTrue()
    }

    @Test
    fun bluetoothIsAnActionNotAnAppGuess() {
        val hits = SearchInterpreter.interpret("Turn on Bluetooth", apps)
        assertThat(hits.filterIsInstance<SearchHit.Action>().any { it.id == "bluetooth" }).isTrue()
    }

    private fun app(label: String, packageName: String, category: com.lumen.launcher.data.AppCategory) =
        com.lumen.launcher.data.AppInfo(label, packageName, "Main", 0L, category)
}

class CalculatorTest {

    @Test
    fun orderOfOperations() {
        val result = Calculator.interpret("2+5*7")
        assertThat(result!!.value).isEqualTo("37")
    }

    @Test
    fun tipOnAmount() {
        val result = Calculator.interpret("18% tip on $86")
        assertThat(result).isNotNull()
        assertThat(result!!.value).isEqualTo("15.48")
        assertThat(result.detail).contains("101.48")
    }

    @Test
    fun percentOf() {
        val result = Calculator.interpret("18% of 86")
        assertThat(result!!.value).isEqualTo("15.48")
    }
}

class VoiceMatchTest {

    @Test
    fun workDoesNotMatchWord() {
        assertThat(VoiceMatch.score("work", "Word")).isLessThan(0)
        assertThat(VoiceMatch.score("work", "Microsoft Word")).isLessThan(0)
    }

    @Test
    fun wordStillMatchesWord() {
        assertThat(VoiceMatch.score("word", "Word")).isAtLeast(980)
        assertThat(VoiceMatch.score("word", "Microsoft Word")).isAtLeast(980)
    }

    @Test
    fun outlookMatchesMicrosoftOutlook() {
        assertThat(VoiceMatch.score("outlook", "Microsoft Outlook")).isAtLeast(980)
        assertThat(VoiceMatch.score("chrome", "Google Chrome")).isAtLeast(980)
    }

    @Test
    fun anyInstalledLabelIsSpokenWithoutPerAppRules() {
        assertThat(VoiceMatch.spokenForms("PhotoLab")).containsAtLeast("photolab", "photo lab")
        assertThat(VoiceMatch.spokenForms("ChatGPT")).containsAtLeast("chatgpt", "chat gpt", "chat g p t")
        assertThat(VoiceMatch.spokenForms("FXNow")).containsAtLeast("fxnow", "fx now", "f x now")
        assertThat(VoiceMatch.spokenForms("TikTok")).containsAtLeast("tiktok", "tik tok")
    }

    @Test
    fun newAppsWinFromTheLiveCatalog() {
        val installed = listOf("Word", "WhatsApp", "PhotoLab", "FXNow")
        assertThat(VoiceMatch.best("photo lab", installed)).isEqualTo("PhotoLab")
        assertThat(VoiceMatch.best("f x now", installed)).isEqualTo("FXNow")
    }

    @Test
    fun workDoesNotOpenWordFromCatalog() {
        assertThat(VoiceMatch.best("work", listOf("Word", "WhatsApp"))).isNull()
    }

    @Test
    fun closeNamesDoNotGuess() {
        assertThat(VoiceMatch.best("note", listOf("Notes", "Notion"))).isNull()
    }

    @Test
    fun spokenFullNameMatchesAcronymLabel() {
        assertThat(VoiceMatch.best("bank of america", listOf("BOA", "WhatsApp"))).isEqualTo("BOA")
        assertThat(VoiceMatch.best("cable news network", listOf("CNN", "Chrome"))).isEqualTo("CNN")
        assertThat(VoiceMatch.best("photos", listOf("OP", "Photos"))).isEqualTo("Photos")
        assertThat(VoiceMatch.best("photos", listOf("OP"))).isNull()
    }
}
