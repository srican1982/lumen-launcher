package com.lumen.launcher.voice.intent

import org.junit.Assert.*
import org.junit.Test

class AiAppCandidatesTest {
    @Test fun commandPrefixesDoNotHideLateAlphabetApps() {
        val labels = (1..100).map { "A filler app $it" } + "Zoom"
        val result = AiAppCandidates.rank("Could you please open the Zoom app", labels)
        assertEquals("Zoom", result.first())
        assertEquals(80, result.size)
    }
    @Test fun spokenInitialsFindAbbreviatedBankName() {
        assertEquals("BOA", AiAppCandidates.rank("open Bank of America", listOf("Browser", "BOA")).first())
    }
}
