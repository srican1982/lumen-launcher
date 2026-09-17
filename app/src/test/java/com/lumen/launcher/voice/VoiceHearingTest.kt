package com.lumen.launcher.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceHearingTest {

    @Test
    fun repairsCommandWordsTheEngineMangles() {
        assertThat(VoiceHearing.repair("create a folder cold work"))
            .isEqualTo("create a folder called work")
        assertThat(VoiceHearing.repair("whats the whether"))
            .isEqualTo("whats the weather")
        assertThat(VoiceHearing.repair("open sitting"))
            .isEqualTo("open settings")
        assertThat(VoiceHearing.repair("hide app tables"))
            .isEqualTo("hide app labels")
        assertThat(VoiceHearing.repair("create a older called work"))
            .isEqualTo("create a folder called work")
        assertThat(VoiceHearing.repair("open draw"))
            .isEqualTo("open drawer")
        assertThat(VoiceHearing.repair("lunch outlook"))
            .isEqualTo("launch outlook")
        assertThat(VoiceHearing.expand("high app labels")).contains("hide app labels")
    }

    @Test
    fun keepsWeatherColdAndAppNames() {
        val expanded = VoiceHearing.expand("how cold is it")
        assertThat(expanded).contains("how cold is it")
        assertThat(VoiceHearing.repair("open word", listOf("Word")))
            .isEqualTo("open word")
    }

    @Test
    fun calledAndColdShareASound() {
        assertThat(VoiceHearing.phonetic("called")).isEqualTo(VoiceHearing.phonetic("cold"))
    }
}
