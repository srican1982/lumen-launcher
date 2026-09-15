package com.lumen.launcher.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WakePhraseTest {

    @Test
    fun detectsHeyLumen() {
        assertThat(WakePhrase.detect("Hey Lumen")).isEqualTo(WakePhrase.Hit(""))
        assertThat(WakePhrase.detect("hey lumen,")).isEqualTo(WakePhrase.Hit(""))
        assertThat(WakePhrase.detect("Okay Lumen")).isEqualTo(WakePhrase.Hit(""))
        assertThat(WakePhrase.detect("Hi Lumen")).isEqualTo(WakePhrase.Hit(""))
        assertThat(WakePhrase.detect("hello lumen")).isEqualTo(WakePhrase.Hit(""))
    }

    @Test
    fun hearsCommonMishearings() {
        assertThat(WakePhrase.detect("hey lemon")).isEqualTo(WakePhrase.Hit(""))
        assertThat(WakePhrase.detect("hi luman")).isEqualTo(WakePhrase.Hit(""))
    }

    @Test
    fun keepsTheCommandAfterTheWake() {
        val hit = WakePhrase.detect("Hey Lumen set an alarm for 7 am")
        assertThat(hit?.remainder).isEqualTo("set an alarm for 7 am")
    }

    @Test
    fun ignoresNearbyWords() {
        assertThat(WakePhrase.detect("aluminum")).isNull()
        assertThat(WakePhrase.detect("hey human")).isNull()
        assertThat(WakePhrase.detect("lumen")).isNull()
    }
}
