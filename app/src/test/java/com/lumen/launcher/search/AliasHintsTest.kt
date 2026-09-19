package com.lumen.launcher.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AliasHintsTest {

    @Test
    fun boaIsLearnedForBankOfAmerica() {
        assertThat(AliasHints.shouldTrack("boa", "Bank of America")).isTrue()
        assertThat(AliasHints.aliasKey("open BOA")).isEqualTo("boa")
        assertThat(AliasHints.shouldPromote(1)).isFalse()
        assertThat(AliasHints.shouldPromote(2)).isTrue()
    }

    @Test
    fun exactLabelIsNotAnAlias() {
        assertThat(AliasHints.shouldTrack("WhatsApp", "WhatsApp")).isFalse()
        assertThat(AliasHints.shouldTrack("what", "WhatsApp")).isFalse()
    }

    @Test
    fun longSpokenNamesAreKept() {
        assertThat(AliasHints.shouldTrack("bank of america", "BOA")).isTrue()
        assertThat(AliasHints.aliasKey("bank of america").length).isAtMost(40)
    }
}
